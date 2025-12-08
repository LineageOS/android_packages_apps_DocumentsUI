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

import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.GeneralClickAction
import androidx.test.espresso.action.GeneralLocation
import androidx.test.espresso.action.Press
import androidx.test.espresso.action.ViewActions
import com.android.documentsui.actions.EspressoViewActionsFork.SingleClick

/**
 * A ViewAction using a custom tap action to perform a right click with button press events.
 */
fun rightClick(): ViewAction {
    return ViewActions.actionWithAssertions(
        GeneralClickAction(
            SingleClick(),
            GeneralLocation.VISIBLE_CENTER,
            Press.PINPOINT,
            InputDevice.SOURCE_MOUSE,
            MotionEvent.BUTTON_SECONDARY
        )
    )
}
