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

import android.annotation.Nullable;
import android.content.Context;
import android.view.View;

import com.android.documentsui.Metrics;
import com.android.documentsui.State;
import com.android.documentsui.State.ViewMode;
import com.android.documentsui.dirlist.DropdownSortWidgetController;
import com.android.documentsui.dirlist.header.TableHeaderController;

/**
 * A high level controller that manages sort widgets. This is useful when sort widgets can and will
 * appear in different locations in the UI, like the menu, above the file list (pinned) and embedded
 * at the top of file list... and maybe other places too.
 */
public class SortController {

    private static final WidgetController DUMMY_CONTROLLER = new WidgetController() {};

    private final SortModel mModel;
    private final Context mContext;

    private WidgetController mTableHeaderController = DUMMY_CONTROLLER;
    private WidgetController mDropdownController = DUMMY_CONTROLLER;

    public SortController(SortModel model, Context context) {
        mModel = model;
        mContext = context.getApplicationContext();

        mModel.setMetricRecorder(this::recordSortMetric);
    }

    public void manage(
            @Nullable TableHeaderController headerController,
            @Nullable DropdownSortWidgetController gridController,
            @ViewMode int mode) {
        assert(mTableHeaderController == DUMMY_CONTROLLER);
        assert(mDropdownController == DUMMY_CONTROLLER);

        if (headerController != null) {
            mTableHeaderController = headerController;
            mTableHeaderController.setModel(mModel);
        }

        if (gridController != null) {
            mDropdownController = gridController;
            mDropdownController.setModel(mModel);
        }

        onViewModeChanged(mode);
    }

    public void clean(
            @Nullable TableHeaderController headerController,
            @Nullable DropdownSortWidgetController gridController) {
        assert(headerController == null || mTableHeaderController == headerController);
        assert(gridController == null || mDropdownController == gridController);

        if (headerController != null) {
            headerController.setModel(null);
        }
        mTableHeaderController = DUMMY_CONTROLLER;

        if (gridController != null) {
            gridController.setModel(null);
        }
        mDropdownController = DUMMY_CONTROLLER;
    }

    public void onViewModeChanged(@ViewMode int mode) {
        setVisibilityPerViewMode(mTableHeaderController, mode, View.GONE, View.VISIBLE);

        if (mTableHeaderController == DUMMY_CONTROLLER) {
            mDropdownController.setVisibility(View.VISIBLE);
        } else {
            setVisibilityPerViewMode(mDropdownController, mode, View.VISIBLE, View.GONE);
        }
    }

    private static void setVisibilityPerViewMode(
            WidgetController controller,
            @ViewMode int mode,
            int visibilityInGrid,
            int visibilityInList) {
        switch (mode) {
            case State.MODE_GRID:
                controller.setVisibility(visibilityInGrid);
                break;
            case State.MODE_LIST:
                controller.setVisibility(visibilityInList);
                break;
            default:
                throw new IllegalArgumentException("Unknown view mode: " + mode + ".");
        }
    }

    private void recordSortMetric(SortDimension dimension) {
        switch (dimension.getId()) {
            case SortModel.SORT_DIMENSION_ID_TITLE:
                Metrics.logUserAction(mContext, Metrics.USER_ACTION_SORT_NAME);
                break;
            case SortModel.SORT_DIMENSION_ID_SIZE:
                Metrics.logUserAction(mContext, Metrics.USER_ACTION_SORT_SIZE);
                break;
            case SortModel.SORT_DIMENSION_ID_DATE:
                Metrics.logUserAction(mContext, Metrics.USER_ACTION_SORT_DATE);
                break;
        }
    }

    public interface WidgetController {
        default void setModel(SortModel model) {}
        default void setVisibility(int visibility) {}
    }
}
