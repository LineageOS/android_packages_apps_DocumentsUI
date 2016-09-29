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

package com.android.documentsui.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.net.Uri;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.testing.TestConfirmationCallback;
import com.android.documentsui.testing.TestEnv;
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
    private TestConfirmationCallback mCallback;
    private ActionHandler<TestActivity> mHandler;
    private Selection mSelection;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create();
        mDialogs = new TestDialogController();
        mCallback = new TestConfirmationCallback();

        mHandler = new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.roots,
                mEnv::lookupExecutor,
                mDialogs,
                null,  // tuner, not currently used.
                null,  // clipper, only used in drag/drop
                null  // clip storage, not utilized unless we venture into *jumbo* clip terratory.
                );

        mDialogs.confirmNext();

        mSelection = new Selection();
        mSelection.add("1");
    }

    @Test
    public void testDeleteDocuments() {
        mHandler.deleteDocuments(mEnv.model, mSelection, mCallback);
        mDialogs.assertNoFileFailures();
        mActivity.startService.assertCalled();
        mCallback.assertConfirmed();
    }

    @Test
    public void testDeleteDocuments_Cancelable() {
        mDialogs.rejectNext();
        mHandler.deleteDocuments(mEnv.model, mSelection, mCallback);
        mDialogs.assertNoFileFailures();
        mActivity.startService.assertNotCalled();
        mCallback.assertRejected();
    }

    @Test
    public void testInitLocation_DefaultsToHome() throws Exception {
        mActivity.resources.bools.put(R.bool.productivity_device, true);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(Shared.getDefaultRootUri(mActivity));
    }

    private void assertRootPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.rootPicked.assertCalled();
        RootInfo root = mActivity.rootPicked.getLastValue();
        assertNotNull(root);
        assertEquals(expectedUri, root.getUri());
    }
}
