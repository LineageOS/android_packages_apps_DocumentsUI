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

import android.os.Parcelable
import android.util.Log
import androidx.lifecycle.ViewModel
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress

/**
 * Manages the UI state for the [JobPanelController].
 */
class JobPanelViewModel : ViewModel() {
    companion object {
        private const val TAG = "JobPanelViewModel"
    }

    /**
     * The UI state representation of a single progress item.
     *
     * @property jobProgress The progress shown by this item.
     * @property expanded Whether the UI card for this item is expanded or not.
     */
    data class ProgressViewModel(val jobProgress: JobProgress, val expanded: Boolean = false)

    /**
     * The UI state representation of the toolbar progress icon.
     */
    sealed class MenuIconState {
        abstract val hasFailures: Boolean
        data object INVISIBLE : MenuIconState() {
            override val hasFailures get() = false
        }
        data class INDETERMINATE(override val hasFailures: Boolean) : MenuIconState()
        data class VISIBLE(val totalProgress: Int, override val hasFailures: Boolean) :
            MenuIconState()
    }

    /** List of jobs currently tracked. */
    private val _currentJobs = LinkedHashMap<String, ProgressViewModel>()
    val currentJobs: Map<String, ProgressViewModel> get() = _currentJobs
    var listState: Parcelable? = null

    /**
     * Gets the state of the toolbar progress icon based off the current jobs tracked.
     */
    fun getMenuState(): MenuIconState {
        var currentPercent = 0f
        var allIndeterminate = true
        var hasFailures = false

        for ((jobProgress, _) in currentJobs.values) {
            if (!jobProgress.isIndeterminate) {
                allIndeterminate = false
                currentPercent += jobProgress.toPercent()
            }
            if (jobProgress.hasFailures) {
                hasFailures = true
            }
        }

        var state: MenuIconState
        if (currentJobs.isEmpty()) {
            state = MenuIconState.INVISIBLE
        } else if (allIndeterminate) {
            state = MenuIconState.INDETERMINATE(hasFailures)
        } else {
            state = MenuIconState.VISIBLE((currentPercent / currentJobs.size).toInt(), hasFailures)
        }
        return state
    }

    /**
     * Updates the list of progresses managed by this class. This function will add and update all
     * given items, while removing any queued/in progress items not in [progresses]. Completed items
     * are kept.
     */
    fun updateProgress(progresses: List<JobProgress>) {
        val seen = hashSetOf<String>()
        for (jobProgress in progresses) {
            if (DEBUG) Log.d(TAG, "Received $jobProgress")
            seen.add(jobProgress.id)
            if (jobProgress.state == Job.STATE_CANCELED) {
                _currentJobs.remove(jobProgress.id)
            } else {
                _currentJobs.merge(jobProgress.id, ProgressViewModel(jobProgress)) { old, new ->
                    ProgressViewModel(new.jobProgress, old.expanded)
                }
            }
        }
        _currentJobs.entries.removeAll { (id, model) -> !model.jobProgress.isFinal && id !in seen }
    }

    /**
     * Removes a specific progress item from the list managed by this class.
     */
    fun dismissProgress(id: String) {
        _currentJobs.remove(id)
    }

    /**
     * Toggles the expanded state of a specific progress item.
     */
    fun toggleExpanded(id: String) {
        _currentJobs.computeIfPresent(id) { _, (jobProgress, expanded) ->
            ProgressViewModel(jobProgress, !expanded)
        }
    }
}
