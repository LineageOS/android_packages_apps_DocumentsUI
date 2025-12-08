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

import androidx.annotation.StringRes
import com.android.documentsui.R
import com.android.documentsui.util.Material3Config.Companion.getRes

/**
 * Enumerates possible file types used for restricting file search. These values correspond directly
 * to the values of the search_file_type_menu.
 */
enum class FileTypeOption(
    private val rawValueId: Int,
    @StringRes private val rawTextId: Int
) {
    ANY_TYPE(R.id.file_type_all_option, R.string.search_file_type_all),
    AUDIO(R.id.file_type_audio_option, R.string.chip_title_audio),
    DOCUMENTS(R.id.file_type_documents_option, R.string.chip_title_documents),
    IMAGES(R.id.file_type_images_option, R.string.chip_title_images),
    VIDEO(R.id.file_type_videos_option, R.string.chip_title_videos);

    /**
     * Returns the resource ID assigned to this FileTypeOption. The ID may be a resource ID
     * converted from non-Material3 to Material3 version.
     */
    val value: Int
        get() = getRes(rawValueId)

    /**
     * Returns the string ID for the text associated with this file type. The ID may be a resource
     * ID converted from non-Material3 to Material3 version.
     */
    @get:StringRes
    val textId: Int
        get() = getRes(rawTextId)
}

/**
 * For the given integer value, attempts to return the corresponding FileTypeOption enum.
 */
fun fileTypeOptionFor(value: Int): FileTypeOption? =
    enumValues<FileTypeOption>().firstOrNull { it.value == value }
