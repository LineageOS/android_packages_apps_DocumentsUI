/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.documentsui.base;

import android.annotation.Nullable;
import android.os.Build;
import android.util.Log;

/**
 * Contains the minimum number of utilities (contants, helpers, etc...) that can be used by both the
 * main package and the minimal APK that's used by Android TV (and other devices).
 *
 * <p>In other words, it should not include any external dependency that would increase the APK
 * size.
 */
public final class SharedMinimal {

    public static final String TAG = "Documents";

    public static final boolean DEBUG = Build.IS_DEBUGGABLE;
    public static final boolean VERBOSE = DEBUG && Log.isLoggable(TAG, Log.VERBOSE);

    // Special directory name representing the full volume of a scoped directory request.
    public static final String DIRECTORY_ROOT = "ROOT_DIRECTORY";

    /**
     * Gets the name of a directory name in the format that's used internally by the app
     * (i.e., mapping {@code null} to {@link #DIRECTORY_ROOT});
     * if necessary.
     */
    public static String getInternalDirectoryName(@Nullable String name) {
        return name == null ? DIRECTORY_ROOT : name;
    }

    /**
     * Gets the name of a directory name in the format that is used externally
     * (i.e., mapping {@link #DIRECTORY_ROOT} to {@code null} if necessary);
     */
    @Nullable
    public static String getExternalDirectoryName(String name) {
        return name.equals(DIRECTORY_ROOT) ? null : name;
    }

    private SharedMinimal() {
        throw new UnsupportedOperationException("provides static fields only");
    }
}
