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
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.Matcher

/**
 * A custom action to remove the constraints on requiring 90% of the views area to be covered.
 * Useful to use on elements (such as menus or search chips which don't fulfill this criteria).
 */
class RelaxedClickAction internal constructor() : ViewAction {
    private val mWrappedClickAction: ViewAction = ViewActions.click()

    override fun getConstraints(): Matcher<View?> = ViewMatchers.isEnabled()

    override fun getDescription(): String? = mWrappedClickAction.description

    override fun perform(uiController: UiController?, view: View?) {
        mWrappedClickAction.perform(uiController, view)
    }
}
