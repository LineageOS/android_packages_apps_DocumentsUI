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

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.android.documentsui.base.DocumentInfo

/** Manages the UI state for Peek. */
class PeekViewModel : ViewModel() {
    // Whether the Peek overlay is active or not.
    private val _overlayActive: MutableLiveData<Boolean> = MutableLiveData(false)
    val overlayActive: LiveData<Boolean> = _overlayActive

    // Peek fragment's state.
    private val _docInfo: MutableLiveData<DocumentInfo> = MutableLiveData(null)
    val docInfo: LiveData<DocumentInfo> = _docInfo

    fun clear() {
        _overlayActive.value = false
        _docInfo.value = null
    }

    /**
     * A DocumentInfo is set when a new file has been selected for preview. Doing so is always
     * associated with the overlay being active.
     */
    fun setDocInfoAndActivateOverlay(docInfo: DocumentInfo) {
        // OverlayActive has to be set first, since its value is relevant for the listener of
        // docInfo.
        _overlayActive.value = true
        _docInfo.value = docInfo
    }
}
