/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.internal.util.Preconditions.checkArgument;

import android.annotation.DrawableRes;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;

import com.android.documentsui.selection.addons.BandSelectionHelper.BandHost;

/**
 * RecyclerView backed {@link BandHost}.
 */
public final class DefaultBandHost extends BandHost {

    private final RecyclerView mView;
    private final Drawable mBand;
    private final BandPredicate mBandPredicate;

    private boolean mIsOverlayShown;

    public DefaultBandHost(
            RecyclerView view,
            @DrawableRes int bandOverlayId,
            BandPredicate bandPredicate) {

        checkArgument(view != null);
        checkArgument(bandPredicate != null);

        mView = view;
        mBandPredicate = bandPredicate;
        mBand = mView.getContext().getTheme().getDrawable(bandOverlayId);

        checkArgument(mBand != null);
    }

    @Override
    public boolean canInitiateBand(MotionEvent e) {
        return mBandPredicate.canInitiate(e);
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