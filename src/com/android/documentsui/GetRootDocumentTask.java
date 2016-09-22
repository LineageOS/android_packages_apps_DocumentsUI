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

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.util.Log;

import com.android.documentsui.base.CheckedTask;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;

import java.util.function.Consumer;

/**
 * A {@link CheckedTask} that takes {@link RootInfo} and query SAF to obtain the
 * {@link DocumentInfo} of its root document and call supplied callback to handle the
 * {@link DocumentInfo}.
 */
public class GetRootDocumentTask extends CheckedTask<Void, DocumentInfo> {

    private final static String TAG = "GetRootDocumentTask";

    private final RootInfo mRootInfo;
    private final Context mContext;
    private final Consumer<DocumentInfo> mCallback;

    public GetRootDocumentTask(
            RootInfo rootInfo, Activity activity, Consumer<DocumentInfo> callback) {
        this(rootInfo, activity, activity::isDestroyed, callback);
    }

    public GetRootDocumentTask(
            RootInfo rootInfo, Fragment fragment, Consumer<DocumentInfo> callback) {
        this(rootInfo, fragment.getContext(), fragment::isDetached, callback);
    }

    public GetRootDocumentTask(
            RootInfo rootInfo, Context context, Check check, Consumer<DocumentInfo> callback) {
        super(check);
        mRootInfo = rootInfo;
        mContext = context.getApplicationContext();
        mCallback = callback;
    }

    @Override
    public @Nullable DocumentInfo run(Void... rootInfo) {
        return mRootInfo.getRootDocumentBlocking(mContext);
    }

    @Override
    public void finish(@Nullable DocumentInfo documentInfo) {
        if (documentInfo != null) {
            mCallback.accept(documentInfo);
        } else {
            Log.e(TAG, "Cannot find document info for root: " + mRootInfo);
        }
    }
}
