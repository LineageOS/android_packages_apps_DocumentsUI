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

import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Shared.DEBUG;

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
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The data model for the current loaded directory.
 */
@VisibleForTesting
public class Model {
    private static final String TAG = "Model";

    private boolean mIsLoading;
    private List<EventListener<Update>> mUpdateListeners = new ArrayList<>();
    @Nullable private Cursor mCursor;
    private int mCursorCount;
    /** Maps Model ID to cursor positions, for looking up items by Model ID. */
    private Map<String, Integer> mPositions = new HashMap<>();
    /**
     * A sorted array of model IDs for the files currently in the Model.  Sort order is determined
     * by {@link #mSortModel}
     */
    private String mIds[] = new String[0];
    private SortModel mSortModel;

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
        mSortModel = result.sortModel;
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
        int[] positions = new int[mCursorCount];
        mIds = new String[mCursorCount];
        boolean[] isDirs = new boolean[mCursorCount];
        String[] displayNames = null;
        long[] longValues = null;

        final int id = mSortModel.getSortedDimensionId();
        switch (id) {
            case SortModel.SORT_DIMENSION_ID_TITLE:
                displayNames = new String[mCursorCount];
                break;
            case SortModel.SORT_DIMENSION_ID_DATE:
            case SortModel.SORT_DIMENSION_ID_SIZE:
                longValues = new long[mCursorCount];
                break;
        }

        String mimeType;

        mCursor.moveToPosition(-1);
        for (int pos = 0; pos < mCursorCount; ++pos) {
            if (!mCursor.moveToNext()) {
                Log.e(TAG, "Fail to move cursor to next pos: " + pos);
                return;
            }
            positions[pos] = pos;

            // Generates a Model ID for a cursor entry that refers to a document. The Model ID is a
            // unique string that can be used to identify the document referred to by the cursor.
            // If the cursor is a merged cursor over multiple authorities, then prefix the ids
            // with the authority to avoid collisions.
            if (mCursor instanceof MergeCursor) {
                mIds[pos] = getCursorString(mCursor, RootCursorWrapper.COLUMN_AUTHORITY) + "|" +
                        getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);
            } else {
                mIds[pos] = getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);
            }

            mimeType = getCursorString(mCursor, Document.COLUMN_MIME_TYPE);
            isDirs[pos] = Document.MIME_TYPE_DIR.equals(mimeType);

            switch(id) {
                case SortModel.SORT_DIMENSION_ID_TITLE:
                    final String displayName = getCursorString(
                            mCursor, Document.COLUMN_DISPLAY_NAME);
                    displayNames[pos] = displayName;
                    break;
                case SortModel.SORT_DIMENSION_ID_DATE:
                    longValues[pos] = getLastModified(mCursor);
                    break;
                case SortModel.SORT_DIMENSION_ID_SIZE:
                    longValues[pos] = getCursorLong(mCursor, Document.COLUMN_SIZE);
                    break;
            }
        }

        final SortDimension dimension = mSortModel.getDimensionById(id);
        switch (id) {
            case SortModel.SORT_DIMENSION_ID_TITLE:
                binarySort(displayNames, isDirs, positions, mIds, dimension.getSortDirection());
                break;
            case SortModel.SORT_DIMENSION_ID_DATE:
            case SortModel.SORT_DIMENSION_ID_SIZE:
                binarySort(longValues, isDirs, positions, mIds, dimension.getSortDirection());
                break;
        }

        // Populate the positions.
        mPositions.clear();
        for (int i = 0; i < mCursorCount; ++i) {
            mPositions.put(mIds[i], positions[i]);
        }
    }

    /**
     * Sorts model data. Takes three columns of index-corresponded data. The first column is the
     * sort key. Rows are sorted in ascending alphabetical order on the sort key.
     * Directories are always shown first. This code is based on TimSort.binarySort().
     *
     * @param sortKey Data is sorted in ascending alphabetical order.
     * @param isDirs Array saying whether an item is a directory or not.
     * @param positions Cursor positions to be sorted.
     * @param ids Model IDs to be sorted.
     */
    private static void binarySort(
            String[] sortKey,
            boolean[] isDirs,
            int[] positions,
            String[] ids,
            @SortDimension.SortDirection int direction) {
        final int count = positions.length;
        for (int start = 1; start < count; start++) {
            final int pivotPosition = positions[start];
            final String pivotValue = sortKey[start];
            final boolean pivotIsDir = isDirs[start];
            final String pivotId = ids[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = (left + right) >>> 1;

                // Directories always go in front.
                int compare = 0;
                final boolean rhsIsDir = isDirs[mid];
                if (pivotIsDir && !rhsIsDir) {
                    compare = -1;
                } else if (!pivotIsDir && rhsIsDir) {
                    compare = 1;
                } else {
                    final String lhs = pivotValue;
                    final String rhs = sortKey[mid];
                    switch (direction) {
                        case SortDimension.SORT_DIRECTION_ASCENDING:
                            compare = Shared.compareToIgnoreCaseNullable(lhs, rhs);
                            break;
                        case SortDimension.SORT_DIRECTION_DESCENDING:
                            compare = -Shared.compareToIgnoreCaseNullable(lhs, rhs);
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unknown sorting direction: " + direction);
                    }
                }

                if (compare < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    positions[left + 2] = positions[left + 1];
                    sortKey[left + 2] = sortKey[left + 1];
                    isDirs[left + 2] = isDirs[left + 1];
                    ids[left + 2] = ids[left + 1];
                case 1:
                    positions[left + 1] = positions[left];
                    sortKey[left + 1] = sortKey[left];
                    isDirs[left + 1] = isDirs[left];
                    ids[left + 1] = ids[left];
                    break;
                default:
                    System.arraycopy(positions, left, positions, left + 1, n);
                    System.arraycopy(sortKey, left, sortKey, left + 1, n);
                    System.arraycopy(isDirs, left, isDirs, left + 1, n);
                    System.arraycopy(ids, left, ids, left + 1, n);
            }

            positions[left] = pivotPosition;
            sortKey[left] = pivotValue;
            isDirs[left] = pivotIsDir;
            ids[left] = pivotId;
        }
    }

    /**
     * Sorts model data. Takes four columns of index-corresponded data. The first column is the sort
     * key, and the second is an array of mime types. The rows are first bucketed by mime type
     * (directories vs documents) and then each bucket is sorted independently in descending
     * numerical order on the sort key. This code is based on TimSort.binarySort().
     *
     * @param sortKey Data is sorted in descending numerical order.
     * @param isDirs Array saying whether an item is a directory or not.
     * @param positions Cursor positions to be sorted.
     * @param ids Model IDs to be sorted.
     */
    private static void binarySort(
            long[] sortKey,
            boolean[] isDirs,
            int[] positions,
            String[] ids,
            @SortDimension.SortDirection int direction) {
        final int count = positions.length;
        for (int start = 1; start < count; start++) {
            final int pivotPosition = positions[start];
            final long pivotValue = sortKey[start];
            final boolean pivotIsDir = isDirs[start];
            final String pivotId = ids[start];

            int left = 0;
            int right = start;

            while (left < right) {
                int mid = ((left + right) >>> 1);

                // Directories always go in front.
                int compare = 0;
                final boolean rhsIsDir = isDirs[mid];
                if (pivotIsDir && !rhsIsDir) {
                    compare = -1;
                } else if (!pivotIsDir && rhsIsDir) {
                    compare = 1;
                } else {
                    final long lhs = pivotValue;
                    final long rhs = sortKey[mid];
                    switch (direction) {
                        case SortDimension.SORT_DIRECTION_ASCENDING:
                            compare = Long.compare(lhs, rhs);
                            break;
                        case SortDimension.SORT_DIRECTION_DESCENDING:
                            compare = -Long.compare(lhs, rhs);
                            break;
                        default:
                            throw new IllegalArgumentException(
                                    "Unknown sorting direction: " + direction);
                    }
                }

                // If numerical comparison yields a tie, use document ID as a tie breaker.  This
                // will yield stable results even if incoming items are continually shuffling and
                // have identical numerical sort keys.  One common example of this scenario is seen
                // when sorting a set of active downloads by mod time.
                if (compare == 0) {
                    compare = pivotId.compareTo(ids[mid]);
                }

                if (compare < 0) {
                    right = mid;
                } else {
                    left = mid + 1;
                }
            }

            int n = start - left;
            switch (n) {
                case 2:
                    positions[left + 2] = positions[left + 1];
                    sortKey[left + 2] = sortKey[left + 1];
                    isDirs[left + 2] = isDirs[left + 1];
                    ids[left + 2] = ids[left + 1];
                case 1:
                    positions[left + 1] = positions[left];
                    sortKey[left + 1] = sortKey[left];
                    isDirs[left + 1] = isDirs[left];
                    ids[left + 1] = ids[left];
                    break;
                default:
                    System.arraycopy(positions, left, positions, left + 1, n);
                    System.arraycopy(sortKey, left, sortKey, left + 1, n);
                    System.arraycopy(isDirs, left, isDirs, left + 1, n);
                    System.arraycopy(ids, left, ids, left + 1, n);
            }

            positions[left] = pivotPosition;
            sortKey[left] = pivotValue;
            isDirs[left] = pivotIsDir;
            ids[left] = pivotId;
        }
    }

    /**
     * @return Timestamp for the given document. Some docs (e.g. active downloads) have a null
     * timestamp - these will be replaced with MAX_LONG so that such files get sorted to the top
     * when sorting descending by date.
     */
    long getLastModified(Cursor cursor) {
        long l = getCursorLong(mCursor, Document.COLUMN_LAST_MODIFIED);
        return (l == -1) ? Long.MAX_VALUE : l;
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

    List<DocumentInfo> getDocuments(Selection selection) {
        final int size = (selection != null) ? selection.size() : 0;

        final List<DocumentInfo> docs =  new ArrayList<>(size);
        // NOTE: That as this now iterates over only final (non-provisional) selection.
        for (String modelId: selection) {
            DocumentInfo doc = getDocument(modelId);
            if (doc == null) {
                Log.w(TAG, "Unable to obtain document for modelId: " + modelId);
                continue;
            }
            docs.add(doc);
        }
        return docs;
    }

    public @Nullable DocumentInfo getDocument(String modelId) {
        final Cursor cursor = getItem(modelId);
        return (cursor == null)
                ? null
                : DocumentInfo.fromDirectoryCursor(cursor);
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
