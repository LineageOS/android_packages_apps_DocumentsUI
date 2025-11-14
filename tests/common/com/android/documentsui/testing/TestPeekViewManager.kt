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
package com.android.documentsui.testing

import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.peek.PeekViewManager
import com.android.documentsui.peek.PeekViewModel
import org.mockito.Mockito.mock

class TestPeekViewManager() :
    PeekViewManager(
        PeekViewModel(),
        mock(FrameLayout::class.java),
        mock(FragmentManager::class.java)
    ) {

    val peekDocument = TestEventListener<DocumentInfo>()

    override fun initialize() {}

    override fun peekDocument(doc: DocumentInfo) {
        peekDocument.accept(doc)
    }
}
