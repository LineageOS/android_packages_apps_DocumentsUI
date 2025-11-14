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

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;

import android.graphics.Point;
import android.graphics.Rect;

import androidx.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

@LargeTest
public class ContextMenuUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createFolderInRoot(ROOT_0_ID, TestFilesRule.DIR_NAME_1)
                    .createFolderWithParent(TestFilesRule.DIR_NAME_1, "ChildDir1")
                    .createFileInRoot(ROOT_0_ID, "file0.log", "text/plain")
                    .createFileInRoot(ROOT_0_ID, "file1.png", "image/png")
                    .createFileInRoot(ROOT_0_ID, "file2.csv", "text/csv")
                    .createFileInRoot(ROOT_0_ID, "anotherFile0.log", "text/plain")
                    .createFileInRoot(ROOT_0_ID, "poodles.text", "text/plain");

    private Map<String, Boolean> menuItems;

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
        menuItems = new HashMap<>();

        menuItems.put("Share", false);
        menuItems.put("Open", false);
        menuItems.put("Open with", false);
        menuItems.put("Cut", false);
        menuItems.put("Copy", false);
        menuItems.put("Rename", false);
        menuItems.put("Delete", false);
        menuItems.put("Open in new window", false);
        menuItems.put("Select all", false);
        menuItems.put("New folder", false);
    }

    @Test
    public void testContextMenu_onFile() throws Exception {
        menuItems.put("Share", true);
        menuItems.put("Open", isDesktopFileHandlingFlagEnabled());
        menuItems.put("Open with", true);
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Rename", true);
        menuItems.put("Delete", true);

        bots.directory.rightClickDocument("file1.png");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    public void testContextMenu_onDir() throws Exception {
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Open in new window", true);
        menuItems.put("Delete", true);
        menuItems.put("Rename", true);
        bots.directory.rightClickDocument("Dir1");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    public void testContextMenu_onMixedFileDir() throws Exception {
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Delete", true);
        bots.directory.selectDocument("file1.png", 1);
        bots.directory.selectDocument("Dir1", 2);
        bots.directory.rightClickDocument("Dir1");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    public void testContextMenu_onEmptyArea() throws Exception {
        menuItems.put("Select all", true);
        menuItems.put("New folder", true);
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Rect dirBounds = bots.directory.findDocument(TestFilesRule.DIR_NAME_1).getBounds();

        bots.main.switchToGridMode();
        // right side of dir1 area
        bots.directory.rightClickDocument(new Point(dirListBounds.right - 1, dirBounds.centerY()));
        bots.menu.assertPresentMenuItems(menuItems);
    }
}
