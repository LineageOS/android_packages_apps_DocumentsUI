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

package com.android.documentsui.archives;

import com.android.internal.annotations.GuardedBy;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Loads an instance of Archive lazily.
 */
public class Loader {
    private static final String TAG = "Loader";

    public static final int STATUS_OPENING = 0;
    public static final int STATUS_OPENED = 1;
    public static final int STATUS_FAILED = 2;

    private final Context mContext;
    private final Uri mArchiveUri;
    private final Uri mNotificationUri;
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Object mStatusLock = new Object();
    @GuardedBy("mStatusLock")
    private int mStatus = STATUS_OPENING;
    private Archive mArchive = null;

    Loader(Context context, Uri archiveUri, Uri notificationUri) {
        this.mContext = context;
        this.mArchiveUri = archiveUri;
        this.mNotificationUri = notificationUri;

        // Start loading the archive immediately in the background.
        mExecutor.submit(this::get);
    }

    synchronized Archive get() {
        synchronized (mStatusLock) {
            if (mStatus == STATUS_OPENED) {
                return mArchive;
            }
        }

        // Once loading the archive failed, do not to retry opening it until the
        // archive file has changed (the loader is deleted once we receive
        // a notification about the archive file being changed).
        synchronized (mStatusLock) {
            if (mStatus == STATUS_FAILED) {
                throw new IllegalStateException(
                        "Trying to perform an operation on an archive which failed to load.");
            }
        }

        try {
            mArchive = Archive.createForParcelFileDescriptor(
                    mContext,
                    mContext.getContentResolver().openFileDescriptor(
                            mArchiveUri, "r", null /* signal */),
                    mArchiveUri, mNotificationUri);
            synchronized (mStatusLock) {
                mStatus = STATUS_OPENED;
            }
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Failed to open the archive.", e);
            synchronized (mStatusLock) {
                mStatus = STATUS_FAILED;
            }
            throw new IllegalStateException("Failed to open the archive.", e);
        } finally {
            // Notify observers that the root directory is loaded (or failed)
            // so clients reload it.
            mContext.getContentResolver().notifyChange(
                    ArchivesProvider.buildUriForArchive(mArchiveUri),
                    null /* observer */, false /* syncToNetwork */);
        }

        return mArchive;
    }

    int getStatus() {
        synchronized (mStatusLock) {
            return mStatus;
        }
    }

    Lock getReadLock() {
        return mLock.readLock();
    }

    Lock getWriteLock() {
        return mLock.writeLock();
    }
}
