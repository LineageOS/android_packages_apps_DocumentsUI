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

package com.android.documentsui;

import android.view.Menu;
import android.view.MenuItem;

import com.android.documentsui.model.RootInfo;

public abstract class MenuManager {

    final State mState;
    final SearchViewManager mSearchManager;

    public MenuManager(SearchViewManager searchManager, State displayState) {
        mSearchManager = searchManager;
        mState = displayState;
    }

    /** @See DirectoryFragment.SelectionModeListener#updateActionMenu */
    public void updateActionMenu(Menu menu, SelectionDetails selection) {
        updateOpen(menu.findItem(R.id.menu_open), selection);
        updateDelete(menu.findItem(R.id.menu_delete), selection);
        updateShare(menu.findItem(R.id.menu_share), selection);
        updateRename(menu.findItem(R.id.menu_rename), selection);
        updateSelectAll(menu.findItem(R.id.menu_select_all), selection);
        updateMoveTo(menu.findItem(R.id.menu_move_to), selection);
        updateCopyTo(menu.findItem(R.id.menu_copy_to), selection);

        Menus.disableHiddenItems(menu);
    }

    /** @See Activity#onPrepareOptionsMenu */
    public void updateOptionMenu(Menu menu, DirectoryDetails directoryDetails) {
        updateCreateDir(menu.findItem(R.id.menu_create_dir), directoryDetails);
        updateSettings(menu.findItem(R.id.menu_settings), directoryDetails);
        updateNewWindow(menu.findItem(R.id.menu_new_window), directoryDetails);
        updateModePicker(menu.findItem(
                R.id.menu_grid), menu.findItem(R.id.menu_list), directoryDetails);
        // Sort menu item is managed by SortMenuManager
        updateAdvanced(menu.findItem(R.id.menu_advanced), directoryDetails);

        Menus.disableHiddenItems(menu);
    }

    /** @See DirectoryFragment.onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to a file.
     * */
    public void updateContextMenuForFile(
            Menu menu,
            SelectionDetails selectionDetails,
            DirectoryDetails directoryDetails) {
        assert(selectionDetails != null);

        MenuItem cut = menu.findItem(R.id.menu_cut_to_clipboard);
        MenuItem copy = menu.findItem(R.id.menu_copy_to_clipboard);
        MenuItem pasteInto = menu.findItem(R.id.menu_paste_into_folder);
        MenuItem delete = menu.findItem(R.id.menu_delete);
        MenuItem rename = menu.findItem(R.id.menu_rename);

        copy.setEnabled(!selectionDetails.containsPartialFiles());
        cut.setEnabled(
                !selectionDetails.containsPartialFiles() && selectionDetails.canDelete());
        updatePasteInto(pasteInto, selectionDetails);
        updateRename(rename, selectionDetails);
        updateDelete(delete, selectionDetails);

        updateContextMenu(menu, directoryDetails);
    }

    /** @See DirectoryFragment.onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to an empty pane.
     * */
    public void updateContextMenuForContainer(Menu menu, DirectoryDetails directoryDetails) {
        MenuItem cut = menu.findItem(R.id.menu_cut_to_clipboard);
        MenuItem copy = menu.findItem(R.id.menu_copy_to_clipboard);
        MenuItem pasteInto = menu.findItem(R.id.menu_paste_into_folder);
        MenuItem delete = menu.findItem(R.id.menu_delete);
        MenuItem rename = menu.findItem(R.id.menu_rename);

        cut.setEnabled(false);
        copy.setEnabled(false);
        pasteInto.setEnabled(false);
        rename.setEnabled(false);
        delete.setEnabled(false);

        updateContextMenu(menu, directoryDetails);
    }

    private void updateContextMenu(Menu menu, DirectoryDetails directoryDetails) {

        MenuItem cut = menu.findItem(R.id.menu_cut_to_clipboard);
        MenuItem copy = menu.findItem(R.id.menu_copy_to_clipboard);
        MenuItem paste = menu.findItem(R.id.menu_paste_from_clipboard);
        MenuItem pasteInto = menu.findItem(R.id.menu_paste_into_folder);
        MenuItem delete = menu.findItem(R.id.menu_delete);
        MenuItem createDir = menu.findItem(R.id.menu_create_dir);

        updateCreateDir(createDir, directoryDetails);
        paste.setEnabled(directoryDetails.hasItemsToPaste());

        //Cut, Copy and Delete should always be visible
        cut.setVisible(true);
        copy.setVisible(true);
        delete.setVisible(true);

        // PasteInto should only show if it is enabled. If it's not enabled, Paste shows (regardless
        // of whether it is enabled or not).
        // Paste then hides itself whenever PasteInto is enabled/visible
        pasteInto.setVisible(pasteInto.isEnabled());
        paste.setVisible(!pasteInto.isVisible());
    }

    public void updateRootContextMenu(Menu menu, RootInfo root) {
        MenuItem settings = menu.findItem(R.id.menu_settings);
        MenuItem eject = menu.findItem(R.id.menu_eject_root);

        updateSettings(settings, root);
        updateEject(eject, root);
    }

    void updateModePicker(MenuItem grid, MenuItem list, DirectoryDetails directoryDetails) {
        grid.setVisible(mState.derivedMode != State.MODE_GRID);
        list.setVisible(mState.derivedMode != State.MODE_LIST);
    }

    void updateAdvanced(MenuItem advanced, DirectoryDetails directoryDetails) {
        advanced.setVisible(mState.showAdvancedOption);
        advanced.setTitle(mState.showAdvancedOption && mState.showAdvanced
                ? R.string.menu_advanced_hide : R.string.menu_advanced_show);
    }

    void updateSettings(MenuItem settings, DirectoryDetails directoryDetails) {
        settings.setVisible(false);
    }

    void updateSettings(MenuItem settings, RootInfo root) {
        settings.setVisible(false);
    }

    void updateEject(MenuItem eject, RootInfo root) {
        eject.setVisible(false);
    }

    void updateNewWindow(MenuItem newWindow, DirectoryDetails directoryDetails) {
        newWindow.setVisible(false);
    }

    void updateOpen(MenuItem open, SelectionDetails selectionDetails) {
        open.setVisible(false);
    }

    void updateShare(MenuItem share, SelectionDetails selectionDetails) {
        share.setVisible(false);
    }

    void updateDelete(MenuItem delete, SelectionDetails selectionDetails) {
        delete.setVisible(false);
    }

    void updateRename(MenuItem rename, SelectionDetails selectionDetails) {
        rename.setVisible(false);
    }

    void updateMoveTo(MenuItem moveTo, SelectionDetails selectionDetails) {
        moveTo.setVisible(false);
    }

    void updateCopyTo(MenuItem copyTo, SelectionDetails selectionDetails) {
        copyTo.setVisible(false);
    }

    void updatePasteInto(MenuItem pasteInto, SelectionDetails selectionDetails) {
        pasteInto.setEnabled(false);
    }

    abstract void updateSelectAll(MenuItem selectAll, SelectionDetails selectionDetails);
    abstract void updateCreateDir(MenuItem createDir, DirectoryDetails directoryDetails);

    /**
     * Access to meta data about the selection.
     */
    public interface SelectionDetails {
        boolean containsDirectories();

        boolean containsPartialFiles();

        // TODO: Update these to express characteristics instead of answering concrete questions,
        // since the answer to those questions is (or can be) activity specific.
        boolean canDelete();

        boolean canRename();

        boolean canPasteInto();
    }

    public static class DirectoryDetails {
        private final BaseActivity mActivity;

        public DirectoryDetails(BaseActivity activity) {
            mActivity = activity;
        }

        public boolean shouldShowFancyFeatures() {
            return Shared.shouldShowFancyFeatures(mActivity);
        }

        public boolean hasRootSettings() {
            return mActivity.getCurrentRoot().hasSettings();
        }

        public boolean hasItemsToPaste() {
            return false;
        }

        public boolean isInRecents() {
            return mActivity.getCurrentDirectory() == null;
        }

        public boolean canCreateDirectory() {
            return mActivity.canCreateDirectory();
        }
    }
}
