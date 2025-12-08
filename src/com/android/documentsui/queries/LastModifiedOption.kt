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
import kotlin.time.Duration.Companion.days

/**
 * Enumerates possible options for the last modified filters. These values correspond directly
 * to the values of hte search_last_modified_menu.
 */
enum class LastModifiedOption(val value: Int, val millis: Long, @StringRes val textId: Int) {
    ANY_TIME(getRes(R.id.last_modified_any_time_option), 0, R.string.search_last_modified_any_time),
    LAST_DAY(
        getRes(R.id.last_modified_1_day_option),
        1.days.inWholeMilliseconds,
        R.string.search_last_modified_1_day
    ),
    LAST_2_DAYS(
        getRes(R.id.last_modified_2_days_option),
        2.days.inWholeMilliseconds,
        R.string.search_last_modified_2_days
    ),
    LAST_7_DAYS(
        getRes(R.id.last_modified_7_days_option),
        7.days.inWholeMilliseconds,
        R.string.search_last_modified_7_days
    ),
    LAST_30_DAYS(
        getRes(R.id.last_modified_30_days_option),
        30.days.inWholeMilliseconds,
        R.string.search_last_modified_30_days
    ),
    LAST_365_DAYS(
        getRes(R.id.last_modified_365_days_option),
        365.days.inWholeMilliseconds,
        R.string.search_last_modified_365_days
    ),
}

/**
 * For the given integer value, attempts to return the corresponding LastModifiedOption enum.
 */
fun lastModifiedOptionFor(value: Int): LastModifiedOption? =
    enumValues<LastModifiedOption>().firstOrNull { it.value == value }
