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

import static com.android.documentsui.testing.TestEvents.Mouse.ALT_CLICK;
import static com.android.documentsui.testing.TestEvents.Mouse.CLICK;
import static com.android.documentsui.testing.TestEvents.Mouse.CTRL_CLICK;
import static com.android.documentsui.testing.TestEvents.Mouse.SECONDARY_CLICK;
import static com.android.documentsui.testing.TestEvents.Mouse.SHIFT_CLICK;
import static com.android.documentsui.testing.TestEvents.Mouse.TERTIARY_CLICK;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionProbe;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestDocumentDetails;
import com.android.documentsui.testing.TestEventDetailsLookup;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestEvents;
import com.android.documentsui.testing.TestEvents.Builder;
import com.android.documentsui.testing.TestPredicate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_MouseTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler mInputHandler;
    private TestActionHandler mActionHandler;
    private TestEventDetailsLookup mDetailsLookup;
    private TestFocusHandler mFocusHandler;
    private SelectionProbe mSelection;
    private SelectionManager mSelectionMgr;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestEventHandler<MotionEvent> mContextMenuClickHandler;
    private TestEventHandler<MotionEvent> mDragAndDropHandler;
    private TestEventHandler<MotionEvent> mGestureSelectHandler;
    private TestEventHandler<Void> mPerformHapticFeedback;

    private Builder mEvent;

    @Before
    public void setUp() {

        mSelectionMgr = SelectionManagers.createTestInstance(ITEMS);
        mActionHandler = new TestActionHandler();
        mDetailsLookup = new TestEventDetailsLookup();
        mSelection = new SelectionProbe(mSelectionMgr);
        mCanSelect = new TestPredicate<>();
        mContextMenuClickHandler = new TestEventHandler<>();
        mDragAndDropHandler = new TestEventHandler<>();
        mGestureSelectHandler = new TestEventHandler<>();
        mFocusHandler = new TestFocusHandler();

        mInputHandler = new UserInputHandler(
                mActionHandler,
                mFocusHandler,
                mSelectionMgr,
                mDetailsLookup,
                mCanSelect,
                mContextMenuClickHandler::accept,
                mDragAndDropHandler::accept,
                mGestureSelectHandler::accept,
                () -> mPerformHapticFeedback.accept(null));

        mEvent = TestEvents.builder().mouse();
        mDetailsLookup.initAt(RecyclerView.NO_POSITION);
    }

    @Test
    public void testConfirmedClick_StartsSelection() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mSelection.assertSelection(11);
    }

    @Test
    public void testClickOnSelectRegion_AddsToSelection() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(10).setInItemSelectRegion(true);
        mInputHandler.onSingleTapUp(CLICK);

        mSelection.assertSelected(10, 11);
    }

    @Test
    public void testClickOnIconOfSelectedItem_RemovesFromSelection() {
        mDetailsLookup.initAt(8).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);
        mSelection.assertSelected(8, 9, 10, 11);

        mDetailsLookup.initAt(9);
        mInputHandler.onSingleTapUp(CLICK);
        mSelection.assertSelected(8, 10, 11);
    }

    @Test
    public void testRightClickDown_StartsContextMenu() {
        // sadly, MotionEvent doesn't implement equals. Save off the reference for comparison.
        mInputHandler.onDown(SECONDARY_CLICK);
        mContextMenuClickHandler.assertLastArgument(SECONDARY_CLICK);
    }

    @Test
    public void testAltClickDown_StartsContextMenu() {
        mInputHandler.onDown(ALT_CLICK);
        mContextMenuClickHandler.assertLastArgument(ALT_CLICK);
    }

    @Test
    public void testScroll_shouldTrap() {
        mDetailsLookup.initAt(0);
        assertTrue(mInputHandler.onScroll(
                null,
                mEvent.action(MotionEvent.ACTION_MOVE).primary().build(),
                0,
                0));
    }

    @Test
    public void testScroll_NoTrapForTwoFinger() {
        mDetailsLookup.initAt(0);
        assertFalse(mInputHandler.onScroll(
                null,
                mEvent.action(MotionEvent.ACTION_MOVE).build(),
                0,
                0));
    }

    @Test
    public void testUnconfirmedCtrlClick_AddsToExistingSelection() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(CTRL_CLICK);

        mSelection.assertSelection(7, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_ExtendsSelection() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testConfirmedShiftClick_ExtendsSelectionFromOriginFocus() {
        mFocusHandler.focusPos = 7;
        mFocusHandler.focusModelId = "7";

        // This is a hack-y test, since the real FocusManager would've set range begin itself.
        mSelectionMgr.setSelectionRangeBegin(7);
        mSelection.assertNoSelection();

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapConfirmed(SHIFT_CLICK);
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_RotatesAroundOrigin() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);
        mSelection.assertSelection(7, 8, 9, 10, 11);

        mDetailsLookup.initAt(5);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertSelection(5, 6, 7);
        mSelection.assertNotSelected(8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftCtrlClick_Combination() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);
        mSelection.assertSelection(7, 8, 9, 10, 11);

        mDetailsLookup.initAt(5);
        mInputHandler.onSingleTapUp(CTRL_CLICK);

        mSelection.assertSelection(5, 7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftCtrlClick_ShiftTakesPriority() {
        mDetailsLookup.initAt(7).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(mEvent.ctrl().shift().build());

        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    // TODO: Add testSpaceBar_Previews, but we need to set a system property
    // to have a deterministic state.

    @Test
    public void testDoubleClick_Opens() {
        TestDocumentDetails doc = mDetailsLookup.initAt(11);
        mInputHandler.onDoubleTap(CLICK);

        mActionHandler.open.assertLastArgument(doc);
    }

    @Test
    public void testMiddleClick_DoesNothing() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(TERTIARY_CLICK);

        mSelection.assertNoSelection();
    }

    @Test
    public void testClickOff_ClearsSelection() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(RecyclerView.NO_POSITION);
        mInputHandler.onSingleTapUp(CLICK);

        mSelection.assertNoSelection();
    }

    @Test
    public void testClick_Focuses() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(false);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mFocusHandler.assertHasFocus(true);
        mFocusHandler.assertFocused("11");
    }

    @Test
    public void testClickOff_ClearsFocus() {
        mDetailsLookup.initAt(11).setInItemSelectRegion(false);
        mInputHandler.onSingleTapConfirmed(CLICK);
        mFocusHandler.assertHasFocus(true);

        mDetailsLookup.initAt(RecyclerView.NO_POSITION);
        mInputHandler.onSingleTapUp(CLICK);
        mFocusHandler.assertHasFocus(false);
    }

    @Test
    public void testClickOffSelection_RemovesSelectionAndFocuses() {
        mDetailsLookup.initAt(1).setInItemSelectRegion(true);
        mInputHandler.onSingleTapConfirmed(CLICK);

        mDetailsLookup.initAt(5);
        mInputHandler.onSingleTapUp(SHIFT_CLICK);

        mSelection.assertSelection(1, 2, 3, 4, 5);

        mDetailsLookup.initAt(11);
        mInputHandler.onSingleTapUp(CLICK);

        assertTrue(mFocusHandler.getFocusModelId().equals("11"));
        mSelection.assertNoSelection();
    }
}
