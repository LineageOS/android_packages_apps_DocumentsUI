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

import android.app.Activity;
import android.content.ContentResolver;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.roots.RootsAccess;

import java.util.Collection;

/**
 * Monitors roots change and refresh the page when necessary.
 */
final class RootsMonitor<T extends Activity & CommonAddons> {

    private final ContentResolver mResolver;
    private final ContentObserver mObserver;

    RootsMonitor(
            final T activity,
            final ActionHandler actions,
            final RootsAccess roots,
            final DocumentsAccess docs,
            final State state,
            final SearchViewManager searchMgr) {
        mResolver = activity.getContentResolver();

        mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                new HandleRootsChangedTask<T>(
                        activity,
                        actions,
                        roots,
                        docs,
                        state,
                        searchMgr).execute(activity.getCurrentRoot());
            }
        };
    }

    void start() {
        mResolver.registerContentObserver(RootsAccess.NOTIFICATION_URI, false, mObserver);
    }

    void stop() {
        mResolver.unregisterContentObserver(mObserver);
    }

    private static class HandleRootsChangedTask<T extends Activity & CommonAddons>
            extends PairedTask<T, RootInfo, RootInfo> {
        private final ActionHandler mActions;
        private final RootsAccess mRoots;
        private final DocumentsAccess mDocs;
        private final State mState;
        private final SearchViewManager mSearchMgr;

        private RootInfo mCurrentRoot;
        private DocumentInfo mDefaultRootDocument;

        private HandleRootsChangedTask(
                T activity,
                ActionHandler actions,
                RootsAccess roots,
                DocumentsAccess docs,
                State state,
                SearchViewManager searchMgr) {
            super(activity);
            mActions = actions;
            mRoots = roots;
            mDocs = docs;
            mState = state;
            mSearchMgr = searchMgr;
        }

        @Override
        protected RootInfo run(RootInfo... roots) {
            assert (roots.length == 1);
            mCurrentRoot = roots[0];
            final Collection<RootInfo> cachedRoots = mRoots.getRootsBlocking();
            for (final RootInfo root : cachedRoots) {
                if (root.getUri().equals(mCurrentRoot.getUri())) {
                    // We don't need to change the current root as the current root was not removed.
                    return null;
                }
            }

            // Choose the default root.
            final RootInfo defaultRoot = mRoots.getDefaultRootBlocking(mState);
            assert (defaultRoot != null);
            if (!defaultRoot.isRecents()) {
                mDefaultRootDocument = mDocs.getRootDocument(defaultRoot);
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
            mState.onRootChanged(defaultRoot);
            mSearchMgr.update(defaultRoot);

            if (defaultRoot.isRecents()) {
                mOwner.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
            } else {
                mActions.openContainerDocument(mDefaultRootDocument);
            }
        }
    }
}
