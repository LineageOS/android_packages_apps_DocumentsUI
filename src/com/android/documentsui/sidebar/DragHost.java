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

package com.android.documentsui.sidebar;

import android.app.Activity;
import android.util.Log;
import android.view.View;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.DragAndDropHelper;
import com.android.documentsui.DragShadowBuilder;
import com.android.documentsui.ItemDragListener;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Lookup;

/**
 * Drag host for items in {@link RootsFragment}.
 */
class DragHost implements ItemDragListener.DragHost {

    private static final String TAG = "RootsDragHost";
    private static final int DRAG_LOAD_TIME_OUT = 500;

    private final Activity mActivity;
    private final DragShadowBuilder mShadowBuilder;
    private final Lookup<View, Item> mDestinationLookup;
    private final ActionHandler mActions;

    DragHost(
            Activity activity,
            DragShadowBuilder shadowBuilder,
            Lookup<View, Item> destinationLookup,
            ActionHandler actions) {
        mActivity = activity;
        mShadowBuilder = shadowBuilder;
        mDestinationLookup = destinationLookup;
        mActions = actions;
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }

    @Override
    public void setDropTargetHighlight(View v, Object localState, boolean highlight) {
        // SpacerView doesn't have DragListener so this view is guaranteed to be a RootItemView.
        RootItemView itemView = (RootItemView) v;
        itemView.setHighlight(highlight);
    }

    @Override
    public void onViewHovered(View v) {
        // SpacerView doesn't have DragListener so this view is guaranteed to be a RootItemView.
        RootItemView itemView = (RootItemView) v;
        itemView.drawRipple();

        mDestinationLookup.lookup(v).open();
    }

    @Override
    public void onDragEntered(View v, Object localState) {
        final Item item = mDestinationLookup.lookup(v);

        // If a read-only root, no need to see if top level is writable (it's not)
        if (!item.isDropTarget()) {
            mShadowBuilder.setAppearDroppable(false);
            v.updateDragShadow(mShadowBuilder);
            return;
        }

        final RootItem rootItem = (RootItem) item;
        mActions.getRootDocument(
                rootItem.root,
                DRAG_LOAD_TIME_OUT,
                (DocumentInfo doc) -> {
                    updateDropShadow(v, localState, rootItem, doc);
                });
    }

    private void updateDropShadow(
            View v, Object localState, RootItem rootItem, DocumentInfo rootDoc) {
        if (rootDoc == null) {
            Log.e(TAG, "Root DocumentInfo is null. Defaulting to appear not droppable.");
            mShadowBuilder.setAppearDroppable(false);
        } else {
            rootItem.docInfo = rootDoc;
            mShadowBuilder.setAppearDroppable(rootDoc.isCreateSupported()
                    && DragAndDropHelper.canCopyTo(localState, rootDoc));
        }
        v.updateDragShadow(mShadowBuilder);
    }

    @Override
    public void onDragExited(View v, Object localState) {
        mShadowBuilder.resetBackground();
        v.updateDragShadow(mShadowBuilder);
    }
}
