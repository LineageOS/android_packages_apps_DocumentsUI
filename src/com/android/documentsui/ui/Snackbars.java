/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.Activity;
import android.icu.text.MessageFormat;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.StringRes;

import com.android.documentsui.R;
import com.android.documentsui.base.Shared;

import com.google.android.material.snackbar.Snackbar;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class Snackbars {

    private Snackbars() {}

    public static final void showDocumentsClipped(Activity activity, int docCount) {
        String msg =
                Shared.getQuantityString(
                        activity, getRes(R.plurals.clipboard_files_clipped), docCount);
        Snackbars.makeSnackbar(activity, msg, Snackbar.LENGTH_LONG).show();
    }

    public static final void showMove(Activity activity, int docCount) {
        CharSequence message =
                Shared.getQuantityString(activity, getRes(R.plurals.move_begin), docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_LONG).show();
    }

    public static final void showCopy(Activity activity, int docCount) {
        CharSequence message =
                Shared.getQuantityString(activity, getRes(R.plurals.copy_begin), docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_LONG).show();
    }

    public static final void showCompress(Activity activity, int docCount) {
        CharSequence message =
                Shared.getQuantityString(activity, getRes(R.plurals.compress_begin), docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_LONG).show();
    }

    public static final void showExtract(Activity activity, int docCount) {
        CharSequence message =
                Shared.getQuantityString(activity, getRes(R.plurals.extract_begin), docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_LONG).show();
    }

    public static final void showDelete(Activity activity, int docCount) {
        CharSequence message =
                Shared.getQuantityString(activity, getRes(R.plurals.deleting), docCount);
        makeSnackbar(activity, message, Snackbar.LENGTH_LONG).show();
    }

    public static void showError(Activity activity, @StringRes int id) {
        makeSnackbar(activity, getRes(id), Snackbar.LENGTH_LONG).show();
    }

    /**
     * Show Trash snackbar.
     */
    public static void showTrash(Activity activity, int docCount) {
        Map<String, Object> formatArgs = new HashMap<>();
        formatArgs.put("count", docCount);

        String message = new MessageFormat(activity.getString(getRes(R.string.trashing)),
                Locale.getDefault()).format(formatArgs);
        makeSnackbar(activity, message, Snackbar.LENGTH_LONG).show();
    }

    /**
     * Show Restore snackbar
     */
    public static void showRestore(Activity activity, int docCount) {
        Map<String, Object> formatArgs = new HashMap<>();
        formatArgs.put("count", docCount);

        String message = new MessageFormat(
                activity.getString(getRes(R.string.restoring_from_trash)),
                Locale.getDefault()).format(formatArgs);
        makeSnackbar(activity, message, Snackbar.LENGTH_LONG).show();
    }

    public static final void showOperationRejected(Activity activity) {
        makeSnackbar(activity, getRes(R.string.file_operation_rejected), Snackbar.LENGTH_LONG)
                .show();
    }

    public static final void showOperationFailed(Activity activity) {
        makeSnackbar(activity, getRes(R.string.file_operation_error), Snackbar.LENGTH_LONG).show();
    }

    public static final void showRenameFailed(Activity activity) {
        makeSnackbar(activity, getRes(R.string.rename_error), Snackbar.LENGTH_LONG).show();
    }

    public static final void showInspectorError(Activity activity) {
        // Document Inspector uses a different view from other files app activities.
        final View view = activity.findViewById(getRes(R.id.inspector_root));
        Snackbar.make(view, getRes(R.string.inspector_load_error), Snackbar.LENGTH_INDEFINITE)
                .show();
    }

    public static final void showCustomTextWithImage(Activity activity, String text, int imageRes) {
        Snackbar snackbar = makeSnackbar(activity, text, Snackbar.LENGTH_LONG);
        View snackbarLayout = snackbar.getView();
        TextView textView = (TextView)snackbarLayout.findViewById(
                com.google.android.material.R.id.snackbar_text);
        textView.setGravity(Gravity.CENTER_HORIZONTAL);
        textView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        textView.setCompoundDrawablesWithIntrinsicBounds(imageRes, 0, 0, 0);
        snackbar.show();
    }

    public static final Snackbar makeSnackbarWithAction(Activity activity, int docCount,
            CharSequence message, int duration, CharSequence actionText,
            Consumer<View> action, final Snackbar.Callback callback) {
        return makeSnackbar(activity, message, duration)
                .setAction(actionText, action::accept)
                .addCallback(callback);
    }

    public static final Snackbar makeSnackbar(Activity activity, @StringRes int messageId,
            int duration) {
        return Snackbars.makeSnackbar(
                activity, activity.getResources().getText(messageId), duration);
    }

    public static final Snackbar makeSnackbar(
            Activity activity, CharSequence message, int duration) {
        final View view =
                activity.findViewById(
                        isUseMaterial3FlagEnabled()
                                ? getRes(R.id.coordinator_layout)
                                : getRes(R.id.container_save));
        return Snackbar.make(view, message, duration);
    }
}
