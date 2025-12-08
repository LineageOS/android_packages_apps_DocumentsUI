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
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.constraintlayout.widget.ConstraintLayout
import com.android.documentsui.R
import com.google.android.material.sidesheet.SideSheetBehavior
import com.google.android.material.sidesheet.SideSheetCallback

class MetadataSideSheetController(
    context: Context,
    viewModel: PeekViewModel,
    sheetContainer: FrameLayout,
) : MetadataSheetController(context, viewModel) {
    private val sheetBehavior = SideSheetBehavior.from(sheetContainer)

    // Listening for side sheet state updates is relevant when the metadata sheet is dragged.
    private val sheetStateListener =
        object : SideSheetCallback() {
            override fun onStateChanged(sideSheet: View, newState: Int) {
                when (newState) {
                    SideSheetBehavior.STATE_HIDDEN -> viewModel.toggleMetadataSheet(
                        expanded = false
                    )
                    SideSheetBehavior.STATE_EXPANDED -> viewModel.toggleMetadataSheet(
                        expanded = true
                    )
                    else -> {}
                }
            }

            override fun onSlide(sideSheet: View, slideOffset: Float) {}
        }

    init {
        sheetContainer.addView(metadataView)
        sheetBehavior.addCallback(sheetStateListener)

        // Apply background, margins and width to the MetadataView. For the modal and bottom sheet
        // version, the MetadataView just fits its parent container.
        metadataView.findViewById<ConstraintLayout>(R.id.peek_metadata_contents)?.apply {
            background = getDrawable(context, R.drawable.peek_metadata_sheet_background)
            layoutParams =
                LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.peek_metadata_sheet_width),
                    LayoutParams.MATCH_PARENT
                ).apply {
                    val marginsPixels = resources.getDimensionPixelSize(R.dimen.space_small_1)
                    setMargins(marginsPixels, marginsPixels, marginsPixels, marginsPixels)
                }
        }
    }

    override fun show() {
        sheetBehavior.expand()
    }

    override fun hide() {
        sheetBehavior.hide()
    }

    override fun onDestroyView() {
        sheetBehavior.removeCallback(sheetStateListener)
    }
}
