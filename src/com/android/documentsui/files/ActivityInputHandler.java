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

package com.android.documentsui.files;

import android.view.KeyEvent;

import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.ActionHandler;

/**
 * Used by {@link FilesActivity} to manage global keyboard shortcuts tied to file actions
 */
final class ActivityInputHandler {

    private final SelectionManager mSelectionMgr;
    private final ActionHandler mActions;

    ActivityInputHandler(SelectionManager selectionMgr, ActionHandler actionHandler) {
        mSelectionMgr = selectionMgr;
        mActions = actionHandler;
    }

    boolean onKeyDown(int keyCode, KeyEvent event) {
        if ((keyCode == KeyEvent.KEYCODE_DEL && event.isAltPressed())
                || keyCode == KeyEvent.KEYCODE_FORWARD_DEL) {
            if (mSelectionMgr.hasSelection()) {
                mActions.deleteSelectedDocuments();
                return true;
            } else {
                return false;
            }
        }
        return false;
    }
}