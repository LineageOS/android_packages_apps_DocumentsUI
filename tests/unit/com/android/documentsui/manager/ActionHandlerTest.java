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

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.dirlist.TestModel;
import com.android.documentsui.testing.TestConfirmationCallback;
import com.android.documentsui.ui.TestDialogController;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActionHandlerTest {

    private static final String AUTHORITY = "voltron";

    private TestActivity mActivity;
    private TestDialogController mDialogs;
    private State mState;
    private TestModel mModel;
    private TestConfirmationCallback mCallback;

    private ActionHandler<TestActivity> mHandler;

    private Selection mSelection;

    @Before
    public void setUp() {
        mActivity = TestActivity.create();
        Assert.assertNotNull(mActivity);
        mState = new State();
        mModel = new TestModel(AUTHORITY);
        mCallback = new TestConfirmationCallback();
        mDialogs = new TestDialogController();

        mHandler = new ActionHandler<>(
                mActivity,
                mDialogs,
                mState,
                null,  // tuner, not currently used.
                null,  // clipper, only used in drag/drop
                null  // clip storage, not utilized unless we venture into *jumbo* clip terratory.
                );

        mModel.update("a", "b");
        mDialogs.confirmNext();
        mState.stack.push(mModel.getDocument("1"));

        mSelection = new Selection();
        mSelection.add("1");
    }

    @Test
    public void testDeleteDocuments() {
        mHandler.deleteDocuments(mModel, mSelection, mCallback);
        mDialogs.assertNoFileFailures();
        mActivity.assertSomethingStarted();
        mCallback.assertConfirmed();
    }

    @Test
    public void testDeleteDocuments_Cancelable() {
        mDialogs.rejectNext();
        mHandler.deleteDocuments(mModel, mSelection, mCallback);
        mDialogs.assertNoFileFailures();
        mActivity.assertNothingStarted();
        mCallback.assertRejected();
    }
}
