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
package com.android.documentsui.dirlist;

import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;

import com.android.documentsui.base.EventDetailsLookup;

/**
 * Access to document details relating to {@link MotionEvent} instances.
 */
final class RuntimeEventDetailsLookup implements EventDetailsLookup {

    private final RecyclerView mRecView;

    public RuntimeEventDetailsLookup(RecyclerView view) {
        mRecView = view;
    }

    @Override
    public boolean overItem(MotionEvent e) {
        return getItemPosition(e) != RecyclerView.NO_POSITION;
    }

    @Override
    public boolean overModelItem(MotionEvent e) {
        return overItem(e) && getDocumentDetails(e).hasModelId();
    }

    @Override
    public boolean inItemDragRegion(MotionEvent e) {
        return overItem(e) && getDocumentDetails(e).inDragRegion(e);
    }

    @Override
    public boolean inItemSelectRegion(MotionEvent e) {
        return overItem(e) && getDocumentDetails(e).inSelectRegion(e);
    }

    @Override
    public int getItemPosition(MotionEvent e) {
        View child = mRecView.findChildViewUnder(e.getX(), e.getY());
        return (child != null)
                ? mRecView.getChildAdapterPosition(child)
                : RecyclerView.NO_POSITION;
    }

    @Override
    public DocumentDetails getDocumentDetails(MotionEvent e) {
        View childView = mRecView.findChildViewUnder(e.getX(), e.getY());
        return (childView != null)
            ? (DocumentHolder) mRecView.getChildViewHolder(childView)
            : null;
    }
}
