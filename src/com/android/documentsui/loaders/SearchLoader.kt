/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.documentsui.loaders

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.text.TextUtils
import android.util.Log
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.FilteringCursorWrapper
import com.android.documentsui.base.FolderInfo
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.base.UserId
import com.android.documentsui.sorting.SortModel
import com.google.common.util.concurrent.AbstractFuture
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

/**
 * A specialization of the BaseFileLoader that searches the set of specified roots. To search
 * the roots you must provider:
 *
 *  - The current application context
 *  - A content lock for which a locking content observer is built
 *  - A list of user IDs, on whose behalf we query content provider clients.
 *  - A list of RootInfo objects representing searched roots
 *  - A query used to search for matching files.
 *  - Query options such as maximum number of results, last modified time delta, etc.
 *  - a lookup from file extension to file type
 *  - The model capable of sorting results
 *  - An executor for running searches across multiple roots in parallel
 *
 *  SearchLoader requires that either a query is not null and not empty or that QueryOptions
 *  specify a last modified time restriction. This is to prevent searching for every file
 *  across every specified root.
 */
class SearchLoader(
    context: Context,
    userIdList: List<UserId>,
    mimeTypeLookup: Lookup<String, String>,
    private val observer: LockingContentObserver,
    private val folderList: Collection<FolderInfo>,
    private val query: String?,
    private val options: QueryOptions,
    private val sortModel: SortModel,
    private val executorService: ExecutorService,
) : BaseFileLoader(context, userIdList, mimeTypeLookup) {

    /**
     * Helper class that runs query on a single user for the given parameter. This class implements
     * an abstract future so that if the task is completed, we can retrieve the cursor via the get
     * method.
     */
    inner class SearchTask(
        private val rootId: String,
        private val searchUri: Uri,
        private val queryArgs: Bundle,
        private val latch: CountDownLatch,
    ) : Closeable, Runnable, AbstractFuture<Cursor>() {
        internal var cursor: Cursor? = null
        val taskId: String get() = searchUri.toString()

        override fun close() {
            cursor = null
        }

        override fun run() {
            val queryDuration = measureTime {
                try {
                    cursor = queryLocation(rootId, searchUri, queryArgs, options.maxResults)
                    set(cursor)
                } finally {
                    latch.countDown()
                }
            }
            if (DEBUG) {
                Log.d(TAG, "Query on $searchUri took $queryDuration")
            }
        }
    }

    @Volatile
    private var mSearchTaskList: List<SearchTask> = listOf()

    // Creates a directory result object corresponding to the current parameters of the loader.
    override fun loadInBackground(): DirectoryResult? {
        val result = DirectoryResult()
        // TODO(b:378590632): If root list has one root use it to construct result.doc
        result.doc = DocumentInfo()
        result.cursor = emptyCursor()

        val countDownLatch = CountDownLatch(folderList.size)
        val rejectBeforeTimestamp = options.getRejectBeforeTimestamp()

        // Step 1: Build a list of search tasks.
        val searchTaskList =
            createSearchTaskList(rejectBeforeTimestamp, countDownLatch, folderList)
        if (DEBUG) {
            Log.d(TAG, "${searchTaskList.size} tasks have been created")
        }

        // Check if we are cancelled; if not copy the task list.
        if (isLoadInBackgroundCanceled) {
            return result
        }
        mSearchTaskList = searchTaskList

        // Step 2: Enqueue tasks and wait for them to complete or time out.
        for (task in mSearchTaskList) {
            executorService.execute(task)
        }
        if (DEBUG) {
            Log.d(TAG, "${mSearchTaskList.size} tasks have been enqueued")
        }

        // Step 3: Wait for the results.
        try {
            if (options.isQueryTimeUnlimited()) {
                if (DEBUG) {
                    Log.d(TAG, "Waiting for results with no time limit")
                }
                countDownLatch.await()
            } else {
                if (DEBUG) {
                    Log.d(TAG, "Waiting ${options.maxQueryTime!!.toMillis()}ms for results")
                }
                countDownLatch.await(
                    options.maxQueryTime!!.toMillis(),
                    TimeUnit.MILLISECONDS
                )
            }
            if (DEBUG) {
                Log.d(TAG, "Waiting for results is done")
            }
        } catch (e: InterruptedException) {
            if (DEBUG) {
                Log.d(TAG, "Failed to complete all searches within ${options.maxQueryTime}")
            }
            // TODO(b:388336095): Record a metrics indicating incomplete search.
            throw RuntimeException(e)
        }

        // Step 4: Collect cursors from done tasks.
        var allDone = true
        val cursorList = mutableListOf<Cursor>()
        for (task in mSearchTaskList) {
            if (DEBUG) {
                Log.d(TAG, "Processing task ${task.taskId}")
            }
            if (isLoadInBackgroundCanceled) {
                break
            }
            // TODO(b:388336095): Record a metric for each done and not done task.
            val cursor = task.cursor
            if (!task.isDone) {
                allDone = false
            } else if (cursor != null) {
                // TODO(b:388336095): Record a metric for null and not null cursor.
                if (DEBUG) {
                    Log.d(TAG, "Task ${task.taskId} has ${cursor.count} results")
                }
                cursorList.add(cursor)
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Search complete with ${cursorList.size} cursors collected")
        }

        // Step 5: Assign the cursor, after adding filtering and sorting, to the results.
        val cursorExtras = Bundle().apply {
            putBoolean(DocumentsContract.EXTRA_LOADING, !allDone)
        }
        val mergedCursor = toSingleCursor(cursorList).apply {
            setExtras(cursorExtras)
        }
        mergedCursor.registerContentObserver(observer)
        val filteringCursor = FilteringCursorWrapper(mergedCursor)
        filteringCursor.filterHiddenFiles(options.showHidden)
        filteringCursor.filterMimes(
            options.acceptableMimeTypes,
            if (TextUtils.isEmpty(query)) arrayOf(Document.MIME_TYPE_DIR) else null
        )
        if (rejectBeforeTimestamp > 0L) {
            filteringCursor.filterLastModified(rejectBeforeTimestamp)
        }
        result.cursor = sortModel.sortCursor(filteringCursor, mimeTypeLookup)

        // TODO(b:388336095): Record the total time it took to complete search.
        return result
    }

    private fun createContentProviderQuery(folder: FolderInfo) =
        if (TextUtils.isEmpty(query) && options.otherQueryArgs.isEmpty) {
            // NOTE: recent document URI does not respect query-arg-mime-types restrictions. Thus
            // we only create the recents URI if both the query and other args are empty.
            DocumentsContract.buildRecentDocumentsUri(
                folder.authority,
                folder.folderId
            )
        } else {
            DocumentsContract.buildSearchDocumentsUri(
                folder.authority,
                folder.folderId,
                query,
            )
        }

    private fun createQueryArgs(
        rootSupportsSearchResultLimiting: Boolean,
        rejectBeforeTimestamp: Long
    ): Bundle {
        val queryArgs = Bundle()
        sortModel.addQuerySortArgs(queryArgs)
        if (rejectBeforeTimestamp > 0L) {
            queryArgs.putLong(
                DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
                rejectBeforeTimestamp
            )
        }
        if (!TextUtils.isEmpty(query)) {
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, query)
        }
        if (rootSupportsSearchResultLimiting && options.maxResultsPerRoot > ALL_RESULTS) {
            queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, options.maxResultsPerRoot)
        }
        queryArgs.putAll(options.otherQueryArgs)
        return queryArgs
    }

    /**
     * Helper function that creates a list of search tasks for the given countdown latch.
     */
    private fun createSearchTaskList(
        rejectBeforeTimestamp: Long,
        countDownLatch: CountDownLatch,
        folderList: Collection<FolderInfo>
    ): List<SearchTask> {
        val searchTaskList = mutableListOf<SearchTask>()
        for (folder in folderList) {
            if (isLoadInBackgroundCanceled) {
                break
            }
            val rootSearchUri = createContentProviderQuery(folder)
            // TODO(b:385789236): Correctly pass sort order information.
            val queryArgs =
                createQueryArgs(folder.supportsSearchResultLimiting, rejectBeforeTimestamp)
            sortModel.addQuerySortArgs(queryArgs)
            if (DEBUG) {
                Log.d(TAG, "Query $rootSearchUri and queryArgs $queryArgs")
            }
            val task = SearchTask(
                folder.folderId,
                rootSearchUri,
                queryArgs,
                countDownLatch
            )
            searchTaskList.add(task)
        }
        return searchTaskList
    }

    override fun onReset() {
        for (task in mSearchTaskList) {
            task.close()
        }
        if (DEBUG) {
            Log.d(TAG, "Resetting search loader; search task list emptied.")
        }
        super.onReset()
    }
}
