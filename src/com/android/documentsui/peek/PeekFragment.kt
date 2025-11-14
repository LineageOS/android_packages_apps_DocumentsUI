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

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.sidesheet.SideSheetBehavior
import java.io.FileNotFoundException

/** Manages the Peek UI. */
class PeekFragment : Fragment() {
    companion object {
        private const val TAG = "PeekFragment"
    }

    // Interface for custom view components that are rendered based on a DocumentInfo.
    interface Display {
        fun accept(doc: DocumentInfo)

        fun clear()
    }

    // ViewModel holding the UI state of Peek, scoped to DocumentsUI's activity.
    private lateinit var viewModel: PeekViewModel

    // Top bar view.
    private var toolbar: MaterialToolbar? = null

    // Rendering view.
    private var previewFrame: FrameLayout? = null
    private var previewHandler: PreviewHandler? = null

    // Metadata view.
    private var metadataContainer: FrameLayout? = null
    private var metadataView: MetadataView? = null
    private var metadataSheetBehavior: SideSheetBehavior<FrameLayout>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[PeekViewModel::class.java]
        viewModel.docInfo.observe(
            requireActivity(),
            Observer { docInfo ->
                // Executes immediately when the observer is set.
                updateView(docInfo)
            }
        )
    }

    @Suppress("ktlint:standard:comment-wrapping")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view =
            inflater.inflate(getRes(R.layout.peek_layout), container, /* attachToRoot= */ false)
        toolbar = view.findViewById(getRes(R.id.peek_toolbar))
        toolbar!!.setNavigationOnClickListener { clearAndHide() }
        previewFrame = view.findViewById(getRes(R.id.peek_preview_frame))

        metadataContainer = view.findViewById(R.id.peek_metadata_container)
        metadataView = MetadataView(requireContext())
        metadataContainer!!.addView(metadataView)
        metadataSheetBehavior = SideSheetBehavior.from(metadataContainer!!)

        val savedDocInfo = viewModel.docInfo.value
        if (savedDocInfo != null) {
            try {
                savedDocInfo.updateSelf(
                    savedDocInfo.userId.getContentResolver(context),
                    savedDocInfo.userId
                )
                updateView(savedDocInfo)
            } catch (e: FileNotFoundException) {
                Log.e(TAG, "Stale document info: $e")
                clearAndHide()
            }
        }
        return view
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        // Hide the metadata sheet to override the Material SideSheet state restoration behavior.
        // Details:
        // After `onCreateView`, the metadataSheetBehavior restores the expanded state of the sheet
        // via its `onRestoreInstanceState` implementation. `onViewStateRestored` is subsequently
        // called, allowing us to override this state restoration with `metadataSheetBehavior.hide`.
        // `updateView` is responsible for re-expanding the sheet, by posting a
        // `metadataSheetBehavior.expand` task which executes after the fragment has been rendered
        // on screen (which triggers an expand animation, addressing b/419446581).
        metadataSheetBehavior?.hide()
    }

    private fun updateView(docInfo: DocumentInfo?) {
        if (docInfo == null) {
            return
        }
        if (toolbar == null ||
            previewFrame == null ||
            metadataView == null ||
            metadataContainer == null ||
            metadataSheetBehavior == null) {
            // `updateView` will be called again when onCreateView executes.
            return
        }
        // `Expand` needs to be called after the view is visible and fully drawn (see b/419446581).
        // With the check above, we know that onCreateView has been called.
        // The `post` task will execute after the current layout pass, ensuring that we can see the
        // "expand" animation.
        if (viewModel.overlayActive.value == true) {
            metadataContainer!!.post { metadataSheetBehavior!!.expand() }
        }
        toolbar!!.title = docInfo.displayName
        previewHandler?.clear()
        previewHandler =
            when {
                docInfo.mimeType.startsWith("image/") ->
                    ImagePreviewHandler(previewFrame!!, docInfo)
                else -> DefaultPreviewHandler(previewFrame!!)
            }
        metadataView!!.accept(docInfo)
    }

    private fun clearAndHide() {
        viewModel.clear()
        toolbar?.title = ""
        previewHandler?.clear()
        previewHandler = null
    }
}
