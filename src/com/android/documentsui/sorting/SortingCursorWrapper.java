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

package com.android.documentsui.sorting;

import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.database.AbstractCursor;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.DocumentsContract.Document;

import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.Shared;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Cursor wrapper that presents a sorted view of the underlying cursor. Handles
 * common {@link Document} sorting modes, such as ordering directories first.
 */
class SortingCursorWrapper extends AbstractCursor {
    private final Cursor mCursor;

    private final int[] mPosition;

    public SortingCursorWrapper(
            Cursor cursor, SortDimension dimension, Lookup<String, String> fileTypeLookup) {
        mCursor = cursor;

        final int count = cursor.getCount();
        final boolean[] isDirs = new boolean[count];
        final String[] ids = new String[count];

        final int id = dimension.getId();
        boolean stringy = (id == SortModel.SORT_DIMENSION_ID_TITLE)
                || (id == SortModel.SORT_DIMENSION_ID_FILE_TYPE);
        final String[] stringValues = stringy ? new String[count] : null;
        final long[] longValues = !stringy ? new long[count] : null;

        Integer[] boxedInts = new Integer[count];
        cursor.moveToPosition(-1);
        for (int i = 0; i < count; i++) {
            cursor.moveToNext();
            boxedInts[i] = Integer.valueOf(i);

            final String mimeType = getCursorString(mCursor, Document.COLUMN_MIME_TYPE);
            isDirs[i] = Document.MIME_TYPE_DIR.equals(mimeType);
            ids[i] = getCursorString(mCursor, Document.COLUMN_DOCUMENT_ID);

            if (id == SortModel.SORT_DIMENSION_ID_TITLE) {
                final String displayName = getCursorString(
                        mCursor, Document.COLUMN_DISPLAY_NAME);
                stringValues[i] = displayName;
            } else if (id == SortModel.SORT_DIMENSION_ID_FILE_TYPE) {
                stringValues[i] = fileTypeLookup.lookup(mimeType);
            } else if (id == SortModel.SORT_DIMENSION_ID_DATE) {
                longValues[i] = getLastModified(mCursor);
            } else if (id == SortModel.SORT_DIMENSION_ID_SIZE) {
                longValues[i] = getCursorLong(mCursor, Document.COLUMN_SIZE);
            }
        }

        final boolean ascending =
                dimension.getSortDirection() == SortDimension.SORT_DIRECTION_ASCENDING;

        Arrays.sort(boxedInts, new Comparator<Integer>() {
            @Override
            public int compare(Integer index1, Integer index2) {
                int i1 = index1.intValue();
                int i2 = index2.intValue();
                boolean isDir1 = isDirs[i1];
                boolean isDir2 = isDirs[i2];
                int result = 0;

                // Directories always go in front of non-directories...
                if (isDir1 != isDir2) {
                    result = isDir1 ? -1 : +1;

                // ...otherwise sort by stringValues or longValues.
                } else if (stringValues != null) {
                    result = ascending
                        ? Shared.compareToIgnoreCaseNullable(stringValues[i1], stringValues[i2])
                        : Shared.compareToIgnoreCaseNullable(stringValues[i2], stringValues[i1]);
                } else if (longValues != null) {
                    result = ascending
                        ? Long.compare(longValues[i1], longValues[i2])
                        : Long.compare(longValues[i2], longValues[i1]);
                }

                // Use document ID as a tie breaker to achieve a stable sort.
                if (result == 0) {
                    result = ids[i1].compareTo(ids[i2]);
                }

                return result;
            }
        });

        mPosition = new int[count];
        for (int i = 0; i < count; i++) {
            mPosition[i] = boxedInts[i].intValue();
        }
    }

    @Override
    public void close() {
        super.close();
        mCursor.close();
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        return mCursor.moveToPosition(mPosition[newPosition]);
    }

    @Override
    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    @Override
    public int getCount() {
        return mCursor.getCount();
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public int getType(int column) {
        return mCursor.getType(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }

    @Override
    public Bundle getExtras() {
        return mCursor.getExtras();
    }

    @Override
    public void registerContentObserver(ContentObserver observer) {
        mCursor.registerContentObserver(observer);
    }

    @Override
    public void unregisterContentObserver(ContentObserver observer) {
        mCursor.unregisterContentObserver(observer);
    }

    /**
     * @return Timestamp for the given document. Some docs (e.g. active downloads) have a null
     * timestamp - these will be replaced with MAX_LONG so that such files get sorted to the top
     * when sorting descending by date.
     */
    private static long getLastModified(Cursor cursor) {
        long l = getCursorLong(cursor, Document.COLUMN_LAST_MODIFIED);
        return (l == -1) ? Long.MAX_VALUE : l;
    }
}
