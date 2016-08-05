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
    private WidgetController mSortMenuController = DUMMY_CONTROLLER;

    public SortController(SortModel model, Context context) {
        mModel = model;
        mContext = context.getApplicationContext();

        mModel.setMetricRecorder(this::recordSortMetric);
    }

    public void manage(
            @Nullable TableHeaderController controller, @State.ViewMode int mode) {
        assert(mTableHeaderController == DUMMY_CONTROLLER);

        if (controller == null) {
            return;
        }

        mTableHeaderController = controller;
        mTableHeaderController.setModel(mModel);

        setVisibilityPerViewMode(mTableHeaderController, mode, View.GONE, View.VISIBLE);
    }

    public void clean(@Nullable TableHeaderController controller) {
        assert(controller == null || mTableHeaderController == controller);

        if (controller != null) {
            controller.setModel(null);
        }

        mTableHeaderController = DUMMY_CONTROLLER;
    }

    public void manage(SortMenuController controller) {
        assert(mSortMenuController == DUMMY_CONTROLLER);

        if (controller != null) {
            controller.setModel(mModel);
        }

        mSortMenuController = controller;
    }

    public void clean(SortMenuController controller) {
        assert(mSortMenuController == controller);

        if (controller != null) {
            controller.setModel(null);
        }

        mSortMenuController = DUMMY_CONTROLLER;
    }

    public void onViewModeChanged(@State.ViewMode int mode) {
        setVisibilityPerViewMode(mTableHeaderController, mode, View.GONE, View.VISIBLE);
    }

    private static void setVisibilityPerViewMode(
            WidgetController controller,
            @State.ViewMode int mode,
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
