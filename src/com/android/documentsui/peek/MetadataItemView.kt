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
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import com.android.documentsui.R

class MetadataItemView(context: Context, title: String, layoutType: LayoutType) :
    FrameLayout(context) {
    enum class LayoutType {
        TOP_CARD,
        MIDDLE_CARD,
        BOTTOM_CARD
    }

    init {
        @Suppress("ktlint:standard:comment-wrapping")
        LayoutInflater.from(context).inflate(R.layout.peek_metadata_item_layout, /* root= */ this)

        layoutParams =
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = resources.getDimensionPixelSize(R.dimen.size_extra_small_1)
            }
        background =
            when (layoutType) {
                LayoutType.TOP_CARD ->
                    getDrawable(context, R.drawable.peek_metadata_item_top_card_background)

                LayoutType.MIDDLE_CARD ->
                    getDrawable(context, R.drawable.peek_metadata_item_middle_card_background)

                LayoutType.BOTTOM_CARD ->
                    getDrawable(context, R.drawable.peek_metadata_item_bottom_card_background)
            }

        findViewById<TextView>(R.id.peek_item_title)?.apply { text = title }
    }

    fun setValue(value: String) {
        findViewById<TextView>(R.id.peek_item_value)?.apply { text = value }
    }
}
