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

import static com.android.documentsui.StubProvider.ROOT_0_ID;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.support.test.filters.LargeTest;
import android.support.test.uiautomator.UiObject;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.services.TestNotificationService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
* This class test the below points
* - Undo the deleted files
*/
@LargeTest
public class FileUndoDeletionUiTest extends ActivityTest<FilesActivity> {
    private static final String TAG = "FileUndoDeletionUiTest";

    private static final int DUMMY_FILE_COUNT = 3;

    private String[] filenames = new String[DUMMY_FILE_COUNT];

    public FileUndoDeletionUiTest() {
        super(FilesActivity.class);
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Set a flag to prevent many refreshes.
        Bundle bundle = new Bundle();
        bundle.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        mDocsHelper.configure(null, bundle);

        initTestFiles();
    }

    @Override
    public void initTestFiles() {
        for (int i = 0; i < DUMMY_FILE_COUNT; i++) {
            filenames[i] = "file" + i + ".log";
            mDocsHelper.createDocument(rootDir0, "text/plain", filenames[i]);
        }
    }

    public void testDeleteUndoDocumentsUI() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);
        bots.main.clickToolbarOverflowItem(
                context.getResources().getString(R.string.menu_select_all));
        device.waitForIdle();

        bots.main.clickToolbarItem(R.id.action_menu_delete);
        device.waitForIdle();

        bots.directory.waitForDeleteSnackbar();
        for (String filename : filenames) {
            assertFalse("files are not deleted", bots.directory.hasDocuments(filename));
        }

        bots.directory.clickSnackbarAction();
        device.waitForIdle();

        assertTrue("deleted files are not restored", bots.directory.hasDocuments(filenames));
    }

    public void testSelectionStateAfterDeleteUndo() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.selectDocument("file1.log");
        device.waitForIdle();

        bots.main.clickToolbarItem(R.id.action_menu_delete);
        device.waitForIdle();

        bots.directory.waitForDeleteSnackbar();

        bots.directory.clickSnackbarAction();
        device.waitForIdle();

        assertFalse("the file after deleting/undo is still selected",
                bots.directory.isDocumentSelected("file1.log"));
    }
}
