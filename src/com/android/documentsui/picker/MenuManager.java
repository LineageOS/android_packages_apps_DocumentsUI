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

package com.android.documentsui.picker;

import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import android.view.Menu;
import android.view.MenuItem;

import com.android.documentsui.SearchViewManager;
import com.android.documentsui.base.State;

public final class MenuManager extends com.android.documentsui.MenuManager {

    private boolean mPicking;

    public MenuManager(SearchViewManager searchManager, State displayState) {
        super(searchManager, displayState);

        mPicking = mState.action == ACTION_CREATE
                || mState.action == ACTION_OPEN_TREE
                || mState.action == ACTION_PICK_COPY_DESTINATION;
    }

    @Override
    public void updateOptionMenu(Menu menu, DirectoryDetails details) {
        super.updateOptionMenu(menu, details);
        if (mPicking) {
            // May already be hidden because the root
            // doesn't support search.
            mSearchManager.showMenu(false);
        }
    }

    @Override
    protected void updateModePicker(
            MenuItem grid, MenuItem list, DirectoryDetails directoryDetails) {
        // No display options in recent directories
        if (mPicking && directoryDetails.isInRecents()) {
            grid.setVisible(false);
            list.setVisible(false);
        } else {
            super.updateModePicker(grid, list, directoryDetails);
        }
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll) {
        selectAll.setVisible(mState.allowMultiple);
    }

    @Override
    protected void updateCreateDir(MenuItem createDir, DirectoryDetails directoryDetails) {
        createDir.setVisible(mPicking);
        createDir.setEnabled(mPicking && directoryDetails.canCreateDirectory());
    }

    @Override
    protected void updateOpenInActionMode(MenuItem open, SelectionDetails selectionDetails) {
        updateOpen(open, selectionDetails);
    }

    @Override
    protected void updateOpenInContextMenu(MenuItem open, SelectionDetails selectionDetails) {
        updateOpen(open, selectionDetails);
    }

    private void updateOpen(MenuItem open, SelectionDetails selectionDetails) {
        open.setVisible(mState.action == ACTION_GET_CONTENT
                || mState.action == ACTION_OPEN);
        open.setEnabled(selectionDetails.size() > 0);
    }
}
