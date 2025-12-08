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

package com.android.documentsui.utils

import android.content.Context
import android.util.TypedValue
import com.android.documentsui.R
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.Material3Config.Companion.getRes

/** Check if the app is running in fixed_layout.  */
fun inFixedLayout(context: Context): Boolean {
    val value = TypedValue()
    // We alias files_activity to either fixed or drawer or nav_rail layouts based on screen
    // dimensions. In order to determine which layout has been selected, we check the
    // resolved value.
    context.getResources().getValue(getRes(R.layout.files_activity), value, true)
    return value.resourceId == getRes(R.layout.fixed_layout)
}

/** Check if the app is running in nav_rail_layout.  */
fun inNavRailLayout(context: Context): Boolean {
    if (!isUseMaterial3FlagEnabled()) {
        // NavRail is only enabled for material3, so the resource `nav_rail_layout` might
        // not exist in the apk.
        return false
    }
    val value = TypedValue()
    context.getResources().getValue(getRes(R.layout.files_activity), value, true)
    return value.resourceId == getRes(R.layout.nav_rail_layout)
}

/** Check if the app is running in drawer_layout.  */
fun inDrawerLayout(context: Context): Boolean {
    val value = TypedValue()
    context.getResources().getValue(getRes(R.layout.files_activity), value, true)
    return value.resourceId == getRes(R.layout.drawer_layout)
}
