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

import android.content.ClipData;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.MotionEvent;
import android.view.View;

import com.android.documentsui.Events.InputEvent;
import com.android.documentsui.State;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.testing.TestEvent;
import com.android.documentsui.testing.Views;

import java.util.ArrayList;

@SmallTest
public class DragStartListenerTest extends AndroidTestCase {

    private DragStartListener mListener;
    private TestEvent.Builder mEvent;
    private MultiSelectManager mMultiSelectManager;
    private String mViewModelId;
    private boolean mDragStarted;

    @Override
    public void setUp() throws Exception {

        mMultiSelectManager = new MultiSelectManager(
                new TestDocumentsAdapter(new ArrayList<String>()),
                MultiSelectManager.MODE_MULTIPLE);

        mListener = new DragStartListener.ActiveListener(
                new State(),
                mMultiSelectManager,
                // view finder
                (float x, float y) -> {
                    return Views.createTestView(x, y);
                },
                // model id finder
                (View view) -> {
                    return mViewModelId;
                },
                // docInfo Converter
                (Selection selection) -> {
                    return new ArrayList<>();
                },
                // ClipDataFactory
                (Selection selection, int operationType) -> {
                    return null;
                },
                // shawdowBuilderFactory
                (Selection selection) -> {
                    return null;
                }) {

            @Override
            void startDragAndDrop(
                    View view,
                    ClipData data,
                    DragShadowBuilder shadowBuilder,
                    Object localState,
                    int flags) {

                mDragStarted = true;
            }
        };

        mDragStarted = false;
        mViewModelId = "1234";

        mEvent = TestEvent.builder()
                .action(MotionEvent.ACTION_MOVE)
                .mouse()
                .at(1)
                .primary();
    }

    public void testDragStarted_OnMouseMove() {
        assertTrue(mListener.onMouseDragEvent(mEvent.build()));
        assertTrue(mDragStarted);
    }

    public void testDragNotStarted_NonModelBackedView() {
        mViewModelId = null;
        assertFalse(mListener.onMouseDragEvent(mEvent.build()));
        assertFalse(mDragStarted);
    }

    public void testThrows_OnNonMouseMove() {
        TestEvent e = TestEvent.builder()
                .at(1)
                .action(MotionEvent.ACTION_MOVE).build();
        assertThrows(e);
    }

    public void testThrows_OnNonPrimaryMove() {
        assertThrows(mEvent.pressButton(MotionEvent.BUTTON_PRIMARY).build());
    }

    public void testThrows_OnNonMove() {
        assertThrows(mEvent.action(MotionEvent.ACTION_UP).build());
    }

    public void testThrows_WhenNotOnItem() {
        assertThrows(mEvent.at(-1).build());
    }

    private void assertThrows(InputEvent e) {
        try {
            assertFalse(mListener.onMouseDragEvent(e));
            fail();
        } catch (AssertionError expected) {}
    }
}
