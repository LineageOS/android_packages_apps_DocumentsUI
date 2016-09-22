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

import android.content.ClipData;
import android.content.pm.ResolveInfo;

import com.android.documentsui.base.CheckedTask.Check;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.sidebar.EjectRootTask;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Provides support for specializing the actions (viewDocument etc.) to the host activity.
 */
public abstract class ActionHandler<T extends BaseActivity> {

    protected T mActivity;

    public ActionHandler(T activity) {
        mActivity = activity;
    }

    public void openSettings(RootInfo root) {
        throw new UnsupportedOperationException("Can't open settings.");
    }

    /**
     * Drops documents on a root.
     * @param check The check to make sure RootsFragment is not detached from activity.
     */
    public boolean dropOn(ClipData data, RootInfo root) {
        new GetRootDocumentTask(
                root,
                mActivity,
                mActivity::isDestroyed,
                (DocumentInfo doc) -> dropOn(data, root, doc)
        ).executeOnExecutor(ProviderExecutor.forAuthority(root.authority));
        return true;
    }

    private void dropOn(ClipData data, RootInfo root, DocumentInfo doc) {
        DocumentClipper clipper
                = DocumentsApplication.getDocumentClipper(mActivity);
        clipper.copyFromClipData(root, doc, data, mActivity.fileOpCallback);
    }

    public void ejectRoot(
            RootInfo root, BooleanSupplier ejectCanceledCheck, Consumer<Boolean> listener) {
        assert(ejectCanceledCheck != null);
        ejectRoot(
                root.authority,
                root.rootId,
                ejectCanceledCheck,
                listener);
    }

    private void ejectRoot(
            String authority,
            String rootId,
            BooleanSupplier ejectCanceledCheck,
            Consumer<Boolean> listener) {
        new EjectRootTask(
                mActivity,
                authority,
                rootId,
                ejectCanceledCheck,
                listener).executeOnExecutor(ProviderExecutor.forAuthority(authority));
    }

    public void showAppDetails(ResolveInfo info) {
        throw new UnsupportedOperationException("Can't show app details.");
    }

    public void openRoot(RootInfo root) {
        Metrics.logRootVisited(mActivity, root);
        mActivity.onRootPicked(root);
    }

    public void openRoot(ResolveInfo app) {
        throw new UnsupportedOperationException("Can't open an app.");
    }

    public void openInNewWindow(RootInfo root) {
        throw new UnsupportedOperationException("Can't open in new window");
    }

    public void pasteIntoFolder(RootInfo root) {
        throw new UnsupportedOperationException("Can't paste into folder.");
    }

    public boolean viewDocument(DocumentDetails doc) {
        throw new UnsupportedOperationException("Direct view not supported!");
    }

    public boolean previewDocument(DocumentDetails doc) {
        throw new UnsupportedOperationException("Preview not supported!");
    }

    public abstract boolean openDocument(DocumentDetails doc);
}
