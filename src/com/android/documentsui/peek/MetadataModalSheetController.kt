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
import com.android.documentsui.R
import com.google.android.material.sidesheet.SideSheetDialog

class MetadataModalSheetController(context: Context, viewModel: PeekViewModel) :
    MetadataSheetController(context, viewModel) {
    private val sheetDialog = SideSheetDialog(context, R.style.PeekMetadataSheetDialogTheme)

    init {
        sheetDialog.setContentView(metadataView)
        sheetDialog.setOnShowListener { dialog -> viewModel.toggleMetadataSheet(true) }
        sheetDialog.setOnDismissListener { dialog -> viewModel.toggleMetadataSheet(false) }
    }

    override fun show() {
        sheetDialog.show()
    }

    override fun hide() {
        sheetDialog.hide()
    }

    override fun onDestroyView() {
        sheetDialog.dismiss()
    }
}
