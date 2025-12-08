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

import android.platform.test.annotations.EnableFlags
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.android.documentsui.JobPanelViewModel.MenuIconState
import com.android.documentsui.JobPanelViewModel.ProgressViewModel
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.FileOperationService
import com.android.documentsui.services.Job
import com.android.documentsui.testing.MutableJobProgress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun List<MutableJobProgress>.toJobProgressList() = map { item -> item.toJobProgress() }

private fun List<MutableJobProgress>.withExpandStates(vararg expandStates: Boolean):
        List<ProgressViewModel> =
    toJobProgressList().zip(expandStates.asList(), ::ProgressViewModel)

@SmallTest
@EnableFlags(FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO)
@RunWith(AndroidJUnit4::class)
class JobPanelViewModelTest {
    @get:Rule
    val setFlags = OverrideFlagsRule()

    @Test
    fun testListModifications() {
        val viewModel = JobPanelViewModel()

        val progress1 = MutableJobProgress(
            id = "Job1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_CREATED,
            msg = "Job1",
            hasFailures = false,
        )
        val progress2 = MutableJobProgress(
            id = "Job2",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_CREATED,
            msg = "Job2",
            hasFailures = false,
        )

        viewModel.updateProgress(listOf(progress1, progress2).toJobProgressList())
        assertEquals(
            listOf(progress1, progress2)
                .withExpandStates(false, false),
            ArrayList(viewModel.currentJobs.values)
        )

        val progress3 = MutableJobProgress(
            id = "Job3",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_STARTED,
            msg = "Job3",
            hasFailures = false,
        )

        val progress4 = MutableJobProgress(
            id = "Job4",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job4",
            hasFailures = false,
            currentBytes = 50,
            requiredBytes = 100,
            msRemaining = 4000
        )

        viewModel.toggleExpanded("Job2")
        viewModel
            .updateProgress(listOf(progress1, progress2, progress3, progress4).toJobProgressList())
        assertEquals(
            listOf(progress1, progress2, progress3, progress4)
                .withExpandStates(false, true, false, false),
            ArrayList(viewModel.currentJobs.values)
        )

        progress1.state = Job.STATE_COMPLETED
        progress2.state = Job.STATE_COMPLETED
        progress2.hasFailures = true

        viewModel.updateProgress(listOf(progress1, progress2, progress4).toJobProgressList())
        assertEquals(
            listOf(progress1, progress2, progress4)
                .withExpandStates(false, true, false),
            ArrayList(viewModel.currentJobs.values)
        )

        progress4.state = Job.STATE_CANCELED
        viewModel.updateProgress(listOf(progress4).toJobProgressList())
        // Progresses 1, 2 and 4 should be kept as they are in final states.
        assertEquals(
            listOf(progress1, progress2, progress4)
                .withExpandStates(false, true, false),
            ArrayList(viewModel.currentJobs.values)
        )

        viewModel.updateProgress(emptyList())
        assertEquals(
            listOf(progress1, progress2, progress4)
                .withExpandStates(false, true, false),
            ArrayList(viewModel.currentJobs.values)
        )

        viewModel.dismissProgress("Job1")
        assertEquals(
            listOf(progress2, progress4)
                .withExpandStates(true, false),
            ArrayList(viewModel.currentJobs.values)
        )

        viewModel.dismissProgress("Job4")
        assertEquals(
            listOf(progress2).withExpandStates(true),
            ArrayList(viewModel.currentJobs.values)
        )
    }

    @Test
    fun testDismissNonExistentItem() {
        val viewModel = JobPanelViewModel()

        viewModel.dismissProgress("Job1")
        assertTrue(viewModel.currentJobs.isEmpty())

        val progress1 = MutableJobProgress(
            id = "Job1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_CREATED,
            msg = "Job1",
            hasFailures = false,
        )

        viewModel.updateProgress(listOf(progress1).toJobProgressList())
        viewModel.dismissProgress("Job2")
        assertEquals(
            listOf(progress1).withExpandStates(false),
            ArrayList(viewModel.currentJobs.values)
        )
    }

    @Test
    fun testExpandNonExistentItem() {
        val viewModel = JobPanelViewModel()

        viewModel.toggleExpanded("Job1")
        assertTrue(viewModel.currentJobs.isEmpty())

        val progress1 = MutableJobProgress(
            id = "Job1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_CREATED,
            msg = "Job1",
            hasFailures = false,
        )

        viewModel.updateProgress(listOf(progress1).toJobProgressList())
        viewModel.toggleExpanded("Job2")
        assertEquals(
            listOf(progress1).withExpandStates(false),
            ArrayList(viewModel.currentJobs.values)
        )
    }

    @Test
    fun testHasFailures() {
        val viewModel = JobPanelViewModel()

        val inProgress = MutableJobProgress(
            id = "in_progress_job",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job in progress",
            hasFailures = false,
            currentBytes = 40,
            requiredBytes = 100,
        )

        val failed = MutableJobProgress(
            id = "failed_job",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_COMPLETED,
            msg = "Job failed",
            hasFailures = true,
        )

        assertEquals(MenuIconState.INVISIBLE, viewModel.getMenuState())

        viewModel.updateProgress(listOf(failed).toJobProgressList())
        assertEquals(
            MenuIconState.VISIBLE(totalProgress = 100, hasFailures = true),
            viewModel.getMenuState()
        )

        viewModel.updateProgress(listOf(inProgress).toJobProgressList())
        assertEquals(
            MenuIconState.VISIBLE(totalProgress = 70, hasFailures = true),
            viewModel.getMenuState()
        )

        viewModel.dismissProgress(failed.id)
        assertEquals(
            MenuIconState.VISIBLE(totalProgress = 40, hasFailures = false),
            viewModel.getMenuState()
        )

        inProgress.hasFailures = true
        viewModel.updateProgress(listOf(inProgress).toJobProgressList())
        assertEquals(
            MenuIconState.VISIBLE(totalProgress = 40, hasFailures = true),
            viewModel.getMenuState()
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun testDismissCompleted() = runTest {
        val viewModel = JobPanelViewModel()
        val inProgress = MutableJobProgress(
            id = "in_progress_job",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job in progress",
            hasFailures = false,
            currentBytes = 40,
            requiredBytes = 100,
        )

        val succeeded = MutableJobProgress(
            id = "succeeded_job",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_COMPLETED,
            msg = "Job succeeded",
            hasFailures = false,
        )

        val failed = MutableJobProgress(
            id = "failed_job",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_COMPLETED,
            msg = "Job failed",
            hasFailures = true,
        )

        // Launch coroutines to collect the flow values in the background.
        // UnconfinedTestDispatcher is used to ensure that the coroutines are executed immediately
        // without waiting for the test scheduler.
        var updates = 0
        val menuIconUpdates = ArrayList<MenuIconState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            launch {
                viewModel.jobUpdateEvent.collect { updates++ }
            }
            launch {
                viewModel.menuIconState.collect { state -> menuIconUpdates.add(state) }
            }
        }

        viewModel.updateProgress(listOf(inProgress, succeeded, failed).toJobProgressList())
        assertEquals(1, updates)

        // Two completed jobs and one in progress job at 40%, so the total progress is 80%.
        assertEquals(MenuIconState.VISIBLE(80, hasFailures = true), menuIconUpdates.last())

        viewModel.dismissCompleted()
        assertEquals(2, updates)

        // Now only the 40% job is tracked, so total progress is 40%.
        assertEquals(MenuIconState.VISIBLE(40, hasFailures = false), menuIconUpdates.last())

        // dismissCompleted() should only remove the completed jobs.
        assertEquals(
            listOf(inProgress).withExpandStates(false),
            ArrayList(viewModel.currentJobs.values)
        )

        // Change the in progress job to be completed and check that it would get dismissed by
        // dismissCompleted().
        inProgress.state = Job.STATE_COMPLETED
        viewModel.updateProgress(listOf(inProgress).toJobProgressList())
        viewModel.dismissCompleted()

        // One update for updateProgress(), and one more for dismissCompleted().
        assertEquals(4, updates)

        // There are no more jobs, so the icon should be invisible.
        assertEquals(MenuIconState.INVISIBLE, menuIconUpdates.last())
    }
}
