/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.documentsui.picker

import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.android.documentsui.util.getPhotopickerGetContentComponentNameForType

/**
 * DocumentsUI PickActivity currently defers picking of media mime types to the Photopicker. This
 * activity trampolines the intent to either Photopicker or to the PickActivity depending on whether
 * there are non-media mime types to handle.
 */
class TrampolineActivity : AppCompatActivity() {
    companion object {
        const val TAG = "TrampolineActivity"
    }

    override fun onCreate(savedInstanceBundle: Bundle?) {
        super.onCreate(savedInstanceBundle)

        // This activity should not be present in the back stack nor should handle any of the
        // corresponding results when picking items.
        intent?.apply {
            addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT)
            addFlags(Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP)
        }

        // In the event there is no photopicker returned, just refer to DocumentsUI.
        val photopickerComponentName =
            getPhotopickerGetContentComponentNameForType(packageManager, intent.type)
        if (photopickerComponentName == null) {
            forwardIntentToDocumentsUI()
            return
        }

        // The Photopicker has an entry point to take them back to DocumentsUI. In the event the
        // user originated from Photopicker, we don't want to send them back.
        val referredFromPhotopicker = referrer?.host == photopickerComponentName.packageName
        if (referredFromPhotopicker || !shouldForwardIntentToPhotopicker(intent)) {
            forwardIntentToDocumentsUI()
            return
        }

        // Forward intent to Photopicker.
        intent.setComponent(photopickerComponentName)
        startActivity(intent)
        finish()
    }

    private fun forwardIntentToDocumentsUI() {
        intent.setClass(applicationContext, PickActivity::class.java)
        startActivity(intent)
        finish()
    }
}

private fun shouldForwardIntentToPhotopicker(intent: Intent): Boolean {
    // Photopicker can only handle `ACTION_GET_CONTENT` intents.
    if (intent.action != ACTION_GET_CONTENT) {
        return false
    }

    // Photopicker only handles media mime types (i.e. image/* or video/*), however, it also handles
    // requests that have type */* with EXTRA_MIME_TYPES that are media mime types. In that scenario
    // it provides an escape hatch to the user to go back to DocumentsUI.
    val intentTypeIsMedia = isMediaMimeType(intent.type)
    if (!intentTypeIsMedia && intent.type != "*/*") {
        return false
    }

    val extraMimeTypes = intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)

    // In the event there were no `EXTRA_MIME_TYPES` this should exclusively be handled by
    // DocumentsUI and not Photopicker.
    if (intent.type == "*/*" && extraMimeTypes == null) {
        return false
    }

    if (extraMimeTypes == null) {
        return intentTypeIsMedia
    }

    return extraMimeTypes.isNotEmpty() && extraMimeTypes.none { !isMediaMimeType(it) }
}

private fun isMediaMimeType(mimeType: String?): Boolean {
    return mimeType?.let { mimeType ->
        mimeType.startsWith("image/") || mimeType.startsWith("video/")
    } == true
}
