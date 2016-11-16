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

import android.annotation.CallSuper;
import android.os.AsyncTask;
import android.os.Handler;

import com.android.documentsui.base.CheckedTask;
import com.android.documentsui.base.DocumentInfo;

/**
 * A {@link CheckedTask} that takes  and query SAF to obtain the
 * {@link DocumentInfo} of its root document and call supplied callback to handle the
 * {@link DocumentInfo}.
 */
public abstract class TimeoutTask<Input, Output> extends CheckedTask<Input, Output> {
    private static final int DEFAULT_TIMEOUT = -1;

    private long mTimeout = DEFAULT_TIMEOUT;

    public TimeoutTask(Check check) {
        super(check);
    }

    public void setTimeout(long timeout) {
        mTimeout = timeout;
    }

    @CallSuper
    @Override
    protected void prepare() {
        if (mTimeout < 0) {
            return;
        }

        Handler handler = new Handler();
        handler.postDelayed(() -> {
            if (getStatus() == AsyncTask.Status.RUNNING) {
                onTimeout();
                cancel(true);
                this.finish(null);
            }
        }, mTimeout);
    }

    /*
     * Override this do more proper clean up in case of timeout, such as using
     * CancellationSignal#cancel.
     */
    protected void onTimeout() {}
}
