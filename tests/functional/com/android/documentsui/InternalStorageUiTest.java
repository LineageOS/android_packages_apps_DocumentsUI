/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.assertNull;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * A Ui test will do tests in the internal storage root. It is implemented because some operations
 * is failed and its result will different from the test on the StubProvider. b/115304092 is a
 * example which only happen on root from ExternalStorageProvider.
 */
@LargeTest
public class InternalStorageUiTest extends ActivityTestJunit4<FilesActivity> {

    private static final String fileName = "!Test3345678";
    private static final String newFileName = "!9527Test";
    private RootInfo rootPrimary;

    @Before
    public void setUpTest() throws Exception {
        mDocsHelper = new DocumentsProviderHelper(userId, Providers.AUTHORITY_STORAGE, context,
                Providers.AUTHORITY_STORAGE);
        rootPrimary = mDocsHelper.getRoot(Providers.ROOT_ID_DEVICE);

        bots.roots.openRoot(rootPrimary.title);
        deleteTestFiles();
    }

    @After
    public void tearDownTest() throws Exception {
        deleteTestFiles();
    }

    @HugeLongTest
    @Test
    public void testRenameFile() throws Exception {
        createTestFiles();

        bots.directory.selectDocument(fileName, 1);
        device.waitForIdle();

        bots.main.clickRename();

        bots.main.setDialogText(newFileName);
        device.waitForIdle();

        bots.keyboard.pressEnter();

        bots.directory.assertDocumentsAbsent(fileName);
        bots.directory.assertDocumentsVisible(newFileName);
        // Snackbar will not show if no exception.
        assertNull(bots.directory.getSnackbar(context.getString(R.string.rename_error)));
    }

    private void createTestFiles() {
        mDocsHelper.createFolder(rootPrimary, fileName);
    }

    private void deleteTestFiles() throws Exception {
        boolean selected = false;
        // Delete the added file for not affect user and also avoid error on next test.
        if (bots.directory.hasDocuments(fileName)) {
            bots.directory.selectDocument(fileName, 1);
            device.waitForIdle();
            selected = true;
        }
        if (bots.directory.hasDocuments(newFileName)) {
            bots.directory.selectDocument(newFileName, 1);
            device.waitForIdle();
            selected = true;
        }
        if (selected) {
            bots.main.clickDelete();
            device.waitForIdle();
            bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        }
    }
}
