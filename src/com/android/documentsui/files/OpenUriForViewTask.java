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
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.roots.RootsAccess;

import java.io.FileNotFoundException;
import java.util.Collection;

/**
 * Builds a stack for the specific Uris. Multi roots are not supported, as it's impossible
 * to know which root to select. Also, the stack doesn't contain intermediate directories.
 * It's primarly used for opening ZIP archives from Downloads app.
 */
final class OpenUriForViewTask<T extends Activity & CommonAddons>
        extends PairedTask<T, Uri, Void> {

    private final State mState;
    public OpenUriForViewTask(T activity, State state) {
        super(activity);
        mState = state;
    }

    @Override
    public Void run(Uri... params) {
        final Uri uri = params[0];

        final RootsAccess rootsCache = DocumentsApplication.getRootsCache(mOwner);
        final String authority = uri.getAuthority();

        final Collection<RootInfo> roots =
                rootsCache.getRootsForAuthorityBlocking(authority);
        if (roots.isEmpty()) {
            Log.e(FilesActivity.TAG, "Failed to find root for the requested Uri: " + uri);
            return null;
        }

        final RootInfo root = roots.iterator().next();
        mState.stack.root = root;
        mState.stack.add(root.getRootDocumentBlocking(mOwner));
        try {
            mState.stack.add(DocumentInfo.fromUri(mOwner.getContentResolver(), uri));
        } catch (FileNotFoundException e) {
            Log.e(FilesActivity.TAG, "Failed to resolve DocumentInfo from Uri: " + uri);
        }

        return null;
    }

    @Override
    public void finish(Void result) {
        mOwner.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
    }
}