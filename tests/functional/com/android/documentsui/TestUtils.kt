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
package com.android.documentsui

import android.app.Activity
import android.graphics.Rect
import android.util.DisplayMetrics
import android.util.TypedValue

class TestUtils {
    companion object {
        fun dpToPx(dp: Float, metrics: DisplayMetrics?): Float {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics)
        }

        fun pxToDp(px: Float, metrics: DisplayMetrics): Float {
            return TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_DIP, px, metrics)
        }

        /** Returns the bounds of the activity window in device independent pixels (dp). */
        fun getActivityBounds(activity: Activity): Rect {
            val windowMetrics = activity.windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            val displayMetrics: DisplayMetrics = activity.resources.displayMetrics
            val density = displayMetrics.density

            val windowWidthDp = bounds.width() / density
            val windowHeightDp = bounds.height() / density
            return Rect(0, 0, windowWidthDp.toInt(), windowHeightDp.toInt())
        }
    }
}
