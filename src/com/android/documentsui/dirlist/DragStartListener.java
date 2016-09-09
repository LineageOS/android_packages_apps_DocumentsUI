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

import static com.android.documentsui.Shared.DEBUG;

import android.content.ClipData;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.VisibleForTesting;
import android.util.Log;
import android.view.View;

import com.android.documentsui.Events;
import com.android.documentsui.Events.InputEvent;
import com.android.documentsui.State;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.model.DocumentInfo;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;

import java.util.function.Function;

import javax.annotation.Nullable;

/**
 * Listens for potential "drag-like" events and kick-start dragging as needed. Also allows external
 * direct call to {@code #startDrag(RecyclerView, View)} if explicit start is needed, such as long-
 * pressing on an item via touch. (e.g. {@link UserInputHandler#onLongPress(InputEvent)} via touch.)
 */
interface DragStartListener {

    public static final DragStartListener DUMMY = new DragStartListener() {
        @Override
        public boolean onMouseDragEvent(InputEvent event) {
            return false;
        }
        @Override
        public boolean onTouchDragEvent(InputEvent event) {
            return false;
        }
    };

    boolean onMouseDragEvent(InputEvent event);
    boolean onTouchDragEvent(InputEvent event);

    @VisibleForTesting
    static class ActiveListener implements DragStartListener {

        private static String TAG = "DragStartListener";

        private final State mState;
        private final MultiSelectManager mSelectionMgr;
        private final ViewFinder mViewFinder;
        private final Function<View, String> mIdFinder;
        private final ClipDataFactory mClipFactory;
        private final Function<Selection, DragShadowBuilder> mShadowFactory;

        // use DragStartListener.create
        @VisibleForTesting
        public ActiveListener(
                State state,
                MultiSelectManager selectionMgr,
                ViewFinder viewFinder,
                Function<View, String> idFinder,
                ClipDataFactory clipFactory,
                Function<Selection, DragShadowBuilder> shadowFactory) {

            mState = state;
            mSelectionMgr = selectionMgr;
            mViewFinder = viewFinder;
            mIdFinder = idFinder;
            mClipFactory = clipFactory;
            mShadowFactory = shadowFactory;
        }

        @Override
        public final boolean onMouseDragEvent(InputEvent event) {
            assert(Events.isMouseDragEvent(event));
            return startDrag(mViewFinder.findView(event.getX(), event.getY()));
        }

        @Override
        public final boolean onTouchDragEvent(InputEvent event) {
            return startDrag(mViewFinder.findView(event.getX(), event.getY()));
        }

        /**
         * May be called externally when drag is initiated from other event handling code.
         */
        private final boolean startDrag(@Nullable View view) {

            if (view == null) {
                if (DEBUG) Log.d(TAG, "Ignoring drag event, null view.");
                return false;
            }

            @Nullable String modelId = mIdFinder.apply(view);
            if (modelId == null) {
                if (DEBUG) Log.d(TAG, "Ignoring drag on view not represented in model.");
                return false;
            }


            Selection selection = new Selection();

            // User can drag an unselected item. Ideally if CTRL key was pressed
            // we'd extend the selection, if not, the selection would be cleared.
            // Buuuuuut, there's an impedance mismatch between event-handling policies,
            // and drag and drop. So we only initiate drag of a single item when
            // drag starts on an item that is unselected. This behavior
            // would look like a bug, if it were not for the implicitly coupled
            // behavior where we clear the selection in the UI (finish action mode)
            // in DirectoryFragment#onDragStart.
            if (!mSelectionMgr.getSelection().contains(modelId)) {
                selection.add(modelId);
            } else {
                mSelectionMgr.getSelection(selection);
            }

            // NOTE: Preparation of the ClipData object can require a lot of time
            // and ideally should be done in the background. Unfortunately
            // the current code layout and framework assumptions don't support
            // this. So for now, we could end up doing a bunch of i/o on main thread.
            startDragAndDrop(
                    view,
                    mClipFactory.create(
                            selection,
                            FileOperationService.OPERATION_COPY),
                    mShadowFactory.apply(selection),
                    mState.stack.peek(),
                    View.DRAG_FLAG_GLOBAL
                            | View.DRAG_FLAG_GLOBAL_URI_READ
                            | View.DRAG_FLAG_GLOBAL_URI_WRITE);

            return true;
        }

        /**
         * This exists as a testing workaround since {@link View#startDragAndDrop} is final.
         */
        @VisibleForTesting
        void startDragAndDrop(
                View view,
                ClipData data,
                DragShadowBuilder shadowBuilder,
                DocumentInfo currentDirectory,
                int flags) {

            view.startDragAndDrop(data, shadowBuilder, currentDirectory, flags);
        }
    }

    public static DragStartListener create(
            IconHelper iconHelper,
            Context context,
            Model model,
            MultiSelectManager selectionMgr,
            DocumentClipper clipper,
            State state,
            Function<View, String> idFinder,
            ViewFinder viewFinder,
            Drawable defaultDragIcon) {

        DragShadowBuilder.Factory shadowFactory =
                new DragShadowBuilder.Factory(context, model, iconHelper, defaultDragIcon);

        return new ActiveListener(
                state,
                selectionMgr,
                viewFinder,
                idFinder,
                (Selection selection, @OpType int operationType) -> {
                    return clipper.getClipDataForDocuments(
                            model::getItemUri,
                            selection,
                            FileOperationService.OPERATION_COPY);
                },
                shadowFactory);
    }

    @FunctionalInterface
    interface ViewFinder {
        @Nullable View findView(float x, float y);
    }

    @FunctionalInterface
    interface ClipDataFactory {
        ClipData create(Selection selection, @OpType int operationType);
    }
}
