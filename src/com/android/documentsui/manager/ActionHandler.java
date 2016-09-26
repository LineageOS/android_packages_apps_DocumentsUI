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

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.GetRootDocumentTask;
import com.android.documentsui.Metrics;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.base.ConfirmationCallback;
import com.android.documentsui.base.ConfirmationCallback.Result;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.clipping.ClipStore;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.MultiSelectManager;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.manager.ActionHandler.Addons;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.ui.DialogController;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Provides {@link ManageActivity} action specializations to fragments.
 */
public class ActionHandler<T extends Activity & Addons> extends AbstractActionHandler<T> {

    private static final String TAG = "ManagerActionHandler";

    private final DialogController mDialogs;
    private final State mState;
    private final FragmentTuner mTuner;
    private final DocumentClipper mClipper;
    private final ClipStore mClipStore;

    private final Config mConfig;

    ActionHandler(
            T activity,
            DialogController dialogs,
            State state,
            FragmentTuner tuner,
            DocumentClipper clipper,
            ClipStore clipStore) {
        super(activity);

        mDialogs = dialogs;
        mState = state;
        mTuner = tuner;
        mClipper = clipper;
        mClipStore = clipStore;

        mConfig = new Config();
    }

    @Override
    public boolean dropOn(ClipData data, RootInfo root) {
        new GetRootDocumentTask(
                root,
                mActivity,
                mActivity::isDestroyed,
                (DocumentInfo doc) -> mClipper.copyFromClipData(
                        root, doc, data, mDialogs::showFileOperationFailures)
        ).executeOnExecutor(ProviderExecutor.forAuthority(root.authority));
        return true;
    }

    @Override
    public void openSettings(RootInfo root) {
        Metrics.logUserAction(mActivity, Metrics.USER_ACTION_SETTINGS);
        final Intent intent = new Intent(DocumentsContract.ACTION_DOCUMENT_ROOT_SETTINGS);
        intent.setDataAndType(root.getUri(), DocumentsContract.Root.MIME_TYPE_ITEM);
        mActivity.startActivity(intent);
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
        clipper.copyFromClipboard(doc, stack, mDialogs::showFileOperationFailures);
    }

    @Override
    public void openRoot(RootInfo root) {
        Metrics.logRootVisited(mActivity, root);
        mActivity.onRootPicked(root);
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

    @Override
    public void deleteDocuments(Model model, Selection selected, ConfirmationCallback callback) {
        Metrics.logUserAction(mActivity, Metrics.USER_ACTION_DELETE);

        assert(!selected.isEmpty());

        final DocumentInfo srcParent = mState.stack.peek();

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = model.getDocuments(selected);

        ConfirmationCallback result = (@Result int code) -> {
            // share the news with our caller, be it good or bad.
            callback.accept(code);

            if (code != ConfirmationCallback.CONFIRM) {
                return;
            }

            UrisSupplier srcs;
            try {
                srcs = UrisSupplier.create(
                        selected,
                        model::getItemUri,
                        mClipStore);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create uri supplier.", e);
            }

            FileOperation operation = new FileOperation.Builder()
                    .withOpType(FileOperationService.OPERATION_DELETE)
                    .withDestination(mState.stack)
                    .withSrcs(srcs)
                    .withSrcParent(srcParent.derivedUri)
                    .build();

            FileOperations.start(mActivity, operation, mDialogs::showFileOperationFailures);
        };

        mDialogs.confirmDelete(docs, result);
    }

    ActionHandler<T> reset(Model model, MultiSelectManager selectionMgr) {
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

    public interface Addons extends CommonAddons {
        boolean viewDocument(DocumentInfo doc);
        boolean previewDocument(DocumentInfo doc, Model model);
    }
}
