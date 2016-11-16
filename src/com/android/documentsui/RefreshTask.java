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

import static com.android.documentsui.base.Shared.DEBUG;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.Fragment;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.CancellationSignal;
import android.util.Log;

import com.android.documentsui.TimeoutTask;
import com.android.documentsui.base.CheckedTask;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.Model;

import java.util.function.Consumer;

/**
 * A {@link CheckedTask} that calls
 * {@link ContentResolver#refresh(Uri, android.os.Bundle, android.os.CancellationSignal)} on the
 * current directory, and then calls the supplied callback with the refresh return value.
 */
public class RefreshTask extends TimeoutTask<Void, Boolean> {

    private final static String TAG = "RefreshTask";

    private final Context mContext;
    private final State mState;
    private final Consumer<Boolean> mCallback;
    private final CancellationSignal mSignal;


    public RefreshTask(State state, Activity activity, Consumer<Boolean> callback) {
        this(state, activity, activity::isDestroyed, callback);
    }

    public RefreshTask(State state, Fragment fragment, Consumer<Boolean> callback) {
        this(state, fragment.getContext(), fragment::isDetached, callback);
    }

    public RefreshTask(State state, Context context, Check check, Consumer<Boolean> callback) {
        super(check);
        mContext = context.getApplicationContext();
        mState = state;
        mCallback = callback;
        mSignal = new CancellationSignal();
    }

    @Override
    public @Nullable Boolean run(Void... params) {
        final Uri uri = mState.stack.peek().derivedUri;
        final ContentResolver resolver = mContext.getContentResolver();
        final String authority = uri.getAuthority();
        boolean refreshed = false;
        ContentProviderClient client = null;
        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority);
            refreshed = client.refresh(uri, null, mSignal);
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh", e);
        } finally {
            ContentProviderClient.releaseQuietly(client);
        }
        return refreshed;
    }

    @Override
    protected void onTimeout() {
        mSignal.cancel();
    }

    @Override
    public void finish(Boolean refreshed) {
        if (DEBUG) {
            if (refreshed) {
                Log.v(TAG, "Provider has new content and has refreshed");
            } else {
                Log.v(TAG, "Provider has no new content and did not refresh");
            }
        }
        mCallback.accept(refreshed);
    }
}
