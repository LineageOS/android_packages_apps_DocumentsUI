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

package com.android.documentsui.selection.addons;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyFloat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.view.MotionEvent;

import com.android.documentsui.selection.addons.InputEventDispatcher.Delegate;
import com.android.documentsui.selection.addons.TestEvents.Mouse;
import com.android.documentsui.selection.addons.TestEvents.Touch;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class InputEventDispatcherTest {

    private TestDelegate mAlt;
    private TestDelegate mDelegate;
    private InputEventDispatcher mDispatcher;

    @Before
    public void setUp() {
        mAlt = new TestDelegate();
        mDelegate = new TestDelegate();
    }

    @Test
    public void testDelegates() {
        mDispatcher = new InputEventDispatcher();
        mDispatcher.register(MotionEvent.TOOL_TYPE_MOUSE, mDelegate);
        mDispatcher.register(MotionEvent.TOOL_TYPE_FINGER, mAlt);

        mDispatcher.onDown(Mouse.CLICK);
        mDelegate.assertCalled_onDown(Mouse.CLICK);
        mAlt.assertNotCalled_onDown();

        mDispatcher.onShowPress(Mouse.CLICK);
        mDelegate.assertCalled_onShowPress(Mouse.CLICK);
        mAlt.assertNotCalled_onShowPress();

        mDispatcher.onSingleTapUp(Mouse.CLICK);
        mDelegate.assertCalled_onSingleTapUp(Mouse.CLICK);
        mAlt.assertNotCalled_onSingleTapUp();

        mDispatcher.onScroll(null, Mouse.CLICK, -1, -1);
        mDelegate.assertCalled_onScroll(null, Mouse.CLICK, -1, -1);
        mAlt.assertNotCalled_onScroll();

        mDispatcher.onLongPress(Mouse.CLICK);
        mDelegate.assertCalled_onLongPress(Mouse.CLICK);
        mAlt.assertNotCalled_onLongPress();

        mDispatcher.onFling(null, Mouse.CLICK, -1, -1);
        mDelegate.assertCalled_onFling(null, Mouse.CLICK, -1, -1);
        mAlt.assertNotCalled_onFling();

        mDispatcher.onSingleTapConfirmed(Mouse.CLICK);
        mDelegate.assertCalled_onSingleTapConfirmed(Mouse.CLICK);
        mAlt.assertNotCalled_onSingleTapConfirmed();

        mDispatcher.onDoubleTap(Mouse.CLICK);
        mDelegate.assertCalled_onDoubleTap(Mouse.CLICK);
        mAlt.assertNotCalled_onDoubleTap();

        mDispatcher.onDoubleTapEvent(Mouse.CLICK);
        mDelegate.assertCalled_onDoubleTapEvent(Mouse.CLICK);
        mAlt.assertNotCalled_onDoubleTapEvent();
    }

    @Test
    public void testFallsback() {
        mDispatcher = new InputEventDispatcher(mAlt);
        mDispatcher.register(MotionEvent.TOOL_TYPE_MOUSE, mDelegate);

        mDispatcher.onDown(Touch.TAP);
        mAlt.assertCalled_onDown(Touch.TAP);

        mDispatcher.onShowPress(Touch.TAP);
        mAlt.assertCalled_onShowPress(Touch.TAP);

        mDispatcher.onSingleTapUp(Touch.TAP);
        mAlt.assertCalled_onSingleTapUp(Touch.TAP);

        mDispatcher.onScroll(null, Touch.TAP, -1, -1);
        mAlt.assertCalled_onScroll(null, Touch.TAP, -1, -1);

        mDispatcher.onLongPress(Touch.TAP);
        mAlt.assertCalled_onLongPress(Touch.TAP);

        mDispatcher.onFling(null, Touch.TAP, -1, -1);
        mAlt.assertCalled_onFling(null, Touch.TAP, -1, -1);

        mDispatcher.onSingleTapConfirmed(Touch.TAP);
        mAlt.assertCalled_onSingleTapConfirmed(Touch.TAP);

        mDispatcher.onDoubleTap(Touch.TAP);
        mAlt.assertCalled_onDoubleTap(Touch.TAP);

        mDispatcher.onDoubleTapEvent(Touch.TAP);
        mAlt.assertCalled_onDoubleTapEvent(Touch.TAP);
    }

    @Test
    public void testEatsEventsWhenNoFallback() {
        mDispatcher = new InputEventDispatcher();
        // Register the the delegate on mouse so touch events don't get handled.
        mDispatcher.register(MotionEvent.TOOL_TYPE_MOUSE, mDelegate);

        mDispatcher.onDown(Touch.TAP);
        mAlt.assertNotCalled_onDown();

        mDispatcher.onShowPress(Touch.TAP);
        mAlt.assertNotCalled_onShowPress();

        mDispatcher.onSingleTapUp(Touch.TAP);
        mAlt.assertNotCalled_onSingleTapUp();

        mDispatcher.onScroll(null, Touch.TAP, -1, -1);
        mAlt.assertNotCalled_onScroll();

        mDispatcher.onLongPress(Touch.TAP);
        mAlt.assertNotCalled_onLongPress();

        mDispatcher.onFling(null, Touch.TAP, -1, -1);
        mAlt.assertNotCalled_onFling();

        mDispatcher.onSingleTapConfirmed(Touch.TAP);
        mAlt.assertNotCalled_onSingleTapConfirmed();

        mDispatcher.onDoubleTap(Touch.TAP);
        mAlt.assertNotCalled_onDoubleTap();

        mDispatcher.onDoubleTapEvent(Touch.TAP);
        mAlt.assertNotCalled_onDoubleTapEvent();
    }

    private static final class TestDelegate implements Delegate {

        private final Delegate mDelegate = Mockito.mock(Delegate.class);

        @Override
        public boolean onDown(MotionEvent e) {
            return mDelegate.onDown(e);
        }

        @Override
        public void onShowPress(MotionEvent e) {
            mDelegate.onShowPress(e);
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return mDelegate.onSingleTapUp(e);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return mDelegate.onScroll(e1, e2, distanceX, distanceY);
        }

        @Override
        public void onLongPress(MotionEvent e) {
            mDelegate.onLongPress(e);
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            return mDelegate.onFling(e1, e2, velocityX, velocityY);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            return mDelegate.onSingleTapConfirmed(e);
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            return mDelegate.onDoubleTap(e);
        }

        @Override
        public boolean onDoubleTapEvent(MotionEvent e) {
            return mDelegate.onDoubleTapEvent(e);
        }

        void assertCalled_onDown(MotionEvent e) {
            verify(mDelegate).onDown(e);
        }

        void assertCalled_onShowPress(MotionEvent e) {
            verify(mDelegate).onShowPress(e);
        }

        void assertCalled_onSingleTapUp(MotionEvent e) {
            verify(mDelegate).onSingleTapUp(e);
        }

        void assertCalled_onScroll(MotionEvent e1, MotionEvent e2, float x, float y) {
            verify(mDelegate).onScroll(e1, e2, x, y);
        }

        void assertCalled_onLongPress(MotionEvent e) {
            verify(mDelegate).onLongPress(e);
        }

        void assertCalled_onFling(MotionEvent e1, MotionEvent e2, float x, float y) {
            Mockito.verify(mDelegate).onFling(e1, e2, x, y);
        }

        void assertCalled_onSingleTapConfirmed(MotionEvent e) {
            Mockito.verify(mDelegate).onSingleTapConfirmed(e);
        }

        void assertCalled_onDoubleTap(MotionEvent e) {
            Mockito.verify(mDelegate).onDoubleTap(e);
        }

        void assertCalled_onDoubleTapEvent(MotionEvent e) {
            Mockito.verify(mDelegate).onDoubleTapEvent(e);
        }

        void assertNotCalled_onDown() {
            verify(mDelegate, never()).onDown(any());
        }

        void assertNotCalled_onShowPress() {
            verify(mDelegate, never()).onShowPress(any());
        }

        void assertNotCalled_onSingleTapUp() {
            verify(mDelegate, never()).onSingleTapUp(any());
        }

        void assertNotCalled_onScroll() {
            verify(mDelegate, never()).onScroll(any(), any(), anyFloat(), anyFloat());
        }

        void assertNotCalled_onLongPress() {
            verify(mDelegate, never()).onLongPress(any());
        }

        void assertNotCalled_onFling() {
            Mockito.verify(mDelegate, never()).onFling(any(), any(), anyFloat(), anyFloat());
        }

        void assertNotCalled_onSingleTapConfirmed() {
            Mockito.verify(mDelegate, never()).onSingleTapConfirmed(any());
        }

        void assertNotCalled_onDoubleTap() {
            Mockito.verify(mDelegate, never()).onDoubleTap(any());
        }

        void assertNotCalled_onDoubleTapEvent() {
            Mockito.verify(mDelegate, never()).onDoubleTapEvent(any());
        }
    }
}
