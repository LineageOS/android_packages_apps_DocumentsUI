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

import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.net.Uri;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestRootsAccess;
import com.android.documentsui.ui.TestDialogController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActionHandlerTest {

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestDialogController mDialogs;
    private ActionHandler<TestActivity> mHandler;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create();
        mDialogs = new TestDialogController();
        mEnv.roots.configurePm(mActivity.packageMgr);

        mHandler = new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.roots,
                mEnv.docs,
                mEnv.selectionMgr,
                mEnv::lookupExecutor,
                null  // tuner, not currently used.
                );

        mDialogs.confirmNext();

        mEnv.selectionMgr.toggleSelection("1");

        mHandler.reset(mEnv.model, false);
    }

    @Test
    public void testOpenDrawerOnLaunchingEmptyRoot() {
        mEnv.model.reset();
        // state should not say we've changed our location

        mEnv.model.update();

        mActivity.setRootsDrawerOpen.assertLastArgument(true);
    }

    @Test
    public void testOpenDrawerOnGettingContent() {
        mEnv.state.external = true;
        mEnv.state.action = ACTION_GET_CONTENT;

        mEnv.model.update();

        mActivity.setRootsDrawerOpen.assertLastArgument(true);
    }

    @Test
    public void testOpenDrawerOnPickingCopyDestination() {
        mEnv.state.action = ACTION_PICK_COPY_DESTINATION;

        mEnv.model.update();

        mActivity.setRootsDrawerOpen.assertLastArgument(true);
    }

    @Test
    public void testInitLocation_CopyDestination_DefaultsToDownloads() throws Exception {
        mActivity.resources.bools.put(R.bool.productivity_device, false);

        Intent intent = mActivity.getIntent();
        intent.setAction(Shared.ACTION_PICK_COPY_DESTINATION);
        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestRootsAccess.DOWNLOADS.getUri());
    }

    @Test
    public void testInitLocation_CopyDestination_DefaultsToHome() throws Exception {
        mActivity.resources.bools.put(R.bool.productivity_device, true);

        Intent intent = mActivity.getIntent();
        intent.setAction(Shared.ACTION_PICK_COPY_DESTINATION);
        mHandler.initLocation(intent);
        assertRootPicked(TestRootsAccess.HOME.getUri());
    }

    private void assertRootPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.rootPicked.assertCalled();
        RootInfo root = mActivity.rootPicked.getLastValue();
        assertNotNull(root);
        assertEquals(expectedUri, root.getUri());
    }
}
