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

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import com.android.documentsui.Events.InputEvent;
import com.android.documentsui.testing.TestEvent;
import com.android.documentsui.testing.TestEvent.Builder;
import com.android.documentsui.testing.TestPredicate;
import com.android.documentsui.testing.dirlist.SelectionProbe;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class UserInputHandler_MouseTest {

    private static final List<String> ITEMS = TestData.create(100);

    private UserInputHandler<TestEvent> mInputHandler;

    private TestDocumentsAdapter mAdapter;
    private SelectionProbe mSelection;
    private TestPredicate<DocumentDetails> mCanSelect;
    private TestPredicate<InputEvent> mRightClickHandler;
    private TestPredicate<DocumentDetails> mActivateHandler;
    private TestPredicate<DocumentDetails> mDeleteHandler;

    private Builder mEvent;

    @Before
    public void setUp() {

        mAdapter = new TestDocumentsAdapter(ITEMS);
        MultiSelectManager selectionMgr =
                new MultiSelectManager(mAdapter, MultiSelectManager.MODE_MULTIPLE);

        mSelection = new SelectionProbe(selectionMgr);
        mCanSelect = new TestPredicate<>();
        mRightClickHandler = new TestPredicate<>();
        mActivateHandler = new TestPredicate<>();
        mDeleteHandler = new TestPredicate<>();

        mInputHandler = new UserInputHandler<>(
                selectionMgr,
                new TestFocusHandler(),
                (MotionEvent event) -> {
                    throw new UnsupportedOperationException("Not exercised in tests.");
                },
                (TestEvent event) -> {
                    return event.getDocument();
                },
                mCanSelect,
                mRightClickHandler::test,
                mActivateHandler::test,
                mDeleteHandler::test);

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
    public void testUnconfirmedClick_AddsToExistingSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(7).build());

        mInputHandler.onSingleTapUp(mEvent.at(11).build());
        mSelection.assertSelection(7, 11);
    }

    @Test
    public void testDoubleClick_Activates() {
        mInputHandler.onDoubleTap(mEvent.at(11).build());
        mActivateHandler.assertLastArgument(mEvent.build());
    }

    @Test
    public void testClickOff_ClearsSelection() {
        mInputHandler.onSingleTapConfirmed(mEvent.at(11).build());
        mInputHandler.onSingleTapUp(mEvent.at(RecyclerView.NO_POSITION).build());
        mSelection.assertNoSelection();
    }
}
