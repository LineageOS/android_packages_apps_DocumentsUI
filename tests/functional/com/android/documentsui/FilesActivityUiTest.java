/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.base.Providers.AUTHORITY_STORAGE;
import static com.android.documentsui.base.Providers.ROOT_ID_DEVICE;
import static com.android.documentsui.flags.Flags.FLAG_SINGLE_CLICK_TO_SELECT;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.util.Material3Config.getRes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.net.Uri;
import android.platform.test.annotations.DesktopTest;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.inspector.InspectorActivity;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FilesActivityUiTest extends ActivityTestJunit4<FilesActivity> {

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
                                docsHelper.createFolder(dir1, TestFilesRule.CHILD_DIR_1);
                                docsHelper.createDocument(root, "text/plain", "file0.log");
                                docsHelper.createDocument(root, "image/png", "file1.png");
                                docsHelper.createDocument(root, "text/csv", "file2.csv");
                                docsHelper.createDocument(root, "text/plain", "anotherFile0.log");
                                docsHelper.createDocument(root, "text/plain", "poodles.text");
                            });

    // Recents is a strange meta root that gathers entries from other providers.
    // It is special cased in a variety of ways, which is why we just want
    // to be able to click on it.
    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testClickRecent() throws Exception {
        bots.roots.openRoot("Recent");

        boolean showSearchBar = context.getResources().getBoolean(R.bool.show_search_bar);
        if (showSearchBar) {
            bots.main.assertSearchBarShow();
        } else {
            bots.main.assertSearchBarGone();
            bots.search.assertIconVisible(true);
            bots.main.assertWindowTitle("Recent");
        }
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testClickRecentM3() throws Exception {
        bots.roots.openRoot("Recent");

        bots.main.assertSearchBarGone();
        boolean showDockedSearch = context.getResources().getBoolean(
                getRes(R.bool.show_docked_search));
        if (showDockedSearch) {
            bots.main.assertDockedSearchBarShow();
        } else {
            bots.main.assertOptionsMenuSearchShow();
        }
        bots.main.assertWindowTitle("Recent");
    }

    private DocumentsProviderHelper setupStorageAuthorityDocsHelper() throws Exception {
        // Create DocumentsProviderHelper to create files in Internal storage.
        DocumentsProviderHelper storageDocsHelper =
                new DocumentsProviderHelper(
                        UserId.DEFAULT_USER, AUTHORITY_STORAGE, context, AUTHORITY_STORAGE);

        RootInfo primaryRoot = storageDocsHelper.getRoot(ROOT_ID_DEVICE);

        // Create Download folder if it doesn't exist.
        DocumentInfo info = storageDocsHelper.findFile(primaryRoot.documentId, "Download");

        if (info == null) {
            ContentResolver cr = context.getContentResolver();
            Uri uri = storageDocsHelper.createFolder(primaryRoot.documentId, "Download");
            info = DocumentInfo.fromUri(cr, uri, UserId.DEFAULT_USER);
        }

        assertTrue(info != null && info.isDirectory());
        return storageDocsHelper;
    }

    private void cleanupFile(String fileName, String primaryRootTitle)
            throws UiObjectNotFoundException {
        bots.roots.openRoot(primaryRootTitle);
        bots.directory.openDocument("Download");

        bots.directory.waitForDocument(fileName);
        bots.directory.selectDocument(fileName, 1);

        bots.main.clickDelete();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        bots.directory.findDocument(fileName).waitUntilGone(5000);
        assertFalse(bots.directory.hasDocuments(fileName));
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootClick_SetsWindowTitle() throws Exception {
        bots.roots.openRoot("Images");
        bots.main.assertWindowTitle("Images");
    }

    @Test
    public void testFilesListed() throws Exception {
        bots.directory.assertDocumentsVisible("file0.log", "file1.png", "file2.csv");
    }

    @Test
    public void testFilesList_LiveUpdate() throws Exception {
        // Minimize the chances of the files being invisible.
        bots.main.switchToListMode();

        // Create a file with a unique name.
        RootInfo root = mTestFilesRule.docsHelper.getRoot(ROOT_0_ID);
        String newFileName = "mxuadkjf.txt";
        mTestFilesRule.docsHelper.createDocument(root, "text/plain", newFileName);

        bots.directory.waitForDocument(newFileName);
        // Documents should be present, but may not necessary be visible on small screen.
        bots.directory.assertDocumentsPresent("file0.log", "file1.png", "file2.csv", newFileName);
    }

    @DesktopTest(cujs = {"b/434068747"})
    @Test
    public void testNavigate_byBreadcrumb() throws Exception {
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.CHILD_DIR_1);  // wait for known content
        bots.directory.assertDocumentsVisible(TestFilesRule.CHILD_DIR_1);

        device.waitForIdle();
        bots.breadcrumb.assertItemsPresent(TestFilesRule.DIR_NAME_1, "TEST_ROOT_0");

        bots.breadcrumb.clickItem("TEST_ROOT_0");
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);
    }

    @Test
    public void testNavigate_inFixedLayout_whileHasSelection() throws Exception {
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot(mTestFilesRule.getRoot(ROOT_0_ID).title);
            device.waitForIdle();
            bots.directory.selectDocument("file0.log", 1);

            // ensure no exception is thrown while navigating to a different root
            bots.roots.openRoot(mTestFilesRule.getRoot(ROOT_1_ID).title);
        }
    }

    @Test
    public void testNavigationToInspector() throws Exception {
        if(!features.isInspectorEnabled()) {
            return;
        }
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                InspectorActivity.class.getName(), null, false);
        bots.directory.selectDocument("file0.log", 1);
        bots.main.clickActionItem("Get info");
        monitor.waitForActivityWithTimeout(TIMEOUT);
    }

    @Test
    @HugeLongTest
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootChange_UpdatesSortHeader() throws Exception {

        // switch to separate display modes for two separate roots. Each
        // mode has its own distinct sort header. This should be remembered
        // by files app.
        bots.roots.openRoot("Images");
        bots.main.switchToGridMode();
        bots.roots.openRoot("Videos");
        bots.main.switchToListMode();

        // Now switch back and assert the correct mode sort header mode
        // is restored when we load the root with that display mode.
        bots.roots.openRoot("Images");
        bots.sort.assertHeaderHide();
        if (bots.main.inFixedLayout()) {
            bots.roots.openRoot("Videos");
            bots.sort.assertHeaderShow();
        } else {
            bots.roots.openRoot("Videos");
            bots.sort.assertHeaderHide();
        }
    }

    @Test
    @DisableFlags(FLAG_USE_MATERIAL3)
    public void testRootChange_NonM3PerRootViewModeState() throws Exception {
        // Assign different view modes across "Images" and "Videos" roots.
        // Images root --> grid mode
        // Videos root --> list mode
        bots.roots.openRoot("Images");
        bots.main.switchToGridMode();
        bots.main.assertInGridMode();
        bots.roots.openRoot("Videos");
        bots.main.switchToListMode();
        bots.main.assertInListMode();

        // Assert that the different roots maintain their respective view modes.
        bots.roots.openRoot("Images");
        bots.main.assertInGridMode();
        bots.roots.openRoot("Videos");
        bots.main.assertInListMode();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testRootChange_M3GlobalViewModeState() throws Exception {
        bots.roots.openRoot("Recent");
        bots.main.switchToGridMode();
        bots.main.assertInGridMode();

        // Switch to a different root and assert still in grid mode.
        bots.roots.openRoot(ROOT_0_ID);
        bots.main.assertInGridMode();

        // Switch back to list mode and assert still in list mode on a different root.
        bots.main.switchToListMode();
        bots.main.assertInListMode();
        bots.roots.openRoot("Recent");
        bots.main.assertInListMode();
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    public void testClearSelectionInRecentsResetsActions() throws Exception {
        // Ensure Downloads exists and get the location of the main root (e.g. "Pixel Tablet").
        DocumentsProviderHelper storageDocsHelper = setupStorageAuthorityDocsHelper();
        RootInfo primaryRoot = storageDocsHelper.getRoot(ROOT_ID_DEVICE);
        DocumentInfo info = storageDocsHelper.findFile(primaryRoot.documentId, "Download");

        // Create a file in "Download".
        final String fileName = "recent_file.txt";
        storageDocsHelper.createDocument(info.documentId, "text/plain", fileName);

        // Navigate to "Download" and ensure the file exists (this should ensure it also exists in
        // Recent).
        bots.roots.openRoot(primaryRoot.title);
        bots.directory.openDocument("Download");
        bots.directory.waitForDocument(fileName);

        // Open Recent and wait for the document to appear.
        bots.roots.openRoot("Recent");
        bots.directory.waitForDocument(fileName);

        try {
            // The search option menu shows up when no items are selected, use this as a proxy for
            // the options menu being refreshed (it should only show when a file is selected).
            onView(withId(R.id.action_menu_share)).check(doesNotExist());

            // Select the first document in Recents that has a selectable region. We have previously
            // staged a file so there should be at least 1, but depending on the device and the
            // cleanup of the device this might not always be the case. The document that gets
            // selected doesn't really matter, just that one is selected.
            bots.directory.selectFirstDocument();
            onView(withId(R.id.action_menu_share)).check(matches(isDisplayed()));

            // Deselect the file and ensure the share menu disappears (this ensures the menu is
            // refreshed).
            bots.directory.clearSelection();
            device.wait(Until.gone(By.desc("Share")), /* timeout= */ 5000);
        } finally {
            cleanupFile(fileName, primaryRoot.title);
        }
    }

    @Test
    public void testRecentsShowsZipFiles() throws Exception {
        DocumentsProviderHelper storageDocsHelper = setupStorageAuthorityDocsHelper();
        RootInfo primaryRoot = storageDocsHelper.getRoot(ROOT_ID_DEVICE);

        String createdFileName = null;
        try {
            DocumentInfo info = storageDocsHelper.findFile(primaryRoot.documentId, "Download");
            assertNotNull(info);

            // Create a zip file in "Download" folder. Since we are creating a file in the Download
            // folder, create a unique name that has little to no chance of colliding with actual
            // user files.
            createdFileName = "a_zip_test_" + UUID.randomUUID() + ".zip";
            storageDocsHelper.createDocument(info.documentId, "application/zip", createdFileName);
            bots.directory.waitForDocument(createdFileName);

            // Open Recent and wait for the newly created files to appear. We limit searches to just
            // this week to make the test run more efficiently.
            bots.roots.openRoot("Recent");

            // Verify that just created zip file appears among recent files. It should appear on top
            // so no scrolling.
            assertTrue(bots.directory.findDocument(createdFileName).exists());
        } finally {
            if (createdFileName != null) {
                cleanupFile(createdFileName, primaryRoot.title);
            }
        }
    }

    @Test
    @EnableFlags(FLAG_SINGLE_CLICK_TO_SELECT)
    public void testSingleClickToSelect_enabled() throws Exception {
        doTestSingleClickToSelect(true);
    }

    @Test
    @DisableFlags(FLAG_SINGLE_CLICK_TO_SELECT)
    public void testSingleClickToSelect_disabled() throws Exception {
        doTestSingleClickToSelect(false);
    }

    private void doTestSingleClickToSelect(boolean flagEnabled) throws Exception {
        final String label = TestFilesRule.DIR_NAME_1;
        UiObject2 ancestorObject = bots.directory.findItemAndSelectionHotspot(label)[0];
        UiObject2 labelObject = ancestorObject.findObject(By.text(label));
        labelObject.click();

        if (flagEnabled) {
            bots.directory.assertSelection(1);
        } else {
            bots.directory.assertNoSelection();
        }
    }
}
