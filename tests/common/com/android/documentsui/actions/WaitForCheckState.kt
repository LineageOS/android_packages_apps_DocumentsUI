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
package com.android.documentsui.actions

import android.view.View
import android.widget.CompoundButton
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.util.HumanReadables
import java.util.concurrent.TimeoutException
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher

/**
 * An action that waits until checked state matches the specified one. The view to which this
 * action is applied must be a CompoundButton or one of its descendants. Typical use:
 * <pre>
 *  onView(withId(R.id.my_compound_button_id)).perform(WaitForChecked(true, 500L))
 * </pre>
 */
class WaitForCheckState(private val check: Boolean, private val timeoutMs: Long = 500L) :
    ViewAction {
    override fun getConstraints(): Matcher<View> {
        return CoreMatchers.instanceOf(CompoundButton::class.java)
    }

    override fun getDescription(): String {
        val state = if (check) "checked" else "unchecked"
        return "wait up to ${timeoutMs}ms for the view to become $state"
    }

    override fun perform(uiController: UiController, view: View) {
        val endTime = System.currentTimeMillis() + timeoutMs
        do {
            if ((view as CompoundButton).isChecked == check) {
                return
            }
            uiController.loopMainThreadForAtLeast(50L)
        } while (System.currentTimeMillis() < endTime)

        throw PerformException.Builder()
            .withActionDescription(description)
            .withCause(
                TimeoutException("Waited ${timeoutMs}ms for $view's check == $check")
            )
            .withViewDescription(HumanReadables.describe(view))
            .build()
    }
}
