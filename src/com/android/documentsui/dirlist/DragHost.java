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

import android.app.Activity;
import android.content.ClipData;
import android.view.DragEvent;
import android.view.View;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.ActionHandler;
import com.android.documentsui.DragAndDropHelper;
import com.android.documentsui.DragShadowBuilder;
import com.android.documentsui.ItemDragListener;
import com.android.documentsui.Metrics;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.State;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.ui.DialogController;

import java.util.function.Predicate;

/**
 * Drag host for items in {@link DirectoryFragment}.
 */
class DragHost<T extends Activity & AbstractActionHandler.CommonAddons>
        implements ItemDragListener.DragHost {

    private final T mActivity;
    private final DragShadowBuilder mShadowBuilder;
    private final SelectionManager mSelectionMgr;
    private final ActionHandler mActions;
    private final State mState;
    private final DialogController mDialogs;
    private final Predicate<View> mIsDocumentView;
    private final Lookup<View, DocumentHolder> mHolderLookup;
    private final Lookup<View, DocumentInfo> mDestinationLookup;
    private final DocumentClipper mClipper;

    DragHost(
            T activity,
            DragShadowBuilder shadowBuilder,
            SelectionManager selectionMgr,
            ActionHandler actions,
            State state,
            DialogController dialogs,
            Predicate<View> isDocumentView,
            Lookup<View, DocumentHolder> holderLookup,
            Lookup<View, DocumentInfo> destinationLookup,
            DocumentClipper clipper) {
        mActivity = activity;
        mShadowBuilder = shadowBuilder;
        mSelectionMgr = selectionMgr;
        mActions = actions;
        mState = state;
        mDialogs = dialogs;
        mIsDocumentView = isDocumentView;
        mHolderLookup = holderLookup;
        mDestinationLookup = destinationLookup;
        mClipper = clipper;
    }

    void dragStopped(boolean result) {
        if (result) {
            mSelectionMgr.clearSelection();
        }
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        mActivity.runOnUiThread(runnable);
    }

    @Override
    public void setDropTargetHighlight(View v, Object localState, boolean highlight) {
        // Note: use exact comparison - this code is searching for views which are children of
        // the RecyclerView instance in the UI.
        if (mIsDocumentView.test(v)) {
            DocumentHolder holder = mHolderLookup.lookup(v);
            if (holder != null) {
                if (!highlight) {
                    holder.resetDropHighlight();
                } else {
                    holder.setDroppableHighlight(canCopyTo(localState, v));
                }
            }
        }
    }

    @Override
    public void onViewHovered(View v) {
        if (mIsDocumentView.test(v)) {
            mActions.springOpenDirectory(mDestinationLookup.lookup(v));
        }
        mActivity.setRootsDrawerOpen(false);
    }

    @Override
    public void onDragEntered(View v, Object localState) {
        mActivity.setRootsDrawerOpen(false);
        mShadowBuilder.setAppearDroppable(canCopyTo(localState, v));
        v.updateDragShadow(mShadowBuilder);
    }

    @Override
    public void onDragExited(View v, Object localState) {
        mShadowBuilder.resetBackground();
        v.updateDragShadow(mShadowBuilder);
        if (mIsDocumentView.test(v)) {
            DocumentHolder holder = mHolderLookup.lookup(v);
            if (holder != null) {
                holder.resetDropHighlight();
            }
        }
    }

    boolean handleDropEvent(View v, DragEvent event) {
        mActivity.setRootsDrawerOpen(false);

        ClipData clipData = event.getClipData();
        assert (clipData != null);

        assert(mClipper.getOpType(clipData) == FileOperationService.OPERATION_COPY);

        if (!canCopyTo(event.getLocalState(), v)) {
            return false;
        }

        // Recognize multi-window drag and drop based on the fact that localState is not
        // carried between processes. It will stop working when the localsState behavior
        // is changed. The info about window should be passed in the localState then.
        // The localState could also be null for copying from Recents in single window
        // mode, but Recents doesn't offer this functionality (no directories).
        Metrics.logUserAction(mActivity,
                event.getLocalState() == null ? Metrics.USER_ACTION_DRAG_N_DROP_MULTI_WINDOW
                        : Metrics.USER_ACTION_DRAG_N_DROP);

        DocumentInfo dst = mDestinationLookup.lookup(v);
        // If destination is already at top of stack, no need to pass it in
        if (dst.equals(mState.stack.peek())) {
            mClipper.copyFromClipData(
                    mState.stack,
                    clipData,
                    mDialogs::showFileOperationStatus);
        } else {
            mClipper.copyFromClipData(
                    dst,
                    mState.stack,
                    clipData,
                    mDialogs::showFileOperationStatus);
        }
        return true;
    }

    boolean canCopyTo(Object localState, View v) {
        return DragAndDropHelper.canCopyTo(localState, mDestinationLookup.lookup(v));
    }
}
