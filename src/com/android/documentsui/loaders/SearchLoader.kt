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
import android.os.Trace
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.text.TextUtils
import android.util.Log
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.FilteringCursorWrapper
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.sorting.SortModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.time.measureTime

/**
 * A wrapper around the cursor. We use it to distinguish between pending tasks (null query result)
 * and completed tasks. Completed tasks may have cursor null, if this is what a given content
 * provider returns.
 */
private data class QueryResult(var cursor: Cursor? = null)

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
    private val rootInfoList: Collection<RootInfo>,
    mimeTypeLookup: Lookup<String, String>,
    private val observer: LockingContentObserver,
    private val query: String?,
    private val options: QueryOptions,
    private val sortModel: SortModel,
    private val executorService: ExecutorService,
) : BaseFileLoader(context, mimeTypeLookup) {

    /**
     * Helper class that runs query on a single user for the given parameter. This class implements
     * an abstract future so that if the task is completed, we can retrieve the cursor via the get
     * method.
     */
    inner class SearchTask(
        private val rootInfo: RootInfo,
        private val searchUri: Uri,
        private val queryArgs: Bundle,
        internal val index: Int,
        private val latch: CountDownLatch,
    ) : Runnable {
        internal var cursor: Cursor? = null
        internal val taskId: String get() = searchUri.toString()

        override fun run() {
            val queryDuration = measureTime {
                try {
                    cursor = queryLocation(rootInfo, searchUri, queryArgs, options.maxResults)
                    // Content observer must be set only once. This is why we are setting it
                    // on each retrieved cursor, rather than on the merged cursor.
                    // TODO(b:388130971): Content change should only force requery (#comment3).
                    cursor?.registerContentObserver(observer)
                } catch (e: Exception) {
                    if (DEBUG) {
                        Log.d(TAG, "Failed to get cursor for $searchUri", e)
                    }
                } finally {
                    onTaskCompleted(this)
                    latch.countDown()
                }
            }
            if (DEBUG) {
                Log.d(TAG, "Query on $searchUri took $queryDuration")
            }
        }
    }

    private val searchTaskList = mutableListOf<SearchTask>()

    // The results cursors, set by search task as they become available.
    private val queryResults = Array<QueryResult?>(rootInfoList.size) { null }

    // Indicates if the first pass for results is done. This is used to prevent tasks
    // that completed before the deadline from forcing content change calls.
    private var firstPassDone = false

    // A latch that counts the number of tasks done. Used to check if all tasks are completed.
    private var countDownLatch = CountDownLatch(rootInfoList.size)

    // Creates a directory result object corresponding to the current parameters of the loader.
    override fun loadInBackground(): DirectoryResult? {
        try {
            Trace.beginSection("documentsui.searchv2.SearchLoader#loadInBackground")
            return loadInBackgroundTraced()
        } finally {
            Trace.endSection()
        }
    }

    /**
     * Forces content fresh if the first pass has been done. This method is called by each search
     * tasks  that complete. If the call is made after the first pass, it triggers onContentChanged
     * call, which results in re-run for loadInBackground. This re-run tries to create not yet
     * created search tasks, and runs them on the provided executor. For already running, but not
     * yet completed tasks the code just waits for them to be completed.
     */
    private fun maybeRefreshContent(taskId: String) {
        if (firstPassDone) {
            if (DEBUG) {
                Log.d(TAG, "Forcing refresh on cursor $taskId completed")
            }
            onContentChanged()
        }
    }

    /**
     * Runs search for the first time. The code creates a new list of search tasks, schedules
     * them to be run on the executor and gives tasks up to maxQueryTime (if set) to complete
     * the first run.
     */
    @Throws(InterruptedException::class)
    private fun firstPassRun(rejectBeforeTimestamp: Long) {
        // Step 1: Create a list of new search tasks.
        createSearchTaskList(rejectBeforeTimestamp, countDownLatch)
        if (DEBUG) {
            Log.d(TAG, "First run created ${searchTaskList.size} tasks")
        }

        // Check if we are cancelled; if not copy the task list.
        if (isLoadInBackgroundCanceled) {
            return
        }

        // Step 2: Enqueue tasks and wait for them to complete or time out.
        for (task in searchTaskList) {
            executorService.execute(task)
        }
        if (DEBUG) {
            Log.d(TAG, "Started ${searchTaskList.size} search tasks")
        }

        // Step 3: Wait for the results.
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
    }

    /**
     * The loadInBackground code run within a trace.
     */
    private fun loadInBackgroundTraced(): DirectoryResult? {
        val rejectBeforeTimestamp = options.getRejectBeforeTimestamp()
        val result = DirectoryResult()
        // TODO(b:378590632): If root list has one root use it to construct result.doc
        result.doc = DocumentInfo()
        result.cursor = emptyCursor()

        if (!firstPassDone) {
            try {
                // Create a new task list and schedule it with the executor.
                firstPassRun(rejectBeforeTimestamp)
            } catch (e: InterruptedException) {
                if (DEBUG) {
                    Log.d(TAG, "Interrupted during first pass ${options.maxQueryTime}")
                }
                // TODO(b:388336095): Record a metrics indicating incomplete search.
                throw RuntimeException(e)
            } finally {
                firstPassDone = true
            }
        }

        // Collect cursors from done tasks.
        var allDone = true
        val cursorList = mutableListOf<Cursor>()
        for (data in queryResults) {
            if (isLoadInBackgroundCanceled) {
                break
            }
            // TODO(b:388336095): Record a metric for each done and not done task.
            if (data == null) {
                allDone = false
            } else {
                // TODO(b:388336095): Record a metric for null and not null cursor.
                val cursor = data.cursor
                if (cursor != null) {
                    cursorList.add(cursor)
                }
            }
        }
        if (DEBUG) {
            Log.d(TAG, "Search complete with ${cursorList.size} cursors collected")
        }

        // Assign the cursor, after adding filtering and sorting, to the results.
        val cursorExtras = Bundle().apply {
            putBoolean(DocumentsContract.EXTRA_LOADING, !allDone)
        }
        val mergedCursor = toSingleCursor(cursorList).apply {
            setExtras(cursorExtras)
        }
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

    /**
     * Notifies this loader that a task running on an executor thread has been completed.
     * Search tasks update different queryResults, so no locks are used in this method.
     * For every completed task we check if we need to refresh the content.
     */
    private fun onTaskCompleted(searchTask: SearchTask) {
        queryResults[searchTask.index] = QueryResult(searchTask.cursor)
        maybeRefreshContent(searchTask.taskId)
    }

    private fun createContentProviderQuery(rootInfo: RootInfo) =
        if (TextUtils.isEmpty(query) && options.otherQueryArgs.isEmpty) {
            // NOTE: recent document URI does not respect query-arg-mime-types restrictions. Thus
            // we only create the recents URI if both the query and other args are empty.
            DocumentsContract.buildRecentDocumentsUri(
                rootInfo.authority,
                rootInfo.rootId
            )
        } else {
            DocumentsContract.buildSearchDocumentsUri(
                rootInfo.authority,
                rootInfo.rootId,
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
     * Helper function that sets the list of search tasks for the given countdown latch.
     */
    private fun createSearchTaskList(
        rejectBeforeTimestamp: Long,
        countDownLatch: CountDownLatch,
    ) {
        searchTaskList.clear()
        for ((index, rootInfo) in rootInfoList.withIndex()) {
            if (isLoadInBackgroundCanceled) {
                break
            }
            // Create a task that will set the cursor, once query call completes.
            val rootSearchUri = createContentProviderQuery(rootInfo)
            val queryArgs =
                createQueryArgs(rootInfo.supportsSearchResultLimit(), rejectBeforeTimestamp)
            sortModel.addQuerySortArgs(queryArgs)
            if (DEBUG) {
                Log.d(TAG, "Query $rootSearchUri and queryArgs $queryArgs")
            }
            searchTaskList.add(
                SearchTask(
                    rootInfo,
                    rootSearchUri,
                    queryArgs,
                    index,
                    countDownLatch
                )
            )
        }
    }

    override fun onReset() {
        for (data in queryResults) {
            val cursor = data?.cursor
            if (cursor != null) {
                cursor.close()
                cursor.unregisterContentObserver(observer)
            }
        }
        queryResults.fill(null)
        searchTaskList.clear()
        if (DEBUG) {
            Log.d(TAG, "Resetting search loader; search task list emptied.")
        }
        super.onReset()
    }
}
