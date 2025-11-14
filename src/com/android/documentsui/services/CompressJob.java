/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.content.ContentResolver.wrap;

import static com.android.documentsui.services.FileOperationService.OPERATION_MOVE;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Notification;
import android.app.Notification.Builder;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.R;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.UrisSupplier;

import java.io.FileNotFoundException;

// TODO: Stop extending CopyJob.
final class CompressJob extends CopyJob {

    private static final String TAG = "CompressJob";
    private static final String NEW_ARCHIVE_EXTENSION = ".zip";

    private Uri mArchiveUri;

    /**
     * Moves files to a destination identified by {@code destination}.
     * Performs most work by delegating to CopyJob, then deleting
     * a file after it has been copied.
     *
     * @see @link {@link Job} constructor for most param descriptions.
     */
    CompressJob(Context service, Listener listener, String id, DocumentStack destination,
            UrisSupplier srcs, Messenger messenger, Features features) {
        super(service, listener, id, OPERATION_MOVE, destination, srcs, messenger, features);
    }

    @Override
    Builder createProgressBuilder() {
        return super.createProgressBuilder(
                service.getString(getRes(R.string.compress_notification_title)),
                getRes(R.drawable.ic_menu_compress),
                service.getString(android.R.string.cancel),
                getRes(R.drawable.ic_cab_cancel));
    }

    @Override
    public Notification getSetupNotification() {
        return getSetupNotification(service.getString(getRes(R.string.compress_preparing)));
    }

    @Override
    public Notification getProgressNotification() {
        return getProgressNotification(getRes(R.string.copy_remaining));
    }

    @Override
    public Notification getFailureNotification() {
        return getFailureNotification(
                getRes(R.plurals.compress_error_notification_title),
                getRes(R.drawable.ic_menu_compress));
    }

    @Override
    protected String getProgressMessage() {
        return getProgressMessage(R.string.compress_in_progress);
    }

    @Override
    public boolean setUp() {
        if (!super.setUp()) {
            return false;
        }

        final ContentResolver resolver = appContext.getContentResolver();

        // TODO: Move this to DocumentsProvider.

        String displayName;
        if (mResolvedDocs.size() == 1) {
            displayName = mResolvedDocs.get(0).displayName + NEW_ARCHIVE_EXTENSION;
        } else {
            displayName =
                    service.getString(
                            getRes(R.string.new_archive_file_name), NEW_ARCHIVE_EXTENSION);
        }

        try {
            mArchiveUri = DocumentsContract.createDocument(
                    resolver, mDstInfo.derivedUri, "application/zip", displayName);
        } catch (Exception e) {
            mArchiveUri = null;
        }

        try {
            mDstInfo = DocumentInfo.fromUri(resolver, ArchivesProvider.buildUriForArchive(
                    mArchiveUri, ParcelFileDescriptor.MODE_WRITE_ONLY), UserId.DEFAULT_USER);
            ArchivesProvider.acquireArchive(getClient(mDstInfo), mDstInfo.derivedUri);
        } catch (FileNotFoundException e) {
            Log.e(TAG, "Cannot create document info", e);
            failureCount = mResourceUris.getItemCount();
            return false;
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot acquire archive", e);
            failureCount = mResourceUris.getItemCount();
            return false;
        }

        return true;
    }

    @Override
    void finish() {
        try {
            ArchivesProvider.releaseArchive(getClient(mDstInfo), mDstInfo.derivedUri);
        } catch (RemoteException e) {
            Log.e(TAG, "Cannot release archive", e);
        }

        // Remove the archive file in case of an error.
        try {
            if (!isFinished() || isCanceled()) {
                DocumentsContract.deleteDocument(wrap(getClient(mArchiveUri)), mArchiveUri);
            }
        } catch (RemoteException | FileNotFoundException e) {
            Log.w(TAG, "Cannot clean up after compress error: " + mDstInfo.toString(), e);
        }

        super.finish();
    }

    /**
     * {@inheritDoc}
     *
     * Only check space for moves across authorities. For now we don't know if the doc in
     * {@link #mSrcs} is in the same root of destination, and if it's optimized move in the same
     * root it should succeed regardless of free space, but it's for sure a failure if there is no
     * enough free space if docs are moved from another authority.
     */
    @Override
    boolean checkSpace() {
        // We're unable to say how much space the archive will take, so assume
        // it will fit.
        return true;
    }

    void processDocument(DocumentInfo src, DocumentInfo dest) throws ResourceException {
        byteCopyDocument(src, dest);
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("CompressJob")
                .append("{")
                .append("id=" + id)
                .append(", uris=" + mResourceUris)
                .append(", docs=" + mResolvedDocs)
                .append(", destination=" + stack)
                .append("}")
                .toString();
    }
}
