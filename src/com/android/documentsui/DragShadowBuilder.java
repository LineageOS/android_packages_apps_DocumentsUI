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

package com.android.documentsui;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.android.documentsui.DragAndDropManager.State;

import java.text.NumberFormat;

class DragShadowBuilder extends View.DragShadowBuilder {

    private final View mShadowView;
    private final TextView mTitle;
    private final DropBadgeView mIcon;
    private final int mWidth;
    private final int mHeight;
    private final int mShadowRadius;
    private final int mPadding;
    private Paint paint;
    // This will be null if use_material3 flag is OFF.
    private final @Nullable View mAdditionalShadowView;
    // This will always be 0 if the use_material3 flag is OFF.
    private int mDragFileCount = 0;
    // The following 5 dimensions will be 0 if the use_material3 flag is OFF.
    private final int mDragContentRadius;
    private final int mAdditionalLayerOffset;
    private final int mDragFileCounterOffset;
    private final int mShadow2Radius;
    private final int mShadowYOffset;

    DragShadowBuilder(Context context) {
        mWidth = context.getResources().getDimensionPixelSize(getRes(R.dimen.drag_shadow_width));
        mHeight = context.getResources().getDimensionPixelSize(getRes(R.dimen.drag_shadow_height));
        mShadowRadius =
                context.getResources().getDimensionPixelSize(getRes(R.dimen.drag_shadow_radius));
        mPadding =
                context.getResources().getDimensionPixelSize(getRes(R.dimen.drag_shadow_padding));

        mShadowView =
                LayoutInflater.from(context).inflate(getRes(R.layout.drag_shadow_layout), null);
        mTitle = (TextView) mShadowView.findViewById(android.R.id.title);
        mIcon = (DropBadgeView) mShadowView.findViewById(android.R.id.icon);
        if (isUseMaterial3FlagEnabled()) {
            mAdditionalShadowView =
                    LayoutInflater.from(context)
                            .inflate(getRes(R.layout.additional_drag_shadow), null);
            mDragContentRadius =
                    context.getResources()
                            .getDimensionPixelSize(getRes(R.dimen.drag_content_radius));
            mAdditionalLayerOffset =
                    context.getResources()
                            .getDimensionPixelSize(getRes(R.dimen.drag_additional_layer_offset));
            mDragFileCounterOffset =
                    context.getResources()
                            .getDimensionPixelSize(getRes(R.dimen.drag_file_counter_offset));
            mShadow2Radius =
                    context.getResources()
                            .getDimensionPixelSize(getRes(R.dimen.drag_shadow_2_radius));
            mShadowYOffset =
                    context.getResources()
                            .getDimensionPixelSize(getRes(R.dimen.drag_shadow_y_offset));
        } else {
            mAdditionalShadowView = null;
            mDragContentRadius = 0;
            mAdditionalLayerOffset = 0;
            mDragFileCounterOffset = 0;
            mShadow2Radius = 0;
            mShadowYOffset = 0;
        }

        // Important for certain APIs
        mShadowView.setLayerType(View.LAYER_TYPE_SOFTWARE, paint);
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    @Override
    public void onProvideShadowMetrics(
            Point shadowSize, Point shadowTouchPoint) {
        shadowSize.set(mWidth, mHeight);
        shadowTouchPoint.set(mWidth, mHeight);
    }

    @Override
    public void onDrawShadow(Canvas canvas) {
        Rect r = canvas.getClipBounds();
        // Calling measure is necessary in order for all child views to get correctly laid out.
        mShadowView.measure(
                View.MeasureSpec.makeMeasureSpec(r.right - r.left, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(r.bottom - r.top , View.MeasureSpec.EXACTLY));
        mShadowView.layout(r.left, r.top, r.right, r.bottom);

        // Since DragShadow is not an actual view drawn in hardware-accelerated window,
        // android:elevation does not work; we need to draw the shadow ourselves manually.
        paint.setColor(Color.TRANSPARENT);

        // Layers on the canvas (from bottom to top):
        // 1. Two shadows for the additional drag layer (if drag file count > 1)
        // 2. The additional layer view itself (if drag file count > 1)
        // 3. Two shadows for the drag content layer (icon, title)
        // 4. The drag content layer itself
        final int shadowOneOpacity = (int) (255 * 0.15);
        final int shadowTwoOpacity = (int) (255 * 0.30);
        if (mAdditionalShadowView != null && mDragFileCount > 1) {
            mAdditionalShadowView.measure(
                    View.MeasureSpec.makeMeasureSpec(r.right - r.left, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(r.bottom - r.top , View.MeasureSpec.EXACTLY));
            mAdditionalShadowView.layout(r.left, r.top, r.right, r.bottom);
            // Shadow 1
            paint.setShadowLayer(
                    mShadowRadius, 0, mShadowYOffset, Color.argb(shadowOneOpacity, 0, 0, 0));
            canvas.drawRoundRect(
                    r.left + mShadowRadius,
                    r.top + mDragFileCounterOffset + mAdditionalLayerOffset,
                    r.right - mDragFileCounterOffset - mAdditionalLayerOffset,
                    r.bottom - mShadowRadius,
                    mDragContentRadius,
                    mDragContentRadius,
                    paint);
            // Shadow 2
            paint.setShadowLayer(
                    mShadow2Radius, 0, mShadowYOffset, Color.argb(shadowTwoOpacity, 0, 0, 0));
            canvas.drawRoundRect(
                    r.left + mShadowRadius,
                    r.top + mDragFileCounterOffset + mAdditionalLayerOffset,
                    r.right - mDragFileCounterOffset - mAdditionalLayerOffset,
                    r.bottom - mShadowRadius,
                    mDragContentRadius,
                    mDragContentRadius,
                    paint);
            mAdditionalShadowView.draw(canvas);
        }

        if (isUseMaterial3FlagEnabled()) {
            // Shadow 1
            paint.setShadowLayer(
                    mShadowRadius, 0, mShadowYOffset, Color.argb(shadowOneOpacity, 0, 0, 0));
            canvas.drawRoundRect(
                    r.left + mShadowRadius + mAdditionalLayerOffset,
                    r.top + mDragFileCounterOffset,
                    r.right - mDragFileCounterOffset,
                    r.bottom - mShadowRadius - mAdditionalLayerOffset,
                    mDragContentRadius,
                    mDragContentRadius,
                    paint);
            // Shadow 2
            paint.setShadowLayer(
                    mShadow2Radius, 0, mShadowYOffset, Color.argb(shadowTwoOpacity, 0, 0, 0));
            canvas.drawRoundRect(
                    r.left + mShadowRadius + mAdditionalLayerOffset,
                    r.top + mDragFileCounterOffset,
                    r.right - mDragFileCounterOffset,
                    r.bottom - mShadowRadius - mAdditionalLayerOffset,
                    mDragContentRadius,
                    mDragContentRadius,
                    paint);
        } else {
            // Shadow 1
            int opacity = (int) (255 * 0.1);
            paint.setShadowLayer(mShadowRadius, 0, 0, Color.argb(opacity, 0, 0, 0));
            canvas.drawRect(r.left + mPadding, r.top + mPadding, r.right - mPadding,
                    r.bottom - mPadding, paint);
            // Shadow 2
            opacity = (int) (255 * 0.24);
            paint.setShadowLayer(mShadowRadius, 0, mShadowRadius, Color.argb(opacity, 0, 0, 0));
            canvas.drawRect(r.left + mPadding, r.top + mPadding, r.right - mPadding,
                    r.bottom - mPadding, paint);
        }
        mShadowView.draw(canvas);
    }

    void updateTitle(String title) {
        mTitle.setText(title);
    }

    void updateIcon(Drawable icon) {
        mIcon.updateIcon(icon);
    }

    void onStateUpdated(@State int state) {
        mIcon.updateState(state);
    }

    void updateDragFileCount(int count) {
        if (!isUseMaterial3FlagEnabled()) {
            return;
        }
        mDragFileCount = count;
        TextView dragFileCountView = mShadowView.findViewById(getRes(R.id.drag_file_counter));
        if (dragFileCountView != null) {
            dragFileCountView.setVisibility(count > 1 ? View.VISIBLE : View.GONE);
            if (count > 1) {
                NumberFormat numberFormat = NumberFormat.getInstance();
                dragFileCountView.setText(numberFormat.format(count));
            }
        }
    }
}
