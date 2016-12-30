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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.services.FileOperationService.OpType;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Abstract job that resolves all resource URIs into mResolvedDocs. This provides
 * uniform error handling and reporting on resource resolution failures, as well
 * as an easy path for sub-classes to recover and continue past partial failures.
 */
public abstract class ResolvedResourcesJob extends Job {
    private static final String TAG = "ResolvedResourcesJob";

    final List<DocumentInfo> mResolvedDocs;

    ResolvedResourcesJob(Context service, Listener listener, String id, @OpType int opType,
            DocumentStack destination, UrisSupplier srcs) {
        super(service, listener, id, opType, destination, srcs);

        assert(srcs.getItemCount() > 0);

        // delay the initialization of it to setUp() because it may be IO extensive.
        mResolvedDocs = new ArrayList<>(srcs.getItemCount());

    }

    boolean setUp() {
        if (!super.setUp()) {
            return false;
        }

        int docsResolved = buildDocumentList();
        if (!isCanceled() && docsResolved < mResourceUris.getItemCount()) {
            if (docsResolved == 0) {
                Log.e(TAG, "Failed to load any documents. Aborting.");
                return false;
            } else {
                Log.e(TAG, "Failed to load some documents. Processing loaded documents only.");
            }
        }

        return true;
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
    protected int buildDocumentList() {
        final ContentResolver resolver = appContext.getContentResolver();
        Iterable<Uri> uris;
        try {
            uris = mResourceUris.getUris(appContext);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read list of target resource Uris. Cannot continue.", e);
            failureCount = this.mResourceUris.getItemCount();
            return 0;
        }

        int docsLoaded = 0;
        for (Uri uri : uris) {

            DocumentInfo doc;
            try {
                doc = DocumentInfo.fromUri(resolver, uri);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Failed to resolve content from Uri: " + uri
                        + ". Skipping to next resource.", e);
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
}
