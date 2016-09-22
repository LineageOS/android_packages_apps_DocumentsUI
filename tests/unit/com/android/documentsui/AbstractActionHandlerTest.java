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

import android.content.Intent;
import android.os.Parcelable;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.manager.LauncherActivity;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class AbstractActionHandlerTest {

    private TestActivity mActivity;
    private AbstractActionHandler<TestActivity> mHandler;

    @Before
    public void setUp() {
        mActivity = TestActivity.create();
        mHandler = new AbstractActionHandler<TestActivity>(mActivity) {

            @Override
            public void openRoot(RootInfo root) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean openDocument(DocumentDetails doc) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Test
    public void testOpenNewWindow() {
        DocumentStack path = new DocumentStack(Roots.create("123"));
        mHandler.openInNewWindow(path);

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);
        mActivity.assertStarted(expected);
    }
}
