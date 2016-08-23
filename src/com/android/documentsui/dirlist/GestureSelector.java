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
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;

import com.android.documentsui.Events;
import com.android.documentsui.Events.InputEvent;
import com.android.documentsui.Events.MotionInputEvent;
import com.android.documentsui.dirlist.ViewAutoScroller.ScrollActionDelegate;
import com.android.documentsui.dirlist.ViewAutoScroller.ScrollDistanceDelegate;

import java.util.function.IntSupplier;

/*
 * Helper class used to intercept events that could cause a gesture multi-select, and keeps
 * the interception going if necessary.
 */
final class GestureSelector {

    private final MultiSelectManager mSelectionMgr;
    private final Runnable mDragScroller;
    private final int mAutoScrollEdgeHeight;
    private final IntSupplier mHeight;
    private int mLastStartedItemPos = -1;
    private boolean mStarted = false;
    private Point mLastInterceptedPoint;

    GestureSelector(
            int autoScrollEdgeHeight,
            MultiSelectManager selectionMgr,
            IntSupplier heightSupplier,
            ScrollActionDelegate actionDelegate) {
        mAutoScrollEdgeHeight = autoScrollEdgeHeight;
        mSelectionMgr = selectionMgr;
        mHeight = heightSupplier;

        ScrollDistanceDelegate distanceDelegate = new ScrollDistanceDelegate() {
            @Override
            public Point getCurrentPosition() {
                return mLastInterceptedPoint;
            }

            @Override
            public int getViewHeight() {
                return mHeight.getAsInt();
            }

            @Override
            public boolean isActive() {
                return mStarted && mSelectionMgr.hasSelection();
            }
        };

        mDragScroller = new ViewAutoScroller(
                mAutoScrollEdgeHeight, distanceDelegate, actionDelegate);
    }

    static GestureSelector create(
            int autoScrollEdgeHeight,
            MultiSelectManager selectionMgr,
            View scrollView) {
        ScrollActionDelegate actionDelegate = new ScrollActionDelegate() {
            @Override
            public void scrollBy(int dy) {
                scrollView.scrollBy(0, dy);
            }

            @Override
            public void runAtNextFrame(Runnable r) {
                scrollView.postOnAnimation(r);
            }

            @Override
            public void removeCallback(Runnable r) {
                scrollView.removeCallbacks(r);
            }
        };
        GestureSelector helper =
                new GestureSelector(
                        autoScrollEdgeHeight, selectionMgr, scrollView::getHeight, actionDelegate);

        return helper;
    }

    // Explicitly kick off a gesture multi-select.
    boolean start(InputEvent event) {
        if (mStarted) {
            return false;
        }
        mStarted = true;
        return true;
    }

    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {

        if (Events.isMouseEvent(e)) {
            return false;
        }

        boolean handled = false;

        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            handled = handleInterceptedDownEvent(rv, e);
        }

        if (e.getAction() == MotionEvent.ACTION_UP) {
            handled = handleUpEvent(rv, e);
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            handled = handleInterceptedMoveEvent(rv, e);
        }

        return handled;
    }

    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        if (!mStarted) {
            return;
        }

        if (e.getAction() == MotionEvent.ACTION_UP) {
            handleUpEvent(rv, e);
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            handleOnTouchMoveEvent(rv, e);
        }
    }

    // Called when an ACTION_DOWN event is intercepted.
    // If down event happens on a file/doc, we mark that item's position as last started.
    private boolean handleInterceptedDownEvent(RecyclerView rv, MotionEvent e) {
        View itemView = rv.findChildViewUnder(e.getX(), e.getY());
        try (InputEvent event = MotionInputEvent.obtain(e, rv)) {
            if (itemView != null) {
                mLastStartedItemPos = rv.getChildAdapterPosition(itemView);
            }
        }
        return false;
    }

    // Called when an ACTION_MOVE event is intercepted.
    private boolean handleInterceptedMoveEvent(RecyclerView rv, MotionEvent e) {
        try (InputEvent event = MotionInputEvent.obtain(e, rv)) {
            mLastInterceptedPoint = event.getOrigin();
            if (mStarted) {
                mSelectionMgr.startRangeSelection(mLastStartedItemPos);
                return true;
            }
        }
        return false;
    }

    // Called when ACTION_UP event is intercepted.
    // Essentially, since this means all gesture movement is over, reset everything.
    private boolean handleUpEvent(RecyclerView rv, MotionEvent e) {
        mLastStartedItemPos = -1;
        mStarted = false;
        mSelectionMgr.getSelection().applyProvisionalSelection();
        return false;
    }

    // Call when an intercepted ACTION_MOVE event is passed down.
    // At this point, we are sure user wants to gesture multi-select.
    private void handleOnTouchMoveEvent(RecyclerView rv, MotionEvent e) {
        try (InputEvent event = MotionInputEvent.obtain(e, rv)) {
            mLastInterceptedPoint = event.getOrigin();

            // If user has moved his pointer to the bottom-right empty pane (ie. to the right of the
            // last item of the recycler view), we would want to set that as the currentItemPos
            View lastItem = rv.getLayoutManager()
                    .getChildAt(rv.getLayoutManager().getChildCount() - 1);
            boolean bottomRight = e.getX() > lastItem.getRight() && e.getY() > lastItem.getTop();

            // Since views get attached & detached from RecyclerView,
            // {@link LayoutManager#getChildCount} can return a different number from the actual
            // number
            // of items in the adapter. Using the adapter is the for sure way to get the actual last
            // item position.
            final float inboundY = getInboundY(rv.getHeight(), e.getY());
            final int lastGlidedItemPos = (bottomRight) ? rv.getAdapter().getItemCount() - 1
                    : rv.getChildAdapterPosition(rv.findChildViewUnder(e.getX(), inboundY));
            if (lastGlidedItemPos != RecyclerView.NO_POSITION) {
                doGestureMultiSelect(lastGlidedItemPos);
            }
            if (insideDragZone(rv)) {
                mDragScroller.run();
            }
        }
    }

    // It's possible for events to go over the top/bottom of the RecyclerView.
    // We want to get a Y-coordinate within the RecyclerView so we can find the childView underneath
    // correctly.
    private float getInboundY(float max, float y) {
        if (y < 0f) {
            return 0f;
        } else if (y > max) {
            return max;
        }
        return y;
    }

    /* Given the end position, select everything in-between.
     * @param endPos  The adapter position of the end item.
     */
    private void doGestureMultiSelect(int endPos) {
        mSelectionMgr.snapProvisionalRangeSelection(endPos);
    }

    private boolean insideDragZone(View scrollView) {
        if (mLastInterceptedPoint == null) {
            return false;
        }

        boolean shouldScrollUp = mLastInterceptedPoint.y < mAutoScrollEdgeHeight
                && scrollView.canScrollVertically(-1);
        boolean shouldScrollDown = mLastInterceptedPoint.y > scrollView.getHeight() -
                mAutoScrollEdgeHeight && scrollView.canScrollVertically(1);
        return shouldScrollUp || shouldScrollDown;
    }
}