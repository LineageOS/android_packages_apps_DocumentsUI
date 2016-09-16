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

package com.android.documentsui.manager;

import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.Model.Update;
import com.android.documentsui.dirlist.MultiSelectManager;

import javax.annotation.Nullable;

/**
 * Provides support for Files activity specific specializations of DirectoryFragment.
 */
public final class Tuner extends FragmentTuner {

    private static final String TAG = "ManageTuner";

    private final ManageActivity mActivity;
    private final State mState;

    private FragState mFragState = new FragState(this::onModelLoaded);

    public Tuner(ManageActivity activity, State state) {

        assert(activity != null);
        assert(state != null);

        mActivity = activity;
        mState = state;
    }

    private void onModelLoaded(Model.Update update) {
        mFragState.modelLoadObserved = true;

        // When launched into empty root, open drawer.
        if (mFragState.model.isEmpty()
                && !mState.hasInitialLocationChanged()
                && !mFragState.searchMode
                && !mFragState.modelLoadObserved) {
            // Opens the drawer *if* an openable drawer is present
            // else this is a no-op.
            mActivity.setRootsDrawerOpen(true);
        }
    }

    @Override
    public boolean managedModeEnabled() {
        // When in downloads top level directory, we also show active downloads.
        // And while we don't allow folders in Downloads, we do allow Zip files in
        // downloads that themselves can be opened and viewed like directories.
        // This method helps us understand when to kick in on those special behaviors.
        return mState.stack.root != null
                && mState.stack.root.isDownloads()
                && mState.stack.size() == 1;
    }

    @Override
    public boolean dragAndDropEnabled() {
        return true;
    }

    @Override
    public void showChooserForDoc(DocumentInfo doc) {
        mActivity.showChooserForDoc(doc);
    }

    @Override
    public void openInNewWindow(DocumentStack stack, DocumentInfo doc) {
        mActivity.openInNewWindow(stack, doc);
    }

    @Override
    protected boolean onDocumentPicked(String id) {
        DocumentInfo doc = mFragState.model.getDocument(id);
        if (doc == null) {
            Log.w(TAG, "Can't view item. No Document available for modeId: " + id);
            return false;
        }

        if (isDocumentEnabled(doc.mimeType, doc.flags)) {
            mActivity.onDocumentPicked(doc, mFragState.model);
            mFragState.selectionMgr.clearSelection();
            return true;
        }
        return false;
    }

    Tuner reset(Model model, MultiSelectManager selectionMgr, boolean searchMode) {
        mFragState.reset(model, selectionMgr, searchMode);
        return this;
    }

    private static final class FragState {

        @Nullable Model model;
        @Nullable MultiSelectManager selectionMgr;
        boolean searchMode;

        private final EventListener<Update> mModelUpdateListener;

        public FragState(EventListener<Update> modelUpdateListener) {
            mModelUpdateListener = modelUpdateListener;
        }

        // We use this to keep track of whether a model has been previously loaded or not so we can
        // open the drawer on empty directories on first launch
        private boolean modelLoadObserved;

        public void reset(Model model, MultiSelectManager selectionMgr, boolean searchMode) {
            this.searchMode = searchMode;
            assert(model != null);
            assert(selectionMgr != null);

            this.model = model;
            this.selectionMgr = selectionMgr;

            model.addUpdateListener(mModelUpdateListener);
            modelLoadObserved = false;
        }
    }
}
