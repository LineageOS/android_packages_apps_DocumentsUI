/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import android.test.AndroidTestCase;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.filters.SmallTest;

import com.android.documentsui.testing.Views;

@SmallTest
public class AccessibilityTest extends AndroidTestCase {

    private AccessibilityEventRouter mAccessibilityDelegate;
    private boolean mClickCallbackCalled = false;
    private boolean mLongClickCallbackCalled = false;

    @Override
    public void setUp() throws Exception {
        mAccessibilityDelegate = new AccessibilityEventRouter((View v) -> {
            mClickCallbackCalled = true;
            return true;
        }, (View v) -> {
            mLongClickCallbackCalled = true;
            return true;
        });
    }

    public void test_announceSelected() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        mAccessibilityDelegate.onInitializeAccessibilityNodeInfo(item, info);
        assertTrue(info.isSelected());
    }

    public void test_routesAccessibilityClicks() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        mAccessibilityDelegate.onInitializeAccessibilityNodeInfo(item, info);
        mAccessibilityDelegate.performAccessibilityAction(
                item, AccessibilityNodeInfo.ACTION_CLICK, null);
        assertTrue(mClickCallbackCalled);
    }

    public void test_routesAccessibilityLongClicks() throws Exception {
        View item = Views.createTestView(true);
        AccessibilityNodeInfo info = AccessibilityNodeInfo.obtain();
        mAccessibilityDelegate.onInitializeAccessibilityNodeInfo(item, info);
        mAccessibilityDelegate.performAccessibilityAction(
                item, AccessibilityNodeInfo.ACTION_LONG_CLICK, null);
        assertTrue(mLongClickCallbackCalled);
    }
}
