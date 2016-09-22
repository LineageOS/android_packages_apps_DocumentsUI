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

package com.android.documentsui.picker;

import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import android.provider.DocumentsContract.Document;

import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.MimePredicate;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.Model.Update;

import javax.annotation.Nullable;

/**
 * Provides support for Platform specific specializations of DirectoryFragment.
 */
final class Tuner extends FragmentTuner {

    private static final String TAG = "PickTuner";


    private final PickActivity mActivity;
    private final State mState;

    private final Config mConfig = new Config(this::onModelLoaded);

    public Tuner(PickActivity activity, State state) {

        assert(activity != null);
        assert(state != null);

        mActivity = activity;
        mState = state;
    }

    @Override
    public boolean canSelectType(String docMimeType, int docFlags) {
        if (!isDocumentEnabled(docMimeType, docFlags)) {
            return false;
        }

        if (MimePredicate.isDirectoryType(docMimeType)) {
            return false;
        }

        if (mState.action == ACTION_OPEN_TREE
                || mState.action == ACTION_PICK_COPY_DESTINATION) {
            // In this case nothing *ever* is selectable...the expected user behavior is
            // they navigate *into* a folder, then click a confirmation button indicating
            // that the current directory is the directory they are picking.
            return false;
        }

        return true;
    }

    @Override
    public boolean isDocumentEnabled(String mimeType, int docFlags) {
        // Directories are always enabled.
        if (MimePredicate.isDirectoryType(mimeType)) {
            return true;
        }

        switch (mState.action) {
            case ACTION_CREATE:
                // Read-only files are disabled when creating.
                if ((docFlags & Document.FLAG_SUPPORTS_WRITE) == 0) {
                    return false;
                }
            case ACTION_OPEN:
            case ACTION_GET_CONTENT:
                final boolean isVirtual = (docFlags & Document.FLAG_VIRTUAL_DOCUMENT) != 0;
                if (isVirtual && mState.openableOnly) {
                    return false;
                }
        }

        return MimePredicate.mimeMatches(mState.acceptMimes, mimeType);
    }

    private void onModelLoaded(Model.Update update) {
        mConfig.modelLoadObserved = true;
        boolean showDrawer = false;

        if (MimePredicate.mimeMatches(MimePredicate.VISUAL_MIMES, mState.acceptMimes)) {
            showDrawer = false;
        }
        if (mState.external && mState.action == ACTION_GET_CONTENT) {
            showDrawer = true;
        }
        if (mState.action == ACTION_PICK_COPY_DESTINATION) {
            showDrawer = true;
        }

        // When launched into empty root, open drawer.
        if (mConfig.model.isEmpty()) {
            showDrawer = true;
        }

        if (showDrawer && !mState.hasInitialLocationChanged() && !mConfig.searchMode
                && !mConfig.modelLoadObserved) {
            // This noops on layouts without drawer, so no need to guard.
            mActivity.setRootsDrawerOpen(true);
        }
    }

    Tuner reset(Model model, boolean searchMode) {
        mConfig.reset(model, searchMode);
        return this;
    }

    private static final class Config {

        @Nullable Model model;
        boolean searchMode;

        private final EventListener<Update> mModelUpdateListener;

        public Config(EventListener<Update> modelUpdateListener) {
            mModelUpdateListener = modelUpdateListener;
        }

        // We use this to keep track of whether a model has been previously loaded or not so we can
        // open the drawer on empty directories on first launch
        private boolean modelLoadObserved;

        public void reset(Model model, boolean searchMode) {
            assert(model != null);

            this.searchMode = searchMode;
            this.model = model;

            model.addUpdateListener(mModelUpdateListener);
            modelLoadObserved = false;
        }
    }
}
