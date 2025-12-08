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
package com.android.documentsui.peek

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.view.ContextThemeWrapper
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_USE_PEEK_PREVIEW_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.util.Material3Config.Companion.getRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@EnableFlags(FLAG_USE_MATERIAL3)
// TODO(b/433858983): Change to EnableFlags once peek is overridable in FlagUtils.
@RequiresFlagsEnabled(FLAG_USE_PEEK_PREVIEW_RO)
@RunWith(AndroidJUnit4::class)
class MetadataViewTest {
    @get:Rule
    val setFlags = OverrideFlagsRule()

    // TODO(b/433858983): Remove CheckFlagsRule once peek is overridable in FlagUtils.
    @get:Rule
    val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        // Material design theme attributes are required to inflate the MetadataView contents.
        context =
            ContextThemeWrapper(
                InstrumentationRegistry.getInstrumentation().targetContext,
                getRes(R.style.DocumentsDefaultTheme))
    }

    @Test
    fun testAcceptValidMetadata() {
        val metadataView = MetadataView(context, PeekViewModel())
        val docInfo = DocumentInfo()
        docInfo.displayName = "IMG_1234.jpg"
        docInfo.mimeType = "image/jpeg"
        docInfo.size = 757 * 1000L // 757 KB
        docInfo.lastModified = 1726359000000L // UTC: September 15, 2024, 12:10:00 AM
        var metadataItemCount = 0
        val metadataContentView =
            metadataView.findViewById<LinearLayout>(R.id.peek_metadata_content)

        metadataView.accept(docInfo)
        for (i in 0 until metadataContentView.childCount) {
            val child = metadataContentView.getChildAt(i)
            if (child is MetadataItemView) {
                ++metadataItemCount
                val titleView = child.findViewById<TextView>(R.id.peek_item_title)
                val valueView = child.findViewById<TextView>(R.id.peek_item_value)
                when (titleView.text) {
                    context.resources.getString(R.string.peek_metadata_size) ->
                        assertEquals("757 kB", valueView.text)
                    context.resources.getString(R.string.peek_metadata_type) ->
                        assertEquals("JPG image", valueView.text)
                    context.resources.getString(R.string.peek_metadata_date_modified) ->
                        assertTrue(valueView.text.contains("September"))
                    else -> error("Unexpected metadata item title: ${titleView.text}")
                }
            }
        }
        assertEquals(metadataItemCount, 3)
    }

    @Test
    fun testAcceptInvalidMetadata() {
        val metadataView = MetadataView(context, PeekViewModel())
        val docInfo = DocumentInfo()
        docInfo.displayName = ""
        docInfo.mimeType = "application/jpeg"
        docInfo.size = -1L
        docInfo.lastModified = -1L
        var metadataItemCount = 0
        val metadataContentView =
            metadataView.findViewById<LinearLayout>(R.id.peek_metadata_content)

        metadataView.accept(docInfo)
        for (i in 0 until metadataContentView.childCount) {
            val child = metadataContentView.getChildAt(i)
            if (child is MetadataItemView) {
                ++metadataItemCount
                val titleView = child.findViewById<TextView>(R.id.peek_item_title)
                val valueView = child.findViewById<TextView>(R.id.peek_item_value)
                when (titleView.text) {
                    context.resources.getString(R.string.peek_metadata_size) ->
                        assertEquals("-1 B", valueView.text)
                    context.resources.getString(R.string.peek_metadata_type) ->
                        assertEquals("File", valueView.text)
                    context.resources.getString(R.string.peek_metadata_date_modified) ->
                        assertFalse(valueView.text.isEmpty()) // Interpreted as 1ms before 1970.
                    else -> error("Unexpected metadata item title: ${titleView.text}")
                }
            }
        }
        assertEquals(metadataItemCount, 3)
    }

    @Test
    fun testClearMetadata() {
        val metadataView = MetadataView(context, PeekViewModel())
        val docInfo = DocumentInfo()
        docInfo.displayName = "IMG_1234.jpg"
        docInfo.mimeType = "image/jpeg"
        docInfo.size = 757 * 1000L
        docInfo.lastModified = 1726359000000L
        var metadataItemCount = 0
        val metadataContentView =
            metadataView.findViewById<LinearLayout>(R.id.peek_metadata_content)

        metadataView.accept(docInfo)
        metadataView.clear()
        for (i in 0 until metadataContentView.childCount) {
            val child = metadataContentView.getChildAt(i)
            if (child is MetadataItemView) {
                ++metadataItemCount
                val titleView = child.findViewById<TextView>(R.id.peek_item_title)
                val valueView = child.findViewById<TextView>(R.id.peek_item_value)
                when (titleView.text) {
                    context.resources.getString(R.string.peek_metadata_size) ->
                        assertTrue(valueView.text.isEmpty())
                    context.resources.getString(R.string.peek_metadata_type) ->
                        assertTrue(valueView.text.isEmpty())
                    context.resources.getString(R.string.peek_metadata_date_modified) ->
                        assertTrue(valueView.text.isEmpty())
                    else -> error("Unexpected metadata item title: ${titleView.text}")
                }
            }
        }
        assertEquals(metadataItemCount, 3)
    }
}
