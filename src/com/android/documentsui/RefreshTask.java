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

import com.android.documentsui.base.ApplicationScope;
import com.android.documentsui.base.CheckedTask;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;

import java.util.function.Consumer;

/**
 * A {@link CheckedTask} that calls
 * {@link ContentResolver#refresh(Uri, android.os.Bundle, android.os.CancellationSignal)} on the
 * current directory, and then calls the supplied callback with the refresh return value.
 */
public class RefreshTask extends TimeoutTask<Void, Boolean> {

    private final static String TAG = "RefreshTask";

    private final @ApplicationScope Context mContext;
    private final State mState;
    private final Uri mUri;
    private final Consumer<Boolean> mCallback;
    private final CancellationSignal mSignal;

    public RefreshTask(State state, Uri uri, long timeout, @ApplicationScope Context context, Check check,
            Consumer<Boolean> callback) {
        super(check);
        mUri = uri;
        mContext = context;
        mState = state;
        mCallback = callback;
        mSignal = new CancellationSignal();
        setTimeout(timeout);
    }

    @Override
    public @Nullable Boolean run(Void... params) {
        if (mUri == null) {
            Log.w(TAG, "Attempted to refresh on a null uri. Aborting.");
            return false;
        }

        if (mUri != mState.stack.peek().derivedUri) {
            Log.w(TAG, "Attempted to refresh on a non-top-level uri. Aborting.");
            return false;
        }

        // API O introduces ContentResolver#refresh, and if available and the ContentProvider
        // supports it, the ContentProvider will automatically send a content updated notification
        // and we will update accordingly. Else, we just tell the callback that Refresh is not
        // supported.
        if (!Shared.ENABLE_OMC_API_FEATURES) {
            Log.w(TAG, "Attempted to call Refresh on an older Android platform. Aborting.");
            return false;
        }

        final ContentResolver resolver = mContext.getContentResolver();
        final String authority = mUri.getAuthority();
        boolean refreshSupported = false;
        ContentProviderClient client = null;
        try {
            client = DocumentsApplication.acquireUnstableProviderOrThrow(resolver, authority);
            refreshSupported = client.refresh(mUri, null, mSignal);
        } catch (Exception e) {
            Log.w(TAG, "Failed to refresh", e);
        } finally {
            ContentProviderClient.releaseQuietly(client);
        }
        return refreshSupported;
    }

    @Override
    protected void onTimeout() {
        mSignal.cancel();
        Log.w(TAG, "Provider taking too long to respond. Cancelling.");
    }

    @Override
    public void finish(Boolean refreshSupported) {
        if (DEBUG) {
            // In case of timeout, refreshSupported is null.
            if (Boolean.TRUE.equals(refreshSupported)) {
                Log.v(TAG, "Provider supports refresh and has refreshed");
            } else {
                Log.v(TAG, "Provider does not support refresh and did not refresh");
            }
        }
        mCallback.accept(refreshSupported != null ? refreshSupported : Boolean.FALSE);
    }
}
