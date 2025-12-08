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
package com.android.documentsui

import android.content.Context
import com.bumptech.glide.GlideBuilder
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.load.engine.cache.LruResourceCache
import com.bumptech.glide.module.AppGlideModule

/**
 * Custom Glide module for DocumentsUI. Generates {@link GlideApp}.
 */
@GlideModule
class DocumentsUiGlideModule : AppGlideModule() {
    companion object {
        // Memory cache size for DocumentsUI: 50MB.
        const val MEMORY_CACHE_SIZE_BYTES: Long = 50 * 1024 * 1024
    }

    override fun applyOptions(context: Context, builder: GlideBuilder) {
        builder.setMemoryCache(LruResourceCache(MEMORY_CACHE_SIZE_BYTES))
    }

    override fun isManifestParsingEnabled(): Boolean {
        return false
    }
}
