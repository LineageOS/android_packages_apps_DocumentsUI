/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui.services

import android.app.Notification
import android.content.ContentResolver
import android.content.Context
import android.os.DeadObjectException
import android.provider.DocumentsContract
import android.util.Log
import com.android.documentsui.MetricConsts
import com.android.documentsui.Metrics
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.Features
import com.android.documentsui.base.SharedMinimal
import com.android.documentsui.clipping.UrisSupplier
import kotlin.concurrent.Volatile

class TrashJob(
    service: Context?,
    listener: Listener?,
    id: String?,
    stack: DocumentStack?,
    srcs: UrisSupplier?,
    features: Features?
) : ResolvedResourcesJob(
    service,
    listener,
    id,
    FileOperationService.OPERATION_TRASH,
    stack,
    srcs,
    features
) {
    @Volatile
    private var mDocsProcessed = 0

    override fun createProgressBuilder(): Notification.Builder {
        return super.createProgressBuilder(
            service.getString(R.string.move_to_trash_notification_title),
            R.drawable.ic_menu_delete,
            service.getString(android.R.string.cancel),
            R.drawable.ic_cab_cancel
        )
    }

    override fun getSetupNotification(): Notification {
        return getSetupNotification(service.getString(R.string.move_to_trash_preparing))
    }

    override fun getProgressNotification(): Notification {
        mProgressBuilder.setProgress(mResourceUris.itemCount, mDocsProcessed, false)
        val format = service.getString(R.string.move_to_trash_progress)
        mProgressBuilder.setSubText(
            String.format(format, mDocsProcessed, mResourceUris.itemCount)
        )

        mProgressBuilder.setContentText(null)

        return mProgressBuilder.build()
    }

    override fun getFailureNotification(): Notification {
        return getFailureNotification(
            getFailureContentTitle(R.string.move_to_trash_error_notification_title),
            R.drawable.ic_menu_delete
        )
    }

    override fun getWarningNotification(): Notification {
        throw UnsupportedOperationException()
    }

    public override fun getJobProgress(): JobProgress {
        return JobProgress(
            id,
            operationType,
            state,
            getProgressMessage(R.string.trash_in_progress),
            hasFailures()
        )
    }

    override fun start() {
        for (doc in mResolvedDocs) {
            if (SharedMinimal.DEBUG) {
                Log.d(TAG, "Trashing document @ " + doc.derivedUri)
            }
            try {
                trashDocument(doc)
            } catch (e: ResourceException) {
                Metrics.logFileOperationFailure(
                    appContext,
                    MetricConsts.SUBFILEOP_TRASH_DOCUMENT,
                    doc.derivedUri
                )
                Log.e(TAG, "Failed to trash document @ " + doc.derivedUri, e)
                onFileFailed(doc)
            }

            mDocsProcessed++
            if (isCanceled) {
                return
            }
        }
    }

    override fun toString(): String {
        return StringBuilder()
            .append("TrashJob")
            .append("{")
            .append("id=$id")
            .append(", uris=$mResourceUris")
            .append(", docs=$mResolvedDocs")
            .append(", location=$stack")
            .append("}")
            .toString()
    }

    private fun trashDocument(doc: DocumentInfo) {
        try {
            DocumentsContract.trashDocument(ContentResolver.wrap(getClient(doc)), doc.derivedUri)
        } catch (e: Exception) {
            if (e is DeadObjectException) {
                releaseClient(doc)
            }
            throw ResourceException(
                "Failed to trash file %s due to an exception.",
                doc.derivedUri,
                e
            )
        }
    }

    companion object {
        private const val TAG = "TrashJob"
    }
}
