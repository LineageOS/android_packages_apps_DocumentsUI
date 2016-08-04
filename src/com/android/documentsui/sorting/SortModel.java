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

import android.annotation.IntDef;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseArray;
import android.view.View;

import com.android.documentsui.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Sort model that contains all columns and their sorting state.
 */
public class SortModel implements Parcelable {
    @IntDef({
            SORT_DIMENSION_ID_TITLE,
            SORT_DIMENSION_ID_SUMMARY,
            SORT_DIMENSION_ID_DATE,
            SORT_DIMENSION_ID_SIZE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SortDimensionId {}
    public static final int SORT_DIMENSION_ID_TITLE = android.R.id.title;
    public static final int SORT_DIMENSION_ID_SUMMARY = android.R.id.summary;
    public static final int SORT_DIMENSION_ID_SIZE = R.id.size;
    public static final int SORT_DIMENSION_ID_DATE = R.id.date;

    private final SparseArray<SortDimension> mDimensions;

    private transient final List<UpdateListener> mListeners;

    private SortDimension mSortedDimension;

    private boolean mIsSortEnabled = true;

    public SortModel(Collection<SortDimension> columns) {
        mDimensions = new SparseArray<>(columns.size());

        for (SortDimension column : columns) {
            if (mDimensions.get(column.getId()) != null) {
                throw new IllegalStateException(
                        "SortDimension id must be unique. Duplicate id: " + column.getId());
            }
            mDimensions.put(column.getId(), column);
        }

        mListeners = new ArrayList<>();
    }

    public int getSize() {
        return mDimensions.size();
    }

    public SortDimension getDimensionAt(int index) {
        return mDimensions.valueAt(index);
    }

    public SortDimension getDimensionById(int id) {
        return mDimensions.get(id);
    }

    public SortDimension getSortedDimension() {
        return mSortedDimension;
    }

    public void setSortEnabled(boolean enabled) {
        if (!enabled) {
            clearSortDirection();
        }
        mIsSortEnabled = enabled;

        notifyListeners();
    }

    public boolean isSortEnabled() {
        return mIsSortEnabled;
    }

    public void sortBy(int columnId, @SortDimension.SortDirection int direction) {
        if (!mIsSortEnabled) {
            throw new IllegalStateException("Sort is not enabled.");
        }
        if (mDimensions.get(columnId) == null) {
            throw new IllegalArgumentException("Unknown column id: " + columnId);
        }

        SortDimension newSortedDimension = mDimensions.get(columnId);
        if ((direction & newSortedDimension.getSortCapability()) == 0) {
            throw new IllegalStateException(
                    "SortDimension " + columnId + " can't be sorted in direction " + direction);
        }
        switch (direction) {
            case SortDimension.SORT_DIRECTION_ASCENDING:
            case SortDimension.SORT_DIRECTION_DESCENDING:
                newSortedDimension.mSortDirection = direction;
                break;
            default:
                throw new IllegalArgumentException("Unknown sort direction: " + direction);
        }

        if (mSortedDimension != null && mSortedDimension != newSortedDimension) {
            mSortedDimension.mSortDirection = SortDimension.SORT_DIRECTION_NONE;
        }

        mSortedDimension = newSortedDimension;

        notifyListeners();
    }

    public void setDimensionVisibility(int columnId, int visibility) {
        assert(mDimensions.get(columnId) != null);

        mDimensions.get(columnId).mVisibility = visibility;

        notifyListeners();
    }

    private void notifyListeners() {
        for (int i = mListeners.size() - 1; i >= 0; --i) {
            mListeners.get(i).onModelUpdate(this);
        }
    }

    public void addListener(UpdateListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(UpdateListener listener) {
        mListeners.remove(listener);
    }

    public void clearSortDirection() {
        if (mSortedDimension != null) {
            mSortedDimension.mSortDirection = SortDimension.SORT_DIRECTION_NONE;
            mSortedDimension = null;
        }
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flag) {
        out.writeInt(mDimensions.size());
        for (int i = 0; i < mDimensions.size(); ++i) {
            out.writeParcelable(mDimensions.valueAt(i), flag);
        }
    }

    public static Parcelable.Creator<SortModel> CREATOR = new Parcelable.Creator<SortModel>() {

        @Override
        public SortModel createFromParcel(Parcel in) {
            int size = in.readInt();
            Collection<SortDimension> columns = new ArrayList<>(size);
            for (int i = 0; i < size; ++i) {
                columns.add(in.readParcelable(getClass().getClassLoader()));
            }
            return new SortModel(columns);
        }

        @Override
        public SortModel[] newArray(int size) {
            return new SortModel[size];
        }
    };

    /**
     * Creates a model for all other roots.
     *
     * TODO: move definition of columns into xml, and inflate model from it.
     */
    public static SortModel createModel() {
        List<SortDimension> dimensions = new ArrayList<>(4);
        SortDimension.Builder builder = new SortDimension.Builder();

        // Name column
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_TITLE)
                .withLabelId(R.string.column_name)
                .withDataType(SortDimension.DATA_TYPE_STRING)
                .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
                .withDefaultSortDirection(SortDimension.SORT_DIRECTION_ASCENDING)
                .withVisibility(View.VISIBLE)
                .build()
        );

        // Summary column
        // Summary is only visible in Downloads and Recents root.
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_SUMMARY)
                .withLabelId(R.string.column_summary)
                .withDataType(SortDimension.DATA_TYPE_STRING)
                .withSortCapability(SortDimension.SORT_CAPABILITY_NONE)
                .withVisibility(View.INVISIBLE)
                .build()
        );

        // Size column
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_SIZE)
                .withLabelId(R.string.column_size)
                .withDataType(SortDimension.DATA_TYPE_NUMBER)
                .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
                .withDefaultSortDirection(SortDimension.SORT_DIRECTION_ASCENDING)
                .withVisibility(View.VISIBLE)
                .build()
        );

        // Date column
        dimensions.add(builder
                .withId(SORT_DIMENSION_ID_DATE)
                .withLabelId(R.string.column_date)
                .withDataType(SortDimension.DATA_TYPE_NUMBER)
                .withSortCapability(SortDimension.SORT_CAPABILITY_BOTH_DIRECTION)
                .withDefaultSortDirection(SortDimension.SORT_DIRECTION_DESCENDING)
                .withVisibility(View.VISIBLE)
                .build()
        );

        return new SortModel(dimensions);
    }

    public interface UpdateListener {
        void onModelUpdate(SortModel newModel);
    }
}
