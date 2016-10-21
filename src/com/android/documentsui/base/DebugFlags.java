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
package com.android.documentsui.base;

import javax.annotation.Nullable;

/**
 * Shared values that may be set by {@link DebugCommandProcessor}.
 */
public final class DebugFlags {

    private DebugFlags() {}

    private static String mQvPackage;
    private static boolean sGestureScaleEnabled;
    private static boolean sDocumentDetailsEnabled;

    public static void setQuickViewer(@Nullable String qvPackage) {
        mQvPackage = qvPackage;
    }

    public static @Nullable String getQuickViewer() {
        return mQvPackage;
    }

    public static void setDocumentDetailsEnabled(boolean enabled) {
        sDocumentDetailsEnabled = enabled;
    }

    public static boolean getDocumentDetailsEnabled() {
        return sDocumentDetailsEnabled;
    }

    public static void setGestureScaleEnabled(boolean enabled) {
        sGestureScaleEnabled = enabled;
    }

    public static boolean getGestureScaleEnabled() {
        return sGestureScaleEnabled;
    }
}
