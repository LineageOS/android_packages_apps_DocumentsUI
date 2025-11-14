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
 * Enumerates possible file types used for restricting file search. These values correspond directly
 * to the values of the search_file_type_menu.
 */
enum class FileTypeOption(val value: Int) {
    ANY_TYPE(getRes(R.id.file_type_all_option)),
    AUDIO(getRes(R.id.file_type_audio_option)),
    DOCUMENTS(getRes(R.id.file_type_documents_option)),
    IMAGES(getRes(R.id.file_type_images_option)),
    VIDEO(getRes(R.id.file_type_videos_option)),
}

/**
 * For the given integer value, attempts to return the corresponding FileTypeOption enum.
 */
fun fileTypeOptionFor(value: Int): FileTypeOption? =
    enumValues<FileTypeOption>().firstOrNull { it.value == value }
