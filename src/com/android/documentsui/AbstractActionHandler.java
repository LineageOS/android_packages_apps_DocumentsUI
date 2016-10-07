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
import android.net.Uri;
import android.os.Parcelable;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.ConfirmationCallback;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.files.LauncherActivity;
import com.android.documentsui.files.OpenUriForViewTask;
import com.android.documentsui.roots.LoadRootTask;
import com.android.documentsui.roots.RootsAccess;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.sidebar.EjectRootTask;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Provides support for specializing the actions (viewDocument etc.) to the host activity.
 */
public abstract class AbstractActionHandler<T extends Activity & CommonAddons>
        implements ActionHandler {

    protected final T mActivity;
    protected final State mState;
    protected final RootsAccess mRoots;
    protected final DocumentsAccess mDocs;
    protected final SelectionManager mSelectionMgr;
    protected final Lookup<String, Executor> mExecutors;

    public AbstractActionHandler(
            T activity,
            State state,
            RootsAccess roots,
            DocumentsAccess docs,
            SelectionManager selectionMgr,
            Lookup<String, Executor> executors) {

        assert(activity != null);
        assert(state != null);
        assert(roots != null);
        assert(selectionMgr != null);
        assert(docs != null);

        mActivity = activity;
        mState = state;
        mRoots = roots;
        mDocs = docs;
        mSelectionMgr = selectionMgr;
        mExecutors = executors;
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
    public void openSelectedInNewWindow() {
        throw new UnsupportedOperationException("Can't open in new window.");
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
    public void openSettings(RootInfo root) {
        throw new UnsupportedOperationException("Can't open settings.");
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

    @Override
    public void showChooserForDoc(DocumentInfo doc) {
        throw new UnsupportedOperationException("Show chooser for doc not supported!");
    }

    @Override
    public void deleteSelectedDocuments(Model model, ConfirmationCallback callback) {
        throw new UnsupportedOperationException("Delete not supported!");
    }

    @Override
    public final void loadDocument(Uri uri) {
        new OpenUriForViewTask<>(mActivity, mState, mRoots, mDocs, uri)
                .executeOnExecutor(mExecutors.lookup(uri.getAuthority()));
    }

    @Override
    public final void loadRoot(Uri uri) {
        new LoadRootTask<>(mActivity, mRoots, mState, uri)
                .executeOnExecutor(mExecutors.lookup(uri.getAuthority()));
    }

    protected final void loadHomeDir() {
        loadRoot(Shared.getDefaultRootUri(mActivity));
    }

    protected Selection getStableSelection() {
        return mSelectionMgr.getSelection(new Selection());
    }
    /**
     * A class primarily for the support of isolating our tests
     * from our concrete activity implementations.
     */
    public interface CommonAddons {
        void refreshCurrentRootAndDirectory(@AnimationType int anim);
        void onRootPicked(RootInfo root);
        // TODO: Move this to PickAddons as multi-document picking is exclusive to that activity.
        void onDocumentsPicked(List<DocumentInfo> docs);
        void onDocumentPicked(DocumentInfo doc);
        void openContainerDocument(DocumentInfo doc);
        RootInfo getCurrentRoot();
        DocumentInfo getCurrentDirectory();
        void setRootsDrawerOpen(boolean open);
    }
}
