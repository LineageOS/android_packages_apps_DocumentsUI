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
package com.android.documentsui.roots

import android.database.MatrixCursor
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.base.UserId
import com.google.common.truth.Expect
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class RootCursorWrapperTest {
    @get:Rule
    val expect: Expect = Expect.create()

    val baseCursor = MatrixCursor(arrayOf("column-a", "column-b"))
    val rootCursor =
        RootCursorWrapper(UserId.CURRENT_USER, "com.example.authority", "root-id", baseCursor, 10)

    @Before
    fun setUp() {
        baseCursor.newRow().add(0, "column-a-value").add(1, 1)
    }

    @Test
    fun testSetExtras() {
        expect.that(rootCursor.extras.isEmpty).isTrue()
        rootCursor.extras = Bundle().apply {
            putString("key", "value")
        }
        expect.that(rootCursor.extras.isEmpty).isFalse()
        expect.that(rootCursor.extras.containsKey("key")).isTrue()
        expect.that(rootCursor.extras.getString("key")).isEqualTo("value")
        rootCursor.extras.putString("key", "different-value")
        expect.that(rootCursor.extras.getString("key")).isEqualTo("different-value")

        // Setting extras (via setExtras() method) swaps null for an empty bundle.
        rootCursor.extras = null
        expect.that(rootCursor.extras).isNotNull()
        expect.that(rootCursor.extras.isEmpty).isTrue()
    }

    @Test
    fun testRootId() {
        val rootIdColumnIndex = rootCursor.getColumnIndex(RootCursorWrapper.COLUMN_ROOT_ID)
        expect.that(rootIdColumnIndex).isGreaterThan(-1)
        expect.that(rootCursor.getString(rootIdColumnIndex)).isEqualTo("root-id")
    }

    @Test
    fun testAuthority() {
        val authorityColumnIndex = rootCursor.getColumnIndex(RootCursorWrapper.COLUMN_AUTHORITY)
        expect.that(authorityColumnIndex).isGreaterThan(-1)
        expect.that(rootCursor.getString(authorityColumnIndex)).isEqualTo("com.example.authority")
    }

    @Test
    fun testUserId() {
        val userIdColumnIndex = rootCursor.getColumnIndex(RootCursorWrapper.COLUMN_USER_ID)
        expect.that(userIdColumnIndex).isGreaterThan(-1)
        expect.that(rootCursor.getInt(userIdColumnIndex)).isEqualTo(UserId.CURRENT_USER.identifier)
    }
}
