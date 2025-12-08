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

import android.app.ActivityOptions
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Intent
import android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT
import android.content.res.Resources
import android.graphics.Rect
import android.os.RemoteException
import android.platform.test.annotations.DesktopTest
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract
import android.view.Display
import androidx.media3.ui.R as ExoPlayerR
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import com.android.documentsui.ActivityTestJunit4
import com.android.documentsui.R
import com.android.documentsui.TestUtils.Companion.dpToPx
import com.android.documentsui.TestUtils.Companion.pxToDp
import com.android.documentsui.bots.PeekBot
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.rules.TestFilesRule
import java.io.IOException
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import kotlin.math.roundToInt
import org.hamcrest.CoreMatchers.allOf
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@EnableFlags(Flags.FLAG_USE_MATERIAL3)
// TODO(b/433858983): Change to EnableFlags once peek is overridable in FlagUtils.
@RequiresFlagsEnabled(Flags.FLAG_USE_PEEK_PREVIEW_RO)
class PeekUiTest : ActivityTestJunit4<FilesActivity?>() {
    @get:Rule val setFlags = OverrideFlagsRule()

    // TODO(b/433858983): Remove CheckFlagsRule once peek is overridable in FlagUtils.
    @get:Rule val checkFlags = DeviceFlagsValueProvider.createCheckFlagsRule()

    @Suppress("ktlint:standard:comment-wrapping")
    @get:Rule
    val testFilesRule: TestFilesRule = TestFilesRule(/* skipCreation= */ true)

    private lateinit var peekBot: PeekBot

    private fun relaunchActivityWithBounds(pxWidth: Int, pxHeight: Int) {
        // Check assumption before launching the activity.
        assumeMinWindowSizeAndFreeFormWindowFeature(pxWidth, pxHeight)

        mActivityScenario!!.close()

        val intent = Intent(context, FilesActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (this.initialRoot != null) {
            intent.setAction(Intent.ACTION_VIEW)
            intent.setDataAndType(
                this.initialRoot!!.uri,
                DocumentsContract.Root.MIME_TYPE_ITEM
            )
        }
        val displayMetrics = Resources.getSystem().displayMetrics
        val options = ActivityOptions.makeBasic()
        options.launchWindowingMode = WINDOWING_MODE_FREEFORM
        options.setLaunchBounds(
            Rect(
                0,
                0,
                dpToPx(pxWidth.toFloat(), displayMetrics).roundToInt(),
                dpToPx(pxHeight.toFloat(), displayMetrics).roundToInt(),
            )
        )
        options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
        mActivityScenario = ActivityScenario.launch(intent, options.toBundle())
    }

    /**
     * Make sure that the test device meets the minimum window size and have freeform window
     * feature.
     */
    fun assumeMinWindowSizeAndFreeFormWindowFeature(pxWidth: Int, pxHeight: Int) {
        assumeTrue(
            "Skipping test: test device doesn't support FreeForm window.",
            context!!.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT),
        )
        val displayMetrics = Resources.getSystem().displayMetrics
        assumeTrue(
            "Skipping test: test device display size is too small to support provided " +
                    "dimensions: ${pxWidth}x$pxHeight px.",
            pxToDp(displayMetrics.widthPixels.toFloat(), displayMetrics) >= pxWidth &&
                pxToDp(displayMetrics.heightPixels.toFloat(), displayMetrics) >= pxHeight,
        )
    }

    @Before
    fun setUpTest() {
        peekBot = PeekBot(device!!, context!!, TIMEOUT)
        initFiles()
    }

    @Throws(RemoteException::class, IOException::class)
    fun initFiles() {
        createFile("images/sample.jpg", "image/jpeg", "image.jpg")
        createFile("images/sample.svg", "image/svg+xml", "image.svg")
        createFile("videos/sample.webm", "video/webm", "sample.webm")
        createFile("videos/invalid.mp4", "video/mp4", "invalid.mp4")
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

    @DesktopTest(cujs = ["b/434068614"])
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

    @Test
    @Throws(Exception::class)
    fun testVideoPreview() {
        val playerContentFrameMatcher =
            allOf(
                withId(ExoPlayerR.id.exo_content_frame),
                isDescendantOfA(withId(R.id.peek_container))
            )

        // Check that the preview screen shows for "sample.webm".
        showAndCheckPreview("sample.webm")
        onView(playerContentFrameMatcher).check(matches(isDisplayed()))
        onView(withContentDescription("No preview available")).check(doesNotExist())
        peekBot.hide()

        // Invalid.mp4 does not contain video contents, check that "no preview" shows.
        showAndCheckPreview("invalid.mp4")
        onView(withContentDescription("No preview available")).check(matches(isDisplayed()))
        onView(playerContentFrameMatcher).check(doesNotExist())
        peekBot.hide()
    }

    @Test
    @Throws(Exception::class)
    fun testLargeScreenMetadataSheet() {
        showAndCheckPreview("file0.log")

        // The metadata sheet is expanded by default. Check the metadata sheet state before and
        // after recreating the activity.
        peekBot.validateCoplanarMetadataSheetState(true)
        mActivityScenario!!.recreate()
        peekBot.validateCoplanarMetadataSheetState(true)

        // Check the metadata sheet state after clicking the info toggle button, before and after
        // hiding Peek, recreating the activity, and showing peek again.
        peekBot.toggleMetadataSheet()
        peekBot.validateCoplanarMetadataSheetState(false)
        peekBot.hide()
        mActivityScenario!!.recreate()
        showAndCheckPreview("image.jpg")
        peekBot.validateCoplanarMetadataSheetState(false)

        // Check the metadata sheet state after restoring the metadata sheet with the toggle button.
        peekBot.toggleMetadataSheet()
        peekBot.validateCoplanarMetadataSheetState(true)
        peekBot.closeCoplanarMetadataSheet()
        peekBot.validateCoplanarMetadataSheetState(false)
    }

    @Test
    @Throws(Exception::class)
    fun testResponsiveMetadataLayout() {
        val largeWindowWidth = 1000
        val mediumWindowWidth = 800
        val smallWindowWidth = 400
        val windowHeight = 700

        // Check the large layout version of the metadata sheet.
        relaunchActivityWithBounds(largeWindowWidth, windowHeight)
        showAndCheckPreview("file0.log")
        peekBot.validateCoplanarMetadataSheetState(true)
        mActivityScenario!!.close()

        // Check the medium layout version of the metadata sheet.
        relaunchActivityWithBounds(mediumWindowWidth, windowHeight)
        bots.directory.selectDocument("image.jpg", 1)
        bots.main.clickActionItem("Get info")
        peekBot.validateModalMetadataSheetStateExpanded()

        // Check the small layout version of the metadata sheet.
        relaunchActivityWithBounds(smallWindowWidth, windowHeight)
        showAndCheckPreview("image.svg")
        peekBot.validateBottomMetadataSheetStateExpanded(true)
        peekBot.toggleMetadataSheet()
        peekBot.validateBottomMetadataSheetStateExpanded(false)
    }
}
