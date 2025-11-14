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
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo

/** Custom view component used to display the metadata in Peek. */
class MetadataView(context: Context) : FrameLayout(context), PeekFragment.Display {
    init {
        @Suppress("ktlint:standard:comment-wrapping")
        LayoutInflater.from(context).inflate(R.layout.peek_metadata_layout, /* root= */ this)
    }

    override fun accept(doc: DocumentInfo) {}

    override fun clear() {}
}
