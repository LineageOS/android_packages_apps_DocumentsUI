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

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.DurableUtils;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.picker.LastAccessedProvider.Columns;
import com.android.documentsui.roots.RootsAccess;

import libcore.io.IoUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * Loads the last used path (stack) from Recents (history).
 * The path selected is based on the calling package name. So the last
 * path for an app like Gmail can be different than the last path
 * for an app like DropBox.
 */
final class LoadLastAccessedStackTask<T extends Activity & CommonAddons>
        extends PairedTask<T, Void, DocumentStack> {

    private static final String TAG = "LoadLastAccessedStackTa";

    private final State mState;
    private final RootsAccess mRoots;
    private final Consumer<DocumentStack> mCallback;

    LoadLastAccessedStackTask(
            T activity, State state, RootsAccess roots, Consumer<DocumentStack> callback) {
        super(activity);
        mRoots = roots;
        mState = state;
        mCallback = callback;
    }

    @Override
    protected DocumentStack run(Void... params) {
        String callingPackage = Shared.getCallingPackageName(mOwner);
        Uri resumeUri = LastAccessedProvider.buildLastAccessed(
                callingPackage);
        Cursor cursor = mOwner.getContentResolver().query(resumeUri, null, null, null, null);
        try {
            return DocumentStack.fromLastAccessedCursor(
                    cursor, mRoots.getMatchingRootsBlocking(mState), mOwner.getContentResolver());
        } catch (IOException e) {
            Log.w(TAG, "Failed to resume: ", e);
        } finally {
            IoUtils.closeQuietly(cursor);
        }

        return null;
    }

    @Override
    protected void finish(@Nullable DocumentStack stack) {
        mCallback.accept(stack);
    }
}
