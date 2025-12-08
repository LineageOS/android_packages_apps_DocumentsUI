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
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar
import java.io.FileNotFoundException

/** Manages the Peek UI. */
class PeekFragment : Fragment() {
    companion object {
        private const val TAG = "PeekFragment"
    }

    // ViewModel holding the UI state of Peek, scoped to DocumentsUI's activity.
    private lateinit var viewModel: PeekViewModel

    // Top bar view.
    private var toolbar: MaterialToolbar? = null

    // Rendering view.
    private var previewFrame: FrameLayout? = null
    private var previewHandler: PreviewHandler? = null

    // Metadata view.
    private var metadataSheetController: MetadataSheetController? = null

    @Suppress("ktlint:standard:comment-wrapping")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view =
            inflater.inflate(getRes(R.layout.peek_layout), container, /* attachToRoot= */ false)
        viewModel = ViewModelProvider(requireActivity())[PeekViewModel::class.java]
        toolbar = view.findViewById(getRes(R.id.peek_toolbar))
        toolbar!!.setNavigationOnClickListener { clearAndHide() }
        toolbar!!.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.peek_info -> {
                    viewModel.metadataSheetExpanded.value?.let {
                        viewModel.toggleMetadataSheet(!it)
                    }
                    true
                }
                else -> false
            }
        }

        previewFrame = view.findViewById(getRes(R.id.peek_preview_frame))

        // The side sheet container is only defined in the large screen layout (w >= 900dp).
        val sideSheetContainer = view.findViewById<FrameLayout>(
            R.id.peek_coplanar_metadata_sheet_container
        )
        if (sideSheetContainer != null) {
            metadataSheetController = MetadataSideSheetController(
                requireContext(), viewModel,
                sideSheetContainer
            )
        }
        // The bottom sheet container is only defined in the medium screen layout (w < 600dp).
        val bottomSheetContainer = view.findViewById<FrameLayout>(
            R.id.peek_bottom_metadata_sheet_container
        )
        if (bottomSheetContainer != null) {
            val previewContainer = view.findViewById<FrameLayout>(
                getRes(R.id.peek_preview_container)
            )
            metadataSheetController = MetadataBottomSheetController(requireContext(),
                viewModel,
                bottomSheetContainer, previewContainer!!)
        }
        // Display the modal side sheet by default, if the metadataSheetController hasn't been set.
        if (metadataSheetController == null) {
            metadataSheetController = MetadataModalSheetController(requireContext(), viewModel)
        }

        val savedDocInfo = viewModel.docInfo.value
        if (savedDocInfo != null) {
            try {
                savedDocInfo.updateSelf(
                    savedDocInfo.userId.getContentResolver(context),
                    savedDocInfo.userId
                )
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
        // After `onCreateView`, the expanded state of the sheet is self-restored via
        // `onRestoreInstanceState` (e.g. SideSheetBehavior.onRestoreInstanceState of a coplanar
        // side sheet). However instead, we want to restore this state based on the viewModel, and
        // only after the layout has been fully drawn, in order to trigger an expand animation
        // (addressing b/419446581).
        // To do so, we hide the metadata sheet in `onViewStateRestored` (called after
        // `onRestoreInstanceState`) to override this self-restoration behaviour.
        // `updateView` is then responsible to expanding the sheet based on the view model's state.
        metadataSheetController?.hide()

        // The observer's `onChanged` method is called immediately after the observer is set, if
        // docInfo has a value. Set the observer after `viewModel.docInfo.value` is updated in
        // `onCreateView`.
        viewModel.docInfo.observe(requireActivity(), Observer { docInfo -> updateView(docInfo) })
        viewModel.metadataSheetExpanded.observe(
            requireActivity(),
            Observer { expanded ->
                toolbar!!.menu?.findItem(R.id.peek_info)?.let {
                    if (expanded) {
                        it.icon = getDrawable(requireContext(), R.drawable.ic_info_filled)
                        it.title = getString(R.string.a11y_peek_hide_info_button)
                        it.contentDescription = getString(R.string.a11y_peek_hide_info_button)
                    } else {
                        it.icon = getDrawable(requireContext(), R.drawable.ic_info)
                        it.title = getString(R.string.a11y_peek_show_info_button)
                        it.contentDescription = getString(R.string.a11y_peek_show_info_button)
                    }
                }
                // Only update the metadataSheetBehavior state if the overlay is shown. If not,
                // the metadata sheet is hidden, and its expanded state is restored in `updateView`
                // once the overlay gets shown.
                if (viewModel.overlayActive.value == true) {
                    if (expanded) {
                        // Expand the metadata sheet in a post task to ensure that if the fragment
                        // is being recreated, it triggers the expand animation.
                        this@PeekFragment.view?.post { metadataSheetController?.show() }
                    } else {
                        metadataSheetController?.hide()
                    }
                }
            }
        )
    }

    override fun onDestroyView() {
        previewHandler?.clear()
        metadataSheetController?.onDestroyView()
        super.onDestroyView()
    }

    private fun updateView(docInfo: DocumentInfo?) {
        if (docInfo == null) {
            return
        }
        if (toolbar == null || previewFrame == null || metadataSheetController == null) {
            // `updateView` will be called again when onCreateView executes.
            return
        }
        // `Expand` needs to be called after the view is visible and fully drawn (see b/419446581).
        // Given the check above, onCreateView has been called called.
        if (viewModel.overlayActive.value == true &&
            viewModel.metadataSheetExpanded.value == true) {
            // The `post` task will execute after the current layout pass, ensuring that we can see
            // the "expand" animation.
            view?.post { metadataSheetController?.show() }
        }
        toolbar!!.title = docInfo.displayName
        previewHandler?.clear()
        previewHandler =
            when {
                docInfo.mimeType.startsWith("image/") ->
                    ImagePreviewHandler(previewFrame!!, docInfo)
                docInfo.mimeType.startsWith("audio/") or docInfo.mimeType.startsWith("video/") ->
                    AudioAndVideoPreviewHandler(previewFrame!!, docInfo)
                else -> UnsupportedPreviewHandler(previewFrame!!)
            }
        metadataSheetController?.accept(docInfo)
    }

    private fun clearAndHide() {
        viewModel.clear()
        toolbar?.title = ""
        previewHandler?.clear()
        previewHandler = null
        metadataSheetController?.clear()
    }
}
