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
package com.android.documentsui.ui;

import com.android.documentsui.base.ConfirmationCallback;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.services.FileOperations;

import junit.framework.Assert;

import java.util.List;

public class TestDialogController implements DialogController {

    public int mNextConfirmationCode;
    private boolean mFileOpFailed;
    private boolean mNoApplicationFound;

    public TestDialogController() {
        // by default, always confirm
        mNextConfirmationCode = ConfirmationCallback.CONFIRM;
    }

    @Override
    public void confirmDelete(List<DocumentInfo> docs, ConfirmationCallback callback) {
        callback.accept(mNextConfirmationCode);
    }

    @Override
    public void showFileOperationFailures(int status, int opType, int docCount) {
        if (status == FileOperations.Callback.STATUS_REJECTED) {
            mFileOpFailed = true;
        }
    }

    @Override
    public void showNoApplicationFound() {
        mNoApplicationFound = true;
    }

    public void assertNoFileFailures() {
        Assert.assertFalse(mFileOpFailed);
    }

    public void assertNoAppFoundShown() {
        Assert.assertFalse(mNoApplicationFound);
    }

    public void confirmNext() {
        mNextConfirmationCode = ConfirmationCallback.CONFIRM;
    }

    public void rejectNext() {
        mNextConfirmationCode = ConfirmationCallback.REJECT;
    }
}
