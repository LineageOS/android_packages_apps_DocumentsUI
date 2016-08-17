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

import android.annotation.IntDef;
import android.graphics.Point;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;

import com.android.documentsui.Events;
import com.android.documentsui.Events.InputEvent;
import com.android.documentsui.Events.MotionInputEvent;
import com.android.documentsui.dirlist.ViewAutoScroller.ScrollActionDelegate;
import com.android.documentsui.dirlist.ViewAutoScroller.ScrollDistanceDelegate;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.function.Function;
import java.util.function.IntSupplier;

/*
 * Helper class used to intercept events that could cause a gesture multi-select, and keeps
 * the interception going if necessary.
 */
class GestureMultiSelectHelper {

    // Gesture can be used to either select or erase file selections. These are used to define the
    // type of on-going gestures.
    @IntDef(flag = true, value = {
            TYPE_NONE,
            TYPE_SELECTION,
            TYPE_ERASE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SelectType {}
    public static final int TYPE_NONE = 0;
    public static final int TYPE_SELECTION = 1;
    public static final int TYPE_ERASE = 2;

    // User intent. When intercepting an event, we can see if user intends to scroll, select, or
    // the intent is unknown.
    @IntDef(flag = true, value = {
            TYPE_UNKNOWN,
            TYPE_SELECT,
            TYPE_SCROLL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface GestureSelectIntent {}
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_SELECT = 1;
    public static final int TYPE_SCROLL = 2;

    private final MultiSelectManager mSelectionMgr;
    private final Runnable mDragScroller;
    private final Function<Integer, String> mModelIdFinder;
    private final int mAutoScrollEdgeHeight;
    private final int mColumnCount;
    private final IntSupplier mHeight;
    private int mLastStartedItemPos = -1;
    private boolean mEnabled = false;
    private Point mLastDownPoint;
    private Point mLastInterceptedPoint;
    private @SelectType int mType = TYPE_NONE;
    private @GestureSelectIntent int mUserIntent = TYPE_UNKNOWN;

    GestureMultiSelectHelper(
            int columnCount,
            int autoScrollEdgeHeight,
            Function<Integer, String> modelIdFinder,
            MultiSelectManager selectionMgr,
            IntSupplier heightSupplier,
            ScrollActionDelegate actionDelegate) {
        mColumnCount = columnCount;
        mAutoScrollEdgeHeight = autoScrollEdgeHeight;
        mModelIdFinder = modelIdFinder;
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
                return mSelectionMgr.hasSelection();
            }
        };

        mDragScroller = new ViewAutoScroller(
                mAutoScrollEdgeHeight, distanceDelegate, actionDelegate);
    }

    static GestureMultiSelectHelper create(
            int columnCount,
            int autoScrollEdgeHeight,
            Function<Integer, String> modelIdFinder,
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
        GestureMultiSelectHelper helper =
                new GestureMultiSelectHelper(columnCount, autoScrollEdgeHeight, modelIdFinder,
                        selectionMgr, scrollView::getHeight, actionDelegate);

        return helper;
    }

    // Explicitly kick off a gesture multi-select without any second guessing
    void start() {
        mUserIntent = TYPE_SELECT;
    }

    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {

        if (!mEnabled || Events.isMouseEvent(e)) {
            return false;
        }

        boolean handled = false;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            handled = handleInterceptedDownEvent(rv, e);
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            handled = handleInterceptedMoveEvent(rv, e);
        }

        if (e.getAction() == MotionEvent.ACTION_UP) {
            handled = handleUpEvent(rv, e);
        }

        return handled;
    }

    public void onTouchEvent(RecyclerView rv, MotionEvent e) {
        if (!mEnabled) {
            return;
        }

        if (e.getAction() == MotionEvent.ACTION_UP) {
            handleUpEvent(rv, e);
        }

        if (e.getAction() == MotionEvent.ACTION_MOVE) {
            handleOnTouchMoveEvent(rv, e);
        }
    }

    public void setEnabled(boolean enabled) {
        mEnabled = enabled;
    }

    // Called when an ACTION_DOWN event is intercepted.
    // Sets mode to ERASE if the item below the MotionEvent is already selected
    // Else, sets it to SELECTION mode.
    private boolean handleInterceptedDownEvent(RecyclerView rv, MotionEvent e) {
        View itemView = rv.findChildViewUnder(e.getX(), e.getY());
        try (InputEvent event = MotionInputEvent.obtain(e, rv)) {
            mLastDownPoint = event.getOrigin();
            if (itemView != null) {
                mLastStartedItemPos = rv.getChildAdapterPosition(itemView);
                String modelId = mModelIdFinder.apply(mLastStartedItemPos);
                if (mSelectionMgr.getSelection().contains(modelId)) {
                    mType = TYPE_ERASE;
                } else {
                    mType = TYPE_SELECTION;
                }
            }
        }
        return false;
    }

    // Called when an ACTION_MOVE event is intercepted.
    private boolean handleInterceptedMoveEvent(RecyclerView rv, MotionEvent e) {
        if (shouldInterceptMoveEvent(rv, e)) {
            mSelectionMgr.startRangeSelection(mLastStartedItemPos);
            return true;
        }
        return false;
    }

    // Called when ACTION_UP event is intercepted.
    // Essentially, since this means all gesture movement is over, reset everything.
    private boolean handleUpEvent(RecyclerView rv, MotionEvent e) {
        mType = TYPE_NONE;
        mLastStartedItemPos = -1;
        mLastDownPoint = null;
        mUserIntent = TYPE_UNKNOWN;
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
            int lastGlidedItemPos = (bottomRight) ? rv.getAdapter().getItemCount() - 1
                    : rv.getChildAdapterPosition(rv.findChildViewUnder(e.getX(), e.getY()));
            if (lastGlidedItemPos != RecyclerView.NO_POSITION) {
                doGestureMultiSelect(lastGlidedItemPos);
            }
            if (insideDragZone(rv)) {
                mDragScroller.run();
            }
        }
    }

    /* Given the start position and the end position, select or erase everything in-between.
     * @param startPos The adapter position of the start item.
     * @param endPos  The adapter position of the end item.
     */
    private void doGestureMultiSelect(int endPos) {
        if (mType == TYPE_SELECTION) {
            mSelectionMgr.snapProvisionalRangeSelection(endPos);
        }
    }

    // Logic dictating whether a particular ACTION_MOVE event should be intercepted or not.
    // If user has already shown some clear intent to want to select, we will always return true.
    // If user has moved to an adjacent item, two possible cases:
    // 1. User moved left/right. Then it's explicit that they want to multi-select.
    // 2. User moved top/bottom. Then it's explicit that they want to scroll/natural behavior.
    private boolean shouldInterceptMoveEvent(RecyclerView rv, MotionEvent e) {
        try (InputEvent event = MotionInputEvent.obtain(e, rv)) {
            mLastInterceptedPoint = event.getOrigin();

            if (mUserIntent == TYPE_SELECT) {
                return true;
            }

            int startItemPos = rv.getChildAdapterPosition(rv.findChildViewUnder(mLastDownPoint.x,
                    mLastDownPoint.y));
            int currentItemPos = rv
                    .getChildAdapterPosition(rv.findChildViewUnder(e.getX(), e.getY()));
            if (startItemPos == RecyclerView.NO_POSITION ||
                    currentItemPos == RecyclerView.NO_POSITION) {
                // It's possible that user either started gesture from an empty space, or is so far
                // moving his finger to an empty space. Either way, we should not consume the event,
                // so
                // return false.
                return false;
            }

            if (startItemPos != currentItemPos) {
                int diff = Math.abs(startItemPos - currentItemPos);
                if (diff == 1 && mSelectionMgr.hasSelection()) {
                    mUserIntent = TYPE_SELECT;
                    return true;
                } else if (diff == mColumnCount) {
                    mUserIntent = TYPE_SCROLL;
                }
            }
        }
        return false;
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