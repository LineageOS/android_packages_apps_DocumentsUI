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

package com.android.documentsui.files

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.base.DocumentInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Suite

@RunWith(Suite::class)
@Suite.SuiteClasses(
    NoApplicationFragmentTest.UnitTests::class,
    NoApplicationFragmentTest.TestFileTypes::class
)
class NoApplicationFragmentTest() {

    @SmallTest
    @RunWith(AndroidJUnit4::class)
    class UnitTests {
        @Test
        fun testCreateExtensionEncoding() {
            val intent = NoApplicationFragment.createIntent("c++")
            assertEquals(
                "https://play.google.com/store/search?q=c%2B%2B&c=apps",
                intent.data.toString()
            )
        }
    }

    @SmallTest
    @RunWith(Parameterized::class)
    class TestFileTypes {
        companion object {
            @Parameterized.Parameters(name = "filename={0}")
            @JvmStatic
            fun parameters() = listOf(
                arrayOf(
                    "file",
                    "application/octet-stream",
                    "bin",
                    "https://play.google.com/store/search?q=bin&c=apps"
                ),
                arrayOf(
                    "file.bin",
                    "application/octet-stream",
                    "bin",
                    "https://play.google.com/store/search?q=bin&c=apps"
                ),
                arrayOf(
                    "test.txt",
                    "text/plain",
                    "txt",
                    "https://play.google.com/store/search?q=txt&c=apps"
                ),
                arrayOf(
                    "archive.tar.gz",
                    "application/gzip",
                    "gz",
                    "https://play.google.com/store/search?q=gz&c=apps"
                ),
                arrayOf(
                    "doc.pdf",
                    "application/pdf",
                    "pdf",
                    "https://play.google.com/store/search?q=pdf&c=apps"
                ),
                arrayOf(
                    "obscure.ttml",
                    "application/ttml+xml",
                    "ttml",
                    "https://play.google.com/store/search?q=ttml&c=apps"
                )
            )
        }

        @Parameterized.Parameter(0)
        lateinit var filename: String

        @Parameterized.Parameter(1)
        lateinit var fileMimeType: String

        @Parameterized.Parameter(2)
        lateinit var extension: String

        @Parameterized.Parameter(3)
        lateinit var playUrl: String

        @Test
        fun testGetExtension() {
            val doc = DocumentInfo().apply {
                displayName = filename
                mimeType = fileMimeType
            }

            val actual = NoApplicationFragment.getExtension(doc)
            assertEquals(extension, actual)
        }

        @Test
        fun testCreateIntent() {
            val intent = NoApplicationFragment.createIntent(extension)
            assertEquals(Intent.ACTION_VIEW, intent.action)
            assertEquals(playUrl, intent.data.toString())
        }
    }
}
