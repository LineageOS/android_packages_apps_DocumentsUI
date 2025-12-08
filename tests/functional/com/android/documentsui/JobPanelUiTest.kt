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
import android.platform.test.annotations.EnableFlags
import android.view.KeyEvent
import android.view.View
import android.widget.ProgressBar
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.assertion.ViewAssertions.selectedDescendantsMatch
import androidx.test.espresso.matcher.BoundedDiagnosingMatcher
import androidx.test.espresso.matcher.ViewMatchers.hasChildCount
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withChild
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.actions.RelaxedClickAction
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import com.android.documentsui.services.FileOperationService
import com.android.documentsui.services.FileOperationService.ACTION_PROGRESS
import com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress
import com.android.documentsui.testing.MutableJobProgress
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private fun withProgress(expectedProgress: Int): Matcher<View> {
    return object : BoundedDiagnosingMatcher<View, ProgressBar>(ProgressBar::class.java) {
        override fun matchesSafely(view: ProgressBar, mismatchDescription: Description): Boolean {
            if (view.progress == expectedProgress) {
                return true
            } else {
                mismatchDescription.appendText("actual progress was ${view.progress}")
                return false
            }
        }

        override fun describeMoreTo(description: Description) {
            description.appendText("with progress: $expectedProgress")
        }
    }
}

// Helper function to match views inside a certain progress item view.
private fun insideItem(progress: MutableJobProgress) = hasSibling(withText(progress.msg))

@LargeTest
@EnableFlags(FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO)
@RunWith(AndroidJUnit4::class)
class JobPanelUiTest : ActivityTestJunit4<FilesActivity>() {
    @get:Rule
    val setFlags = OverrideFlagsRule()

    @get:Rule
    val testFiles = TestFilesRule()

    private var lastId = 0L

    private fun sendProgress(progresses: ArrayList<JobProgress>, id: Long = lastId++) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var intent = Intent(ACTION_PROGRESS).apply {
            `package` = context.packageName
            putExtra("id", id)
            putParcelableArrayListExtra(EXTRA_PROGRESS, progresses)
        }
        context.sendBroadcast(intent)
    }

    private fun openPanel() {
        assertTrue(bots.main.waitForJobProgressToolbarIconToAppear())
        onView(withId(R.id.job_progress_toolbar_indicator))
            .check(matches(isDisplayed()))
            .perform(click())
        onView(withId(R.id.job_progress_panel_title)).check(matches(isDisplayed()))
    }

    @Test
    fun testInProgressItems() {
        onView(withId(R.id.job_progress_toolbar_indicator)).check(doesNotExist())
        onView(withId(R.id.job_progress_panel_title)).check(doesNotExist())

        val progress = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 4,
            requiredBytes = 10,
            msRemaining = 10000,
        )
        sendProgress(arrayListOf(progress.toJobProgress()))

        assertTrue(bots.main.waitForJobProgressToolbarIconToAppear())
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(40)))

        openPanel()

        val expectedPrimaryStatus = "4 B of 10 B"
        val expectedSecondaryStatus = "10 seconds left"

        onView(withId(R.id.job_progress_item_title)).check(matches(withText("Job started")))
        onView(withId(R.id.job_progress_item_progress)).check(matches(withProgress(40)))
        onView(allOf(withText(expectedPrimaryStatus), isDisplayed())).check(doesNotExist())
        onView(allOf(withText(expectedSecondaryStatus), isDisplayed())).check(doesNotExist())
        onView(withId(R.id.job_progress_item_cancel)).check(matches(not(isDisplayed())))

        onView(withId(R.id.job_progress_item_expand)).perform(click())
        onView(withText(expectedPrimaryStatus)).check(matches(isDisplayed()))
        onView(withText(expectedSecondaryStatus)).check(matches(isDisplayed()))
        onView(withId(R.id.job_progress_item_cancel)).check(matches(isDisplayed()))

        onView(withId(R.id.job_progress_item_expand)).perform(click())
        onView(allOf(withText(expectedPrimaryStatus), isDisplayed())).check(doesNotExist())
        onView(allOf(withText(expectedSecondaryStatus), isDisplayed())).check(doesNotExist())
        onView(withId(R.id.job_progress_item_cancel)).check(matches(not(isDisplayed())))
    }

    @Test
    fun testCompletedItems() {
        val progress1 = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_EXTRACT,
            state = Job.STATE_COMPLETED,
            msg = "Job1 completed",
            hasFailures = false,
        )
        val progress2 = MutableJobProgress(
            id = "jobId2",
            operationType = FileOperationService.OPERATION_MOVE,
            state = Job.STATE_COMPLETED,
            msg = "Job2 completed",
            hasFailures = true,
        )
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        openPanel()

        onView(withChild(withText(progress1.msg)))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_completed),
                isDisplayed()
            ))
            .check(selectedDescendantsMatch(
                withText(R.string.extract_completed),
                isDisplayed()
            ))
        onView(withChild(withText(progress2.msg)))
            .check(selectedDescendantsMatch(
                withText(R.string.move_failed),
                isDisplayed()
            ))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_see_details),
                isDisplayed()
            ))

        // Dismiss the first item.
        onView(allOf(withId(R.id.job_progress_item_expand), insideItem(progress1)))
            .perform(click())
        onView(allOf(withId(R.id.job_progress_item_dismiss), insideItem(progress1)))
            .perform(click())
        onView(withText(progress1.msg)).check(doesNotExist())

        // Dismiss the second item. The panel should disappear.
        onView(allOf(withId(R.id.job_progress_item_expand), insideItem(progress2)))
            .perform(click())
        onView(allOf(withText(R.string.job_progress_item_see_details), isDisplayed()))
            .check(doesNotExist())
        onView(allOf(withId(R.id.job_progress_item_dismiss), insideItem(progress2)))
            .perform(click())
        onView(withId(R.id.job_progress_toolbar_indicator)).check(doesNotExist())
        onView(withId(R.id.job_progress_panel_title)).check(doesNotExist())
    }

    @Test
    fun testJobCanceled() {
        val progress1 = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job1",
            hasFailures = false,
            currentBytes = 10,
            requiredBytes = 20,
            msRemaining = 1000,
        )
        val progress2 = MutableJobProgress(
            id = "jobId2",
            operationType = FileOperationService.OPERATION_EXTRACT,
            state = Job.STATE_CREATED,
            msg = "Job2",
            hasFailures = false,
        )
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))

        assertTrue(bots.main.waitForJobProgressToolbarIconToAppear())

        // Overall progress should be 25%.
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(25)))

        openPanel()

        // Check both items are displayed.
        onView(withText(progress1.msg)).check(matches(isDisplayed()))
        onView(withText(progress2.msg)).check(matches(isDisplayed()))

        // Cancel the first job.
        progress1.state = Job.STATE_CANCELED
        sendProgress(arrayListOf(progress1.toJobProgress(), progress2.toJobProgress()))
        onView(withChild(withText(progress1.msg)))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_canceled),
                isDisplayed()
            ))
        onView(withText(progress2.msg)).check(matches(isDisplayed()))

        // Overall progress should be 50% as the first job is finished. We need to close the popup
        // panel first in order to check the menu item behind.
        Espresso.pressBack()
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(50)))
        openPanel()

        // Cancel the second job.
        progress2.state = Job.STATE_CANCELED
        sendProgress(arrayListOf(progress2.toJobProgress()))
        onView(withChild(withText(progress1.msg)))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_canceled),
                isDisplayed()
            ))
        onView(withChild(withText(progress2.msg)))
            .check(selectedDescendantsMatch(
                withText(R.string.job_progress_item_canceled),
                isDisplayed()
            ))
        Espresso.pressBack()
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(100)))
    }

    @Test
    fun testPersistsOnRecreate() {
        val progress = MutableJobProgress(
            id = "jobId1",
            operationType = FileOperationService.OPERATION_COPY,
            state = Job.STATE_SET_UP,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 4,
            requiredBytes = 10,
            msRemaining = 10000,
        )
        sendProgress(arrayListOf(progress.toJobProgress()))
        assertTrue(bots.main.waitForJobProgressToolbarIconToAppear())
        mActivityScenario!!.recreate()

        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(40)))

        openPanel()
        onView(withId(R.id.job_progress_item_progress)).check(matches(withProgress(40)))

        mActivityScenario!!.recreate()

        val progress2 = MutableJobProgress(
            id = "jobId2",
            operationType = FileOperationService.OPERATION_MOVE,
            state = Job.STATE_SET_UP,
            msg = "Job started",
            hasFailures = false,
            currentBytes = 6,
            requiredBytes = 10,
            msRemaining = 10000,
        )
        sendProgress(arrayListOf(progress.toJobProgress(), progress2.toJobProgress()))

        onView(withId(R.id.job_progress_list)).check(matches(hasChildCount(2)))

        // Close the job panel.
        Espresso.pressBack()
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(50)))
    }

    @Test
    fun testShowInFolder() {
        // This test relies on the force_material3 config value being true in the out of process
        // FileOperationService, which we cannot easily force from the test.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(context.resources.getBoolean(R.bool.force_material3))

        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1)
        bots.keyboard.pressKey(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON)

        bots.directory.openDocument(TestFilesRule.DIR_NAME_1)
        bots.keyboard.pressKey(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)

        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1)

        openPanel()
        onView(withId(R.id.job_progress_item_title)).perform(click())
        onView(withId(R.id.job_progress_item_show_in_folder)).perform(click())

        // The panel should have been dismissed when we navigate.
        onView(withId(R.id.job_progress_item_title)).check(doesNotExist())
        bots.breadcrumb.assertItemsPresent(StubProvider.ROOT_0_ID, TestFilesRule.DIR_NAME_1)

        // Try from a different folder.
        Espresso.pressBack()
        openPanel()
        onView(withId(R.id.job_progress_item_show_in_folder)).perform(click())
        bots.breadcrumb.assertItemsPresent(StubProvider.ROOT_0_ID, TestFilesRule.DIR_NAME_1)

        // Try from a different root.
        bots.roots.openRoot(StubProvider.ROOT_1_ID)
        openPanel()
        onView(withId(R.id.job_progress_item_show_in_folder)).perform(click())
        bots.breadcrumb.assertItemsPresent(StubProvider.ROOT_0_ID, TestFilesRule.DIR_NAME_1)
    }

    @Test
    fun testFailedItem() {
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

        sendProgress(arrayListOf(inProgress.toJobProgress()))
        assertTrue(bots.main.waitForJobProgressToolbarIconToAppear())
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(40)))
        onView(allOf(withId(R.id.job_progress_toolbar_badge), isDisplayed())).check(doesNotExist())

        sendProgress(arrayListOf(inProgress.toJobProgress(), failed.toJobProgress()))
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(70)))
        onView(withId(R.id.job_progress_toolbar_badge)).check(matches(isDisplayed()))

        mActivityScenario!!.recreate()
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(70)))
        onView(withId(R.id.job_progress_toolbar_badge)).check(matches(isDisplayed()))

        openPanel()
        onView(withText(R.string.copy_failed)).perform(click())
        onView(allOf(withId(R.id.job_progress_item_dismiss), isDisplayed())).perform(click())
        Espresso.pressBack()
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(40)))
        onView(allOf(withId(R.id.job_progress_toolbar_badge), isDisplayed())).check(doesNotExist())

        inProgress.hasFailures = true
        sendProgress(arrayListOf(inProgress.toJobProgress()))
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(40)))
        onView(withId(R.id.job_progress_toolbar_badge)).check(matches(isDisplayed()))
    }

    @Test
    fun testDismissAll() {
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
        sendProgress(arrayListOf(
            inProgress.toJobProgress(),
            succeeded.toJobProgress(),
            failed.toJobProgress(),
        ))

        assertTrue(bots.main.waitForJobProgressToolbarIconToAppear())

        // There are two jobs completed and one job at 40%, so the total progress is 80%.
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(80)))
        onView(withId(R.id.job_progress_toolbar_badge)).check(matches(isDisplayed()))

        // Click dismiss all. Only the two completed jobs should disappear.
        openPanel()
        onView(withId(R.id.job_progress_panel_dismiss_all)).perform(RelaxedClickAction())
        onView(withText(succeeded.msg)).check(doesNotExist())
        onView(withText(failed.msg)).check(doesNotExist())
        onView(withText(inProgress.msg)).check(matches(isDisplayed()))

        // The total progress should now change to 40% as the two completed jobs are gone.
        Espresso.pressBack()
        onView(withId(R.id.job_progress_toolbar_indicator)).check(matches(withProgress(40)))
        onView(allOf(withId(R.id.job_progress_toolbar_badge), isDisplayed())).check(doesNotExist())

        // Update the in progress job to be completed.
        inProgress.state = Job.STATE_COMPLETED
        sendProgress(arrayListOf(inProgress.toJobProgress()))

        // When all tracked jobs are completed, dismiss all should also close the panel.
        openPanel()
        onView(withId(R.id.job_progress_panel_dismiss_all)).perform(RelaxedClickAction())
        onView(withId(R.id.job_progress_toolbar_indicator)).check(doesNotExist())
        onView(withId(R.id.job_progress_panel_title)).check(doesNotExist())
    }
}
