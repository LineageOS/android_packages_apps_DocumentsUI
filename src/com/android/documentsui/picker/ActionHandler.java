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

package com.android.documentsui.picker;

import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.Metrics;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.MultiSelectManager;
import com.android.documentsui.picker.ActionHandler.Addons;
import com.android.documentsui.roots.RootsAccess;

import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * Provides {@link PickActivity} action specializations to fragments.
 */
class ActionHandler<T extends Activity & Addons> extends AbstractActionHandler<T> {

    private static final String TAG = "PickerActionHandler";

    private final FragmentTuner mTuner;
    private final Config mConfig;

    ActionHandler(
            T activity,
            State state,
            RootsAccess roots,
            DocumentsAccess docs,
            Lookup<String, Executor> executors,
            FragmentTuner tuner) {

        super(activity, state, roots, docs, executors);

        mTuner = tuner;
        mConfig = new Config();
    }

    @Override
    public void initLocation(Intent intent) {
        if (mState.restored) {
            if (DEBUG) Log.d(TAG, "Stack already resolved");
        } else {
            // We set the activity title in AsyncTask.onPostExecute().
            // To prevent talkback from reading aloud the default title, we clear it here.
            mActivity.setTitle("");

            // As a matter of policy we don't load the last used stack for the copy
            // destination picker (user is already in Files app).
            // Concensus was that the experice was too confusing.
            // In all other cases, where the user is visiting us from another app
            // we restore the stack as last used from that app.
            if (mState.action == ACTION_PICK_COPY_DESTINATION) {
                if (DEBUG) Log.d(TAG, "Launching directly into Home directory.");
                loadHomeDir();
            } else {
                if (DEBUG) Log.d(TAG, "Attempting to load last used stack for calling package.");
                new LoadLastAccessedStackTask<>(mActivity, mState, mRoots).execute();
            }
        }
    }

    @Override
    public void showAppDetails(ResolveInfo info) {
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", info.activityInfo.packageName, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        mActivity.startActivity(intent);
    }

    @Override
    public void openInNewWindow(DocumentStack path) {
        // Open new window support only depends on vanilla Activity, so it is
        // implemented in our parent class. But we don't support that in
        // picking. So as a matter of defensiveness, we override that here.
        throw new UnsupportedOperationException("Can't open in new window");
    }

    @Override
    public void openRoot(RootInfo root) {
        Metrics.logRootVisited(mActivity, root);
        mActivity.onRootPicked(root);
    }

    @Override
    public void openRoot(ResolveInfo info) {
        Metrics.logAppVisited(mActivity, info);
        mActivity.onAppPicked(info);
    }

    @Override
    public boolean viewDocument(DocumentDetails details) {
        return openDocument(details);
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
            mActivity.onDocumentPicked(doc);
            mConfig.selectionMgr.clearSelection();
            return true;
        }
        return false;
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
        void onAppPicked(ResolveInfo info);
        void onDocumentPicked(DocumentInfo doc);
    }
}
