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

package com.android.documentsui.files;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.MotionEvent;

import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionProbe;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestActionHandler;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActivityInputHandlerTest {

    private static final List<String> ITEMS = TestData.create(100);

    private SelectionProbe mSelection;
    private TestActionHandler mActionHandler;
    private ActivityInputHandler mActivityInputHandler;

    @Before
    public void setUp() {
        SelectionManager selectionMgr = SelectionManagers.createTestInstance(ITEMS);
        mSelection = new SelectionProbe(selectionMgr);
        mActionHandler = new TestActionHandler();
        mActivityInputHandler = new ActivityInputHandler(selectionMgr, mActionHandler);
    }

    @Test
    public void testDelete_noSelection() {
        KeyEvent event = new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0,
                KeyEvent.META_ALT_ON);
        assertFalse(mActivityInputHandler.onKeyDown(event.getKeyCode(), event));
        assertFalse(mActionHandler.mDeleteHappened);
    }

    @Test
    public void testDelete_hasSelection() {
        mSelection.select(1);
        KeyEvent event = new KeyEvent(0, 0, MotionEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL, 0,
                KeyEvent.META_ALT_ON);
        assertTrue(mActivityInputHandler.onKeyDown(event.getKeyCode(), event));
        assertTrue(mActionHandler.mDeleteHappened);
    }
}
