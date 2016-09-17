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

import android.app.Fragment;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;

public final class FilesMenuManager extends MenuManager {

    public FilesMenuManager(SearchViewManager searchManager, State displayState) {
        super(searchManager, displayState);
    }

    @Override
    public void updateOptionMenu(Menu menu, DirectoryDetails details) {
        super.updateOptionMenu(menu, details);

        // It hides icon if searching in progress
        mSearchManager.updateMenu();
    }

    @Override
    public void showContextMenu(Fragment f, View v, float x, float y) {
        // Register context menu here so long-press doesn't trigger this context floating menu.
        f.registerForContextMenu(v);
        v.showContextMenu(x, y);
        f.unregisterForContextMenu(v);
    }

    @Override
    public void inflateContextMenuForContainer(
            Menu menu, MenuInflater inflater, DirectoryDetails directoryDetails) {
        inflater.inflate(R.menu.container_context_menu, menu);
        updateContextMenuForContainer(menu, directoryDetails);
    }

    @Override
    public void inflateContextMenuForDocs(
            Menu menu, MenuInflater inflater, SelectionDetails selectionDetails) {
        final boolean hasDir = selectionDetails.containsDirectories();
        final boolean hasFile = selectionDetails.containsFiles();

        assert(hasDir || hasFile);
        if (!hasDir) {
            inflater.inflate(R.menu.file_context_menu, menu);
            updateContextMenuForFiles(menu, selectionDetails);
            return;
        }

        if (!hasFile) {
            inflater.inflate(R.menu.dir_context_menu, menu);
            updateContextMenuForDirs(menu, selectionDetails);
            return;
        }

        inflater.inflate(R.menu.mixed_context_menu, menu);
        updateContextMenu(menu, selectionDetails);
    }

    @Override
    void updateSettings(MenuItem settings, RootInfo root) {
        settings.setVisible(true);
        settings.setEnabled(root.hasSettings());
    }

    @Override
    void updateEject(MenuItem eject, RootInfo root) {
        eject.setVisible(true);
        eject.setEnabled(root.supportsEject() && !root.ejecting);
    }

    @Override
    void updateSettings(MenuItem settings, DirectoryDetails directoryDetails) {
        settings.setVisible(directoryDetails.hasRootSettings());
    }

    @Override
    void updateNewWindow(MenuItem newWindow, DirectoryDetails directoryDetails) {
        newWindow.setVisible(directoryDetails.shouldShowFancyFeatures());
    }

    @Override
    void updateOpenInContextMenu(MenuItem open, SelectionDetails selectionDetails) {
        open.setEnabled(selectionDetails.size() == 1
                && !selectionDetails.containsPartialFiles());
    }

    @Override
    void updateOpenWith(MenuItem openWith, SelectionDetails selectionDetails) {
        openWith.setEnabled(selectionDetails.size() == 1
                && !selectionDetails.containsPartialFiles());
    }

    @Override
    void updateOpenInNewWindow(MenuItem openInNewWindow, SelectionDetails selectionDetails) {
        openInNewWindow.setEnabled(selectionDetails.size() == 1
            && !selectionDetails.containsPartialFiles());
    }

    @Override
    void updateMoveTo(MenuItem moveTo, SelectionDetails selectionDetails) {
        moveTo.setVisible(true);
        moveTo.setEnabled(!selectionDetails.containsPartialFiles() && selectionDetails.canDelete());
    }

    @Override
    void updateCopyTo(MenuItem copyTo, SelectionDetails selectionDetails) {
        copyTo.setVisible(true);
        copyTo.setEnabled(!selectionDetails.containsPartialFiles());
    }

    @Override
    void updatePasteInto(MenuItem pasteInto, SelectionDetails selectionDetails) {
        pasteInto.setEnabled(selectionDetails.canPasteInto());
    }

    @Override
    void updateSelectAll(MenuItem selectAll) {
        selectAll.setVisible(true);
    }

    @Override
    void updateCreateDir(MenuItem createDir, DirectoryDetails directoryDetails) {
        createDir.setVisible(true);
        createDir.setEnabled(directoryDetails.canCreateDirectory());
    }

    @Override
    void updateShare(MenuItem share, SelectionDetails selectionDetails) {
        share.setVisible(!selectionDetails.containsDirectories()
                && !selectionDetails.containsPartialFiles());
    }

    @Override
    void updateDelete(MenuItem delete, SelectionDetails selectionDetails) {
        delete.setVisible(selectionDetails.canDelete());
    }

    @Override
    void updateRename(MenuItem rename, SelectionDetails selectionDetails) {
        rename.setVisible(true);
        rename.setEnabled(!selectionDetails.containsPartialFiles() && selectionDetails.canRename());
    }
}