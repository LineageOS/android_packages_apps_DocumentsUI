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

import android.content.Intent
import android.platform.test.annotations.RequiresFlagsEnabled
import android.view.MenuItem
import android.widget.ActionMenuView
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO
import com.android.documentsui.rules.CheckAndForceMaterial3Flag
import com.android.documentsui.services.FileOperationService
import com.android.documentsui.services.FileOperationService.ACTION_PROGRESS
import com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress
import com.android.documentsui.testing.MutableJobProgress
import com.android.documentsui.testing.TestActionHandler
import com.android.documentsui.util.Material3Config.Companion.getRes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@SmallTest
@RequiresFlagsEnabled(FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO)
@RunWith(AndroidJUnit4::class)
class JobPanelControllerTest {
    @get:Rule
    val checkFlags = CheckAndForceMaterial3Flag()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var progressBar: ProgressBar
    private lateinit var badge: ImageView
    private lateinit var menuItem: MenuItem

    private lateinit var controller: JobPanelController
    private var lastId = 0L

    private fun sendProgress(progress: ArrayList<JobProgress>, id: Long = lastId++) {
        var intent = Intent(ACTION_PROGRESS).apply {
            `package` = context.packageName
            putExtra("id", id)
            putParcelableArrayListExtra(EXTRA_PROGRESS, progress)
        }
        controller.onReceive(context, intent)
    }

    @Before
    fun setUp() {
        // The default progress bar only has an indeterminate state, so we need to style it to allow
        // determinate progress.
        progressBar = ProgressBar(
            context,
            null,
            android.R.attr.progressBarStyleHorizontal
        ).apply {
            id = getRes(R.id.job_progress_toolbar_indicator)
        }
        badge = ImageView(context).apply {
            id = getRes(R.id.job_progress_toolbar_badge)
        }
        menuItem = ActionMenuView(context).menu.add("job_panel").apply {
            actionView = FrameLayout(context).apply {
                addView(progressBar)
                addView(badge)
            }
        }

        controller = JobPanelController(context, TestActionHandler(), JobPanelViewModel())
        controller.setMenuItem(menuItem)
    }

    @Test
    fun testSingleJob() {
        assertFalse(menuItem.isVisible())
        assertFalse(menuItem.isEnabled())

        val progress = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_STARTED,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 0,
            requiredBytes = 10,
            msRemaining = -1
        )
        sendProgress(arrayListOf(progress.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(0, progressBar.progress)

        progress.apply {
            state = Job.STATE_SET_UP
            msg = "Job in progress"
            currentBytes = 4
        }
        sendProgress(arrayListOf(progress.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(40, progressBar.progress)

        progress.apply {
            state = Job.STATE_COMPLETED
            msg = "Job completed"
            currentBytes = 10
        }
        sendProgress(arrayListOf(progress.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(100, progressBar.progress)
    }

    @Test
    fun testMultipleJobs() {
        assertFalse(menuItem.isVisible())
        assertFalse(menuItem.isEnabled())

        val progress1 = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_MOVE,
            state = Job.STATE_STARTED,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 0,
            requiredBytes = 10,
            msRemaining = -1
        )
        val progress2 = MutableJobProgress(
            id = "jobId2",
            operationType = FileOperationService.OPERATION_DELETE,
            state = Job.STATE_STARTED,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 0,
            requiredBytes = 50,
            msRemaining = -1
        )
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(0, progressBar.progress)

        progress1.apply {
            state = Job.STATE_SET_UP
            msg = "Job in progress"
            currentBytes = 4
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(20, progressBar.progress)

        progress1.apply {
            state = Job.STATE_COMPLETED
            msg = "Job completed"
            currentBytes = 10
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(50, progressBar.progress)

        progress2.apply {
            state = Job.STATE_SET_UP
            msg = "Job in progress"
            currentBytes = 30
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(80, progressBar.progress)

        progress2.apply {
            state = Job.STATE_COMPLETED
            msg = "Job completed"
            currentBytes = 40
        }
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(menuItem.isEnabled())
        assertEquals(100, progressBar.progress)
    }

    @Test
    fun testIndeterminateJobs() {
        val indeterminate = MutableJobProgress(
            id = "indeterminate",
            operationType = FileOperationService.OPERATION_MOVE,
            state = Job.STATE_SET_UP,
            msg = "Job started",
            hasFailures = false,
            currentBytes = -1,
            requiredBytes = -1,
            msRemaining = -1
        )
        val determinate = MutableJobProgress(
            id = "determinate",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 40,
            requiredBytes = 100,
            msRemaining = -1
        )
        sendProgress(arrayListOf(indeterminate.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertTrue(progressBar.isIndeterminate)

        sendProgress(arrayListOf(indeterminate.toJobProgress(), determinate.toJobProgress()))

        assertTrue(menuItem.isVisible())
        assertFalse(progressBar.isIndeterminate)
        assertEquals(20, progressBar.progress)
    }
}
