/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * RecyclerView ItemDecorator that distributes the horizontal space into equal size buckets
 * for each item (to match the bounds given by the GridViewLayout) and adds an offset to
 * either side to centre the item within its bucket. This only works when the layout manager
 * is GridViewLayout and all items have the same fixed width.
 */
public class GridEvenSpacingDecoration extends RecyclerView.ItemDecoration {
    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
            RecyclerView.State state) {
        if (!isUseMaterial3FlagEnabled()) {
            return;
        }
        RecyclerView.LayoutManager layoutManager = parent.getLayoutManager();
        if (!(layoutManager instanceof GridLayoutManager)) {
            return;
        }
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        if (lp.width == ViewGroup.LayoutParams.MATCH_PARENT
                || lp.width == ViewGroup.LayoutParams.WRAP_CONTENT) {
            // This item does not have a fixed width.
            return;
        }
        // Distribute the horizontal space into equal size buckets for each item and add an
        // offset to either side to centre the item within its bucket.
        int spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
        int itemWidth = lp.getMarginStart() + lp.width + lp.getMarginEnd();
        int allocatedGridSpace =
                (parent.getMeasuredWidth() - parent.getPaddingLeft() - parent.getPaddingRight())
                        / spanCount;
        int extraSpace = allocatedGridSpace - itemWidth;
        int offset = extraSpace / 2;
        outRect.left = offset;
        outRect.right = offset;
    }
}
