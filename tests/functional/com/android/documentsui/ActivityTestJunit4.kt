/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.app.Activity
import android.app.ActivityOptions
import android.app.UiAutomation
import android.app.WindowConfiguration
import android.content.Context
import android.content.Intent
import android.os.RemoteException
import android.provider.DocumentsContract
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.android.documentsui.base.Features
import com.android.documentsui.base.Features.RuntimeFeatures
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.bots.Bots
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.util.FlagUtils.Companion.isDesktopFileHandlingFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isSearchV2Enabled
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isUsePeekPreviewFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isVisualSignalsFlagEnabled
import com.android.documentsui.util.FlagUtils.Companion.isZipNgFlagEnabled
import java.io.IOException
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

/**
 * Provides basic test environment for UI tests:
 * - Launches activity
 * - Creates and gives access to test root directories and test files
 * - Cleans up the test environment
 */
@RunWith(TestRunner::class)
abstract class ActivityTestJunit4<T : Activity?> {
    lateinit var bots: Bots

    @JvmField
    var device: UiDevice? = null

    @JvmField
    var context: Context? = null

    @JvmField
    protected var userId: UserId? = null
    var automation: UiAutomation? = null

    @JvmField
    var features: Features? = null

    /**
     * Returns the root that will be opened within the activity.
     * By default tests are started with one of the test roots.
     * Override the method if you want to open different root on start.
     * @return Root that will be opened. Return null if you want to open activity's default root.
     */
    protected open var initialRoot: RootInfo? = null

    @JvmField
    var rootDir0: RootInfo? = null

    @JvmField
    var rootDir1: RootInfo? = null

    @JvmField
    protected var mDocsHelper: DocumentsProviderHelper? = null

    @JvmField
    protected var mActivityScenario: ActivityScenario<T?>? = null
    private var initialScreenOffTimeoutValue: String? = null
    private var initialSleepTimeoutValue: String? = null

    protected val testingProviderAuthority: String
        /**
         * Returns the authority of the testing provider begin used.
         * By default it's StubProvider's authority.
         * @return Authority of the provider.
         */
        get() = StubProvider.DEFAULT_AUTHORITY

    /**
     * Resolves testing roots.
     */
    @Throws(RemoteException::class)
    protected fun setupTestingRoots() {
        rootDir0 = mDocsHelper!!.getRoot(StubProvider.ROOT_0_ID)
        rootDir1 = mDocsHelper!!.getRoot(StubProvider.ROOT_1_ID)
        this.initialRoot = rootDir0
    }

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // NOTE: Must be the "target" context, else security checks in content provider will fail.
        context = InstrumentationRegistry.getInstrumentation().targetContext
        userId = UserId.DEFAULT_USER
        automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        features = RuntimeFeatures(context!!.getResources(), null)

        bots = Bots(device, automation, context, TIMEOUT)

        Configurator.getInstance().toolType = MotionEvent.TOOL_TYPE_MOUSE

        mDocsHelper = DocumentsProviderHelper(
            userId, this.testingProviderAuthority, context,
            this.testingProviderAuthority
        )

        device!!.setOrientationNatural()
        device!!.pressKeyCode(KeyEvent.KEYCODE_WAKEUP)

        disableScreenOffAndSleepTimeouts()

        setupTestingRoots()
        ActivityTest.closeNonDocsUiWindows(context, device)
        launchActivity()

        logLayout()
        logFeatureFlags()

        // Since at the launch of activity, ROOT_0 and ROOT_1 have no files, drawer will
        // automatically open for phone devices. Espresso register click() as (x, y) MotionEvents,
        // so if a drawer is on top of a file we want to select, it will actually click the drawer.
        // Thus to start a clean state, we always try to close first.
        bots.roots!!.closeDrawer()
    }

    @After
    fun tearDown() {
        device!!.unfreezeRotation()
        restoreScreenOffAndSleepTimeouts()
        mActivityScenario?.close()
    }

    protected open fun launchActivity() {
        val intent = Intent(context, FilesActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        val root = this.initialRoot
        if (root != null) {
            intent.setAction(Intent.ACTION_VIEW)
            intent.setDataAndType(root.uri, DocumentsContract.Root.MIME_TYPE_ITEM)
        }

        // If the TestRunner is running tests with different screen sizes, we need to launch the
        // activity in fullscreen mode so that the activity takes up the full device screen.
        if (System.getProperty("documentsui_fullscreen") != null) {
            Log.d(TAG, "using launchWindowingMode=FULLSCREEN")
            val options = ActivityOptions.makeBasic()
            options.setLaunchWindowingMode(WindowConfiguration.WINDOWING_MODE_FULLSCREEN)
            mActivityScenario = ActivityScenario.launch(intent, options.toBundle())
        } else {
            mActivityScenario = ActivityScenario.launch(intent)
        }
    }

    protected fun setNotificationAccess(enabled: Boolean) {
        mActivityScenario?.onActivity(
            { activity ->
                try {
                    bots.notifications.setNotificationAccess(activity, enabled)
                } catch (e: Exception) {
                    Log.d(TAG, "Cannot set notification access. ", e)
                }
            }
        )
    }

    @Throws(IOException::class)
    private fun disableScreenOffAndSleepTimeouts() {
        initialScreenOffTimeoutValue =
            device!!.executeShellCommand("settings get system screen_off_timeout").trimEnd()
        initialSleepTimeoutValue =
            device!!.executeShellCommand("settings get secure sleep_timeout").trimEnd()
        Log.d(TAG, "initialScreenOffTimeoutValue = $initialScreenOffTimeoutValue")
        Log.d(TAG, "initialSleepTimeoutValue = $initialSleepTimeoutValue")
        device!!.executeShellCommand("settings put system screen_off_timeout -1")
        device!!.executeShellCommand("settings put secure sleep_timeout -1")
    }

    @Throws(IOException::class)
    private fun restoreScreenOffAndSleepTimeouts() {
        requireNotNull(
            initialScreenOffTimeoutValue
        ) { "Require the initial screen off timeout value to be non-null" }
        requireNotNull(
            initialSleepTimeoutValue
        ) { "Require the sleep timeout value to be non-null" }
        try {
            device!!.executeShellCommand(
                "settings put system screen_off_timeout $initialScreenOffTimeoutValue"
            )
            device!!.executeShellCommand(
                "settings put secure sleep_timeout $initialSleepTimeoutValue"
            )
        } finally {
            initialScreenOffTimeoutValue = null
            initialSleepTimeoutValue = null
        }
    }

    private fun logLayout() {
        val layoutType = if (bots.main.inFixedLayout()) {
            "Fixed layout"
        } else if (bots.main.inNavRailLayout()) {
            "Nav rail layout"
        } else if (bots.main.inDrawerLayout()) {
            "Drawer layout"
        } else {
            "Unknown layout (should not happen)"
        }
        Log.d(TAG, "Test is running with layout: $layoutType.")
    }

    private fun logFeatureFlags() {
        Log.d(TAG, "Flag isUseMaterial3FlagEnabled() = ${isUseMaterial3FlagEnabled()}")
        Log.d(
            TAG,
            "Flag isDesktopFileHandlingFlagEnabled() = ${isDesktopFileHandlingFlagEnabled()}"
        )
        Log.d(TAG, "Flag isSearchV2Enabled() = ${isSearchV2Enabled()}")
        Log.d(TAG, "Flag isUsePeekPreviewFlagEnabled() = ${isUsePeekPreviewFlagEnabled()}")
        Log.d(TAG, "Flag isVisualSignalsFlagEnabled() = ${isVisualSignalsFlagEnabled()}")
        Log.d(TAG, "Flag isZipNgFlagEnabled() = ${isZipNgFlagEnabled()}")
    }

    companion object {
        const val TIMEOUT = 5000
        const val TAG = "ActivityTestJunit4"
    }
}
