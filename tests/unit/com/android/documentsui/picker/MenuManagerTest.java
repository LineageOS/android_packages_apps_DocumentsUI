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
import static com.android.documentsui.base.State.ACTION_OPEN;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.provider.DocumentsContract.Root;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.testing.TestDirectoryDetails;
import com.android.documentsui.testing.TestMenu;
import com.android.documentsui.testing.TestMenuItem;
import com.android.documentsui.testing.TestSearchViewManager;
import com.android.documentsui.testing.TestSelectionDetails;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class MenuManagerTest {

    private TestMenu testMenu;
    private TestMenuItem open;
    private TestMenuItem openInNewWindow;
    private TestMenuItem openWith;
    private TestMenuItem share;
    private TestMenuItem delete;
    private TestMenuItem rename;
    private TestMenuItem selectAll;
    private TestMenuItem createDir;
    private TestMenuItem grid;
    private TestMenuItem list;
    private TestMenuItem cut;
    private TestMenuItem copy;
    private TestMenuItem paste;
    private TestMenuItem pasteInto;
    private TestMenuItem advanced;
    private TestMenuItem settings;
    private TestMenuItem eject;

    private TestSelectionDetails selectionDetails;
    private TestDirectoryDetails directoryDetails;
    private TestSearchViewManager testSearchManager;
    private State state = new State();
    private RootInfo testRootInfo;

    @Before
    public void setUp() {
        testMenu = TestMenu.create();
        open = testMenu.findItem(R.id.menu_open);
        openInNewWindow = testMenu.findItem(R.id.menu_open_in_new_window);
        openWith = testMenu.findItem(R.id.menu_open_with);
        share = testMenu.findItem(R.id.menu_share);
        delete = testMenu.findItem(R.id.menu_delete);
        rename =  testMenu.findItem(R.id.menu_rename);
        selectAll = testMenu.findItem(R.id.menu_select_all);
        createDir = testMenu.findItem(R.id.menu_create_dir);
        grid = testMenu.findItem(R.id.menu_grid);
        list = testMenu.findItem(R.id.menu_list);
        cut = testMenu.findItem(R.id.menu_cut_to_clipboard);
        copy = testMenu.findItem(R.id.menu_copy_to_clipboard);
        paste = testMenu.findItem(R.id.menu_paste_from_clipboard);
        pasteInto = testMenu.findItem(R.id.menu_paste_into_folder);

        advanced = testMenu.findItem(R.id.menu_advanced);
        settings = testMenu.findItem(R.id.menu_settings);
        eject = testMenu.findItem(R.id.menu_eject_root);

        selectionDetails = new TestSelectionDetails();
        directoryDetails = new TestDirectoryDetails();
        testSearchManager = new TestSearchViewManager();
        testRootInfo = new RootInfo();
        state.action = ACTION_CREATE;
        state.allowMultiple = true;
    }

    @Test
    public void testActionMenu() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateActionMenu(testMenu, selectionDetails);

        open.assertInvisible();
        delete.assertInvisible();
        share.assertInvisible();
        rename.assertInvisible();
        selectAll.assertVisible();
    }

    @Test
    public void testActionMenu_openAction() {
        state.action = ACTION_OPEN;
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateActionMenu(testMenu, selectionDetails);

        open.assertVisible();
    }


    @Test
    public void testActionMenu_notAllowMultiple() {
        state.allowMultiple = false;
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateActionMenu(testMenu, selectionDetails);

        selectAll.assertInvisible();
    }

    @Test
    public void testOptionMenu() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateOptionMenu(testMenu, directoryDetails);

        advanced.assertInvisible();
        advanced.assertTitle(R.string.menu_advanced_show);
        createDir.assertDisabled();
        assertTrue(testSearchManager.showMenuCalled());
    }

    @Test
    public void testOptionMenu_notPicking() {
        state.action = ACTION_OPEN;
        state.derivedMode = State.MODE_LIST;
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateOptionMenu(testMenu, directoryDetails);

        createDir.assertInvisible();
        grid.assertVisible();
        list.assertInvisible();
        assertFalse(testSearchManager.showMenuCalled());
    }

    @Test
    public void testOptionMenu_canCreateDirectory() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        directoryDetails.canCreateDirectory = true;
        mgr.updateOptionMenu(testMenu, directoryDetails);

        createDir.assertEnabled();
    }

    @Test
    public void testOptionMenu_showAdvanced() {
        state.showAdvanced = true;
        state.showAdvancedOption = true;
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateOptionMenu(testMenu, directoryDetails);

        advanced.assertVisible();
        advanced.assertTitle(R.string.menu_advanced_hide);
    }

    @Test
    public void testOptionMenu_inRecents() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        directoryDetails.isInRecents = true;
        mgr.updateOptionMenu(testMenu, directoryDetails);

        grid.assertInvisible();
        list.assertInvisible();
    }

    @Test
    public void testContextMenu_EmptyArea() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateContextMenuForContainer(testMenu, directoryDetails);
        selectAll.assertVisible();
        paste.assertVisible();
        createDir.assertVisible();
    }

    @Test
    public void testContextMenu_OnFile() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateContextMenuForFiles(testMenu, selectionDetails);
        // We don't want share in pickers.
        share.assertInvisible();
        // We don't want openWith in pickers.
        openWith.assertInvisible();
        cut.assertVisible();
        copy.assertVisible();
        rename.assertInvisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnDirectory() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        selectionDetails.canPasteInto = true;
        mgr.updateContextMenuForDirs(testMenu, selectionDetails);
        // We don't want openInNewWindow in pickers
        openInNewWindow.assertInvisible();
        cut.assertVisible();
        copy.assertVisible();
        // Doesn't matter if directory is selected, we don't want pasteInto for DocsActivity
        pasteInto.assertInvisible();
        rename.assertInvisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnMixedDocs() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        cut.assertVisible();
        copy.assertVisible();
        delete.assertVisible();
    }

    @Test
    public void testContextMenu_OnMixedDocs_hasPartialFile() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.containPartial = true;
        selectionDetails.canDelete = true;
        mgr.updateContextMenu(testMenu, selectionDetails);
        cut.assertVisible();
        cut.assertDisabled();
        copy.assertVisible();
        copy.assertDisabled();
        delete.assertVisible();
        delete.assertEnabled();
    }

    @Test
    public void testContextMenu_OnMixedDocs_hasUndeletableFile() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        selectionDetails.containDirectories = true;
        selectionDetails.containFiles = true;
        selectionDetails.size = 2;
        selectionDetails.canDelete = false;
        mgr.updateContextMenu(testMenu, selectionDetails);
        cut.assertVisible();
        cut.assertDisabled();
        copy.assertVisible();
        copy.assertEnabled();
        delete.assertVisible();
        delete.assertDisabled();
    }

    @Test
    public void testRootContextMenu() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        mgr.updateRootContextMenu(testMenu, testRootInfo);

        eject.assertInvisible();
        settings.assertInvisible();
    }

    @Test
    public void testRootContextMenu_hasRootSettings() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        testRootInfo.flags = Root.FLAG_HAS_SETTINGS;
        mgr.updateRootContextMenu(testMenu, testRootInfo);

        settings.assertInvisible();
    }

    @Test
    public void testRootContextMenu_canEject() {
        MenuManager mgr = new MenuManager(testSearchManager, state);
        testRootInfo.flags = Root.FLAG_SUPPORTS_EJECT;
        mgr.updateRootContextMenu(testMenu, testRootInfo);

        eject.assertInvisible();
    }
}
