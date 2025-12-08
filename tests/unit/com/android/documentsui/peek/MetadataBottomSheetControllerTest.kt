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
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.R
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_USE_PEEK_PREVIEW_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@EnableFlags(FLAG_USE_MATERIAL3)
// TODO(b/433858983): Change to EnableFlags once peek is overridable in FlagUtils.
@RequiresFlagsEnabled(FLAG_USE_PEEK_PREVIEW_RO)
@RunWith(AndroidJUnit4::class)
class MetadataBottomSheetControllerTest {
    @get:Rule
    val setFlags = OverrideFlagsRule()

    // TODO(b/433858983): Remove CheckFlagsRule once peek is overridable in FlagUtils.
    @get:Rule
    val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var context: Context
    private lateinit var root: CoordinatorLayout
    private lateinit var bottomSheetView: FrameLayout
    private lateinit var siblingView: FrameLayout
    private lateinit var controller: MetadataBottomSheetController

    private val rootWidth = 500
    private val rootHeight = 1000

    @Before
    fun setUp() {
        // The metadata controller inflates a MetadataView, which uses material design theme
        // attributes.
        context =
            ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().targetContext,
                getRes(R.style.DocumentsDefaultTheme))

        // Define a Coordinator layout with 2 child views, the bottom sheet view and its sibling.
        root =
            CoordinatorLayout(context).apply {
                layoutParams = CoordinatorLayout.LayoutParams(rootWidth, rootHeight)
            }
        siblingView =
            FrameLayout(context).apply {
                layoutParams =
                    CoordinatorLayout.LayoutParams(
                        CoordinatorLayout.LayoutParams.MATCH_PARENT,
                        CoordinatorLayout.LayoutParams.MATCH_PARENT)
            }
        bottomSheetView =
            FrameLayout(context).apply {
                layoutParams =
                    CoordinatorLayout.LayoutParams(
                        CoordinatorLayout.LayoutParams.MATCH_PARENT,
                        CoordinatorLayout.LayoutParams.MATCH_PARENT,
                    )
            }
        root.addView(siblingView)
        root.addView(bottomSheetView)
        val rootWidth = (root.layoutParams as ViewGroup.LayoutParams).width
        val rootHeightFromParams = (root.layoutParams as ViewGroup.LayoutParams).height

        // Set bottom sheet behavior.
        val bottomSheetBehavior = BottomSheetBehavior<FrameLayout>(context, null)
        (bottomSheetView.layoutParams as CoordinatorLayout.LayoutParams).behavior =
            bottomSheetBehavior
        controller =
            MetadataBottomSheetController(context, PeekViewModel(), bottomSheetView, siblingView)

        // Simulate a layout pass.
        val rootWidthMeasureSpec =
            View.MeasureSpec.makeMeasureSpec(rootWidth, View.MeasureSpec.EXACTLY)
        val rootHeightMeasureSpec =
            View.MeasureSpec.makeMeasureSpec(rootHeightFromParams, View.MeasureSpec.EXACTLY)
        root.measure(rootWidthMeasureSpec, rootHeightMeasureSpec)
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        assertEquals(1000, bottomSheetView.height)
    }

    @Test
    fun testOnSlideSiblingMargin() {
        // fitToContents is false to allow setting the expandedOffset and peekHeight manually.
        controller.sheetBehavior.isFitToContents = false
        // The parent's height is 1000. In its expanded state, the bottom sheet's height is 750.
        controller.sheetBehavior.expandedOffset = 250
        // In its collapsed state, the bottom sheet's height is 250.
        controller.sheetBehavior.peekHeight = 250

        assertEquals(1000, bottomSheetView.height)

        // If the slide offset is -1, the bottom sheet is in its hidden state.
        controller.sheetStateListener.onSlide(bottomSheetView, -1f)
        val siblingLayoutParams = siblingView.layoutParams as (ViewGroup.MarginLayoutParams)
        assertEquals(0, siblingLayoutParams.bottomMargin)

        // If the slide offset is -0.5, the bottom sheet is exactly in between its hidden state and
        // its collapsed state.
        controller.sheetStateListener.onSlide(bottomSheetView, -0.5f)
        assertEquals(125, siblingLayoutParams.bottomMargin)

        // If the slide offset is 0, the bottom sheet is in its collapsed state.
        controller.sheetStateListener.onSlide(bottomSheetView, 0f)
        assertEquals(250, siblingLayoutParams.bottomMargin)

        // If the slide offset is 0.5, the bottom sheet is exactly in between its collapsed and its
        // expanded state.
        controller.sheetStateListener.onSlide(bottomSheetView, 0.5f)
        assertEquals(500, siblingLayoutParams.bottomMargin)

        // If the slide offset is 1, the bottom sheet is in its expanded state.
        controller.sheetStateListener.onSlide(bottomSheetView, 1f)
        assertEquals(750, siblingLayoutParams.bottomMargin)
    }
}
