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
package com.android.documentsui.peek

import android.graphics.drawable.Drawable
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import android.widget.ImageView
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/** Abstract class for file rendering in the provided frame. */
abstract class PreviewHandler(protected val previewFrame: FrameLayout) {
    open fun clear() {
        previewFrame.removeAllViews()
    }

    @Suppress("ktlint:standard:comment-wrapping")
    protected fun handleUnsupportedFileType() {
        LayoutInflater.from(previewFrame.context)
            .inflate(getRes(R.layout.peek_no_preview), /* root= */ previewFrame)
    }
}

/** Preview handler for unsupported file types. */
class DefaultPreviewHandler(previewFrame: FrameLayout) : PreviewHandler(previewFrame) {
    init {
        handleUnsupportedFileType()
    }
}

/** Preview handler for unsupported images. */
class ImagePreviewHandler(previewFrame: FrameLayout, doc: DocumentInfo) :
    PreviewHandler(previewFrame) {
    companion object {
        private const val TAG = "ImagePreviewHandler"
    }

    // Keep a reference to the displayed ImageView, for cleanup purpose.
    private var imageView: ImageView? = null

    private val glideRequestListener = object : RequestListener<Drawable> {
        override fun onLoadFailed(
            e: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean,
        ): Boolean {
            Log.e(TAG, "Glide image load request failed: ", e)
            // Remove the ImageView from the view hierarchy.
            imageView = null
            previewFrame.removeAllViews()
            // Display default "No preview available" screen.
            handleUnsupportedFileType()
            // Return false to indicate the target hasn't been modified by the listener.
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>,
            datasource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            // Return false to indicate the target hasn't been modified by the listener.
            return false
        }
    }

    init {
        imageView =
            ImageView(previewFrame.context).apply {
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription =
                    context.getString(R.string.a11y_peek_image_preview, doc.displayName)
            }
        previewFrame.addView(imageView)

        Glide.with(previewFrame)
            .load(doc.derivedUri)
            .fitCenter()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(glideRequestListener)
            .into(imageView!!)
    }

    override fun clear() {
        // Cancel any Glide load request if one is in progress.
        imageView?.let {
            Glide.with(previewFrame).clear(it)
            imageView = null
        }
        super.clear()
    }
}
