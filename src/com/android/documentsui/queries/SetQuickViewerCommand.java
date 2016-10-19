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
package com.android.documentsui.queries;

import android.text.TextUtils;
import android.util.Log;

import com.android.documentsui.base.EventHandler;

public class SetQuickViewerCommand implements EventHandler<String[]> {

    // This is a quick/easy shortcut to sharing quick viewer debug settings
    // with QuickViewIntent builder. Tried setting at a system property
    // but got a native error. This being quick and easy, didn't investigate that err.
    public static String sQuickViewer;
    private static final String TAG = "SetQuickViewerCommand";

    @Override
    public boolean accept(String[] tokens) {
        if ("setqv".equals(tokens[0])) {
            if (tokens.length == 2 && !TextUtils.isEmpty(tokens[1])) {
                sQuickViewer = tokens[1];
                Log.i(TAG, "Set quick viewer to: " + sQuickViewer);
                return true;
            } else {
                Log.w(TAG, "Invalid command structure: " + tokens);
            }
        } else if ("unsetqv".equals(tokens[0])) {
            Log.i(TAG, "Unset quick viewer");
            sQuickViewer = null;
            return true;
        }
        return false;
    }
}
