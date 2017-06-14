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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.Loader;
import com.android.internal.util.Preconditions;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Asynchronously loads a documents metadata into a DocumentInfo object.
 */
public class DocumentLoader implements Loader {

    private final Context mContext;
    private final LoaderManager mLoader;
    private List<Integer> loaderIds;

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
        mLoader.restartLoader(loadId, null, new Callbacks(mContext, uri, mLoader, callback));
    }

    @Override
    public void reset() {
        for (Integer id : loaderIds) {
            mLoader.destroyLoader(id);
        }
        loaderIds.clear();
    }

    /**
     * Implements the callback interface for the Loader.
     */
    static final class Callbacks implements LoaderCallbacks<Cursor> {

        private final Context mContext;
        private final Uri mDocUri;
        private final Consumer<DocumentInfo> mDocConsumer;
        private final LoaderManager mManager;

        Callbacks(Context context, Uri uri, LoaderManager manager,
                Consumer<DocumentInfo> docConsumer) {

            checkArgument(context != null);
            checkArgument(uri != null);
            checkArgument(manager != null);
            checkArgument(docConsumer != null);
            mContext = context;
            mDocUri = uri;
            mDocConsumer = docConsumer;
            mManager = manager;
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
            }
            else {
                DocumentInfo docInfo = DocumentInfo.fromCursor(cursor, mDocUri.getAuthority());
                mDocConsumer.accept(docInfo);
            }

            mManager.destroyLoader(loader.getId());
        }

        @Override
        public void onLoaderReset(android.content.Loader<Cursor> loader) {

        }
    }
}