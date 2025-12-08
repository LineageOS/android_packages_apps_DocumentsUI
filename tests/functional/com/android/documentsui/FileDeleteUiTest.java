/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.content.Context.RECEIVER_EXPORTED;

import static com.android.documentsui.StubProvider.ROOT_0_ID;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.services.TestNotificationService;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * This class test the below points
 *
 * <p>- Delete large number of files
 */
@LargeTest
public class FileDeleteUiTest extends ActivityTestJunit4<FilesActivity> {
    private static final String TAG = "FileDeleteUiTest";

    private static final int STUB_FILE_COUNT = 1000;

    private static final int WAIT_TIME_SECONDS = 60;

    private final List<String> mCopyFileList = new ArrayList<String>();

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TestNotificationService.ACTION_PONG.equals(action)) {
                sRendezvousCountDownLatch.countDown();
            } else if (TestNotificationService.ACTION_OPERATION_RESULT.equals(action)) {
                mOperationExecuted = intent.getBooleanExtra(
                        TestNotificationService.EXTRA_RESULT, false);
                if (!mOperationExecuted) {
                    mErrorReason = intent.getStringExtra(
                            TestNotificationService.EXTRA_ERROR_REASON);
                }
                if (mCountDownLatch != null) {
                    mCountDownLatch.countDown();
                }
            }
        }
    };

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule().createTestFiles(this::initTestFiles);

    private static CountDownLatch sRendezvousCountDownLatch = new CountDownLatch(1);

    private CountDownLatch mCountDownLatch;

    private boolean mOperationExecuted;

    private String mErrorReason;

    @Before
    public void setUpTest() throws Exception {
        setNotificationAccess(true);

        IntentFilter filter = new IntentFilter();
        filter.addAction(TestNotificationService.ACTION_OPERATION_RESULT);
        filter.addAction(TestNotificationService.ACTION_PONG);
        context.registerReceiver(mReceiver, filter, RECEIVER_EXPORTED);
        if (!TestNotificationService.rendezvous(context, sRendezvousCountDownLatch)) {
            fail("TestNotificationService.rendezvous failed");
        }
        context.sendBroadcast(new Intent(
                TestNotificationService.ACTION_CHANGE_EXECUTION_MODE));

        mOperationExecuted = false;
        mErrorReason = "No response from Notification";
        mCountDownLatch = new CountDownLatch(1);
    }

    @After
    public void tearDownTest() {
        context.unregisterReceiver(mReceiver);
        mCountDownLatch = null;
        setNotificationAccess(false);
    }

    private void initTestFiles(DocumentsProviderHelper docsHelper) throws Exception {
        // Set a flag to prevent many refreshes.
        Bundle bundle = new Bundle();
        bundle.putBoolean(StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
        docsHelper.configure(null, bundle);
        final RootInfo root = docsHelper.getRoot(StubProvider.ROOT_0_ID);
        final ThreadPoolExecutor exec = new ThreadPoolExecutor(
                5, 5, 1000L, TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<Runnable>(100, true));
        for (int i = 0; i < STUB_FILE_COUNT; i++) {
            final String fileName = "file" + String.format("%04d", i) + ".log";
            if (exec.getQueue().size() >= 80) {
                Thread.sleep(50);
            }
            exec.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            Uri uri = docsHelper.createDocument(root, "text/plain", fileName);
                            try {
                                docsHelper.writeDocument(uri, new byte[1]);
                            } catch (Exception e) {
                                // ignore
                            }
                        }
                    });
            mCopyFileList.add(fileName);
        }
        exec.shutdown();
    }

    @HugeLongTest
    @Test
    public void testDeleteAllDocument() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);
        bots.main.clickToolbarOverflowItem(
                context.getResources().getString(R.string.menu_select_all));
        device.waitForIdle();

        bots.main.clickDelete();
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        try {
            mCountDownLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            fail("Cannot wait because of error." + e.toString());
        }

        assertTrue(mErrorReason, mOperationExecuted);

        bots.roots.openRoot(ROOT_0_ID);
        device.waitForIdle();

        List<DocumentInfo> root1 = mDocsHelper.listChildren(rootDir0.documentId, 1000);
        assertTrue("Delete operation was not completed", root1.size() == 0);
    }
}
