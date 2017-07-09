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
package com.android.documentsui.inspector;

import static com.android.internal.util.Preconditions.checkArgument;

import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Context;
import android.content.CursorLoader;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.Loader;
import com.android.internal.util.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Asynchronously loads a document's metadata into a DocumentInfo object.
 */
public class DocumentLoader implements Loader {

    private final Context mContext;
    private final LoaderManager mLoader;
    private List<Integer> loaderIds;
    private Callbacks mCallbacks;

    public DocumentLoader(Context context, LoaderManager loader) {
        checkArgument(context != null);
        checkArgument(loader != null);
        mContext = context;
        mLoader = loader;
        loaderIds = new ArrayList<>();
    }

    private int getNextLoaderId() {
        int id = 0;
        while(mLoader.getLoader(id) != null) {
            id++;
            checkArgument(id <= Integer.MAX_VALUE);
        }
        return id;
    }

    /**
     * @param uri is a Content Uri.
     */
    @Override
    public void load(Uri uri, Consumer<DocumentInfo> callback) {
        //Check that we have correct Uri type and that the loader is not already created.
        checkArgument(uri.getScheme().equals("content"));

        //get a new loader id.
        int loadId = getNextLoaderId();
        checkArgument(mLoader.getLoader(loadId) == null);
        loaderIds.add(loadId);
        mCallbacks = new Callbacks(mContext, uri, callback);
        mLoader.restartLoader(loadId, null, mCallbacks);
    }

    @Override
    public void reset() {
        for (Integer id : loaderIds) {
            mLoader.destroyLoader(id);
        }
        loaderIds.clear();
        if (mCallbacks.getObserver() != null) {
            mContext.getContentResolver().unregisterContentObserver(mCallbacks.getObserver());
        }
    }

    /**
     * Implements the callback interface for the Loader.
     */
    static final class Callbacks implements LoaderCallbacks<Cursor> {

        private final Context mContext;
        private final Uri mDocUri;
        private final Consumer<DocumentInfo> mDocConsumer;
        private ContentObserver mObserver;

        Callbacks(Context context, Uri uri, Consumer<DocumentInfo> docConsumer) {
            checkArgument(context != null);
            checkArgument(uri != null);
            checkArgument(docConsumer != null);
            mContext = context;
            mDocUri = uri;
            mDocConsumer = docConsumer;
        }

        @Override
        public android.content.Loader<Cursor> onCreateLoader(int id, Bundle args) {
            return new CursorLoader(mContext, mDocUri, null, null, null, null);
        }

        @Override
        public void onLoadFinished(android.content.Loader<Cursor> loader, Cursor cursor) {

            //returns DocumentInfo null if the cursor is null or isEmpty.
            if (cursor == null || !cursor.moveToFirst()) {
                mDocConsumer.accept(null);
            } else {
                mObserver = new InspectorContentObserver(loader::onContentChanged);
                cursor.registerContentObserver(mObserver);
                DocumentInfo docInfo = DocumentInfo.fromCursor(cursor, mDocUri.getAuthority());
                mDocConsumer.accept(docInfo);
            }
        }

        @Override
        public void onLoaderReset(android.content.Loader<Cursor> loader) {
            if (mObserver != null) {
                mContext.getContentResolver().unregisterContentObserver(mObserver);
            }
        }

        public ContentObserver getObserver() {
            return mObserver;
        }
    }

    private static final class InspectorContentObserver extends ContentObserver {
        private final Runnable mContentChangedCallback;

        public InspectorContentObserver(Runnable contentChangedCallback) {
            super(new Handler(Looper.getMainLooper()));
            mContentChangedCallback = contentChangedCallback;
        }

        @Override
        public void onChange(boolean selfChange) {
            mContentChangedCallback.run();
        }
    }
}