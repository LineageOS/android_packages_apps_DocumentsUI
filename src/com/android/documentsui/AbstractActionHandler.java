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
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Parcelable;

import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.manager.LauncherActivity;
import com.android.documentsui.sidebar.EjectRootTask;

/**
 * Provides support for specializing the actions (viewDocument etc.) to the host activity.
 */
public abstract class AbstractActionHandler<T extends Activity> implements ActionHandler {

    protected final T mActivity;

    public AbstractActionHandler(T activity) {
        mActivity = activity;
    }

    @Override
    public void openSettings(RootInfo root) {
        throw new UnsupportedOperationException("Can't open settings.");
    }

    @Override
    public void ejectRoot(RootInfo root, BooleanConsumer listener) {
        new EjectRootTask(
                mActivity.getContentResolver(),
                root.authority,
                root.rootId,
                listener).executeOnExecutor(ProviderExecutor.forAuthority(root.authority));
    }

    @Override
    public void openRoot(ResolveInfo app) {
        throw new UnsupportedOperationException("Can't open an app.");
    }

    @Override
    public void showAppDetails(ResolveInfo info) {
        throw new UnsupportedOperationException("Can't show app details.");
    }

    @Override
    public boolean dropOn(ClipData data, RootInfo root) {
        throw new UnsupportedOperationException("Can't open an app.");
    }

    @Override
    public void openInNewWindow(DocumentStack path) {
        Metrics.logUserAction(mActivity, Metrics.USER_ACTION_NEW_WINDOW);

        Intent intent = LauncherActivity.createLaunchIntent(mActivity);
        intent.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        // Multi-window necessitates we pick how we are launched.
        // By default we'd be launched in-place above the existing app.
        // By setting launch-to-side ActivityManager will open us to side.
        if (mActivity.isInMultiWindowMode()) {
            intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        }

        mActivity.startActivity(intent);
    }

    @Override
    public void pasteIntoFolder(RootInfo root) {
        throw new UnsupportedOperationException("Can't paste into folder.");
    }

    @Override
    public boolean viewDocument(DocumentDetails doc) {
        throw new UnsupportedOperationException("Direct view not supported!");
    }

    @Override
    public boolean previewDocument(DocumentDetails doc) {
        throw new UnsupportedOperationException("Preview not supported!");
    }
}
