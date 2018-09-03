/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.services;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.services.FileOperationService.MESSAGE_FINISH;
import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;

import android.app.Notification;
import android.app.Notification.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.clipping.UrisSupplier;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import javax.annotation.Nullable;

public final class DeleteJob extends ResolvedResourcesJob {

    private static final String TAG = "DeleteJob";

    public final static String KEY_FAILED_URIS = "deletion_failed_uris";

    private final Uri mParentUri;

    private volatile int mDocsProcessed = 0;

    private final Messenger mMessenger;

    private final ArrayList<Uri> mDeletionFailedUris = new ArrayList<>();

    /**
     * Moves files to a destination identified by {@code destination}.
     * Performs most work by delegating to CopyJob, then deleting
     * a file after it has been copied.
     *
     * @see @link {@link Job} constructor for most param descriptions.
     */
    DeleteJob(Context service, Listener listener, String id, DocumentStack stack,
            UrisSupplier srcs, Messenger messenger, @Nullable Uri srcParent, Features features) {
        super(service, listener, id, OPERATION_DELETE, stack, srcs, features);
        mParentUri = srcParent;
        mMessenger = messenger;
        initDeletionFailedUrisList();
    }

    private void initDeletionFailedUrisList() {
        Iterable<Uri> uris;
        try {
            uris = mResourceUris.getUris(appContext);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read list of target resource Uris.", e);
            failureCount = this.mResourceUris.getItemCount();
            return;
        }
        for (Uri uri : uris) {
            mDeletionFailedUris.add(uri);
        }
    }

    @Override
    Builder createProgressBuilder() {
        return super.createProgressBuilder(
                service.getString(R.string.delete_notification_title),
                R.drawable.ic_menu_delete,
                service.getString(android.R.string.cancel),
                R.drawable.ic_cab_cancel);
    }

    @Override
    public Notification getSetupNotification() {
        return getSetupNotification(service.getString(R.string.delete_preparing));
    }

    @Override
    public Notification getProgressNotification() {
        mProgressBuilder.setProgress(mResourceUris.getItemCount(), mDocsProcessed, false);
        String format = service.getString(R.string.delete_progress);
        mProgressBuilder.setSubText(
                String.format(format, mDocsProcessed, mResourceUris.getItemCount()));

        mProgressBuilder.setContentText(null);

        return mProgressBuilder.build();
    }

    @Override
    Notification getFailureNotification() {
        return getFailureNotification(
                R.plurals.delete_error_notification_title, R.drawable.ic_menu_delete);
    }

    @Override
    Notification getWarningNotification() {
        throw new UnsupportedOperationException();
    }

    @Override
    void start() {
        ContentResolver resolver = appContext.getContentResolver();

        DocumentInfo parentDoc;
        try {
            parentDoc = mParentUri != null
                ? DocumentInfo.fromUri(resolver, mParentUri)
                : null;
        } catch (FileNotFoundException e) {
          Log.e(TAG, "Failed to resolve parent from Uri: " + mParentUri + ". Cannot continue.", e);
          failureCount += this.mResourceUris.getItemCount();
          return;
        }

        for (DocumentInfo doc : mResolvedDocs) {
            if (DEBUG) Log.d(TAG, "Deleting document @ " + doc.derivedUri);
            try {
                deleteDocument(doc, parentDoc);
                mDeletionFailedUris.remove(doc.derivedUri);
            } catch (ResourceException e) {
                Metrics.logFileOperationFailure(
                        appContext, Metrics.SUBFILEOP_DELETE_DOCUMENT, doc.derivedUri);
                Log.e(TAG, "Failed to delete document @ " + doc.derivedUri, e);
                onFileFailed(doc);
            }

            mDocsProcessed++;
            if (isCanceled()) {
                return;
            }
        }

        Metrics.logFileOperation(service, operationType, mResolvedDocs, null);
    }

    @Override
    void finish() {
        super.finish();
        try {
            Message message = Message.obtain();
            message.what = MESSAGE_FINISH;
            // If the size of mDeletionFailedUris is 0, it means either 1). all deletions succeeded
            // or 2). reading all uris from mResourceUris failed. For case 2). We also need to check
            // the failureCount to get the correct count.
            message.arg1 = mDeletionFailedUris.size() == 0
                    ? (failureCount == mResourceUris.getItemCount() ? failureCount : 0)
                    : mDeletionFailedUris.size();
            if (message.arg1 > 0 && message.arg1 < mResourceUris.getItemCount()) {
                Bundle b = new Bundle();
                b.putParcelableArrayList(KEY_FAILED_URIS, mDeletionFailedUris);
                message.setData(b);
            }
            mMessenger.send(message);
        } catch (RemoteException e) {
            // Ignore. Most likely the frontend was killed.
        }
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("DeleteJob")
                .append("{")
                .append("id=" + id)
                .append(", uris=" + mResourceUris)
                .append(", docs=" + mResolvedDocs)
                .append(", srcParent=" + mParentUri)
                .append(", location=" + stack)
                .append("}")
                .toString();
    }
}
