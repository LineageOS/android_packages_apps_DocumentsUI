/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.VERBOSE;
import static com.android.documentsui.base.Shared.ENABLE_OMC_API_FEATURES;

import android.annotation.IntDef;
import android.database.Cursor;
import android.database.MergeCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.documentsui.DirectoryResult;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.selection.Selection;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * The data model for the current loaded directory.
 */
@VisibleForTesting
public class Model {

    /**
     * Filter that passes (returns true) for all files which can be shared.
     */
    public static final Predicate<Cursor> SHARABLE_FILE_FILTER = (Cursor c) -> {
        int flags = getCursorInt(c, Document.COLUMN_FLAGS);
        String authority = getCursorString(c, RootCursorWrapper.COLUMN_AUTHORITY);
        if (!ENABLE_OMC_API_FEATURES) {
            return (flags & Document.FLAG_PARTIAL) == 0
                    && (flags & Document.FLAG_VIRTUAL_DOCUMENT) == 0
                    && !ArchivesProvider.AUTHORITY.equals(authority);
        }
        return (flags & Document.FLAG_PARTIAL) == 0
                && !ArchivesProvider.AUTHORITY.equals(authority);
    };

    /**
     * Filter that passes (returns true) only virtual documents.
     */
    public static final Predicate<Cursor> VIRTUAL_DOCUMENT_FILTER  = (Cursor c) -> {
        int flags = getCursorInt(c, Document.COLUMN_FLAGS);
        return (flags & Document.FLAG_VIRTUAL_DOCUMENT) != 0;
    };

    private static final Predicate<Cursor> ANY_FILE_FILTER = (Cursor c) -> true;

    private static final String TAG = "Model";

    private boolean mIsLoading;
    private List<EventListener<Update>> mUpdateListeners = new ArrayList<>();
    @Nullable private Cursor mCursor;
    private int mCursorCount;
    /** Maps Model ID to cursor positions, for looking up items by Model ID. */
    private Map<String, Integer> mPositions = new HashMap<>();
    private String mIds[] = new String[0];

    @Nullable String info;
    @Nullable String error;
    @Nullable DocumentInfo doc;

    public void addUpdateListener(EventListener<Update> listener) {
        mUpdateListeners.add(listener);
    }

    public void removeUpdateListener(EventListener<Update> listener) {
        mUpdateListeners.remove(listener);
    }

    private void notifyUpdateListeners() {
        for (EventListener<Update> handler: mUpdateListeners) {
            handler.accept(Update.UPDATE);
        }
    }

    private void notifyUpdateListeners(Exception e) {
        Update error = new Update(e);
        for (EventListener<Update> handler: mUpdateListeners) {
            handler.accept(error);
        }
    }

    void onLoaderReset() {
        if (mIsLoading) {
            Log.w(TAG, "Received unexpected loader reset while in loading state for doc: "
                    + DocumentInfo.debugString(doc));
        }

        reset();
    }

    private void reset() {
        mCursor = null;
        mCursorCount = 0;
        mIds = new String[0];
        mPositions.clear();
        info = null;
        error = null;
        doc = null;
        mIsLoading = false;
        notifyUpdateListeners();
    }

    void update(DirectoryResult result) {
        assert(result != null);

        if (DEBUG) Log.i(TAG, "Updating model with new result set.");

        if (result.exception != null) {
            Log.e(TAG, "Error while loading directory contents", result.exception);
            notifyUpdateListeners(result.exception);
            return;
        }

        mCursor = result.cursor;
        mCursorCount = mCursor.getCount();
        doc = result.doc;

        updateModelData();

        final Bundle extras = mCursor.getExtras();
        if (extras != null) {
            info = extras.getString(DocumentsContract.EXTRA_INFO);
            error = extras.getString(DocumentsContract.EXTRA_ERROR);
            mIsLoading = extras.getBoolean(DocumentsContract.EXTRA_LOADING, false);
        }

        notifyUpdateListeners();
    }

    @VisibleForTesting
    int getItemCount() {
        return mCursorCount;
    }

    /**
     * Scan over the incoming cursor data, generate Model IDs for each row, and sort the IDs
     * according to the current sort order.
     */
    private void updateModelData() {
        mIds = new String[mCursorCount];

        mCursor.moveToPosition(-1);
        for (int pos = 0; pos < mCursorCount; ++pos) {
            if (!mCursor.moveToNext()) {
                Log.e(TAG, "Fail to move cursor to next pos: " + pos);
                return;
            }
            // Generates a Model ID for a cursor entry that refers to a document. The Model ID is a
            // unique string that can be used to identify the document referred to by the cursor.
            // If the cursor is a merged cursor over multiple authorities, then prefix the ids
            // with the authority to avoid collisions.
            if (mCursor instanceof MergeCursor) {
                mIds[pos] = getCursorString(mCursor, RootCursorWrapper.COLUMN_AUTHORITY)
                        + "|" + getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);
            } else {
                mIds[pos] = getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);
            }
        }

        // Populate the positions.
        mPositions.clear();
        for (int i = 0; i < mCursorCount; ++i) {
            mPositions.put(mIds[i], i);
        }
    }

    public @Nullable Cursor getItem(String modelId) {
        Integer pos = mPositions.get(modelId);
        if (pos == null) {
            if (DEBUG) Log.d(TAG, "Unabled to find cursor position for modelId: " + modelId);
            return null;
        }

        if (!mCursor.moveToPosition(pos)) {
            if (DEBUG) Log.d(TAG,
                    "Unabled to move cursor to position " + pos + " for modelId: " + modelId);
            return null;
        }

        return mCursor;
    }

    public boolean isEmpty() {
        return mCursorCount == 0;
    }

    boolean isLoading() {
        return mIsLoading;
    }

    public List<DocumentInfo> getDocuments(Selection selection) {
        return loadDocuments(selection, ANY_FILE_FILTER);
    }

    public @Nullable DocumentInfo getDocument(String modelId) {
        final Cursor cursor = getItem(modelId);
        return (cursor == null)
                ? null
                : DocumentInfo.fromDirectoryCursor(cursor);
    }

    public List<DocumentInfo> loadDocuments(Selection selection, Predicate<Cursor> filter) {
        final int size = (selection != null) ? selection.size() : 0;

        final List<DocumentInfo> docs =  new ArrayList<>(size);
        DocumentInfo doc;
        for (String modelId: selection) {
            doc = loadDocument(modelId, filter);
            if (doc != null) {
                docs.add(doc);
            }
        }
        return docs;
    }

    public boolean hasDocuments(Selection selection, Predicate<Cursor> filter) {
        for (String modelId: selection) {
            if (loadDocument(modelId, filter) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return DocumentInfo, or null. If filter returns false, null will be returned.
     */
    private @Nullable DocumentInfo loadDocument(String modelId, Predicate<Cursor> filter) {
        final Cursor cursor = getItem(modelId);

        if (cursor == null) {
            Log.w(TAG, "Unable to obtain document for modelId: " + modelId);
            return null;
        }

        if (filter.test(cursor)) {
            return DocumentInfo.fromDirectoryCursor(cursor);
        }

        if (VERBOSE) Log.v(TAG, "Filtered out document from results: " + modelId);
        return null;
    }

    public Uri getItemUri(String modelId) {
        final Cursor cursor = getItem(modelId);
        return DocumentInfo.getUri(cursor);
    }

    /**
     * @return An ordered array of model IDs representing the documents in the model. It is sorted
     *         according to the current sort order, which was set by the last model update.
     */
    public String[] getModelIds() {
        return mIds;
    }

    public static class Update {

        public static final Update UPDATE = new Update();

        @IntDef(value = {
                TYPE_UPDATE,
                TYPE_UPDATE_ERROR
        })
        @Retention(RetentionPolicy.SOURCE)
        public @interface UpdateType {}
        public static final int TYPE_UPDATE = 0;
        public static final int TYPE_UPDATE_ERROR = 1;

        private final @UpdateType int mType;
        private final @Nullable Exception mException;

        private Update() {
            mType = TYPE_UPDATE;
            mException = null;
        }

        public Update(Exception exception) {
            assert(exception != null);
            mType = TYPE_UPDATE_ERROR;
            mException = exception;
        }

        public boolean isUpdate() {
            return mType == TYPE_UPDATE;
        }

        public boolean hasError() {
            return mType == TYPE_UPDATE_ERROR;
        }

        public @Nullable Exception getError() {
            return mException;
        }
    }
}
