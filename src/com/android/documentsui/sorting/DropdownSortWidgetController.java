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

import android.app.FragmentManager;
import android.view.View;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortController.WidgetController;
import com.android.documentsui.sorting.SortModel.SortDimensionId;
import com.android.documentsui.sorting.SortModel.UpdateType;

/**
 * View controller for the sort widget in grid mode and in small screens.
 */
public final class DropdownSortWidgetController implements WidgetController {

    private final SortModel mModel;
    private final View mWidget;
    private final FragmentManager mFragmentManager;
    private final TextView mDimensionButton;
    private final SortModel.UpdateListener mListener;

    public DropdownSortWidgetController(SortModel model, View widget, FragmentManager fm) {
        mModel = model;
        mWidget = widget;
        mFragmentManager = fm;

        mDimensionButton = (TextView) mWidget.findViewById(R.id.sort_dimen_dropdown);
        mDimensionButton.setOnClickListener(this::showMenu);

        onModelUpdate(mModel, SortModel.UPDATE_TYPE_UNSPECIFIED);

        mListener = this::onModelUpdate;
        mModel.addListener(mListener);
    }

    @Override
    public void setVisibility(int visibility) {
        mWidget.setVisibility(visibility);
    }

    @Override
    public void destroy() {
        mModel.removeListener(mListener);
    }

    private void showMenu(View v) {
        SortListFragment.show(mFragmentManager, mModel);
    }

    private void onModelUpdate(SortModel model, @UpdateType int updateType) {
        if ((updateType & SortModel.UPDATE_TYPE_SORTING) != 0) {
            bindSortedDimension(model);
        }
    }

    private void bindSortedDimension(SortModel model) {
        final @SortDimensionId int sortedId = model.getSortedDimensionId();
        if (sortedId == SortModel.SORT_DIMENSION_ID_UNKNOWN) {
            mDimensionButton.setText(R.string.not_sorted);
        } else {
            SortDimension dimension = model.getDimensionById(sortedId);
            String label = mWidget.getContext().getString(
                    SortListFragment.getSheetLabelId(dimension, model.getCurrentSortDirection()));
            mDimensionButton.setText(
                    mWidget.getContext().getString(R.string.sort_dimension_button_title, label));
        }
    }
}
