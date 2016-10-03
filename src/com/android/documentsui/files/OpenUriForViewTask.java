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

import android.app.Activity;
import android.net.Uri;
import android.util.Log;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.roots.RootsAccess;

import java.util.Collection;

import javax.annotation.Nullable;

/**
 * Builds a stack for the specific Uris. Multi roots are not supported, as it's impossible
 * to know which root to select. Also, the stack doesn't contain intermediate directories.
 * It's primarly used for opening ZIP archives from Downloads app.
 */
public final class OpenUriForViewTask<T extends Activity & CommonAddons>
        extends PairedTask<T, Void, Void> {

    private static final String TAG = "OpenUriForViewTask";

    private final T mActivity;
    private final State mState;
    private final RootsAccess mRoots;
    private final DocumentsAccess mDocs;
    private final Uri mUri;

    public OpenUriForViewTask(
            T activity, State state, RootsAccess roots, DocumentsAccess docs, Uri uri) {
        super(activity);
        mActivity = activity;
        mState = state;
        mRoots = roots;
        mDocs = docs;
        mUri = uri;
    }

    @Override
    public Void run(Void... params) {

        final String authority = mUri.getAuthority();
        final Collection<RootInfo> roots = mRoots.getRootsForAuthorityBlocking(authority);

        if (roots.isEmpty()) {
            Log.e(TAG, "Failed to find root for the requested Uri: " + mUri);
            return null;
        }

        assert(mState.stack.isEmpty());

        // NOTE: There's no guarantee that this root will be the correct root for the doc.
        final RootInfo root = roots.iterator().next();
        mState.stack.root = root;
        mState.stack.add(mDocs.getRootDocument(root));
        @Nullable DocumentInfo doc = mDocs.getDocument(mUri);
        if (doc == null) {
            Log.e(TAG, "Failed to resolve DocumentInfo from Uri: " + mUri);
        } else {
            mState.stack.add(doc);
        }

        return null;
    }

    @Override
    public void finish(Void result) {
        mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
    }
}
