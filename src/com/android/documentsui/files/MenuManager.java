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

package com.android.documentsui.files;

import android.app.Fragment;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.queries.SearchViewManager;

import java.util.List;
import java.util.function.IntFunction;

public final class MenuManager extends com.android.documentsui.MenuManager {

    public MenuManager(
            SearchViewManager searchManager,
            State displayState,
            DirectoryDetails dirDetails) {
        super(searchManager, displayState, dirDetails);
    }

    @Override
    public void updateOptionMenu(Menu menu) {
        super.updateOptionMenu(menu);

        // It hides icon if searching in progress
        mSearchManager.updateMenu();
    }

    @Override
    public void updateKeyboardShortcutsMenu(
            List<KeyboardShortcutGroup> data, IntFunction<String> stringSupplier) {
        KeyboardShortcutGroup group = new KeyboardShortcutGroup(
                stringSupplier.apply(R.string.app_label));
        group.addItem(new KeyboardShortcutInfo(
                stringSupplier.apply(R.string.menu_cut_to_clipboard), KeyEvent.KEYCODE_X,
                KeyEvent.META_CTRL_ON));
        group.addItem(new KeyboardShortcutInfo(
                stringSupplier.apply(R.string.menu_copy_to_clipboard), KeyEvent.KEYCODE_C,
                KeyEvent.META_CTRL_ON));
        group.addItem(new KeyboardShortcutInfo(
                stringSupplier.apply(R.string.menu_paste_from_clipboard), KeyEvent.KEYCODE_V,
                KeyEvent.META_CTRL_ON));
        group.addItem(new KeyboardShortcutInfo(
                stringSupplier.apply(R.string.menu_create_dir), KeyEvent.KEYCODE_E,
                KeyEvent.META_CTRL_ON));
        group.addItem(new KeyboardShortcutInfo(
                stringSupplier.apply(R.string.menu_select_all), KeyEvent.KEYCODE_A,
                KeyEvent.META_CTRL_ON));
        group.addItem(new KeyboardShortcutInfo(
                stringSupplier.apply(R.string.menu_new_window), KeyEvent.KEYCODE_N,
                KeyEvent.META_CTRL_ON));
        data.add(group);
    }

    @Override
    public void showContextMenu(Fragment f, View v, float x, float y) {
        // Register context menu here so long-press doesn't trigger this context floating menu.
        f.registerForContextMenu(v);
        v.showContextMenu(x, y);
        f.unregisterForContextMenu(v);
    }

    @Override
    public void inflateContextMenuForContainer(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.container_context_menu, menu);
        updateContextMenuForContainer(menu);
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
    protected void updateSettings(MenuItem settings, RootInfo root) {
        settings.setVisible(true);
        settings.setEnabled(root.hasSettings());
    }

    @Override
    protected void updateEject(MenuItem eject, RootInfo root) {
        eject.setVisible(root.supportsEject());
        eject.setEnabled(!root.ejecting);
    }

    @Override
    protected void updateSettings(MenuItem settings) {
        settings.setVisible(mDirDetails.hasRootSettings());
    }

    @Override
    protected void updateNewWindow(MenuItem newWindow) {
        newWindow.setVisible(true);
    }

    @Override
    protected void updateOpenInContextMenu(MenuItem open, SelectionDetails selectionDetails) {
        open.setEnabled(selectionDetails.size() == 1
                && !selectionDetails.containsPartialFiles());
    }

    @Override
    protected void updateOpenWith(MenuItem openWith, SelectionDetails selectionDetails) {
        openWith.setEnabled(selectionDetails.canOpenWith());
    }

    @Override
    protected void updateOpenInNewWindow(
            MenuItem openInNewWindow, SelectionDetails selectionDetails) {
        openInNewWindow.setEnabled(selectionDetails.size() == 1
            && !selectionDetails.containsPartialFiles());
    }

    @Override
    protected void updateOpenInNewWindow(MenuItem openInNewWindow, RootInfo root) {
        assert(openInNewWindow.isVisible() && openInNewWindow.isEnabled());
    }

    @Override
    protected void updateMoveTo(MenuItem moveTo, SelectionDetails selectionDetails) {
        moveTo.setVisible(true);
        moveTo.setEnabled(!selectionDetails.containsPartialFiles() && selectionDetails.canDelete());
    }

    @Override
    protected void updateCopyTo(MenuItem copyTo, SelectionDetails selectionDetails) {
        copyTo.setVisible(true);
        copyTo.setEnabled(!selectionDetails.containsPartialFiles() &&
                !selectionDetails.canExtract());
    }

    @Override
    protected void updateCompress(MenuItem compress, SelectionDetails selectionDetails) {
        final boolean readOnly = !mDirDetails.canCreateDoc();
        compress.setVisible(true);
        compress.setEnabled(!readOnly && !selectionDetails.containsPartialFiles() &&
                !selectionDetails.canExtract());
    }

    @Override
    protected void updateExtractTo(MenuItem extractTo, SelectionDetails selectionDetails) {
        extractTo.setVisible(selectionDetails.canExtract());
    }

    @Override
    protected void updatePasteInto(MenuItem pasteInto, SelectionDetails selectionDetails) {
        pasteInto.setEnabled(selectionDetails.canPasteInto() && mDirDetails.hasItemsToPaste());
    }

    @Override
    protected void updatePasteInto(MenuItem pasteInto, RootInfo root, DocumentInfo docInfo) {
        pasteInto.setEnabled(root.supportsCreate()
                && docInfo != null
                && docInfo.isCreateSupported()
                && mDirDetails.hasItemsToPaste());
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll) {
        selectAll.setVisible(true);
    }

    @Override
    protected void updateCreateDir(MenuItem createDir) {
        createDir.setVisible(true);
        createDir.setEnabled(mDirDetails.canCreateDirectory());
    }

    @Override
    protected void updateShare(MenuItem share, SelectionDetails selectionDetails) {
        share.setVisible(!selectionDetails.containsDirectories()
                && !selectionDetails.containsPartialFiles()
                && !selectionDetails.canExtract());
    }

    @Override
    protected void updateDelete(MenuItem delete, SelectionDetails selectionDetails) {
        delete.setVisible(selectionDetails.canDelete());
    }

    @Override
    protected void updateRename(MenuItem rename, SelectionDetails selectionDetails) {
        rename.setVisible(true);
        rename.setEnabled(!selectionDetails.containsPartialFiles() && selectionDetails.canRename());
    }
}
