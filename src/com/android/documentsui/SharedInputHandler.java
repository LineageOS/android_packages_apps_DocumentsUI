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

import android.view.KeyEvent;

import com.android.documentsui.base.Events;
import com.android.documentsui.base.Procedure;
import com.android.documentsui.base.Shared;

public class SharedInputHandler {

    private final FocusManager mFocusManager;
    private Procedure mDirPopper;

    public SharedInputHandler(FocusManager focusManager, Procedure dirPopper) {
        mFocusManager = focusManager;
        mDirPopper = dirPopper;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Events.isNavigationKeyCode(keyCode)) {
            // Forward all unclaimed navigation keystrokes to the directory list.
            // This causes any stray navigation keystrokes to focus the content pane,
            // which is probably what the user is trying to do.
            mFocusManager.focusDirectoryList();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_TAB && !Shared.ENABLE_OMC_API_FEATURES) {
            // Tab toggles focus on the navigation drawer.
            // This should only be called in pre-O devices, since O has built-in keyboard navigation
            // support.
            mFocusManager.advanceFocusArea();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            mDirPopper.run();
            return true;
        }

        return false;
    }
}
