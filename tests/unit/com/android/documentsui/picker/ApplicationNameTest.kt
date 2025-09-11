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
package com.android.documentsui.picker

import android.app.Activity
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.Resources
import com.android.documentsui.R
import com.android.documentsui.base.Shared
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

/**
 * When apps are requesting access to a folder using `ACTION_OPEN_DOCUMENT_TREE` the application
 * label is shown in the UI. Let's ensure that the label is appropriately sanitised.
 */
@RunWith(Parameterized::class)
class ApplicationNameTest {
    @Mock
    private lateinit var mockActivity: Activity

    @Mock
    private lateinit var resources: Resources

    @Mock
    private lateinit var pm: PackageManager

    companion object {
        const val PACKAGE_NAME = "com.package.test"
        const val ANONYMOUS_PACKAGE = "anonymous"

        @Parameterized.Parameters(name = "{index}")
        @JvmStatic
        fun parameters() =
            listOf(
                // Normal labels return their values.
                Pair("label", "label"),

                // Newlines are stripped.
                Pair("label\nlabel", "label label"),

                // Labels are trimmed to 500 characters.
                Pair("a".repeat(600), "a".repeat(500)),

                // An empty label returns the anonymous package name.
                Pair("", ANONYMOUS_PACKAGE)
            )
    }

    @Parameterized.Parameter(0)
    lateinit var testData: Pair<String, String>

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        whenever(mockActivity.resources).thenReturn(resources)
        whenever(mockActivity.packageManager).thenReturn(pm)
        whenever(resources.getString(R.string.anonymous_application)).thenReturn(ANONYMOUS_PACKAGE)
        whenever<String>(mockActivity.callingPackage).thenReturn(PACKAGE_NAME)
    }

    @Test
    fun testNameIsSanitized() {
        val info = ApplicationInfo()
        whenever(pm.getApplicationInfo(PACKAGE_NAME, 0)).thenReturn(info)

        whenever(pm.getApplicationLabel(eq(info))).thenReturn(testData.first)
        assertEquals(Shared.getCallingAppName(mockActivity), testData.second)
    }
}
