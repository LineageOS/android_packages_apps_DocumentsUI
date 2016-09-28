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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnShowListener;
import android.widget.Button;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.base.ConfirmationCallback;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.services.FileOperations.Callback.Status;

import java.util.List;

public interface DialogController {

    public static final DialogController STUB = new DialogController() {

        @Override
        public void confirmDelete(List<DocumentInfo> docs, ConfirmationCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void showFileOperationFailures(int status, int opType, int docCount) {
            throw new UnsupportedOperationException();
        }
    };

    void confirmDelete(List<DocumentInfo> docs, ConfirmationCallback callback);
    void showFileOperationFailures(int status, int opType, int docCount);

    // Should be private, but Java doesn't like me treating an interface like a mini-package.
    public static final class RuntimeDialogController implements DialogController {

        private final Activity mActivity;
        private final MessageBuilder mMessages;

        public RuntimeDialogController(Activity activity) {
            mActivity = activity;
            mMessages = new MessageBuilder(mActivity);
        }

        @Override
        public void confirmDelete(List<DocumentInfo> docs, ConfirmationCallback callback) {
            assert(!docs.isEmpty());

            TextView message =
                    (TextView) mActivity.getLayoutInflater().inflate(
                            R.layout.dialog_delete_confirmation, null);
            message.setText(mMessages.generateDeleteMessage(docs));

            // For now, we implement this dialog NOT
            // as a fragment (which can survive rotation and have its own state),
            // but as a simple runtime dialog. So rotating a device with an
            // active delete dialog...results in that dialog disappearing.
            // We can do better, but don't have cycles for it now.
            final AlertDialog alertDialog = new AlertDialog.Builder(mActivity)
                    .setView(message)
                    .setPositiveButton(
                            android.R.string.ok,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    callback.accept(ConfirmationCallback.CONFIRM);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();

            alertDialog.setOnShowListener(
                    (DialogInterface) -> {
                        Button positive = alertDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                        positive.setFocusable(true);
                        positive.setFocusableInTouchMode(true);
                        positive.requestFocus();
                    });
            alertDialog.show();
        }

        @Override
        public void showFileOperationFailures(@Status int status, @OpType int opType, int docCount) {
            if (status == FileOperations.Callback.STATUS_REJECTED) {
                Snackbars.showPasteFailed(mActivity);
                return;
            }

            if (docCount == 0) {
                // Nothing has been pasted, so there is no need to show a snackbar.
                return;
            }

            switch (opType) {
                case FileOperationService.OPERATION_MOVE:
                    Snackbars.showMove(mActivity, docCount);
                    break;
                case FileOperationService.OPERATION_COPY:
                    Snackbars.showCopy(mActivity, docCount);
                    break;
                case FileOperationService.OPERATION_DELETE:
                    // We don't show anything for deletion.
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported Operation: " + opType);
            }
        };
    }

    static DialogController create(Activity activity) {
        return new RuntimeDialogController(activity);
    }
}
