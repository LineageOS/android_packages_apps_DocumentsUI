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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.Shared;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;

import java.util.List;
import java.util.function.Function;

final class DragShadowBuilder extends View.DragShadowBuilder {

    private final View mShadowView;
    private final TextView mTitle;
    private final ImageView mIcon;
    private final int mWidth;
    private final int mHeight;

    public DragShadowBuilder(Context context, String title, Drawable icon) {
        mWidth = context.getResources().getDimensionPixelSize(R.dimen.drag_shadow_width);
        mHeight= context.getResources().getDimensionPixelSize(R.dimen.drag_shadow_height);

        mShadowView = LayoutInflater.from(context).inflate(R.layout.drag_shadow_layout, null);
        mTitle = (TextView) mShadowView.findViewById(android.R.id.title);
        mIcon = (ImageView) mShadowView.findViewById(android.R.id.icon);

        mTitle.setText(title);
        mIcon.setImageDrawable(icon);
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
                View.MeasureSpec.makeMeasureSpec(r.right- r.left, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(r.top- r.bottom, View.MeasureSpec.EXACTLY));
        mShadowView.layout(r.left, r.top, r.right, r.bottom);
        mShadowView.draw(canvas);
    }

    /**
     * Provides a means of fully isolating the mechanics of building drag shadows (and builders)
     * in support of testing.
     */
    public static final class Factory implements Function<Selection, DragShadowBuilder> {

        private final Context mContext;
        private final IconHelper mIconHelper;
        private final Drawable mDefaultDragIcon;
        private Model mModel;

        public Factory(
                Context context, Model model, IconHelper iconHelper, Drawable defaultDragIcon) {
            mContext = context;
            mModel = model;
            mIconHelper = iconHelper;
            mDefaultDragIcon = defaultDragIcon;
        }

        @Override
        public DragShadowBuilder apply(Selection selection) {
            return new DragShadowBuilder(
                    mContext,
                    getDragTitle(selection),
                    getDragIcon(selection));
        }

        private Drawable getDragIcon(Selection selection) {
            if (selection.size() == 1) {
                DocumentInfo doc = getSingleSelectedDocument(selection);
                return mIconHelper.getDocumentIcon(mContext, doc);
            }
            return mDefaultDragIcon;
        }

        private String getDragTitle(Selection selection) {
            assert (!selection.isEmpty());
            if (selection.size() == 1) {
                DocumentInfo doc = getSingleSelectedDocument(selection);
                return doc.displayName;
            }
            return Shared.getQuantityString(mContext, R.plurals.elements_dragged, selection.size());
        }

        private DocumentInfo getSingleSelectedDocument(Selection selection) {
            assert (selection.size() == 1);
            final List<DocumentInfo> docs = mModel.getDocuments(selection);
            assert (docs.size() == 1);
            return docs.get(0);
        }
    }
}
