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

package com.android.documentsui.files;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.Model.Update;

import javax.annotation.Nullable;

/**
 * Provides support for Files activity specific specializations of DirectoryFragment.
 */
public final class Tuner extends FragmentTuner {

    private final FilesActivity mActivity;
    private final State mState;

    private Config mConfig = new Config(this::onModelLoaded);

    public Tuner(FilesActivity activity, State state) {

        assert(activity != null);
        assert(state != null);

        mActivity = activity;
        mState = state;
    }

    private void onModelLoaded(Model.Update update) {
        mConfig.modelLoadObserved = true;

        // When launched into empty root, open drawer.
        if (mConfig.model.isEmpty()
                && !mState.hasInitialLocationChanged()
                && !mConfig.searchMode
                && !mConfig.modelLoadObserved) {
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

    // TODO: Move to action handler.
    @Override
    public void showChooserForDoc(DocumentInfo doc) {
        mActivity.showChooserForDoc(doc);
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
            this.searchMode = searchMode;
            assert(model != null);

            this.model = model;

            model.addUpdateListener(mModelUpdateListener);
            modelLoadObserved = false;
        }
    }
}
