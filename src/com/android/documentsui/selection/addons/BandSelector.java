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

import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.util.Log;
import android.view.View;

import com.android.documentsui.R;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionManager.SelectionPredicate;
import com.android.documentsui.selection.SelectionManager.StableIdProvider;
import com.android.documentsui.selection.addons.ViewAutoScroller.Callbacks;
import com.android.documentsui.selection.addons.ViewAutoScroller.ScrollHost;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Provides mouse driven band-selection support when used in conjunction with
 * a {@link RecyclerView} instance and a {@link SelectionManager}. This class is responsible
 * for rendering the band select overlay and selecting overlaid items via SelectionManager.
 */
public class BandSelector {

    static final boolean DEBUG = false;
    static final String TAG = "BandController";

    private final Runnable mModelBuilder;

    private final SelectionHost mHost;
    private final StableIdProvider mStableIds;
    private final RecyclerView.Adapter<?> mAdapter;
    private final SelectionManager mSelectionMgr;
    private final Selection mSelection;
    private final SelectionPredicate mSelectionPredicate;
    private final ContentLock mLock;
    private final Runnable mViewScroller;
    private final GridModel.OnSelectionChangedListener mGridListener;
    private final List<Runnable> mStartBandSelectListeners = new ArrayList<>();

    @Nullable private Rect mBounds;
    @Nullable private Point mCurrentPosition;
    @Nullable private Point mOrigin;
    @Nullable private GridModel mModel;

    public BandSelector(
            final RecyclerView view,
            StableIdProvider stableIds,
            SelectionManager selectionManager,
            SelectionPredicate selectionPredicate,
            ContentLock lock) {

        this(new RecyclerViewSelectionHost(view),
                view.getAdapter(),
                stableIds,
                selectionManager,
                selectionPredicate,
                lock);
    }

    @VisibleForTesting
    BandSelector(
            SelectionHost host,
            RecyclerView.Adapter<?> adapter,
            StableIdProvider stableIds,
            SelectionManager selectionManager,
            SelectionPredicate selectionPredicate,
            ContentLock lock) {

        mHost = host;
        mStableIds = stableIds;
        mAdapter = adapter;
        mSelectionMgr = selectionManager;
        mSelectionPredicate = selectionPredicate;

        mLock = lock;

        mSelection = selectionManager.getSelection();

        mHost.addOnScrollListener(
                new OnScrollListener() {
                    @Override
                    public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                        BandSelector.this.onScrolled(recyclerView, dx, dy);
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
                        return BandSelector.this.isActive();
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

        mGridListener = new GridModel.OnSelectionChangedListener() {
                @Override
                public void onSelectionChanged(Set<String> updatedSelection) {
                    mSelectionMgr.setProvisionalSelection(updatedSelection);
                }
            };

        mModelBuilder = new Runnable() {
            @Override
            public void run() {
                mModel = new GridModel(mHost, mStableIds, mSelectionPredicate);
                mModel.addOnSelectionChangedListener(mGridListener);
            }
        };
    }

    @VisibleForTesting
    boolean isActive() {
        return mModel != null;
    }

    public boolean onInterceptTouchEvent(InputEvent e) {
        if (shouldStart(e)) {
            if (!e.isCtrlKeyDown()) {
                mSelectionMgr.clearSelection();
            }
            startBandSelect(e.getOrigin());
        } else if (shouldStop(e)) {
            endBandSelect();
        }

        return isActive();
    }

    public void addBandSelectStartedListener(Runnable listener) {
        mStartBandSelectListeners.add(listener);
    }

    public void removeBandSelectStartedListener(Runnable listener) {
        mStartBandSelectListeners.remove(listener);
    }

    /**
     * Handle a change in layout by cleaning up and getting rid of the old model and creating
     * a new model which will track the new layout.
     */
    public void handleLayoutChanged() {
        if (mModel != null) {
            mModel.removeOnSelectionChangedListener(mGridListener);
            mModel.stopListening();

            // build a new model, all fresh and happy.
            mModelBuilder.run();
        }
    }

    public boolean shouldStart(InputEvent e) {
        // Don't start, or extend bands on non-left clicks.
        if (!e.isPrimaryButtonPressed()) {
            return false;
        }

        if (!e.isMouseEvent() && isActive()) {
            // Weird things happen if we keep up band select
            // when touch events happen.
            endBandSelect();
            return false;
        }

        // b/30146357 && b/23793622. onInterceptTouchEvent does not dispatch events to onTouchEvent
        // unless the event is != ACTION_DOWN. Thus, we need to actually start band selection when
        // mouse moves, or else starting band selection on mouse down can cause problems as events
        // don't get routed correctly to onTouchEvent.
        return !isActive()
                && e.isActionMove() // the initial button move via mouse-touch (ie. down press)
                // The adapter inserts items for UI layout purposes that aren't
                // associated with files. Checking against actual modelIds count
                // effectively ignores those UI layout items.
                && !mStableIds.getStableIds().isEmpty()
                && !e.isOverDragHotspot();
    }

    public boolean shouldStop(InputEvent input) {
        return isActive()
                && input.isMouseEvent()
                && (input.isActionUp() || input.isMultiPointerActionUp() || input.isActionCancel());
    }

    /**
     * Processes a MotionEvent by starting, ending, or resizing the band select overlay.
     * @param input
     */
    public void onTouchEvent(InputEvent input) {
        assert(input.isMouseEvent());

        if (shouldStop(input)) {
            endBandSelect();
            return;
        }

        // We shouldn't get any events in this method when band select is not active,
        // but it turns some guests show up late to the party.
        // Probably happening when a re-layout is happening to the ReyclerView (ie. Pull-To-Refresh)
        if (!isActive()) {
            return;
        }

        assert(input.isActionMove());
        mCurrentPosition = input.getOrigin();
        mModel.resizeSelection(input.getOrigin());
        scrollViewIfNecessary();
        resizeBandSelectRectangle();
    }

    /**
     * Starts band select by adding the drawable to the RecyclerView's overlay.
     */
    private void startBandSelect(Point origin) {
        if (DEBUG) Log.d(TAG, "Starting band select @ " + origin);

        mLock.block();
        notifyBandSelectStartedListeners();
        mOrigin = origin;
        mModelBuilder.run();  // Creates a new selection model.
        mModel.startSelection(mOrigin);
    }

    private void notifyBandSelectStartedListeners() {
        for (Runnable listener : mStartBandSelectListeners) {
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
        mSelectionMgr.mergeProvisionalSelection();
        mModel.endSelection();
        int firstSelected = mModel.getPositionNearestOrigin();
        if (firstSelected != GridModel.NOT_SET) {
            if (mSelection.contains(mStableIds.getStableId(firstSelected))) {
                // TODO: firstSelected should really be lastSelected, we want to anchor the item
                // where the mouse-up occurred.
                mSelectionMgr.setSelectionRangeBegin(firstSelected);
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
    interface SelectionHost extends Callbacks {
        void showBand(Rect rect);
        void hideBand();
        void addOnScrollListener(RecyclerView.OnScrollListener listener);
        void removeOnScrollListener(RecyclerView.OnScrollListener listener);
        int getHeight();
        void invalidateView();
        Point createAbsolutePoint(Point relativePoint);
        Rect getAbsoluteRectForChildViewAt(int index);
        int getAdapterPositionAt(int index);
        int getColumnCount();
        int getChildCount();
        int getVisibleChildCount();
        /**
         * Items may be in the adapter, but without an attached view.
         */
        boolean hasView(int adapterPosition);
    }

    /** Recycler view facade implementation backed by good ol' RecyclerView. */
    private static final class RecyclerViewSelectionHost implements SelectionHost {

        private final RecyclerView mView;
        private final Drawable mBand;

        private boolean mIsOverlayShown = false;

        RecyclerViewSelectionHost(RecyclerView view) {
            mView = view;
            mBand = mView.getContext().getTheme().getDrawable(R.drawable.band_select_overlay);
        }

        @Override
        public int getAdapterPositionAt(int index) {
            return mView.getChildAdapterPosition(mView.getChildAt(index));
        }

        @Override
        public void addOnScrollListener(RecyclerView.OnScrollListener listener) {
            mView.addOnScrollListener(listener);
        }

        @Override
        public void removeOnScrollListener(RecyclerView.OnScrollListener listener) {
            mView.removeOnScrollListener(listener);
        }

        @Override
        public Point createAbsolutePoint(Point relativePoint) {
            return new Point(relativePoint.x + mView.computeHorizontalScrollOffset(),
                    relativePoint.y + mView.computeVerticalScrollOffset());
        }

        @Override
        public Rect getAbsoluteRectForChildViewAt(int index) {
            final View child = mView.getChildAt(index);
            final Rect childRect = new Rect();
            child.getHitRect(childRect);
            childRect.left += mView.computeHorizontalScrollOffset();
            childRect.right += mView.computeHorizontalScrollOffset();
            childRect.top += mView.computeVerticalScrollOffset();
            childRect.bottom += mView.computeVerticalScrollOffset();
            return childRect;
        }

        @Override
        public int getChildCount() {
            return mView.getAdapter().getItemCount();
        }

        @Override
        public int getVisibleChildCount() {
            return mView.getChildCount();
        }

        @Override
        public int getColumnCount() {
            RecyclerView.LayoutManager layoutManager = mView.getLayoutManager();
            if (layoutManager instanceof GridLayoutManager) {
                return ((GridLayoutManager) layoutManager).getSpanCount();
            }

            // Otherwise, it is a list with 1 column.
            return 1;
        }

        @Override
        public int getHeight() {
            return mView.getHeight();
        }

        @Override
        public void invalidateView() {
            mView.invalidate();
        }

        @Override
        public void runAtNextFrame(Runnable r) {
            mView.postOnAnimation(r);
        }

        @Override
        public void removeCallback(Runnable r) {
            mView.removeCallbacks(r);
        }

        @Override
        public void scrollBy(int dy) {
            mView.scrollBy(0, dy);
        }

        @Override
        public void showBand(Rect rect) {
            mBand.setBounds(rect);

            if (!mIsOverlayShown) {
                mView.getOverlay().add(mBand);
            }
        }

        @Override
        public void hideBand() {
            mView.getOverlay().remove(mBand);
        }

        @Override
        public boolean hasView(int pos) {
            return mView.findViewHolderForAdapterPosition(pos) != null;
        }
    }
}
