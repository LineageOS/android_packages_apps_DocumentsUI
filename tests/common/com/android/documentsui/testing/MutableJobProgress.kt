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

import com.android.documentsui.base.DocumentStack
import com.android.documentsui.services.FileOperationService
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress

data class MutableJobProgress(
    var id: String,
    @FileOperationService.OpType val operationType: Int,
    @Job.State var state: Int,
    var msg: String?,
    var hasFailures: Boolean,
    var destination: DocumentStack? = null,
    var currentBytes: Long = -1,
    var requiredBytes: Long = -1,
    var msRemaining: Long = -1,
) {
    fun toJobProgress() =
        JobProgress(
            id,
            operationType,
            state,
            msg,
            hasFailures,
            destination,
            currentBytes,
            requiredBytes,
            msRemaining
        )
}
