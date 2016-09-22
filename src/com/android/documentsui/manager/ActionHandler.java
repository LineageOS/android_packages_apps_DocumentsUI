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

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.GetRootDocumentTask;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.MultiSelectManager;

import javax.annotation.Nullable;

/**
 * Provides {@link ManageActivity} action specializations to fragments.
 */
public class ActionHandler extends com.android.documentsui.ActionHandler<ManageActivity> {

    private static final String TAG = "ManagerActionHandler";

    private final FragmentTuner mTuner;
    private final Config mConfig;

    ActionHandler(ManageActivity activity, FragmentTuner tuner) {
        super(activity);
        mTuner = tuner;
        mConfig = new Config();
    }

    @Override
    public void openSettings(RootInfo root) {
        mActivity.openRootSettings(root);
    }

    @Override
    public void openInNewWindow(RootInfo root) {
        new GetRootDocumentTask(
                root,
                mActivity,
                mActivity::isDestroyed,
                (DocumentInfo doc) -> openInNewWindow(root, doc)
        ).executeOnExecutor(ProviderExecutor.forAuthority(root.authority));
    }

    private void openInNewWindow(RootInfo root, DocumentInfo doc) {
        mActivity.openInNewWindow(new DocumentStack(root), doc);
    }

    @Override
    public void pasteIntoFolder(RootInfo root) {
        new GetRootDocumentTask(
                root,
                mActivity,
                mActivity::isDestroyed,
                (DocumentInfo doc) -> pasteIntoFolder(root, doc)
        ).executeOnExecutor(ProviderExecutor.forAuthority(root.authority));
    }

    private void pasteIntoFolder(RootInfo root, DocumentInfo doc) {
        DocumentClipper clipper = DocumentsApplication.getDocumentClipper(mActivity);
        DocumentStack stack = new DocumentStack(root, doc);
        clipper.copyFromClipboard(doc, stack, mActivity.fileOpCallback);
    }

    @Override
    public boolean openDocument(DocumentDetails details) {
        DocumentInfo doc = mConfig.model.getDocument(details.getModelId());
        if (doc == null) {
            Log.w(TAG,
                    "Can't view item. No Document available for modeId: " + details.getModelId());
            return false;
        }

        if (mTuner.isDocumentEnabled(doc.mimeType, doc.flags)) {
            mActivity.onDocumentPicked(doc, mConfig.model);
            mConfig.selectionMgr.clearSelection();
            return true;
        }
        return false;
    }

    @Override
    public boolean viewDocument(DocumentDetails details) {
        DocumentInfo doc = mConfig.model.getDocument(details.getModelId());
        return mActivity.viewDocument(doc);
    }

    @Override
    public boolean previewDocument(DocumentDetails details) {
        DocumentInfo doc = mConfig.model.getDocument(details.getModelId());
        if (doc.isContainer()) {
            return false;
        }
        return mActivity.previewDocument(doc, mConfig.model);
    }

    ActionHandler reset(Model model, MultiSelectManager selectionMgr) {
        mConfig.reset(model, selectionMgr);
        return this;
    }

    private static final class Config {

        @Nullable Model model;
        @Nullable MultiSelectManager selectionMgr;

        public void reset(Model model, MultiSelectManager selectionMgr) {
            assert(model != null);
            assert(selectionMgr != null);

            this.model = model;
            this.selectionMgr = selectionMgr;
        }
    }
}
