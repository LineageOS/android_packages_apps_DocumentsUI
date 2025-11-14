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

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.KeyEvent;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.CheckAndForceMaterial3Flag;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SortDocumentUiTest extends ActivityTestJunit4<FilesActivity> {

    private static final String DIR_1 = "folder_1";
    private static final String DIR_2 = "dir_2";

    private static final String FILE_1 = "file_1";
    private static final String FILE_2 = "doc_2";
    private static final String FILE_3 = "image_3";

    private static final String MIME_1 = "text/plain"; // Plain text
    private static final String MIME_2 = "text/html"; // HTML document
    private static final String MIME_3 = "image/jpeg"; // JPG image

    private static final String[] FILES = {FILE_1, FILE_3, FILE_2};
    private static final String[] FILES_IN_MODIFIED_DESC = reverse(FILES);
    private static final String[] MIMES = {MIME_1, MIME_3, MIME_2};
    private static final String[] DIRS = {DIR_1, DIR_2};
    private static final String[] DIRS_IN_MODIFIED_DESC = reverse(DIRS);
    private static final String[] DIRS_IN_NAME_ASC = {DIR_2, DIR_1};
    private static final String[] DIRS_IN_NAME_DESC = reverse(DIRS_IN_NAME_ASC);
    private static final String[] FILES_IN_NAME_ASC = {FILE_2, FILE_1, FILE_3};
    private static final String[] FILES_IN_NAME_DESC = reverse(FILES_IN_NAME_ASC);
    private static final String[] FILES_IN_SIZE_ASC = {FILE_2, FILE_1, FILE_3};
    private static final String[] FILES_IN_SIZE_DESC = reverse(FILES_IN_SIZE_ASC);
    private static final String[] FILES_IN_TYPE_ASC = {FILE_2, FILE_3, FILE_1};
    private static final String[] FILES_IN_TYPE_DESC = reverse(FILES_IN_TYPE_ASC);

    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule(/* skipCreation */ true);

    private static String[] reverse(String[] array) {
        String[] ret = new String[array.length];

        for (int i = 0; i < array.length; ++i) {
            ret[ret.length - i - 1] = array[i];
        }

        return ret;
    }

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
    }

    private void initFiles() throws Exception {
        initFiles(0);
    }

    /**
     * Initiate test files. It allows waiting between creations of files, so that we can assure the
     * modified date of each document is different.
     *
     * @param sleep time to sleep in ms
     */
    private void initFiles(long sleep) throws Exception {
        RootInfo root = mTestFilesRule.docsHelper.getRoot(StubProvider.ROOT_0_ID);
        for (int i = 0; i < FILES.length; ++i) {
            Uri uri =
                    mTestFilesRule.docsHelper.createDocument(root, MIMES[i], FILES[i]);
            mTestFilesRule.docsHelper.writeDocument(uri, FILES[i].getBytes());

            Thread.sleep(sleep);
        }

        for (String dir : DIRS) {
            mTestFilesRule.docsHelper.createFolder(root, dir);

            Thread.sleep(sleep);
        }
    }

    @Test
    public void testDefaultSortByNameAscending() throws Exception {
        initFiles();
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_NAME_ASC);
    }

    @Test
    public void testSortByName_Descending_listMode() throws Exception {
        initFiles();

        bots.main.switchToListMode();

        bots.sort.sortBy(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_DESC, FILES_IN_NAME_DESC);
    }

    @Test
    public void testSortBySize_Ascending_listMode() throws Exception {
        initFiles();

        bots.main.switchToListMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_SIZE, SortDimension.SORT_DIRECTION_ASCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_SIZE_ASC);
    }

    @Test
    public void testSortBySize_Descending_listMode() throws Exception {
        initFiles();

        bots.main.switchToListMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_SIZE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_SIZE_DESC);
    }

    @Test
    public void testSortByModified_Ascending_listMode() throws Exception {
        initFiles(1000);

        bots.main.switchToListMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_DATE, SortDimension.SORT_DIRECTION_ASCENDING);
        bots.directory.assertOrder(DIRS, FILES);
    }

    @Test
    public void testSortByModified_Descending_listMode() throws Exception {
        initFiles(1000);

        bots.main.switchToListMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_DATE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_MODIFIED_DESC, FILES_IN_MODIFIED_DESC);
    }

    @Test
    public void testSortByType_Ascending_listMode() throws Exception {
        initFiles();

        bots.main.switchToListMode();

        bots.sort.sortBy(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE, SortDimension.SORT_DIRECTION_ASCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_TYPE_ASC);
    }

    @Test
    public void testSortByType_Descending_listMode() throws Exception {
        initFiles();

        bots.main.switchToListMode();

        bots.sort.sortBy(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_TYPE_DESC);
    }

    @Test
    public void testSortByName_Descending_gridMode() throws Exception {
        initFiles();

        bots.main.switchToGridMode();

        bots.sort.sortBy(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_DESC, FILES_IN_NAME_DESC);
    }

    @Test
    public void testSortBySize_Ascending_gridMode() throws Exception {
        initFiles();

        bots.main.switchToGridMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_SIZE, SortDimension.SORT_DIRECTION_ASCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_SIZE_ASC);
    }

    @Test
    public void testSortBySize_Descending_gridMode() throws Exception {
        initFiles();

        bots.main.switchToGridMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_SIZE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_SIZE_DESC);
    }

    @Test
    public void testSortByModified_Ascending_gridMode() throws Exception {
        initFiles(1000);

        bots.main.switchToGridMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_DATE, SortDimension.SORT_DIRECTION_ASCENDING);
        bots.directory.assertOrder(DIRS, FILES);
    }

    @Test
    public void testSortByModified_Descending_gridMode() throws Exception {
        initFiles(1000);

        bots.main.switchToGridMode();

        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_DATE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_MODIFIED_DESC, FILES_IN_MODIFIED_DESC);
    }

    @Test
    public void testSortByType_Ascending_gridMode() throws Exception {
        initFiles();

        bots.main.switchToGridMode();

        bots.sort.sortBy(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE, SortDimension.SORT_DIRECTION_ASCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_TYPE_ASC);
    }

    @Test
    public void testSortByType_Descending_gridMode() throws Exception {
        initFiles();

        bots.main.switchToGridMode();

        bots.sort.sortBy(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_TYPE_DESC);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_MATERIAL3)
    public void testSortByArrowIcon() throws Exception {
        initFiles();

        bots.main.switchToListMode();

        // Set up the sort in descending direction to allow deterministic behaviour of the sort
        // icon.
        bots.sort.sortBy(
                SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_DESCENDING);
        bots.directory.assertOrder(DIRS_IN_NAME_DESC, FILES_IN_NAME_DESC);

        // Tab in reverse order until the sort icon is the focus (this avoids tabbing through the
        // roots list which is quite long in tests).
        while (!bots.sort.isSortIconFocused()) {
            bots.keyboard.pressKey(KeyEvent.KEYCODE_TAB, KeyEvent.META_SHIFT_LEFT_ON);
        }

        // Press enter on the sort icon and ensure the sort direction is changed.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_ENTER);
        bots.directory.assertOrder(DIRS_IN_NAME_ASC, FILES_IN_NAME_ASC);

        // Space should also work in the same way as the ENTER key.
        bots.keyboard.pressKey(KeyEvent.KEYCODE_SPACE);
        bots.directory.assertOrder(DIRS_IN_NAME_DESC, FILES_IN_NAME_DESC);
    }
}
