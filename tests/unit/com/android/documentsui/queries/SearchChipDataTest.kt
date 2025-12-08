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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class SearchChipDataTest {

    @get:Rule
    val expect: Expect = Expect.create()

    @Test
    fun testEqualsAndHash() {
        val dataA = SearchChipData(1, 2, arrayOf("image/png", "image/jpg"))
        val dataAlike = SearchChipData(1, 2, arrayOf("image/png", "image/jpg"))
        val dataAlikeRearrangedMimeTypes = SearchChipData(1, 2, arrayOf("image/jpg", "image/png"))
        val dataAWithDifferentMime = SearchChipData(1, 2, arrayOf("image/jpg"))
        val dataWithDifferentTitleResourceAndMime = SearchChipData(1, 4, arrayOf("*/*"))
        val dataWithDifferentChipType = SearchChipData(2, 2, arrayOf("image/png"))

        // A must be equal to A.
        expect.that(dataA.equals(dataA)).isTrue()
        expect.that(dataA.hashCode()).isEqualTo(dataA.hashCode())

        // A and Alike are equal, too.
        expect.that(dataA.equals(dataAlike)).isTrue()
        expect.that(dataA.hashCode()).isEqualTo(dataAlike.hashCode())

        // Order of mime types does not matter.
        expect.that(dataA.equals(dataAlikeRearrangedMimeTypes)).isTrue()
        expect.that(dataA.hashCode()).isEqualTo(dataAlikeRearrangedMimeTypes.hashCode())

        // Difference in MIME causes chip data to differ.
        expect.that(dataA.equals(dataAWithDifferentMime)).isFalse()
        expect.that(dataA.hashCode()).isNotEqualTo(dataAWithDifferentMime.hashCode())

        // A and B differ by title res.
        expect.that(dataA.equals(dataWithDifferentTitleResourceAndMime)).isFalse()
        expect.that(dataA.hashCode()).isNotEqualTo(dataWithDifferentTitleResourceAndMime.hashCode())

        // A and C differ by chip type.
        expect.that(dataA.equals(dataWithDifferentChipType)).isFalse()
        expect.that(dataA.hashCode()).isNotEqualTo(dataWithDifferentChipType.hashCode())
    }
}
