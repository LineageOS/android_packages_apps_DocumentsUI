/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui;

import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;

import java.io.FileNotFoundException;

/**
 * Provides synchronous access to {@link DocumentInfo} instances given some identifying information.
 */
public interface DocumentsAccess {

    @Nullable DocumentInfo getRootDocument(RootInfo root);
    @Nullable DocumentInfo getRootDocument(Uri uri);
    @Nullable DocumentInfo getDocument(Uri uri);
    @Nullable DocumentInfo getArchiveDocument(Uri uri);

    public static DocumentsAccess create(Context context) {
        return new RuntimeDocumentAccess(context);
    }

    public final class RuntimeDocumentAccess implements DocumentsAccess {

        private static final String TAG = "DocumentAccess";

        private final Context mContext;

        private RuntimeDocumentAccess(Context context) {
            mContext = context;
        }

        @Override
        public @Nullable DocumentInfo getRootDocument(RootInfo root) {
            return getRootDocument(
                    DocumentsContract.buildDocumentUri(root.authority, root.documentId));
        }

        @Override
        public @Nullable DocumentInfo getRootDocument(Uri uri) {
            try {
                return DocumentInfo.fromUri(mContext.getContentResolver(), uri);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Failed to find root", e);
                return null;
            }
        }

        @Override
        public DocumentInfo getDocument(Uri uri) {
            try {
                return DocumentInfo.fromUri(mContext.getContentResolver(), uri);
            } catch (FileNotFoundException e) {
                Log.w(TAG, "Couldn't create DocumentInfo for uri: " + uri);
            }

            return null;
        }

        @Override
        public DocumentInfo getArchiveDocument(Uri uri) {
            return getDocument(ArchivesProvider.buildUriForArchive(uri));
        }
    }
}
