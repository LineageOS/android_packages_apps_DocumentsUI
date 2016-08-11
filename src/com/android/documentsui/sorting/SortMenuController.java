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

import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;

import com.android.documentsui.R;

import java.util.Formatter;

/**
 * Displays and manages menu items related to sorting. It adds menu items for each
 * {@link SortDimension} that supports sorting.
 */
public class SortMenuController implements SortController.WidgetController {

    private final String mPhrase;
    private final Resources mRes;

    private final SortModel.UpdateListener mListener = this::onModelUpdate;
    private final MenuItem.OnMenuItemClickListener mItemClickListener = this::onMenuItemClicked;

    private boolean mVisible = true;
    private MenuItem mMenu;
    private SortModel mModel;

    public SortMenuController(Resources res) {
        mRes = res;
        mPhrase = mRes.getString(R.string.sort_phrase);
    }

    public void install(MenuItem menu) {
        assert(menu.hasSubMenu());
        mMenu = menu;

        initItem();
        onModelUpdate(mModel, SortModel.UPDATE_TYPE_UNSPECIFIED);
    }

    @Override
    public void setModel(SortModel sortModel) {
        if (mModel != null) {
            clearItem();
            mModel.removeListener(mListener);
        }

        mModel = sortModel;

        if (mModel != null) {
            initItem();
            onModelUpdate(mModel, SortModel.UPDATE_TYPE_UNSPECIFIED);
            mModel.addListener(mListener);
        }
    }

    @Override
    public void setVisibility(int visibility) {
        mVisible = (visibility == View.VISIBLE);
        mMenu.setVisible(mVisible);
    }

    private void initItem() {
        if (mMenu == null || mModel == null) {
            return;
        }

        mMenu.setVisible(mVisible);

        SubMenu menu = mMenu.getSubMenu();

        StringBuilder builder = new StringBuilder();
        Formatter formatter = new Formatter(builder);
        for (int i = 0; i < mModel.getSize(); ++i) {
            final SortDimension dimension = mModel.getDimensionAt(i);

            // We don't need to add menu item if a dimension is not sortable
            if (dimension.getSortCapability() == SortDimension.SORT_CAPABILITY_NONE) {
                continue;
            }

            String title = formatter
                    .format(mPhrase, mRes.getString(dimension.getLabelId()))
                    .toString();

            // Clean the underlying builder so that we can reuse it for next menu item.
            builder.setLength(0);
            menu.add(0, dimension.getId(), Menu.NONE, title);
        }
    }

    private void clearItem() {
        if (mMenu == null) {
            return;
        }

        mMenu.getSubMenu().clear();
    }

    /**
     * Update the state of menu items based on {@link SortModel}. Note it doesn't add or remove any
     * menu item, but set the visibility.
     * @param model the new model
     */
    private void onModelUpdate(SortModel model, @SortModel.UpdateType int updateType) {
        // Sort menu doesn't record sort direction so there is nothing to update if only sort order
        // has changed.
        if (mMenu == null || updateType == SortModel.UPDATE_TYPE_SORTING) {
            return;
        }

        mMenu.setEnabled(mModel.isSortEnabled());

        SubMenu menu = mMenu.getSubMenu();
        if (mModel.isSortEnabled()) {
            for (int i = 0; i < menu.size(); ++i) {
                MenuItem item = menu.getItem(i);
                SortDimension dimension = mModel.getDimensionById(item.getItemId());

                bindItem(item, dimension);
            }
        }
    }

    private void bindItem(MenuItem item, SortDimension dimension) {
        item.setVisible(dimension.getVisibility() == View.VISIBLE);
        if (dimension.getVisibility() == View.VISIBLE) {
            item.setOnMenuItemClickListener(mItemClickListener);
        } else {
            item.setOnMenuItemClickListener(null);
        }
    }

    private boolean onMenuItemClicked(MenuItem item) {
        final SortDimension dimension = mModel.getDimensionById(item.getItemId());

        // Click on menu item will only sort stuff in its default direction
        mModel.sortByUser(dimension.getId(), dimension.getDefaultSortDirection());
        return true;
    }
}
