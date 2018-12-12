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

import static com.android.documentsui.base.SharedMinimal.DEBUG;

import androidx.annotation.StringDef;
import android.content.Context;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Methods for logging scoped directory access metrics.
 */
public final class ScopedAccessMetrics {
    private static final String TAG = "ScopedAccessMetrics";

    // Types for logInvalidScopedAccessRequest
    public static final String SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS =
            "docsui_scoped_directory_access_invalid_args";
    public static final String SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY =
            "docsui_scoped_directory_access_invalid_dir";
    public static final String SCOPED_DIRECTORY_ACCESS_ERROR =
            "docsui_scoped_directory_access_error";
    public static final String SCOPED_DIRECTORY_ACCESS_DEPRECATED =
            "docsui_scoped_directory_access_deprecated";

    @StringDef(value = {
            SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS,
            SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY,
            SCOPED_DIRECTORY_ACCESS_ERROR,
            SCOPED_DIRECTORY_ACCESS_DEPRECATED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InvalidScopedAccess{}

    public static void logInvalidScopedAccessRequest(Context context,
            @InvalidScopedAccess String type) {
        switch (type) {
            case SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS:
            case SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY:
            case SCOPED_DIRECTORY_ACCESS_ERROR:
            case SCOPED_DIRECTORY_ACCESS_DEPRECATED:
                logCount(context, type);
                break;
            default:
                Log.wtf(TAG, "invalid InvalidScopedAccess: " + type);
        }
    }

    /**
     * Internal method for making a MetricsLogger.count call. Increments the given counter by 1.
     *
     * @param context
     * @param name The counter to increment.
     */
    private static void logCount(Context context, String name) {
        if (DEBUG) Log.d(TAG, name + ": " + 1);
        // TODO b/111552654 migrate westworld
    }
}
