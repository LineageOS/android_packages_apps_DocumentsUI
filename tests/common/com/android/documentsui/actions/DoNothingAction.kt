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
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher

/**
 * A custom action that does nothing. Useful as a rollback action. Typical use:
 * <pre>
 *     onView(withId(R.id.my_view_id)).perform(click(new DoNothingAction()));
 * </pre>
 */
class DoNothingAction internal constructor() : ViewAction {

    override fun getConstraints(): Matcher<View?> = CoreMatchers.any(View::class.java)

    override fun getDescription(): String = "Do nothing action"

    override fun perform(uiController: UiController?, view: View?) {
        // Do nothing
    }
}
