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

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import com.android.documentsui.R
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton

class MetadataBottomSheetController(
    context: Context,
    viewModel: PeekViewModel,
    sheetContainer: FrameLayout,
    private val previewContainer: FrameLayout,
) : MetadataSheetController(context, viewModel) {
    @VisibleForTesting
    val sheetBehavior = BottomSheetBehavior.from(sheetContainer)

    @VisibleForTesting
    val sheetStateListener =
        object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_HIDDEN -> viewModel.toggleMetadataSheet(
                        expanded = false
                    )
                    BottomSheetBehavior.STATE_HALF_EXPANDED -> viewModel.toggleMetadataSheet(
                        expanded = true
                    )
                    else -> {}
                }
            }

            /**
             * The slide offset is a ratio between -1 and 1, mapping to the position of the bottom
             * sheet. Update the size of the preview container based on this position.
             */
            override fun onSlide(bottomSheet: View, slideOffset: Float) {
                val siblingView = this@MetadataBottomSheetController.previewContainer
                val siblingLayoutParams = siblingView.layoutParams as (ViewGroup.MarginLayoutParams)
                val collapsedHeight = sheetBehavior.peekHeight
                val expandedHeight = bottomSheet.height - sheetBehavior.expandedOffset
                if (slideOffset > 0) {
                    // Slide offset between 0 and 1, from collapsedHeight to expandedHeight.
                    siblingLayoutParams.bottomMargin =
                        collapsedHeight + ((expandedHeight - collapsedHeight) * slideOffset).toInt()
                } else {
                    // Slide offset between -1 and 0, from 0 to collapsedHeight.
                    siblingLayoutParams.bottomMargin = (collapsedHeight * (slideOffset + 1)).toInt()
                }
                siblingView.requestLayout()
            }
        }

    init {
        sheetContainer.addView(metadataView)
        metadataView.findViewById<MaterialButton>(R.id.peek_side_sheet_close_button)?.visibility =
            View.INVISIBLE
        sheetBehavior.addBottomSheetCallback(sheetStateListener)
    }

    override fun show() {
        sheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
    }

    override fun hide() {
        sheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
    }

    override fun onDestroyView() {
        sheetBehavior.removeBottomSheetCallback(sheetStateListener)
    }
}
