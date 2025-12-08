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

import static com.android.documentsui.StubProvider.EXTRA_SIZE;
import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.services.TestNotificationService;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class tests the below points.
 *
 * <p>- Cancel copying or moving file before starting it.
 *
 * <p>- Cancel during copying or moving file.
 */
@LargeTest
public class CancelFromNotificationUiTest extends ActivityTestJunit4<FilesActivity> {
    private static final String TAG = "CancelFromNotificationUiTest";
    private static final String TARGET_FILE = "stub.data";
    private static final int BUFFER_SIZE = 10 * 1024 * 1024;
    private static final int WAIT_TIME_SECONDS = 120;

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                // TestFilesRule setup will change the storage size to 100MB.
                                // So, reset the storage size again to 500MB.
                                Bundle bundle = new Bundle();
                                bundle.putLong(EXTRA_SIZE, 500L);
                                // Set a flag to prevent many refreshes.
                                bundle.putBoolean(
                                        StubProvider.EXTRA_ENABLE_ROOT_NOTIFICATION, false);
                                docsHelper.configure(null, bundle);
                                final RootInfo root = docsHelper.getRoot(StubProvider.ROOT_0_ID);
                                final Uri uri = docsHelper.createDocument(root, "*/*", TARGET_FILE);
                                final byte[] stubByte = new byte[BUFFER_SIZE];
                                docsHelper.writeDocument(uri, stubByte);
                                for (int i = 0; i < 49; i++) {
                                    docsHelper.writeAppendDocument(uri, stubByte, stubByte.length);
                                }
                            });

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
                mCountDownLatch.countDown();
            }
        }
    };

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
                TestNotificationService.ACTION_CHANGE_CANCEL_MODE));

        mOperationExecuted = false;
        mErrorReason = "No response from Notification";
        mCountDownLatch = new CountDownLatch(1);
    }

    @After
    public void tearDownTest() throws Exception {
        if (mCountDownLatch != null) {
            mCountDownLatch.countDown();
            mCountDownLatch = null;
        }

        context.unregisterReceiver(mReceiver);
        setNotificationAccess(false);
    }

    @HugeLongTest
    @Test
    public void testCopyDocument_Cancel() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);

        bots.directory.findDocument(TARGET_FILE);
        device.waitForIdle();

        bots.directory.selectDocument(TARGET_FILE, 1);
        device.waitForIdle();

        bots.main.clickActionbarOverflowItem(context.getResources().getString(R.string.menu_copy));
        device.waitForIdle();

        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        bots.directory.waitForDocument(TARGET_FILE);
    }

    @HugeLongTest
    @Test
    public void testCopyDocument_CancelFromNotification() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.findDocument(TARGET_FILE);
        device.waitForIdle();

        bots.directory.selectDocument(TARGET_FILE, 1);
        device.waitForIdle();

        bots.main.clickActionbarOverflowItem(context.getResources().getString(R.string.menu_copy));
        device.waitForIdle();

        bots.roots.openRoot(ROOT_1_ID);
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();
        mCountDownLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertTrue(mErrorReason, mOperationExecuted);

        bots.roots.openRoot(ROOT_1_ID);
        device.waitForIdle();
        assertFalse(bots.directory.hasDocuments(TARGET_FILE));

        bots.roots.openRoot(ROOT_0_ID);
        device.waitForIdle();
        assertTrue(bots.directory.hasDocuments(TARGET_FILE));
    }

    @HugeLongTest
    @Test
    public void testMoveDocument_Cancel() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);

        bots.directory.findDocument(TARGET_FILE);
        device.waitForIdle();

        bots.directory.selectDocument(TARGET_FILE, 1);
        device.waitForIdle();

        bots.main.clickActionbarOverflowItem(context.getResources().getString(R.string.menu_move));
        device.waitForIdle();

        bots.main.clickDialogCancelButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();

        bots.directory.waitForDocument(TARGET_FILE);
    }

    @HugeLongTest
    @Ignore("TODO(b/437236527): deflake and re-enable")
    @Test
    public void testMoveDocument_CancelFromNotification() throws Exception {
        bots.roots.openRoot(ROOT_0_ID);
        bots.directory.findDocument(TARGET_FILE);
        device.waitForIdle();

        bots.directory.selectDocument(TARGET_FILE, 1);
        device.waitForIdle();

        bots.main.clickActionbarOverflowItem(context.getResources().getString(R.string.menu_move));
        device.waitForIdle();

        bots.roots.openRoot(ROOT_1_ID);
        bots.main.clickDialogOkButton(/* closeSoftKeyboard */ false);
        device.waitForIdle();
        mCountDownLatch.await(WAIT_TIME_SECONDS, TimeUnit.SECONDS);
        assertTrue(mErrorReason, mOperationExecuted);

        bots.roots.openRoot(ROOT_1_ID);
        device.waitForIdle();
        assertFalse(bots.directory.hasDocuments(TARGET_FILE));

        bots.roots.openRoot(ROOT_0_ID);
        device.waitForIdle();
        assertTrue(bots.directory.hasDocuments(TARGET_FILE));
    }
}
