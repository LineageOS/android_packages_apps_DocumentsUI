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

package com.android.documentsui.dirlist.header;

import android.annotation.Nullable;
import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.sorting.SortModel.SortDimensionId;
import com.android.documentsui.sorting.SortController;

/**
 * View controller for table header that associates header cells in table header and columns.
 */
public final class TableHeaderController implements SortController.WidgetController {
    private View mTableHeader;

    private final HeaderCell mTitleCell;
    private final HeaderCell mSummaryCell;
    private final HeaderCell mSizeCell;
    private final HeaderCell mDateCell;

    private final SortModel.UpdateListener mModelUpdaterListener = this::onModelUpdate;
    private final View.OnClickListener mOnCellClickListener = this::onCellClicked;

    private SortModel mModel;

    public static @Nullable TableHeaderController create(@Nullable View tableHeader) {
        return (tableHeader == null) ? null : new TableHeaderController(tableHeader);
    }

    private TableHeaderController(View tableHeader) {
        mTableHeader = tableHeader;

        mTitleCell = (HeaderCell) tableHeader.findViewById(android.R.id.title);
        mSummaryCell = (HeaderCell) tableHeader.findViewById(android.R.id.summary);
        mSizeCell = (HeaderCell) tableHeader.findViewById(R.id.size);
        mDateCell = (HeaderCell) tableHeader.findViewById(R.id.date);
    }

    @Override
    public void setModel(@Nullable SortModel model) {
        if (mModel != null) {
            mModel.removeListener(mModelUpdaterListener);
        }

        mModel = model;

        if (mModel != null) {
            onModelUpdate(mModel);

            mModel.addListener(mModelUpdaterListener);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        mTableHeader.setVisibility(visibility);
    }

    private void onModelUpdate(SortModel model) {
        bindCell(mTitleCell, SortModel.SORT_DIMENSION_ID_TITLE);
        bindCell(mSummaryCell, SortModel.SORT_DIMENSION_ID_SUMMARY);
        bindCell(mSizeCell, SortModel.SORT_DIMENSION_ID_SIZE);
        bindCell(mDateCell, SortModel.SORT_DIMENSION_ID_DATE);
    }

    private void bindCell(HeaderCell cell, @SortDimensionId int id) {
        SortDimension dimension = mModel.getDimensionById(id);

        cell.setTag(dimension);

        cell.onBind(dimension);
        if (mModel.isSortEnabled()
                && dimension.getVisibility() == View.VISIBLE
                && dimension.getSortCapability() != SortDimension.SORT_CAPABILITY_NONE) {
            cell.setOnClickListener(mOnCellClickListener);
        } else {
            cell.setOnClickListener(null);
        }
    }

    private void onCellClicked(View v) {
        SortDimension dimension = (SortDimension) v.getTag();

        mModel.sortBy(dimension.getId(), dimension.getNextDirection());
    }
}
