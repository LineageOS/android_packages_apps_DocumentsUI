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
import androidx.core.view.isVisible
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.util.HumanReadables
import java.util.concurrent.TimeoutException
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher

/**
 * An action that waits until a view becomes visible. Typical use:
 * <pre>
 *  onView(withId(R.id.my_view_id)).perform(WaitUntilVisible(500L))
 * </pre>
 */
class WaitUntilVisible(private val timeoutMs: Long = 500L) : ViewAction {
    override fun getConstraints(): Matcher<View> {
        return CoreMatchers.any(View::class.java)
    }

    override fun getDescription(): String {
        return "wait up to ${timeoutMs}ms for the view to become visible"
    }

    override fun perform(uiController: UiController, view: View) {
        val endTime = System.currentTimeMillis() + timeoutMs
        do {
            if (view.isVisible) {
                return
            }
            uiController.loopMainThreadForAtLeast(50L)
        } while (System.currentTimeMillis() < endTime)

        throw PerformException.Builder()
            .withActionDescription(description)
            .withCause(
                TimeoutException("Waited ${timeoutMs}ms for $view to become visible")
            )
            .withViewDescription(HumanReadables.describe(view))
            .build()
    }
}
