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

import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.View;

import com.android.documentsui.Events.InputEvent;
import com.android.documentsui.R;
import com.android.documentsui.Shared;
import com.android.documentsui.State;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.model.DocumentInfo;
import com.android.documentsui.services.FileOperationService;

import java.util.List;
import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * Listens for potential "drag-like" events and kick-start dragging as needed. Also allows external
 * direct call to {@code #startDrag(RecyclerView, View)} if explicit start is needed, such as long-
 * pressing on an item via touch. (e.g. {@link UserInputHandler#onLongPress(InputEvent)} via touch.)
 */
class DragStartListener {

    private static String TAG = "DragStartListener";

    private final IconHelper mIconHelper;
    private final Context mContext;
    private final Model mModel;
    private final MultiSelectManager mSelectionMgr;
    private final State mState;
    private final Drawable mDefaultDragIcon;
    private final DocumentClipper mClipper;
    private final Function<View, String> mIdFinder;
    private final ViewFinder mViewFinder;

    public DragStartListener(
            IconHelper iconHelper,
            Context context,
            Model model,
            MultiSelectManager selectionMgr,
            DocumentClipper clipper,
            State state,
            Function<View, String> idFinder,
            ViewFinder viewFinder,
            Drawable defaultDragIcon) {
        mIconHelper = iconHelper;
        mContext = context;
        mModel = model;
        mSelectionMgr = selectionMgr;
        mClipper = clipper;
        mState = state;
        mIdFinder = idFinder;
        mViewFinder = viewFinder;
        mDefaultDragIcon = defaultDragIcon;
    }

    boolean onInterceptTouchEvent(InputEvent event) {
        if (isDragEvent(event)) {
            View child = mViewFinder.findView(event.getX(), event.getY());
            startDrag(child);
            return true;
        }
        return false;
    }

    boolean startDrag(View v) {

        if (v == null) {
            Log.d(TAG, "Ignoring drag event, null view");
            return false;
        }

        final Selection selection = new Selection();
        String modelId = mIdFinder.apply(v);
        if (modelId != null && !mSelectionMgr.getSelection().contains(modelId)) {
            selection.add(modelId);
        } else {
            mSelectionMgr.getSelection(selection);
        }

        DocumentInfo currentDir = mState.stack.peek();
        ClipData clipData = mClipper.getClipDataForDocuments(
                mModel::getItemUri,
                selection,
                FileOperationService.OPERATION_COPY);
        // NOTE: Preparation of the ClipData object can require a lot of time
        // and ideally should be done in the background. Unfortunately
        // the current code layout and framework assumptions don't support
        // this. So for now, we could end up doing a bunch of i/o on main thread.
        v.startDragAndDrop(
                clipData,
                new DragShadowBuilder(
                        mContext,
                        getDragTitle(selection),
                        getDragIcon(selection)),
                currentDir,
                View.DRAG_FLAG_GLOBAL
                        | View.DRAG_FLAG_GLOBAL_URI_READ
                        | View.DRAG_FLAG_GLOBAL_URI_WRITE);

        return true;
    }

    public boolean isDragEvent(InputEvent e) {
        return e.isOverItem() && e.isMouseEvent() && e.isActionMove() && e.isPrimaryButtonPressed();
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

    @FunctionalInterface
    interface ViewFinder {
        @Nullable View findView(float x, float y);
    }
}
