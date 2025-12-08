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
package com.android.documentsui.bots

import android.content.Context
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.uiautomator.UiDevice
import com.android.documentsui.R
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.sidesheet.SideSheetBehavior
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.hamcrest.CoreMatchers.allOf

/**
 * A test helper class that provides support for controlling the peek overlay and making assertions
 * against the state of it.
 */
class PeekBot(device: UiDevice, context: Context, timeout: Int) :
    Bots.BaseBot(device, context, timeout) {
    private val peekOverlayMatcher = withId(R.id.peek_overlay)
    private val peekContainerMatcher =
        allOf(withId(R.id.peek_container), isDescendantOfA(peekOverlayMatcher))
    private val toolbarMatcher =
        allOf(isAssignableFrom(MaterialToolbar::class.java), withId(R.id.peek_toolbar))
    private val coplanarMetadataSheetContainerMatcher =
        allOf(
            withId(R.id.peek_coplanar_metadata_sheet_container),
            isDescendantOfA(peekContainerMatcher)
        )
    private val bottomMetadataSheetContainerMatcher =
        allOf(
            withId(R.id.peek_bottom_metadata_sheet_container),
            isDescendantOfA(peekContainerMatcher)
        )

    /**
     * Validates the coplanar metadata sheet's expanded state. Assertion made on the metadata sheet
     * container.
     */
    private fun coplanarMetadataSheetExpandedStateAssertion(
        expectExpanded: Boolean
    ): ViewAssertion {
        return ViewAssertion { view, noViewFoundException ->
            if (view == null) {
                throw noViewFoundException
            }
            val metadataSheetBehavior = SideSheetBehavior.from(view)
            assertEquals(
                if (expectExpanded) {
                    SideSheetBehavior.STATE_EXPANDED
                } else {
                    SideSheetBehavior.STATE_HIDDEN
                },
                metadataSheetBehavior.state
            )
        }
    }

    /**
     * Validates that if the coplanar metadata sheet is expanded, the preview container is resized
     * so that it doesn't overlap with the metadata sheet. Assertion made on the root of the Peek
     * fragment.
     */
    private fun coplanarMetadataSheetWidthAssertion(expectExpanded: Boolean): ViewAssertion {
        return ViewAssertion { rootView, noViewFoundException ->
            if (rootView == null) {
                throw noViewFoundException
            }
            val previewContainer = rootView.findViewById<FrameLayout>(R.id.peek_preview_container)
            val metadataContainer =
                rootView.findViewById<FrameLayout>(R.id.peek_coplanar_metadata_sheet_container)
            assertTrue(rootView is CoordinatorLayout)
            assertTrue(metadataContainer.width > 0)
            assertEquals(
                rootView.width,
                if (expectExpanded) {
                    previewContainer.width + metadataContainer.width
                } else {
                    previewContainer.width
                }
            )
        }
    }

    /**
     * Validates the bottom metadata sheet's expanded state. Assertion made on the metadata sheet
     * container.
     */
    private fun bottomMetadataSheetExpandedStateAssertion(expectExpanded: Boolean): ViewAssertion {
        return ViewAssertion { view, noViewFoundException ->
            if (view == null) {
                throw noViewFoundException
            }
            val metadataSheetBehavior = BottomSheetBehavior.from(view)
            assertEquals(
                if (expectExpanded) {
                    BottomSheetBehavior.STATE_HALF_EXPANDED
                } else {
                    BottomSheetBehavior.STATE_HIDDEN
                },
                metadataSheetBehavior.state
            )
        }
    }

    /**
     * Validates that if the bottom metadata sheet is expanded, the preview container is resized
     * so that it doesn't overlap with the metadata sheet. Assertion made on the root of the Peek
     * fragment.
     */
    private fun bottomMetadataSheetHeightAssertion(expectExpanded: Boolean): ViewAssertion {
        return ViewAssertion { rootView, noViewFoundException ->
            if (rootView == null) {
                throw noViewFoundException
            }
            val previewContainer = rootView.findViewById<FrameLayout>(R.id.peek_preview_container)
            val metadataContainer =
                rootView.findViewById<FrameLayout>(R.id.peek_bottom_metadata_sheet_container)
            val metadataSheetBehavior = BottomSheetBehavior.from(metadataContainer)
            assertTrue(rootView is CoordinatorLayout)
            assertTrue(metadataContainer.height > 0)
            if (expectExpanded) {
                val metadataContainerHeight =
                    (metadataSheetBehavior.halfExpandedRatio * rootView.height).toInt()
                assertEquals(rootView.height, previewContainer.height + metadataContainerHeight)
            } else {
                assertEquals(rootView.height, previewContainer.height)
            }
        }
    }

    fun assertPeekHidden() {
        onView(peekOverlayMatcher)
            .check(matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)))
    }

    fun assertPeekActive() {
        onView(peekContainerMatcher).check(matches(isDisplayed()))
    }

    fun assertHasTitle(title: String) {
        onView(allOf(withText(title), isDescendantOfA(peekContainerMatcher)))
            .check(matches(isDisplayed()))
    }

    fun validateCoplanarMetadataSheetState(expectExpanded: Boolean) {
        mDevice.waitForIdle()
        onView(coplanarMetadataSheetContainerMatcher)
            .check(coplanarMetadataSheetExpandedStateAssertion(expectExpanded))
        onView(peekContainerMatcher).check(coplanarMetadataSheetWidthAssertion(expectExpanded))
        onView(
                allOf(
                    withId(R.id.peek_info),
                    isDescendantOfA(toolbarMatcher),
                    withContentDescription(
                        if (expectExpanded) {
                            R.string.a11y_peek_hide_info_button
                        } else {
                            R.string.a11y_peek_show_info_button
                        }
                    )
                )
        )
            .check(matches(isDisplayed()))
    }

    fun validateModalMetadataSheetStateExpanded() {
        mDevice.waitForIdle()
        onView(withId(R.id.peek_metadata_content)).inRoot(isDialog()).check(matches(isDisplayed()))
    }

    fun validateBottomMetadataSheetStateExpanded(expectExpanded: Boolean) {
        mDevice.waitForIdle()
        onView(bottomMetadataSheetContainerMatcher)
            .check(bottomMetadataSheetExpandedStateAssertion(expectExpanded))
        onView(peekContainerMatcher).check(bottomMetadataSheetHeightAssertion(expectExpanded))
    }

    fun hide() {
        onView(allOf(withContentDescription("Hide file preview"), isDescendantOfA(toolbarMatcher)))
            .perform(ViewActions.click())
        assertPeekHidden()
    }

    /* Toggles metadata sheet via the "info" toolbar button. */
    fun toggleMetadataSheet() {
        onView(allOf(withId(R.id.peek_info), isDescendantOfA(toolbarMatcher)))
            .perform(ViewActions.click())
    }

    /* Closes the large window metadata sheet via its "close" button. */
    fun closeCoplanarMetadataSheet() {
        onView(
                allOf(
                    withId(R.id.peek_side_sheet_close_button),
                    isDescendantOfA(coplanarMetadataSheetContainerMatcher)
                )
        )
            .perform(ViewActions.click())
    }
}
