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

import static android.os.SystemClock.uptimeMillis;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.FlagUtils.isVisualSignalsFlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.ContentResolver;
import android.content.Context;
import android.icu.text.MessageFormat;
import android.net.Uri;
import android.os.RemoteException;
import android.text.BidiFormatter;
import android.util.Log;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.services.FileOperationService.OpType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Abstract job that resolves all resource URIs into mResolvedDocs. This provides
 * uniform error handling and reporting on resource resolution failures, as well
 * as an easy path for sub-classes to recover and continue past partial failures.
 */
public abstract class ResolvedResourcesJob extends Job {
    private static final String TAG = "ResolvedResourcesJob";

    // Used in logs.
    protected final long mStartTime = uptimeMillis();

    final List<DocumentInfo> mResolvedDocs;
    final List<Uri> mAcquiredArchivedUris = new ArrayList<>();

    ResolvedResourcesJob(Context service, Listener listener, String id, @OpType int opType,
            DocumentStack destination, UrisSupplier srcs, Features features) {
        super(service, listener, id, opType, destination, srcs, features);

        assert(srcs.getItemCount() > 0);

        // Delay the initialization of it to setUp() because it may be IO extensive.
        mResolvedDocs = new ArrayList<>(srcs.getItemCount());

        if (isVisualSignalsFlagEnabled() && srcs.getItemCount() == 1) {
            // Prebuild the document list so we can get the filename for a single file progress
            // message. With a single file only, this should not be IO intensive.
            buildDocumentList();
        }
    }

    boolean setUp() {
        if (!super.setUp()) {
            return false;
        }

        // Acquire all source archived documents, so they are not gone while copying from.
        try {
            Iterable<Uri> uris = mResourceUris.getUris(appContext);
            for (Uri uri : uris) {
                try {
                    if (ArchivesProvider.AUTHORITY.equals(uri.getAuthority())) {
                        ArchivesProvider.acquireArchive(getClient(uri), uri);
                        mAcquiredArchivedUris.add(uri);
                    }
                } catch (RemoteException e) {
                    Log.e(TAG, "Cannot acquire an archive", e);
                    return false;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot read list of target resource URIs", e);
            return false;
        }

        int docsResolved = refreshDocumentList();
        if (!isCanceled() && docsResolved < mResourceUris.getItemCount()) {
            if (docsResolved == 0) {
                Log.e(TAG, "Cannot load any documents. Aborting.");
                return false;
            } else {
                Log.e(TAG, "Cannot load some documents");
            }
        }

        return true;
    }

    @Override
    void finish() {
        // Release all archived documents.
        for (Uri uri : mAcquiredArchivedUris) {
            try {
                ArchivesProvider.releaseArchive(getClient(uri), uri);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot release an archived document", e);
            }
        }

        if (DEBUG) {
            Log.d(TAG, String.format("%s %s finished after %d ms", getClass().getSimpleName(), id,
                    uptimeMillis() - mStartTime));
        }
    }

    /**
     * Allows sub-classes to exclude files from processing.
     * By default all files are eligible.
     */
    boolean isEligibleDoc(DocumentInfo doc, RootInfo root) {
        return true;
    }

    /**
     * @return number of docs successfully loaded.
     */
    private int buildDocumentList() {
        final ContentResolver resolver = appContext.getContentResolver();
        Iterable<Uri> uris;
        try {
            uris = mResourceUris.getUris(appContext);
        } catch (IOException e) {
            Log.e(TAG, "Cannot read list of target resource URIs", e);
            failureCount = this.mResourceUris.getItemCount();
            return 0;
        }

        int docsLoaded = 0;
        for (Uri uri : uris) {

            DocumentInfo doc;
            try {
                doc = DocumentInfo.fromUri(resolver, uri, UserId.DEFAULT_USER);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Cannot resolve content from URI " + uri, e);
                onResolveFailed(uri);
                continue;
            }

            if (isEligibleDoc(doc, stack.getRoot())) {
                mResolvedDocs.add(doc);
            } else {
                onFileFailed(doc);
            }
            docsLoaded++;

            if (isCanceled()) {
                break;
            }
        }

        return docsLoaded;
    }

    private int refreshDocumentList() {
        // We've never built the list in the first place.
        if (mResolvedDocs.isEmpty() && failureCount == 0) {
            return buildDocumentList();
        }

        final ContentResolver resolver = appContext.getContentResolver();
        mResolvedDocs.removeIf(doc -> {
            try {
                doc.updateSelf(resolver, UserId.DEFAULT_USER);
            } catch (FileNotFoundException e) {
                onFileFailed(doc);
                return true;
            }
            return false;
        });
        return mResolvedDocs.size();
    }

    protected String getProgressMessage(int stringId, Map<String, Object> formatArgs) {
        formatArgs.put("count", mResourceUris.getItemCount());
        if (mResourceUris.getItemCount() == 1 && mResolvedDocs.size() == 1) {
            formatArgs.put("filename",
                    BidiFormatter.getInstance().unicodeWrap(mResolvedDocs.get(0).displayName));
        }
        return (new MessageFormat(service.getString(getRes(stringId)), Locale.getDefault()))
                .format(formatArgs);
    }

    protected String getProgressMessage(int stringId) {
        return getProgressMessage(stringId, new HashMap<>());
    }
}
