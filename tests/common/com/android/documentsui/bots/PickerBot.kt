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
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.isNotEnabled
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.uiautomator.UiDevice
import com.android.documentsui.R
import com.android.documentsui.bots.Bots.BaseBot
import com.android.documentsui.util.Material3Config.Companion.getRes

/**
 * A test helper class that provides support for controlling picker activities
 * programmatically, and making assertions against the state of the UI.
 */
class PickerBot(device: UiDevice?, context: Context?, timeout: Int) : BaseBot(
    device,
    context,
    timeout
) {
    /**
     * Clicks the save button with id button1
     */
    fun clickSaveButton() {
        onView(withId(android.R.id.button1)).perform(click())
    }

    /**
     * Checks that the pick button doesn't exist.
     */
    fun checkPickButtonDoesNotExist() {
        onView(withId(getRes(R.id.button_pick))).check(doesNotExist())
    }

    /**
     * Checks that the cancel button doesn't exist.
     */
    fun checkCancelButtonDoesNotExist() {
        onView(withId(getRes(R.id.button_cancel))).check(doesNotExist())
    }

    /**
     * Checks that the pick button is shown.
     */
    fun checkPickButtonDisplayed() {
        onView(withId(getRes(R.id.button_pick))).check(matches(isDisplayed()))
    }

    /**
     * Checks that the cancel button is shown.
     */
    fun checkCancelButtonDisplayed() {
        onView(withId(getRes(R.id.button_cancel))).check(matches(isDisplayed()))
    }

    /**
     * Checks that the pick button is enabled.
     */
    fun checkPickButtonEnabled() {
        onView(withId(getRes(R.id.button_pick))).check(matches(isEnabled()))
    }

    /**
     * Checks that the cancel button is enabled.
     */
    fun checkCancelButtonEnabled() {
        onView(withId(getRes(R.id.button_cancel))).check(matches(isEnabled()))
    }

    /**
     * Checks that the pick button is disabled.
     */
    fun checkPickButtonDisabled() {
        onView(withId(getRes(R.id.button_pick))).check(matches(isNotEnabled()))
    }

    /**
     * Clicks the pick button.
     */
    fun clickPickButton() {
        onView(withId(getRes(R.id.button_pick))).perform(click())
    }

    /**
     * Clicks the cancel button.
     */
    fun clickCancelButton() {
        onView(withId(getRes(R.id.button_cancel))).perform(click())
    }
}
