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

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.database.MergeCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.RemoteException
import android.provider.DocumentsContract.Document
import android.util.Log
import androidx.loader.content.AsyncTaskLoader
import com.android.documentsui.DirectoryResult
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.base.UserId
import com.android.documentsui.roots.RootCursorWrapper

const val TAG = "SearchV2"

val FILE_ENTRY_COLUMNS = arrayOf(
    Document.COLUMN_DOCUMENT_ID,
    Document.COLUMN_MIME_TYPE,
    Document.COLUMN_DISPLAY_NAME,
    Document.COLUMN_LAST_MODIFIED,
    Document.COLUMN_FLAGS,
    Document.COLUMN_SUMMARY,
    Document.COLUMN_SIZE,
    Document.COLUMN_ICON,
)

fun emptyCursor(): Cursor {
    return MatrixCursor(FILE_ENTRY_COLUMNS)
}

/**
 * Helper function that returns a single, non-null cursor constructed from the given list of
 * cursors.
 */
fun toSingleCursor(cursorList: List<Cursor>): Cursor {
    if (cursorList.isEmpty()) {
        return emptyCursor()
    }
    if (cursorList.size == 1) {
        return cursorList[0]
    }
    return MergeCursor(cursorList.toTypedArray())
}

/**
 * The base class for search and directory loaders. This class implements common functionality
 * shared by these loaders. The extending classes should implement loadInBackground, which
 * should call the queryLocation method.
 */
abstract class BaseFileLoader(
    context: Context,
    private val userIdList: List<UserId>,
    protected val mimeTypeLookup: Lookup<String, String>,
) : AsyncTaskLoader<DirectoryResult>(context) {

    private var signal: CancellationSignal? = null
    private var storedResult: DirectoryResult? = null

    override fun cancelLoadInBackground() {
        if (DEBUG) {
            Log.d(TAG, "${this::class.simpleName}.cancelLoadInBackground")
        }
        super.cancelLoadInBackground()

        synchronized(this) {
            signal?.cancel()
        }
    }

    override fun deliverResult(result: DirectoryResult?) {
        if (DEBUG) {
            Log.d(TAG, "${this::class.simpleName}.deliverResult")
        }
        if (isReset) {
            closeResult(result)
            return
        }
        val oldResult: DirectoryResult? = storedResult
        storedResult = result

        if (isStarted) {
            super.deliverResult(result)
        }

        if (oldResult != null && oldResult !== result) {
            closeResult(oldResult)
        }
    }

    override fun onStartLoading() {
        if (DEBUG) {
            Log.d(TAG, "${this::class.simpleName}.onStartLoading")
        }
        val isCursorStale: Boolean = checkIfCursorStale(storedResult)
        if (storedResult != null && !isCursorStale) {
            deliverResult(storedResult)
        }
        if (takeContentChanged() || storedResult == null || isCursorStale) {
            forceLoad()
        }
    }

    override fun onStopLoading() {
        if (DEBUG) {
            Log.d(TAG, "${this::class.simpleName}.onStopLoading")
        }
        cancelLoad()
    }

    override fun onCanceled(result: DirectoryResult?) {
        if (DEBUG) {
            Log.d(TAG, "${this::class.simpleName}.onCanceled")
        }
        closeResult(result)
    }

    override fun onReset() {
        if (DEBUG) {
            Log.d(TAG, "${this::class.simpleName}.onReset")
        }
        super.onReset()

        // Ensure the loader is stopped
        onStopLoading()

        closeResult(storedResult)
        storedResult = null
    }

    /**
     * Quietly closes the result cursor, if results are still available.
     */
    fun closeResult(result: DirectoryResult?) {
        try {
            result?.close()
        } catch (e: Exception) {
            if (DEBUG) {
                Log.d(TAG, "Failed to close result", e)
            }
        }
    }

    private fun checkIfCursorStale(result: DirectoryResult?): Boolean {
        if (result == null) {
            return true
        }
        val cursor = result.cursor ?: return true
        if (cursor.isClosed) {
            return true
        }
        if (DEBUG) {
            Log.d(TAG, "Long check of cursor staleness")
        }
        val count = cursor.count
        if (!cursor.moveToPosition(-1)) {
            return true
        }
        for (i in 1..count) {
            if (!cursor.moveToNext()) {
                return true
            }
        }
        return false
    }

    /**
     * A function that, for the specified location rooted in the root with the given rootId
     * attempts to obtain a non-null cursor from the content provider client obtained for the
     * given locationUri. It returns the first non-null cursor, if one can be found, or null,
     * if it fails to query the given location for all known users.
     */
    fun queryLocation(
        rootId: String,
        locationUri: Uri,
        queryArgs: Bundle?,
        maxResults: Int,
    ): Cursor? {
        val authority = locationUri.authority ?: return null
        for (userId in userIdList) {
            if (DEBUG) {
                Log.d(TAG, "BaseFileLoader.queryLocation for $userId at $locationUri")
            }
            val resolver = userId.getContentResolver(context)
            try {
                resolver.acquireUnstableContentProviderClient(
                    authority
                ).use { client ->
                    if (client == null) {
                        return null
                    }
                    try {
                        val cursor =
                            client.query(locationUri, null, queryArgs, signal) ?: return null
                        return RootCursorWrapper(userId, authority, rootId, cursor, maxResults)
                    } catch (e: RemoteException) {
                        if (DEBUG) {
                            Log.d(TAG, "Failed to get cursor for $locationUri", e)
                        }
                    }
                }
            } catch (e: Exception) {
                if (DEBUG) {
                    Log.d(TAG, "Failed to get a content provider client for $locationUri", e)
                }
            }
        }

        return null
    }
}
