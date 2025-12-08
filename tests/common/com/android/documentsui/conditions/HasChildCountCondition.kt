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

package com.android.documentsui.conditions

import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiObject2Condition

/**
 * A UI Automator condition that is met when the UiObject2 that it is acting on has the number of
 * children elements that matches the supplied `predicate`. The companion object contains some
 * shorthand helpers for common cases. The object that it is checking the child count of must exist.
 */
class HasChildCountCondition(
    private val predicate: (count: Int) -> Boolean
) : UiObject2Condition<Boolean>() {
    override fun apply(o: UiObject2?): Boolean? {
        requireNotNull(o) { "Supplied object to validate for child count must exist" }
        return predicate(o.childCount)
    }

    companion object {
        @JvmStatic
        fun hasOneChild(): HasChildCountCondition {
            return HasChildCountCondition({ it == 1 })
        }

        @JvmStatic
        fun hasMoreThanOneChild(): HasChildCountCondition {
            return HasChildCountCondition({ it > 1 })
        }

        @JvmStatic
        fun hasNoChildren(): HasChildCountCondition {
            return HasChildCountCondition({ it == 0})
        }
    }
}
