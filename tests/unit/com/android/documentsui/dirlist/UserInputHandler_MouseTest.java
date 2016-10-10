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

import static org.junit.Assert.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionProbe;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEvent;
import com.android.documentsui.testing.TestEvent.Builder;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestPredicate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_MouseTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler<TestEvent> mInputHandler;
    private TestActionHandler mActionHandler;
    private SelectionProbe mSelection;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestEventHandler<InputEvent> mRightClickHandler;
    private TestEventHandler<InputEvent> mDragAndDropHandler;
    private TestEventHandler<InputEvent> mGestureSelectHandler;

    private Builder mEvent;

    @Before
    public void setUp() {

        SelectionManager selectionMgr = SelectionManagers.createTestInstance(ITEMS);
        mActionHandler = new TestActionHandler();

        mSelection = new SelectionProbe(selectionMgr);
        mCanSelect = new TestPredicate<>();
        mRightClickHandler = new TestEventHandler<>();
        mDragAndDropHandler = new TestEventHandler<>();
        mGestureSelectHandler = new TestEventHandler<>();

        mInputHandler = new UserInputHandler<>(
                mActionHandler,
                new TestFocusHandler(),
                selectionMgr,
                (MotionEvent event) -> {
                    throw new UnsupportedOperationException("Not exercised in tests.");
                },
                mCanSelect,
                mRightClickHandler::accept,
                mDragAndDropHandler::accept,
                mGestureSelectHandler::accept);

        mEvent = TestEvent.builder().mouse();
    }

    @Test
    public void testConfirmedClick_StartsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).build());
        mSelection.assertSelection(11);
    }

    @Test
    public void testRightClickDown_StartsContextMenu() {
        mInputHandler.onDown(mEvent.secondary().build());
        mRightClickHandler.assertLastArgument(mEvent.secondary().build());
    }

    @Test
    public void testScroll_shouldTrap() {
        assertTrue(mInputHandler.onScroll(mEvent.at(0).build()));
    }

    @Test
    public void testUnconfirmedClick_DoesNotAddToExistingSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).build());
        mSelection.assertSelection(11);
    }

    @Test
    public void testUnconfirmedCtrlClick_AddsToExistingSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).ctrl().build());
        mSelection.assertSelection(7, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_ExtendsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftClick_RotatesAroundOrigin() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);

        mInputHandler.onSingleTapUp(mEvent.at(5).shift().build());
        mSelection.assertSelection(5, 6, 7);
        mSelection.assertNotSelected(8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftCtrlClick_Combination() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);

        mInputHandler.onSingleTapUp(mEvent.at(5).unshift().ctrl().build());

        mSelection.assertSelection(5, 7, 8, 9, 10, 11);
    }

    @Test
    public void testUnconfirmedShiftCtrlClick_ShiftTakesPriority() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).ctrl().shift().build());
        mSelection.assertSelection(7, 8, 9, 10, 11);
    }

    // TODO: Add testSpaceBar_Previews, but we need to set a system property
    // to have a deterministic state.

    @Test
    public void testDoubleClick_Views() {
        mInputHandler.onDoubleTap(mEvent.at(11).build());
        mActionHandler.view.assertLastArgument(mEvent.build().getDocumentDetails());
    }

    @Test
    public void testClickOff_ClearsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).build());
        mInputHandler.onSingleTapUp(mEvent.at(RecyclerView.NO_POSITION).build());
        mSelection.assertNoSelection();
    }
}
