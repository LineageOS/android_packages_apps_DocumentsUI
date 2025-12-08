/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.documentsui.loaders

import android.os.Bundle
import java.time.Duration
import java.util.Objects

/**
 * The constant to be used for the maxResults parameter, if we wish to get all (unlimited) results.
 */
const val ALL_RESULTS: Int = -1

/**
 * Common query options. These are:
 *  - maximum number of results to return across all queried providers; pass ALL_RESULTS to impose
 *    no limits.
 *  - maximum number of results to return *per root*; pass ALL_RESULTS to impose no limits,
 *    and note that this only works for DocumentsProviders that support QUERY_ARG_LIMIT for queries
 *    on their Roots.
 *  - maximum lastModified delta in milliseconds: the delta from now used to reject files that were
 *    not modified in the specified milliseconds; pass null for no limits.
 *  - maximum time the query should return, including empty, results; pass null for no limits.
 *  - whether or not to show hidden files.
 *  - A list of MIME types used to filter returned files.
 *  - "Other" query arguments not covered by the above.
 *
 *  The "other" query arguments are added as due to existing code communicating information such
 *  as acceptable file kind (images, videos, etc.) is done via Bundle arguments. This could be
 *  and should be changed if this code ever is rewritten.
 *  TODO(b:397095797): Merge otherQueryArgs with acceptableMimeTypes and maxLastModifiedDelta.
 */
data class QueryOptions(
    val maxResults: Int,
    val maxResultsPerRoot: Int,
    val maxLastModifiedDelta: Duration?,
    val maxQueryTime: Duration?,
    val showHidden: Boolean,
    val acceptableMimeTypes: Array<String>?,
    val otherQueryArgs: Bundle,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as QueryOptions

        return maxResults == other.maxResults &&
                maxResultsPerRoot == other.maxResultsPerRoot &&
                maxLastModifiedDelta == other.maxLastModifiedDelta &&
                maxQueryTime == other.maxQueryTime &&
                showHidden == other.showHidden &&
                acceptableMimeTypes.contentEquals(other.acceptableMimeTypes)
    }

    /**
     * Helper method that computes the earliest valid last modified timestamp. Converts last
     * modified duration to milliseconds past now. If the maxLastModifiedDelta is negative
     * this method returns 0L.
     */
    fun getRejectBeforeTimestamp() =
        if (maxLastModifiedDelta == null) {
            0L
        } else {
            System.currentTimeMillis() - maxLastModifiedDelta.toMillis()
        }

    /**
     * Helper function that indicates if query time is unlimited. Due to internal reliance on
     * Java's Duration class it assumes anything larger than 60 seconds has unlimited waiting
     * time.
     */
    fun isQueryTimeUnlimited() = maxQueryTime == null

    override fun hashCode(): Int {
        return Objects.hash(
            maxResults,
            maxResultsPerRoot,
            maxLastModifiedDelta,
            maxQueryTime,
            showHidden,
            acceptableMimeTypes.contentHashCode()
        )
    }
}
