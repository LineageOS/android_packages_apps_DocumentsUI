/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui.files

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.Shared
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

/**
 * Dialog shown to users when a file cannot be opened with any of currently installed apps.
 * User sent to Play store search with the file extension (e.g. ".psd") prefilled as the search text.
 */
class NoApplicationFragment : DialogFragment() {
    private var mTargetDoc: DocumentInfo? = null

    companion object {
        private const val TAG = "NoApplicationFragment"
        private const val INSET = 32f
        private const val WIDTH = 320f

        /**
         * Create and show the dialog UI.
         *
         * @param fm  the fragment manager
         * @param targetDoc the document user is trying to open
         */
        @JvmStatic
        fun show(fm: FragmentManager, targetDoc: DocumentInfo?) {
            if (fm.isStateSaved) {
                Log.w(TAG, "Skip showing no application found dialog because state saved")
                return
            }

            if (fm.findFragmentByTag(TAG) != null) {
                Log.w(TAG, "Skip showing no application found dialog again.")
                return
            }

            val dialog = NoApplicationFragment()
            dialog.mTargetDoc = targetDoc
            dialog.show(fm, TAG)
        }

        fun getExtension(doc: DocumentInfo): String? {
            return MimeTypeMap.getSingleton().getExtensionFromMimeType(doc.mimeType)
        }

        fun createIntent(extension: String): Intent {
            val encodedExtension = URLEncoder.encode(
                extension,
                StandardCharsets.UTF_8.toString()
            )
            val playLink = "https://play.google.com/store/search?q=$encodedExtension&c=apps"
            return Intent(Intent.ACTION_VIEW, playLink.toUri())
        }
    }

    /**
     * Creates the dialog UI.
     *
     * @param savedInstanceState bundle with required arg: selectedDoc.
     * @return an AlertDialog instance.
     */
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        if (savedInstanceState != null) {
            mTargetDoc =
                savedInstanceState.getParcelable(Shared.EXTRA_DOC, DocumentInfo::class.java)
        }
        val builder = MaterialAlertDialogBuilder(requireContext())
            // We're setting the inset size explicitly so changes to the default inset size in the
            // future don't change our dialog size (the inset size affect the dialog size because
            // we're overriding the window size to get our desired dialog size).
            .setBackgroundInsetStart(dpToPx(INSET))
            .setBackgroundInsetEnd(dpToPx(INSET))
            .setTitle(getString(R.string.no_application_dialog_title))
            .setMessage(getString(R.string.no_application_dialog_message))

        mTargetDoc?.let { doc ->
            // If the file type is totally unknown, we can't help the user search for a compatible app.
            getExtension(doc)?.let { ext ->
                builder.setPositiveButton(
                    getString(R.string.no_application_dialog_button)
                ) { _, _ ->
                    startActivity(createIntent(ext))
                }
            }
        }

        return builder.create()
    }

    override fun onStart() {
        super.onStart()
        (dialog as? AlertDialog)?.let { d ->
            d.window?.let { w ->
                val params = WindowManager.LayoutParams()
                params.copyFrom(w.attributes)
                val maxWidth = requireContext().resources.displayMetrics.widthPixels
                // The window size is dialog size + right & left insets.
                params.width = dpToPx(WIDTH + (2 * INSET)).coerceAtMost(maxWidth)
                w.attributes = params
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelable(Shared.EXTRA_DOC, mTargetDoc)
    }

    fun dpToPx(dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            requireContext().resources.displayMetrics
        ).roundToInt()
    }
}
