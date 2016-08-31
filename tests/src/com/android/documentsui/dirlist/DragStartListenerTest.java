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

import android.graphics.Point;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View;

import com.android.documentsui.TestInputEvent;

import java.util.ArrayList;

@SmallTest
public class DragStartListenerTest extends AndroidTestCase {

    DragStartListener listener;
    TestInputEvent event = new TestInputEvent();
    boolean dragStarted;

    @Override
    public void setUp() throws Exception {
        listener = new DragStartListener(
                null,
                null,
                new TestModel(""),
                new MultiSelectManager(new TestDocumentsAdapter(new ArrayList<String>()),
                        MultiSelectManager.MODE_MULTIPLE),
                null,
                null,
                (View view) -> "",
                (float x, float y) -> null,
                null) {
            @Override
            boolean startDrag(View v) {
                dragStarted = true;
                return true;
            }
        };

        dragStarted = false;
        event.mouseEvent = true;
        event.primaryButtonPressed = true;
        event.actionMove = true;
        event.position = 1;
        event.location = new Point();
        event.location.x = 0;
        event.location.y = 0;
    }

    public void testDrag_StartsOnMouseMove() {
        assertTrue(listener.onInterceptTouchEvent(event));
        assertTrue(dragStarted);
    }

    public void testDrag_DoesNotStartsOnNonMouseMove() {
        event.mouseEvent = false;
        assertFalse(listener.onInterceptTouchEvent(event));
        assertFalse(dragStarted);
    }

    public void testDrag_DoesNotStartsOnNonPrimaryMove() {
        event.primaryButtonPressed = false;
        assertFalse(listener.onInterceptTouchEvent(event));
        assertFalse(dragStarted);
    }

    public void testDrag_DoesNotStartsOnNonMove() {
        event.actionMove = false;
        assertFalse(listener.onInterceptTouchEvent(event));
        assertFalse(dragStarted);
    }

    public void testDrag_DoesNotStartsWhenNotOnItem() {
        event.position = -1;
        assertFalse(listener.onInterceptTouchEvent(event));
        assertFalse(dragStarted);
    }
}
