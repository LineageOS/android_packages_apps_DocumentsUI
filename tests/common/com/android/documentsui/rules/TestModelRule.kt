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
package com.android.documentsui.rules

import android.database.MatrixCursor
import android.provider.DocumentsContract
import com.android.documentsui.DirectoryResult
import com.android.documentsui.Model
import com.android.documentsui.roots.RootCursorWrapper
import com.android.documentsui.testing.TestFeatures
import org.junit.rules.ExternalResource

/**
 * TestModelRule provides a fake model with no connection to files on disk.
 *
 * If you need actual files in DocsUI, use TestFilesRules. This rule is for unit testing systems
 * that require a Model or a cursor but you don't want the overhead of a full documents provider.
 *
 * The authority and userId can be passed into the rule for tests that want to construct a ModelId.
 */
class TestModelRule(val authority: String = "com.example.test", val userId: Int = 10) :
    ExternalResource() {
    val model = Model(TestFeatures())
    private val cursor = MatrixCursor(
        arrayOf(
            RootCursorWrapper.COLUMN_AUTHORITY,
            RootCursorWrapper.COLUMN_USER_ID,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
    )

    /*
     * Create a file with the given name and type. Optionally specify flags as well.
     */
    fun createFile(name: String, type: String, flags: Int = 0): TestModelRule {
        val row = cursor.newRow()
        row.add(RootCursorWrapper.COLUMN_AUTHORITY, authority)
        row.add(RootCursorWrapper.COLUMN_USER_ID, userId)
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, name)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, name)
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, type)
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)

        return this
    }

    override fun before() {
        cursor.moveToFirst()
        val r = DirectoryResult()
        r.setCursor(cursor)
        model.update(r)
    }
}
