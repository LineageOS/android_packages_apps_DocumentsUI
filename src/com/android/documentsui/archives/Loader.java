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

import android.content.Context;
import android.net.Uri;

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
    private final Context mContext;
    private final Uri mArchiveUri;
    private final Uri mNotificationUri;
    private final ReentrantReadWriteLock mLock = new ReentrantReadWriteLock();
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private Exception mFailureException = null;
    public Archive mArchive = null;

    Loader(Context context, Uri archiveUri, Uri notificationUri) {
        this.mContext = context;
        this.mArchiveUri = archiveUri;
        this.mNotificationUri = notificationUri;

        // Start loading the archive immediately in the background.
        mExecutor.submit(this::get);
    }

    synchronized Archive get() throws FileNotFoundException {
        if (mArchive != null) {
            return mArchive;
        }

        // Once loading the archive failed, do not to retry opening it until the
        // archive file has changed (the loader is deleted once we receive
        // a notification about the archive file being changed).
        if (mFailureException != null) {
            throw new IllegalStateException(
                    "Trying to perform an operation on an archive which failed to load.",
                    mFailureException);
        }

        try {
            mArchive = Archive.createForParcelFileDescriptor(
                    mContext,
                    mContext.getContentResolver().openFileDescriptor(
                            mArchiveUri, "r", null /* signal */),
                    mArchiveUri, mNotificationUri);
        } catch (IOException e) {
            mFailureException = e;
            throw new IllegalStateException(e);
        } catch (RuntimeException e) {
            mFailureException = e;
            throw e;
        } finally {
            // Notify observers that the root directory is loaded (or failed)
            // so clients reload it.
            mContext.getContentResolver().notifyChange(
                    ArchivesProvider.buildUriForArchive(mArchiveUri),
                    null /* observer */, false /* syncToNetwork */);
        }
        return mArchive;
    }

    Lock getReadLock() {
        return mLock.readLock();
    }

    Lock getWriteLock() {
        return mLock.writeLock();
    }
}
