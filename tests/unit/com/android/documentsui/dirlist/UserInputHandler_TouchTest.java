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

package com.android.documentsui.dirlist;

import static com.android.documentsui.testing.TestEvents.Touch.TAP;
import static org.junit.Assert.assertFalse;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionProbe;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEventDetailsLookup;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestPredicate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_TouchTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler mInputHandler;
    private TestActionHandler mActionHandler;
    private TestEventDetailsLookup mDetailsLookup;
    private SelectionProbe mSelection;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestEventHandler<MotionEvent> mRightClickHandler;
    private TestEventHandler<MotionEvent> mDragAndDropHandler;
    private TestEventHandler<MotionEvent> mGestureSelectHandler;
    private TestEventHandler<Void> mPerformHapticFeedback;

    @Before
    public void setUp() {
        SelectionManager selectionMgr = SelectionManagers.createTestInstance(ITEMS);

        mActionHandler = new TestActionHandler();
        mDetailsLookup = new TestEventDetailsLookup();

        mSelection = new SelectionProbe(selectionMgr);
        mCanSelect = new TestPredicate<>();
        mRightClickHandler = new TestEventHandler<>();
        mDragAndDropHandler = new TestEventHandler<>();
        mGestureSelectHandler = new TestEventHandler<>();
        mPerformHapticFeedback = new TestEventHandler<>();

        mInputHandler = new UserInputHandler(
                mActionHandler,
                new TestFocusHandler(),
                selectionMgr,
                mDetailsLookup,
                mCanSelect,
                mRightClickHandler::accept,
                mDragAndDropHandler::accept,
                mGestureSelectHandler::accept,
                () -> mPerformHapticFeedback.accept(null));
    }

    @Test
    public void testTap_ActivatesWhenNoExistingSelection() {
        DocumentDetails doc = mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(TAP);

        mActionHandler.open.assertLastArgument(doc);
    }

    @Test
    public void testScroll_shouldNotBeTrapped() {
        assertFalse(mInputHandler.onScroll(null, TAP, 0, 0));
    }

    @Test
    public void testLongPress_StartsSelectionMode() {
        mCanSelect.nextReturn(true);

        mDetailsLookup.initAt(7);
        mInputHandler.onLongPress(TAP);

        mSelection.assertSelection(7);
    }


    @Test
    public void testSelectHotspot_StartsSelectionMode() {
        mCanSelect.nextReturn(true);

        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapUp(TAP);

        mSelection.assertSelection(7);
    }

    @Test
    public void testSelectionHotspot_UnselectsSelectedItem() {
        mDetailsLookup.initAt(11);
        mInputHandler.onLongPress(TAP);

        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        mInputHandler.onSingleTapUp(TAP);

        mSelection.assertNoSelection();
    }

    @Test
    public void testStartsSelection_PerformsHapticFeedback() {
        mCanSelect.nextReturn(true);

        mDetailsLookup.initAt(7);
        mInputHandler.onLongPress(TAP);

        mPerformHapticFeedback.assertCalled();
    }

    @Test
    public void testLongPress_AddsToSelection() {
        mCanSelect.nextReturn(true);

        mDetailsLookup.initAt(7);
        mInputHandler.onLongPress(TAP);

        mDetailsLookup.initAt(99);
        mInputHandler.onLongPress(TAP);

        mDetailsLookup.initAt(13);
        mInputHandler.onLongPress(TAP);

        mSelection.assertSelection(7, 13, 99);
    }

    @Test
    public void testTap_UnselectsSelectedItem() {
        mDetailsLookup.initAt(11);
        mInputHandler.onLongPress(TAP);
        mInputHandler.onSingleTapUp(TAP);

        mSelection.assertNoSelection();
    }

    @Test
    public void testTapOff_ClearsSelection() {
        mDetailsLookup.initAt(7);
        mInputHandler.onLongPress(TAP);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(TAP);

        mDetailsLookup.initAt(RecyclerView.NO_POSITION).setInItemSelectRegion(false);
        mInputHandler.onSingleTapUp(TAP);

        mSelection.assertNoSelection();
    }
}
