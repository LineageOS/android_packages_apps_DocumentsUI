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
package com.android.documentsui;

import android.net.Uri;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.dirlist.AnimationView;

import java.util.Collection;

final class OnRootsChangedTask
        extends PairedTask<BaseActivity, RootInfo, RootInfo> {
    RootInfo mCurrentRoot;
    DocumentInfo mDefaultRootDocument;

    public OnRootsChangedTask(BaseActivity activity) {
        super(activity);
    }

    @Override
    protected RootInfo run(RootInfo... roots) {
        assert(roots.length == 1);
        mCurrentRoot = roots[0];
        final Collection<RootInfo> cachedRoots = mOwner.mRoots.getRootsBlocking();
        for (final RootInfo root : cachedRoots) {
            if (root.getUri().equals(mCurrentRoot.getUri())) {
                // We don't need to change the current root as the current root was not removed.
                return null;
            }
        }

        // Choose the default root.
        final RootInfo defaultRoot = mOwner.mRoots.getDefaultRootBlocking(mOwner.mState);
        assert(defaultRoot != null);
        if (!defaultRoot.isRecents()) {
            mDefaultRootDocument = defaultRoot.getRootDocumentBlocking(mOwner);
        }
        return defaultRoot;
    }

    @Override
    protected void finish(RootInfo defaultRoot) {
        if (defaultRoot == null) {
            return;
        }

        // If the activity has been launched for the specific root and it is removed, finish the
        // activity.
        final Uri uri = mOwner.getIntent().getData();
        if (uri != null && uri.equals(mCurrentRoot.getUri())) {
            mOwner.finish();
            return;
        }

        // Clear entire backstack and start in new root.
        mOwner.mState.onRootChanged(defaultRoot);
        mOwner.mSearchManager.update(defaultRoot);

        if (defaultRoot.isRecents()) {
            mOwner.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        } else {
            mOwner.openContainerDocument(mDefaultRootDocument);
        }
    }
}