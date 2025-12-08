/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.documentsui.services

import android.os.Parcel
import android.os.Parcelable
import androidx.core.os.ParcelCompat
import com.android.documentsui.base.DocumentStack

/**
 * Represents the current progress on an individual job owned by the FileOperationService.
 * JobProgress objects are broadcast from the service to activities in order to update the UI.
 */
data class JobProgress @JvmOverloads constructor(
    @JvmField val id: String,
    @JvmField @FileOperationService.OpType val operationType: Int,
    @JvmField @Job.State val state: Int,
    @JvmField val msg: String?,
    @JvmField val hasFailures: Boolean,
    @JvmField val destination: DocumentStack? = null,
    @JvmField val currentBytes: Long = -1,
    @JvmField val requiredBytes: Long = -1,
    @JvmField val msRemaining: Long = -1,
) : Parcelable {

    val isIndeterminate get() =
        state == Job.STATE_SET_UP &&
                (currentBytes == -1L || requiredBytes == -1L || requiredBytes == 0L)

    fun toPercent(): Float = when (state) {
        Job.STATE_CREATED, Job.STATE_STARTED -> 0f
        Job.STATE_COMPLETED, Job.STATE_CANCELED -> 100f
        else -> 100f * currentBytes / requiredBytes
    }

    val isFinal get() = when (state) {
        Job.STATE_COMPLETED, Job.STATE_CANCELED -> true
        else -> false
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.apply {
            writeString(id)
            writeInt(operationType)
            writeInt(state)
            writeString(msg)
            writeBoolean(hasFailures)
            writeParcelable(destination, flags)
            writeLong(currentBytes)
            writeLong(requiredBytes)
            writeLong(msRemaining)
        }
    }

    companion object CREATOR : Parcelable.Creator<JobProgress> {
        override fun createFromParcel(parcel: Parcel): JobProgress {
            return JobProgress(
                parcel.readString()!!,
                parcel.readInt(),
                parcel.readInt(),
                parcel.readString(),
                parcel.readBoolean(),
                ParcelCompat.readParcelable(
                    parcel,
                    DocumentStack::class.java.classLoader,
                    DocumentStack::class.java
                ),
                parcel.readLong(),
                parcel.readLong(),
                parcel.readLong(),
            )
        }

        override fun newArray(size: Int): Array<JobProgress?> {
            return arrayOfNulls(size)
        }
    }
}
