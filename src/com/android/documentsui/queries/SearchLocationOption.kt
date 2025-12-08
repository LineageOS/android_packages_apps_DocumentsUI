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
package com.android.documentsui.queries

import com.android.documentsui.R
import com.android.documentsui.util.Material3Config.Companion.getRes

/**
 * Enumerates possible options for the last modified filters. These values correspond directly
 * to the values of hte search_last_modified_menu.
 */
enum class SearchLocationOption(val value: Int) {
    ROOT_FOLDER(getRes(R.id.root_folder_option)),
    EVERYWHERE(getRes(R.id.everywhere_options)),
}

/**
 * For the given integer value, attempts to return the corresponding SearchLocationOption enum.
 */
fun searchLocationOptionFor(value: Int): SearchLocationOption? =
    enumValues<SearchLocationOption>().firstOrNull { it.value == value }
