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

import static org.junit.Assert.assertFalse;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.testing.MultiSelectManagers;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEvent;
import com.android.documentsui.testing.TestEvent.Builder;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestPredicate;
import com.android.documentsui.testing.dirlist.SelectionProbe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_TouchTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler<TestEvent> mInputHandler;
    private TestActionHandler mActionHandler;

    private SelectionProbe mSelection;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestEventHandler<InputEvent> mRightClickHandler;
    private TestEventHandler<DocumentDetails> mDeleteHandler;
    private TestEventHandler<InputEvent> mDragAndDropHandler;
    private TestEventHandler<InputEvent> mGestureSelectHandler;

    private Builder mEvent;

    @Before
    public void setUp() {
        SelectionManager selectionMgr = MultiSelectManagers.createTestInstance(ITEMS);

        mActionHandler = new TestActionHandler();

        mSelection = new SelectionProbe(selectionMgr);
        mCanSelect = new TestPredicate<>();
        mRightClickHandler = new TestEventHandler<>();
        mDeleteHandler = new TestEventHandler<>();
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
                mDeleteHandler::accept,
                mDragAndDropHandler::accept,
                mGestureSelectHandler::accept);

        mEvent = TestEvent.builder();
    }

    @Test
    public void testTap_ActivatesWhenNoExistingSelection() {
        mInputHandler.onSingleTapUp(mEvent.at(11).build());
        mActionHandler.open.assertLastArgument(mEvent.build().getDocumentDetails());
    }

    @Test
    public void testScroll_shouldNotBeTrapped() {
        assertFalse(mInputHandler.onScroll(mEvent.build()));
    }

    @Test
    public void testLongPress_StartsSelectionMode() {
        mInputHandler.onLongPress(mEvent.at(7).build());
        mSelection.assertSelection(7);
    }

    @Test
    public void testLongPress_SecondPressExtendsSelection() {
        mInputHandler.onLongPress(mEvent.at(7).build());
        mInputHandler.onLongPress(mEvent.at(99).build());
        mInputHandler.onLongPress(mEvent.at(13).build());
        mSelection.assertSelection(7, 13, 99);
    }

    @Test
    public void testTap_UnselectsSelectedItem() {
        mInputHandler.onLongPress(mEvent.at(7).build());
        mInputHandler.onSingleTapUp(mEvent.at(7).build());
        mSelection.assertNoSelection();
    }

    @Test
    public void testTapOff_ClearsSelection() {
        mInputHandler.onLongPress(mEvent.at(7).build());
        mInputHandler.onSingleTapUp(mEvent.at(11).build());
        mInputHandler.onSingleTapUp(mEvent.at(RecyclerView.NO_POSITION).build());
        mSelection.assertNoSelection();
    }
}
