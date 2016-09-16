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

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.dirlist.DirectoryFragment;

public abstract class MenuManager {

    final State mState;
    final SearchViewManager mSearchManager;

    public MenuManager(SearchViewManager searchManager, State displayState) {
        mSearchManager = searchManager;
        mState = displayState;
    }

    /** @see ActionModeController */
    public void updateActionMenu(Menu menu, SelectionDetails selection) {
        updateOpenInActionMode(menu.findItem(R.id.menu_open), selection);
        updateDelete(menu.findItem(R.id.menu_delete), selection);
        updateShare(menu.findItem(R.id.menu_share), selection);
        updateRename(menu.findItem(R.id.menu_rename), selection);
        updateSelectAll(menu.findItem(R.id.menu_select_all));
        updateMoveTo(menu.findItem(R.id.menu_move_to), selection);
        updateCopyTo(menu.findItem(R.id.menu_copy_to), selection);

        Menus.disableHiddenItems(menu);
    }

    /** @see BaseActivity#onPrepareOptionsMenu */
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

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to a file when the selection
     * doesn't contain any folder.
     *
     * @param selectionDetails
     *      containsFiles may return false because this may be called when user right clicks on an
     *      unselectable item in pickers
     */
    public void updateContextMenuForFiles(Menu menu, SelectionDetails selectionDetails) {
        assert(selectionDetails != null);

        MenuItem share = menu.findItem(R.id.menu_share);
        MenuItem open = menu.findItem(R.id.menu_open);
        MenuItem openWith = menu.findItem(R.id.menu_open_with);
        MenuItem rename = menu.findItem(R.id.menu_rename);

        updateShare(share, selectionDetails);
        updateOpenInContextMenu(open, selectionDetails);
        updateOpenWith(openWith, selectionDetails);
        updateRename(rename, selectionDetails);

        updateContextMenu(menu, selectionDetails);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to a folder when the selection
     * doesn't contain any file.
     *
     * @param selectionDetails
     *      containDirectories may return false because this may be called when user right clicks on
     *      an unselectable item in pickers
     */
    public void updateContextMenuForDirs(Menu menu, SelectionDetails selectionDetails) {
        assert(selectionDetails != null);

        MenuItem openInNewWindow = menu.findItem(R.id.menu_open_in_new_window);
        MenuItem rename = menu.findItem(R.id.menu_rename);
        MenuItem pasteInto = menu.findItem(R.id.menu_paste_into_folder);

        updateOpenInNewWindow(openInNewWindow, selectionDetails);
        updateRename(rename, selectionDetails);
        updatePasteInto(pasteInto, selectionDetails);

        updateContextMenu(menu, selectionDetails);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Update shared context menu items of both files and folders context menus.
     */
    public void updateContextMenu(Menu menu, SelectionDetails selectionDetails) {
        assert(selectionDetails != null);

        MenuItem cut = menu.findItem(R.id.menu_cut_to_clipboard);
        MenuItem copy = menu.findItem(R.id.menu_copy_to_clipboard);
        MenuItem delete = menu.findItem(R.id.menu_delete);

        final boolean canCopy =
                selectionDetails.size() > 0 && !selectionDetails.containsPartialFiles();
        final boolean canDelete = selectionDetails.canDelete();
        cut.setEnabled(canCopy && canDelete);
        copy.setEnabled(canCopy);
        delete.setEnabled(canDelete);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to an empty pane.
     */
    public void updateContextMenuForContainer(Menu menu, DirectoryDetails directoryDetails) {
        MenuItem paste = menu.findItem(R.id.menu_paste_from_clipboard);
        MenuItem selectAll = menu.findItem(R.id.menu_select_all);

        paste.setEnabled(directoryDetails.hasItemsToPaste() && directoryDetails.canCreateDoc());
        updateSelectAll(selectAll);
    }

    /**
     * @see RootsFragment#onCreateContextMenu
     */
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

    void updateOpenInActionMode(MenuItem open, SelectionDetails selectionDetails) {
        open.setVisible(false);
    }

    void updateOpenWith(MenuItem openWith, SelectionDetails selectionDetails) {
        openWith.setVisible(false);
    }

    void updateOpenInNewWindow(MenuItem openInNewWindow, SelectionDetails selectionDetails) {
        openInNewWindow.setVisible(false);
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
        pasteInto.setVisible(false);
    }

    abstract void updateOpenInContextMenu(MenuItem open, SelectionDetails selectionDetails);
    abstract void updateSelectAll(MenuItem selectAll);
    abstract void updateCreateDir(MenuItem createDir, DirectoryDetails directoryDetails);

    /**
     * Access to meta data about the selection.
     */
    public interface SelectionDetails {
        boolean containsDirectories();

        boolean containsFiles();

        int size();

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

        public boolean canCreateDoc() {
            return isInRecents() ? false : mActivity.getCurrentDirectory().isCreateSupported();
        }

        public boolean isInRecents() {
            return mActivity.getCurrentDirectory() == null;
        }

        public boolean canCreateDirectory() {
            return mActivity.canCreateDirectory();
        }
    }
}
