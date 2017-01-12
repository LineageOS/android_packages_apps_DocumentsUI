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

package com.android.documentsui.dirlist;

import android.annotation.Nullable;

import com.android.documentsui.R;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.DocumentsAdapter.Environment;
import com.android.documentsui.dirlist.Model.Update;

/**
 * Data object used by {@link InflateMessageDocumentHolder} and {@link HeaderMessageDocumentHolder}.
 */
abstract class Message {
    protected final Environment mEnv;
    private @Nullable CharSequence mMessageString;
    private int mIconId = -1;
    private boolean mShouldShow = false;

    Message(Environment env) {
        mEnv = env;
    }

    abstract void update(Update Event);

    protected void update(CharSequence messageString, int iconId) {
        if (messageString == null) {
            return;
        }
        mMessageString = messageString;
        mIconId = iconId;
        mShouldShow = true;
    }

    void reset() {
        mMessageString = null;
        mShouldShow = false;
        mIconId = -1;
    }

    int getIconId() {
        return mIconId;
    }

    boolean shouldShow() {
        return mShouldShow;
    }

    CharSequence getMessageString() {
        return mMessageString;
    }

    final static class HeaderMessage extends Message {

        HeaderMessage(Environment env) {
            super(env);
        }

        @Override
        void update(Update event) {
            reset();
            // Error gets first dibs ... for now
            // TODO: These should be different Message objects getting updated instead of
            // overwriting.
            if (mEnv.getModel().error != null) {
                update(mEnv.getModel().error, R.drawable.ic_dialog_alert);
            } else if (mEnv.getModel().info != null) {
                update(mEnv.getModel().info, R.drawable.ic_dialog_info);
            }
        }
    }

    final static class InflateMessage extends Message {

        InflateMessage(Environment env) {
            super(env);
        }

        @Override
        void update(Update event) {
            reset();
            if (event.hasError()) {
                updateToInflatedErrorMesage(
                        Shared.DEBUG ? Shared.getStackTrace(event.getError()) : null);

            } else if (mEnv.getModel().getModelIds().length == 0) {
                updateToInflatedEmptyMessage();
            }
        }


        private void updateToInflatedErrorMesage(@Nullable String debugString) {
            if (debugString == null) {
                update(mEnv.getContext().getResources().getText(R.string.query_error),
                        R.drawable.hourglass);
            } else {
                assert (Shared.DEBUG);
                update(debugString, R.drawable.hourglass);
            }
        }

        private void updateToInflatedEmptyMessage() {
            final CharSequence message;
            if (mEnv.isInSearchMode()) {
                message = String.format(
                        String.valueOf(
                                mEnv.getContext().getResources().getText(R.string.no_results)),
                        mEnv.getDisplayState().stack.getRoot().title);
            } else {
                message = mEnv.getContext().getResources().getText(R.string.empty);
            }
            update(message, R.drawable.cabinet);
        }
    }
}
