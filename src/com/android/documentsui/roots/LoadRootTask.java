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

package com.android.documentsui.roots;

import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;

public final class LoadRootTask extends PairedTask<BaseActivity, Void, RootInfo> {
    private static final String TAG = "LoadRootTask";

    private final State mState;
    private final RootsCache mRoots;
    private final Uri mRootUri;


    public LoadRootTask(BaseActivity activity, RootsCache roots, State state, Uri rootUri) {
        super(activity);
        mState = state;
        mRoots = roots;
        mRootUri = rootUri;
    }

    @Override
    protected RootInfo run(Void... params) {
        String rootId = DocumentsContract.getRootId(mRootUri);
        return mRoots.getRootOneshot(mRootUri.getAuthority(), rootId);
    }

    @Override
    protected void finish(RootInfo root) {
        mState.restored = true;

        if (root != null) {
            mOwner.onRootPicked(root);
        } else {
            Log.w(TAG, "Failed to find root: " + mRootUri);
            mOwner.finish();
        }
    }
}
