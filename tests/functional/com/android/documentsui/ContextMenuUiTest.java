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
import static com.android.documentsui.flags.Flags.FLAG_DESKTOP_FILE_HANDLING_RO;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_ZIP_NG_RO;
import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;

import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.util.FileUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

@LargeTest
public class ContextMenuUiTest extends ActivityTestJunit4<FilesActivity> {
    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                final Uri dir1 =
                                        docsHelper.createFolder(root, TestFilesRule.DIR_NAME_1);
                                docsHelper.createFolder(dir1, "ChildDir1");
                                docsHelper.createDocument(root, "text/plain", "file0.log");
                                docsHelper.createDocument(root, "image/png", "file1.png");
                                docsHelper.createDocument(root, "text/csv", "file2.csv");
                                docsHelper.createDocument(root, "application/zip", "archive.zip");
                                docsHelper.createDocument(root, "text/plain", "anotherFile0.log");
                                docsHelper.createDocument(root, "text/plain", "poodles.text");
                            });

    private Map<String, Boolean> menuItems;

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
        menuItems = new HashMap<>();

        menuItems.put("Extract", false);
        menuItems.put("Browse", false);
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
    @DisableFlags({FLAG_DESKTOP_FILE_HANDLING_RO})
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
    @EnableFlags({FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testContextMenu_onFilePngDesktop() throws Exception {
        RootInfo root = mDocsHelper.getRoot(ROOT_0_ID);
        DocumentInfo doc = mDocsHelper.findFile(root.documentId, "file1.png");
        int pngOpeningApps = FileUtils.countOpeningApps(doc, context.getPackageManager());

        menuItems.put("Share", true);
        menuItems.put("Open", isDesktopFileHandlingFlagEnabled());
        // On desktop, "open with" is only shown when the file has multiple opening apps.
        // Ideally we would mock this, but we can't in these functional tests.
        menuItems.put("Open with", pngOpeningApps > 1);
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Rename", true);
        menuItems.put("Delete", true);

        bots.directory.rightClickDocument("file1.png");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    /*
     * Repeating the OnFile test again with a CSV to test the behaviour when there are no opening
     * apps. Obviously we cannot enforce this but this is likely on most devices.
     *
     * The test will still pass even if the device has 2+ opening apps for CSV, it just doesn't
     * verify that we are hiding "open with" when it needs to be.
     */
    @Test
    @EnableFlags({FLAG_DESKTOP_FILE_HANDLING_RO})
    public void testContextMenu_onFileCsvDesktop() throws Exception {
        RootInfo root = mDocsHelper.getRoot(ROOT_0_ID);
        DocumentInfo doc = mDocsHelper.findFile(root.documentId, "file2.csv");
        int csvOpeningApps = FileUtils.countOpeningApps(doc, context.getPackageManager());

        menuItems.put("Share", true);
        menuItems.put("Open", isDesktopFileHandlingFlagEnabled());
        // On desktop, "open with" is only shown when the file has multiple opening apps.
        // Ideally we would mock this, but we can't in these functional tests.
        menuItems.put("Open with", csvOpeningApps > 1);
        menuItems.put("Cut", true);
        menuItems.put("Copy", true);
        menuItems.put("Rename", true);
        menuItems.put("Delete", true);

        bots.directory.rightClickDocument("file2.csv");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO})
    public void testContextMenu_onArchive_shouldHaveBrowseMenuItem() throws Exception {
        menuItems.clear();
        menuItems.put("Extract", true);
        menuItems.put("Browse", true);

        bots.directory.rightClickDocument("archive.zip");
        bots.menu.assertPresentMenuItems(menuItems);
    }

    @Test
    @DisableFlags({FLAG_ZIP_NG_RO})
    public void testContextMenu_onArchive_shouldNotHaveBrowseMenuItem() throws Exception {
        menuItems.clear();
        menuItems.put("Extract", false);
        menuItems.put("Browse", false);

        bots.directory.rightClickDocument("archive.zip");
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
