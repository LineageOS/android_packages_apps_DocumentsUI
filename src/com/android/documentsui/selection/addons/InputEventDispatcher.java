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

import static com.google.common.base.Preconditions.checkArgument;

import android.support.annotation.Nullable;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.MotionEvent;

/**
 * Gesture event dispatcher. Dispatches gesture events to respective
 * input type specific handlers.
 */
public final class InputEventDispatcher implements OnGestureListener, OnDoubleTapListener {

    // Currently there are four known input types. ERASER is the last one, so has the
    // highest value. UNKNOWN is zero, so we add one. This allows delegates to be
    // registered by type, and avoid the auto-boxing that would be necessary were we
    // to store delegates in a Map<Integer, Delegate>.
    private static final int sNumInputTypes = MotionEvent.TOOL_TYPE_ERASER + 1;
    private final Delegate[] mDelegates = new Delegate[sNumInputTypes];

    private final Delegate mDefaultDelegate;

    public InputEventDispatcher(Delegate defaultDelegate) {
        checkArgument(defaultDelegate != null);
        mDefaultDelegate = defaultDelegate;
    }

    public InputEventDispatcher() {
        mDefaultDelegate = new DummyDelegate();
    }

    /**
     * @param toolType
     * @param delegate the delegate, or null to unregister.
     */
    public void register(int toolType, @Nullable Delegate delegate) {
        checkArgument(toolType >= 0 && toolType <= MotionEvent.TOOL_TYPE_ERASER);
        mDelegates[toolType] = delegate;
    }

    private Delegate getDelegate(MotionEvent e) {
        Delegate d = mDelegates[e.getToolType(0)];
        return d != null ? d : mDefaultDelegate;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        return getDelegate(e).onSingleTapConfirmed(e);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        return getDelegate(e).onDoubleTap(e);
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return getDelegate(e).onDoubleTapEvent(e);
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return getDelegate(e).onDown(e);
    }

    @Override
    public void onShowPress(MotionEvent e) {
        getDelegate(e).onShowPress(e);
    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return getDelegate(e).onSingleTapUp(e);
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return getDelegate(e2).onScroll(e1, e2, distanceX, distanceY);
    }

    @Override
    public void onLongPress(MotionEvent e) {
        getDelegate(e).onLongPress(e);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return getDelegate(e2).onFling(e1, e2, velocityX, velocityY);
    }

    public static interface Delegate extends OnGestureListener, OnDoubleTapListener {}
    public static class DummyDelegate extends SimpleOnGestureListener implements Delegate {}
}
