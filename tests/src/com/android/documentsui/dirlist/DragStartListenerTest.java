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
import android.graphics.Point;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;

import com.android.documentsui.State;
import com.android.documentsui.TestInputEvent;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.model.DocumentInfo;
import com.android.documentsui.testing.Views;

import java.util.ArrayList;

@SmallTest
public class DragStartListenerTest extends AndroidTestCase {

    private DragStartListener listener;
    private TestInputEvent event;
    private MultiSelectManager mMultiSelectManager;
    private String viewModelId;
    private boolean dragStarted;

    @Override
    public void setUp() throws Exception {

        mMultiSelectManager = new MultiSelectManager(
                new TestDocumentsAdapter(new ArrayList<String>()),
                MultiSelectManager.MODE_MULTIPLE);

        listener = new DragStartListener.ActiveListener(
                new State(),
                mMultiSelectManager,
                // view finder
                (float x, float y) -> {
                    return Views.createTestView(x, y);
                },
                // model id finder
                (View view) -> {
                    return viewModelId;
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
                    DocumentInfo currentDirectory,
                    int flags) {

                dragStarted = true;
            }
        };

        dragStarted = false;
        viewModelId = "1234";

        event = new TestInputEvent();
        event.mouseEvent = true;
        event.primaryButtonPressed = true;
        event.actionMove = true;
        event.position = 1;
        event.location = new Point();
        event.location.x = 0;
        event.location.y = 0;
    }

    public void testDragStarted_OnMouseMove() {
        assertTrue(listener.onMouseDragEvent(event));
        assertTrue(dragStarted);
    }

    public void testDragNotStarted_NonModelBackedView() {
        viewModelId = null;
        assertFalse(listener.onMouseDragEvent(event));
        assertFalse(dragStarted);
    }

    public void testThrows_OnNonMouseMove() {
        event.mouseEvent = false;
        assertThrows(event);
    }

    public void testThrows_OnNonPrimaryMove() {
        event.primaryButtonPressed = false;
        assertThrows(event);
    }

    public void testThrows_OnNonMove() {
        event.actionMove = false;
        assertThrows(event);
    }

    public void testThrows_WhenNotOnItem() {
        event.position = -1;
        assertThrows(event);
    }

    private void assertThrows(TestInputEvent e) {
        try {
            assertFalse(listener.onMouseDragEvent(e));
            fail();
        } catch (AssertionError expected) {}
    }
}
