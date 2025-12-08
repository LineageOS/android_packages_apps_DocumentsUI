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
import android.text.format.DateFormat
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.peek.MetadataItemView.LayoutType
import java.util.Locale

/** Custom view component used to display the metadata in Peek. */
class MetadataView(context: Context, private val viewModel: PeekViewModel) : FrameLayout(context) {
    private val sizeView =
        MetadataItemView(
            context,
            context.getString(R.string.peek_metadata_size),
            LayoutType.TOP_CARD
        )
    private val typeView =
        MetadataItemView(
            context,
            context.getString(R.string.peek_metadata_type),
            LayoutType.MIDDLE_CARD
        )
    private val dateModifiedView =
        MetadataItemView(
            context,
            context.getString(R.string.peek_metadata_date_modified),
            LayoutType.BOTTOM_CARD
        )

    init {
        @Suppress("ktlint:standard:comment-wrapping")
        val view =
            LayoutInflater.from(context).inflate(R.layout.peek_metadata_layout, /* root= */ this)
        view.findViewById<View>(R.id.peek_side_sheet_close_button).setOnClickListener {
            viewModel.toggleMetadataSheet(false)
        }
        val metadataContentView = view.findViewById<LinearLayout>(R.id.peek_metadata_content)
        metadataContentView.addView(sizeView)
        metadataContentView.addView(typeView)
        metadataContentView.addView(dateModifiedView)
    }

    private fun formatDate(date: Long): String {
        val formatRes = if (DateFormat.is24HourFormat(context)) {
            R.string.peek_datetime_format_24
        } else {
            R.string.peek_datetime_format_12
        }
        val format: String? = DateFormat.getBestDateTimePattern(
            Locale.getDefault(),
            resources.getString(formatRes)
        )
        return DateFormat.format(format, date).toString()
    }

    private fun formatMimeType(mimeType: String): String {
        val fileTypeLookup = DocumentsApplication.getFileTypeLookup(context)
        return fileTypeLookup.lookup(mimeType) ?: mimeType
    }

    fun accept(docInfo: DocumentInfo) {
        sizeView.setValue(Formatter.formatFileSize(context, docInfo.size))
        typeView.setValue(formatMimeType(docInfo.mimeType))
        dateModifiedView.setValue(formatDate(docInfo.lastModified))
    }

    fun clear() {
        sizeView.setValue("")
        typeView.setValue("")
        dateModifiedView.setValue("")
    }
}
