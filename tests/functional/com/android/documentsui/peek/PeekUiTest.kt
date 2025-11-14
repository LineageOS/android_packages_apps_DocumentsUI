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

import android.os.RemoteException
import android.platform.test.annotations.RequiresFlagsEnabled
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.bots.PeekBot
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.CheckAndForceMaterial3Flag
import com.android.documentsui.rules.TestFilesRule
import java.io.IOException
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@RequiresFlagsEnabled(Flags.FLAG_USE_MATERIAL3, Flags.FLAG_USE_PEEK_PREVIEW_RO)
class PeekUiTest : ActivityTestJunit4<FilesActivity?>() {
    @get:Rule val checkFlags = CheckAndForceMaterial3Flag()

    @Suppress("ktlint:standard:comment-wrapping")
    @get:Rule
    val testFilesRule: TestFilesRule = TestFilesRule(/* skipCreation= */ true)

    private lateinit var peekBot: PeekBot

    @Before
    fun setUpTest() {
        peekBot = PeekBot(device!!, context!!, TIMEOUT)
        initFiles()
    }

    @Throws(RemoteException::class, IOException::class)
    fun initFiles() {
        createFile("images/sample.jpg", "image/jpeg", "image.jpg")
        createFile("images/sample.svg", "image/svg+xml", "image.svg")
        createFile("documents/sample.log", "text/plain", "file0.log")
    }

    private fun createFile(sourcePath: String, mimeType: String, fileName: String) {
        val file = testFilesRule.docsHelper.createDocument(rootDir0, mimeType, fileName)
        val assetManager = InstrumentationRegistry.getInstrumentation().context.assets
        assetManager.open(sourcePath).use { inputStream ->
            testFilesRule.docsHelper.writeDocument(file, inputStream.readAllBytes())
        }
    }

    private fun showAndCheckPreview(fileName: String) {
        peekBot.assertPeekHidden()
        bots.directory.selectDocument(fileName, 1)
        bots.main.clickActionItem("Get info")
        checkPreviewActive(fileName)
    }

    private fun checkPreviewActive(fileName: String) {
        peekBot.assertPeekActive()
        peekBot.assertHasTitle(fileName)
    }

    @Test
    @Throws(Exception::class)
    fun testSequentialFilePreview() {
        showAndCheckPreview("image.jpg")
        peekBot.hide()
        showAndCheckPreview("file0.log")
    }

    @Test
    @Throws(Exception::class)
    fun testFileCantBeSelectedDuringFilePreview() {
        // Selecting a document should show the "1 selected" label.
        bots.directory.selectDocument("file0.log", 1)
        // Show preview.
        bots.main.clickActionItem("Get info")
        checkPreviewActive("file0.log")
        // When the preview is shown, the selection is still technically possible, but the scrim
        // intercepts click events. The "1 selected" label shouldn't show.
        val selectionHotspot: UiObject2 = bots.directory.findSelectionHotspot("file0.log")
        assertNotNull(selectionHotspot)
        selectionHotspot.click()
        device!!.waitForIdle()
        assertNull(device!!.findObject(By.text("1 selected")))
    }

    @Test
    @Throws(Exception::class)
    fun testPeekRestorationOnConfigurationChange() {
        showAndCheckPreview("image.jpg")

        // Recreate the activity to simulate a configuration change (window resize, for example),
        // and ensure that the preview is restored.
        mActivityScenario!!.recreate()
        checkPreviewActive("image.jpg")

        peekBot.hide()
        // Ensure that the Peek overlay isn't showing when the activity gets recreated after the
        // overlay has been hidden.
        mActivityScenario!!.recreate()
        peekBot.assertPeekHidden()

        bots.directory.selectDocument("file0.log", 1)
        bots.main.clickActionItem("Get info")
        checkPreviewActive("file0.log")
        // Check Peek's contents when restoring a different preview.
        mActivityScenario!!.recreate()
        checkPreviewActive("file0.log")
    }

    @Test
    @Throws(Exception::class)
    fun testNoPreview() {
        showAndCheckPreview("file0.log")

        // Use the "No preview available" content description to ensure that the "No preview" shape
        // is showing.
        onView(withContentDescription("No preview available")).check(matches(isDisplayed()))
    }

    @Test
    @Throws(Exception::class)
    fun testMetadataSheet() {
        bots.directory.selectDocument("file0.log", 1)
        bots.main.clickActionItem("Get info")

        // Check the metadata sheet state before and after recreating the activity.
        peekBot.validateMetadataSheetState(true)
        mActivityScenario!!.recreate()
        peekBot.validateMetadataSheetState(true)
    }

    @Test
    @Throws(Exception::class)
    fun testImagePreview() {
        // Check that the preview screen shows for "image.jpg"
        showAndCheckPreview("image.jpg")
        onView(withContentDescription("No preview available")).check(doesNotExist())
        onView(withContentDescription("Image preview of image.jpg")).check(matches(isDisplayed()))
        peekBot.hide()

        // SVG files are not handled by ImageViews, check that the "no preview" fallback screen
        // shows instead.
        showAndCheckPreview("image.svg")
        onView(withContentDescription("No preview available")).check(matches(isDisplayed()))
        onView(withContentDescription("Image preview of image.svg")).check(doesNotExist())
    }
}
