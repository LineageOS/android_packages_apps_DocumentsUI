/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui.selection;

import static android.support.v4.util.Preconditions.checkState;
import static com.android.documentsui.selection.Shared.DEBUG;
import static com.android.documentsui.selection.Shared.TAG;

import android.annotation.MainThread;
import android.annotation.Nullable;
import android.util.Log;

/**
 * A lock used by {@link BandSelectionHelper} and {@link GestureSelectionHelper} to signal to
 * clients when selection is in-progress. While locked, clients should block changes to content.
 */
public class ContentLock {

    private int mLocks = 0;
    private @Nullable Runnable mCallback;

    /**
     * Increment the block count by 1
     */
    @MainThread
    public synchronized void block() {
        mLocks++;
        if (DEBUG) Log.v(TAG, "Incremented content lock count to " + mLocks + ".");
    }

    /**
     * Decrement the block count by 1; If no other object is trying to block and there exists some
     * callback, that callback will be run
     */
    @MainThread
    public synchronized void unblock() {
        checkState(mLocks > 0);

        mLocks--;
        if (DEBUG) Log.v(TAG, "Decremented content lock count to " + mLocks + ".");

        if (mLocks == 0 && mCallback != null) {
            mCallback.run();
            mCallback = null;
        }
    }

    /**
     * Attempts to run the given Runnable if not-locked, or else the Runnable is set to be ran next
     * (replacing any previous set Runnables).
     */
    public synchronized void runWhenUnlocked(Runnable runnable) {
        if (mLocks == 0) {
            runnable.run();
        } else {
            mCallback = runnable;
        }
    }

    final boolean isLocked() {
        return mLocks > 0;
    }
}
