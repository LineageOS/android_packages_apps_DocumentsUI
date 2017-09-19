/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.support.v4.util.Preconditions.checkArgument;

import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.util.Log;
import android.view.MotionEvent;

import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionHelper;
import com.android.documentsui.selection.SelectionHelper.SelectionPredicate;
import com.android.documentsui.selection.SelectionHelper.StableIdProvider;
import com.android.documentsui.selection.addons.ViewAutoScroller.ScrollHost;
import com.android.documentsui.selection.addons.ViewAutoScroller.ScrollerCallbacks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provides mouse driven band-selection support when used in conjunction with
 * a {@link RecyclerView} instance and a {@link SelectionHelper}. This class is responsible
 * for rendering a band overlay and manipulating selection status of the items it intersects with.
 *
 * <p>Usage:
 *
 * <p><pre>TODO</pre>
 */
public class BandSelectionHelper {

    static final boolean DEBUG = false;
    static final String TAG = "BandController";

    private final BandHost mHost;
    private final StableIdProvider mStableIds;
    private final RecyclerView.Adapter<?> mAdapter;
    private final SelectionHelper mSelectionHelper;
    private final Selection mSelection;
    private final SelectionPredicate mSelectionPredicate;
    private final ContentLock mLock;
    private final Runnable mViewScroller;
    private final GridModel.SelectionObserver mGridObserver;
    private final List<Runnable> mBandStartedListeners = new ArrayList<>();

    @Nullable private Rect mBounds;
    @Nullable private Point mCurrentPosition;
    @Nullable private Point mOrigin;
    @Nullable private GridModel mModel;

    public BandSelectionHelper(
            BandHost host,
            RecyclerView.Adapter<?> adapter,
            StableIdProvider stableIds,
            SelectionHelper selectionHelper,
            SelectionPredicate selectionPredicate,
            ContentLock lock) {

        checkArgument(host != null);
        checkArgument(adapter != null);
        checkArgument(stableIds != null);
        checkArgument(selectionHelper != null);
        checkArgument(selectionPredicate != null);
        checkArgument(lock != null);

        mHost = host;
        mStableIds = stableIds;
        mAdapter = adapter;
        mSelectionHelper = selectionHelper;
        mSelectionPredicate = selectionPredicate;
        mLock = lock;

        mSelection = selectionHelper.getSelection();

        mHost.addOnScrollListener(
                new OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        BandSelectionHelper.this.onScrolled(recyclerView, dx, dy);
                    }
                });

        mViewScroller = new ViewAutoScroller(
                new ScrollHost() {
                    @Override
                    public Point getCurrentPosition() {
                        return mCurrentPosition;
                    }

                    @Override
                    public int getViewHeight() {
                        return mHost.getHeight();
                    }

                    @Override
                    public boolean isActive() {
                        return BandSelectionHelper.this.isActive();
                    }
                },
                host);

        mAdapter.registerAdapterDataObserver(
                new RecyclerView.AdapterDataObserver() {
                    @Override
                    public void onChanged() {
                        if (isActive()) {
                            endBandSelect();
                        }
                    }

                    @Override
                    public void onItemRangeChanged(
                            int startPosition, int itemCount, Object payload) {
                        // No change in position. Ignoring.
                    }

                    @Override
                    public void onItemRangeInserted(int startPosition, int itemCount) {
                        if (isActive()) {
                            endBandSelect();
                        }
                    }

                    @Override
                    public void onItemRangeRemoved(int startPosition, int itemCount) {
                        assert(startPosition >= 0);
                        assert(itemCount > 0);

                        // TODO: Should update grid model.
                    }

                    @Override
                    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                        throw new UnsupportedOperationException();
                    }
                });

        mGridObserver = new GridModel.SelectionObserver() {
                @Override
                public void onSelectionChanged(Set<String> updatedSelection) {
                    mSelectionHelper.setProvisionalSelection(updatedSelection);
                }
            };
    }

    public void createModel() {
        if (mModel != null) {
            mModel.onDestroy();
        }

        mModel = new GridModel(mHost, mStableIds, mSelectionPredicate);
        mModel.addOnSelectionChangedListener(mGridObserver);
    }

    @VisibleForTesting
    boolean isActive() {
        return mModel != null;
    }

    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (shouldStart(e)) {
            if (!MotionEvents.isCtrlKeyPressed(e)) {
                mSelectionHelper.clearSelection();
            }

            startBandSelect(MotionEvents.getOrigin(e));
        } else if (shouldStop(e)) {
            endBandSelect();
        }

        return isActive();
    }

    /**
     * Adds a new listener to be notified when band is created.
     */
    public void addOnBandStartedListener(Runnable listener) {
        mBandStartedListeners.add(listener);
    }

    /**
     * Removes listener. No-op if listener was not previously installed.
     */
    public void removeOnBandStartedListener(Runnable listener) {
        mBandStartedListeners.remove(listener);
    }

    /**
     * Clients must call this when there are any material changes to the layout of items
     * in RecyclerView.
     */
    public void onLayoutChanged() {
        if (mModel != null) {
            createModel();
        }
    }

    public boolean shouldStart(MotionEvent e) {
        // Don't start, or extend bands on non-left clicks.
        if (!MotionEvents.isPrimaryButtonPressed(e)) {
            return false;
        }

        // TODO: Refactor to NOT have side-effects on this "should" method.
        // Weird things happen if we keep up band select
        // when touch events happen.
        if (isActive() && !MotionEvents.isMouseEvent(e)) {
            endBandSelect();
            return false;
        }

        // b/30146357 && b/23793622. onInterceptTouchEvent does not dispatch events to onTouchEvent
        // unless the event is != ACTION_DOWN. Thus, we need to actually start band selection when
        // mouse moves, or else starting band selection on mouse down can cause problems as events
        // don't get routed correctly to onTouchEvent.
        return !isActive()
                && MotionEvents.isActionMove(e) // the initial button move via mouse-touch (ie. down press)
                // The adapter inserts items for UI layout purposes that aren't
                // associated with files. Checking against actual modelIds count
                // effectively ignores those UI layout items.
                && !mStableIds.getStableIds().isEmpty()
                && mHost.canInitiateBand(e);
    }

    public boolean shouldStop(MotionEvent e) {
        return isActive()
                && MotionEvents.isMouseEvent(e)
                && (MotionEvents.isActionUp(e)
                        || MotionEvents.isActionPointerUp(e)
                        || MotionEvents.isActionCancel(e));
    }

    /**
     * Processes a MotionEvent by starting, ending, or resizing the band select overlay.
     * @param input
     */
    public void onTouchEvent(MotionEvent e) {
        assert MotionEvents.isMouseEvent(e);

        if (shouldStop(e)) {
            endBandSelect();
            return;
        }

        // We shouldn't get any events in this method when band select is not active,
        // but it turns some guests show up late to the party.
        // Probably happening when a re-layout is happening to the ReyclerView (ie. Pull-To-Refresh)
        if (!isActive()) {
            return;
        }

        assert MotionEvents.isActionMove(e);

        mCurrentPosition = MotionEvents.getOrigin(e);
        mModel.resizeSelection(mCurrentPosition);

        scrollViewIfNecessary();
        resizeBandSelectRectangle();
    }

    /**
     * Starts band select by adding the drawable to the RecyclerView's overlay.
     */
    private void startBandSelect(Point origin) {
        if (DEBUG) Log.d(TAG, "Starting band select @ " + origin);

        mLock.block();
        onBandStarted();
        mOrigin = origin;
        createModel();
        mModel.startSelection(mOrigin);
    }

    private void onBandStarted() {
        for (Runnable listener : mBandStartedListeners) {
            listener.run();
        }
    }

    /**
     * Scrolls the view if necessary.
     */
    private void scrollViewIfNecessary() {
        mHost.removeCallback(mViewScroller);
        mViewScroller.run();
        mHost.invalidateView();
    }

    /**
     * Resizes the band select rectangle by using the origin and the current pointer position as
     * two opposite corners of the selection.
     */
    private void resizeBandSelectRectangle() {
        mBounds = new Rect(Math.min(mOrigin.x, mCurrentPosition.x),
                Math.min(mOrigin.y, mCurrentPosition.y),
                Math.max(mOrigin.x, mCurrentPosition.x),
                Math.max(mOrigin.y, mCurrentPosition.y));
        mHost.showBand(mBounds);
    }

    /**
     * Ends band select by removing the overlay.
     */
    private void endBandSelect() {
        if (DEBUG) Log.d(TAG, "Ending band select.");

        mHost.hideBand();
        mSelectionHelper.mergeProvisionalSelection();
        mModel.endSelection();
        int firstSelected = mModel.getPositionNearestOrigin();
        if (firstSelected != GridModel.NOT_SET) {
            if (mSelection.contains(mStableIds.getStableId(firstSelected))) {
                // TODO: firstSelected should really be lastSelected, we want to anchor the item
                // where the mouse-up occurred.
                mSelectionHelper.anchorRange(firstSelected);
            } else {
                // TODO: Check if this is really happening.
                Log.w(TAG, "First selected by band is NOT in selection!");
            }
        }

        mModel = null;
        mOrigin = null;
        mLock.unblock();
    }

    private void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        if (!isActive()) {
            return;
        }

        // Adjust the y-coordinate of the origin the opposite number of pixels so that the
        // origin remains in the same place relative to the view's items.
        mOrigin.y -= dy;
        resizeBandSelectRectangle();
    }

    /**
     * Provides functionality for BandController. Exists primarily to tests that are
     * fully isolated from RecyclerView.
     */
    public static abstract class BandHost extends ScrollerCallbacks {
        public abstract boolean canInitiateBand(MotionEvent e);
        public abstract void showBand(Rect rect);
        public abstract void hideBand();
        public abstract void addOnScrollListener(RecyclerView.OnScrollListener listener);
        public abstract void removeOnScrollListener(RecyclerView.OnScrollListener listener);
        public abstract int getHeight();
        public abstract void invalidateView();
        public abstract Point createAbsolutePoint(Point relativePoint);
        public abstract Rect getAbsoluteRectForChildViewAt(int index);
        public abstract int getAdapterPositionAt(int index);
        public abstract int getColumnCount();
        public abstract int getChildCount();
        public abstract int getVisibleChildCount();
        /**
         * @return true if the item at adapter position is attached to a view.
         */
        public abstract boolean hasView(int adapterPosition);
    }
}
