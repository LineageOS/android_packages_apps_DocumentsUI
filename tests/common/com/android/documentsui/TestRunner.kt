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

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.platform.test.microbenchmark.Functional
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags
import com.android.documentsui.util.FlagUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import org.junit.runner.Runner
import org.junit.runner.notification.RunNotifier
import org.junit.runners.Suite
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.InitializationError

/** Enum for different synthetic target profiles. */
enum class SyntheticTarget(val flags: Map<String, Boolean>) {
    NO_OVERRIDE(mapOf()),

    // Staging mirrors the flags in desktop trunk_staging although the flag values here are advanced
    // before advancing the flags in aconfig to ensure tests will pass.
    STAGING(
        mapOf(
            Flags.FLAG_USE_MATERIAL3 to true,
            Flags.FLAG_DESKTOP_FILE_HANDLING_RO to true,
            Flags.FLAG_DESKTOP_UX_PHASE_2_RO to true,
            Flags.FLAG_ENABLE_TRASH_FLOW_RO to false,
            Flags.FLAG_USE_SEARCH_V2_READ_ONLY to true,
            Flags.FLAG_VISUAL_SIGNALS_RO to true,
            Flags.FLAG_ZIP_NG_RO to true,
        )
    ),

    // Prod mirrors the flags in phone nextfood. Similar to staging, the flag values here are
    // advanced before aconfig flags.
    PROD(
        mapOf(
            Flags.FLAG_USE_MATERIAL3 to true,
            Flags.FLAG_DESKTOP_FILE_HANDLING_RO to false,
            Flags.FLAG_DESKTOP_UX_PHASE_2_RO to false,
            Flags.FLAG_ENABLE_TRASH_FLOW_RO to false,
            Flags.FLAG_USE_SEARCH_V2_READ_ONLY to false,
            Flags.FLAG_VISUAL_SIGNALS_RO to false,
            Flags.FLAG_ZIP_NG_RO to false,
        )
    ),

    // Mainline mirrors the flags in last Android release (similar to mainline module flags). The
    // flag values are updated when a new version of Android is released.
    MAINLINE(
        mapOf(
            Flags.FLAG_USE_MATERIAL3 to false,
            Flags.FLAG_DESKTOP_FILE_HANDLING_RO to false,
            Flags.FLAG_DESKTOP_UX_PHASE_2_RO to false,
            Flags.FLAG_ENABLE_TRASH_FLOW_RO to false,
            Flags.FLAG_USE_SEARCH_V2_READ_ONLY to false,
            Flags.FLAG_VISUAL_SIGNALS_RO to false,
            Flags.FLAG_ZIP_NG_RO to false,
        )
    ),
}

/** Annotation to run tests with a list of synthetic targets. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ParameterizeSyntheticTargets(vararg val value: SyntheticTarget)

/** Enum for different screen sizes. */
enum class ScreenSize(val size: String?, val density: String?) {
    NO_OVERRIDE(null, null),
    // Same as cf_x86_64_phone
    PHONE("720x1280", "320"),
    // Same as cf_x86_64_tablet
    TABLET("2560x1800", "320"),
    // Same as cf_x86_64_desktop
    DESKTOP("1600x900", "160"),
}

/** Annotation to run tests with a list of screen sizes. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class ParameterizeScreenSizes(vararg val value: ScreenSize)

/**
 * TestRunner is a JUnit4 `Suite` that supports parameterization of flags and screen sizes.
 *
 * <p>When parameterization is enabled, it creates a `Functional` runner for each combination of
 * parameters. Without parameterization, it creates a single `Functional` runner.
 *
 * <p>This runner supports two annotations for parameterization:
 * <li>`@ParameterizeSyntheticTargets`: Runs tests against a list of synthetic targets that mimic a
 *   specific environment (e.g. STAGING, PROD).
 * <li>`@ParameterizeScreenSizes`: Runs tests on different screen sizes (e.g. PHONE, DESKTOP).
 *
 * <p>The runner creates a JUnit `Suite` containing a separate runner for every combination of
 * synthetic targets and screen sizes. The test name is suffixed with the parameters, for example:
 * `testMethod[SyntheticTarget=STAGING,ScreenSize=PHONE]`
 *
 * <p>If an annotation is not provided, a default `NO_OVERRIDE` value is used, and the corresponding
 * part of the test name suffix is omitted.
 *
 * <p>The parameterization can be disabled with:
 * ```
 * $ atest DocumentsUIGoogleTests -- \
 *     --test-arg com.android.tradefed.testtype.AndroidJUnitTest:instrumentation-arg:documentsui_disable_parameterization:=true
 * ```
 *
 * <p>The screen size parameterization can be forced with:
 * ```
 * $ atest DocumentsUIGoogleTests -- \
 *     --test-arg com.android.tradefed.testtype.AndroidJUnitTest:instrumentation-arg:documentsui_force_screen_size_parameterization:=true
 * ```
 *
 * <p>The flag parameterization can be forced with:
 * ```
 * $ atest DocumentsUIGoogleTests -- \
 *     --test-arg com.android.tradefed.testtype.AndroidJUnitTest:instrumentation-arg:documentsui_force_flag_parameterization:=true
 * ```
 */
class TestRunner(klass: Class<*>) : Suite(klass, createRunners(klass)) {
    companion object {
        private const val TAG = "TestRunner"

        /** Creates a runner for every combination of synthetic targets and screen sizes. */
        private fun createRunners(klass: Class<*>): List<Runner> {
            val args = InstrumentationRegistry.getArguments()
            if (args.getString("documentsui_disable_parameterization") == "true") {
                return listOf(object : Functional(klass) {})
            }

            val syntheticTargets = getSyntheticTargets(klass)
            val screenSizes = getScreenSizes(klass)
            if (
                syntheticTargets == listOf(SyntheticTarget.NO_OVERRIDE) &&
                    screenSizes == listOf(ScreenSize.NO_OVERRIDE)
            ) {
                return listOf(object : Functional(klass) {})
            }

            return syntheticTargets.flatMap { syntheticTarget ->
                screenSizes.map { screenSize ->
                    createParameterizedRunner(klass, syntheticTarget, screenSize)
                }
            }
        }

        /** Creates a runner for the given synthetic target and screen size. */
        private fun createParameterizedRunner(
            klass: Class<*>,
            syntheticTarget: SyntheticTarget,
            screenSize: ScreenSize,
        ): Runner {
            return object : Functional(klass) {
                override fun runChild(method: FrameworkMethod, notifier: RunNotifier) {
                    val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation
                    val originalOverrides = FlagUtils.getInstance().copyOverrideState()
                    try {
                        syntheticTarget.flags.forEach { (flag, state) ->
                            FlagUtils.getInstance().setOverride(flag, state)
                        }

                        if (screenSize != ScreenSize.NO_OVERRIDE) {
                            // Tell ActivityTestJunit4 to launch the activity in fullscreen mode.
                            System.setProperty("documentsui_fullscreen", "true")
                            executeWmShellCommand("size", screenSize.size!!)
                            executeWmShellCommand("density", screenSize.density!!)
                        }
                        super.runChild(method, notifier)
                    } finally {
                        FlagUtils.getInstance().restoreOverrideState(originalOverrides)
                        if (screenSize != ScreenSize.NO_OVERRIDE) {
                            System.clearProperty("documentsui_fullscreen")
                            uiAutomation.executeShellCommand("wm size reset")
                        }
                    }
                }

                override fun testName(method: FrameworkMethod): String {
                    val parts = mutableListOf<String>()
                    if (syntheticTarget != SyntheticTarget.NO_OVERRIDE) {
                        parts.add("SyntheticTarget=${syntheticTarget.name}")
                    }
                    if (screenSize != ScreenSize.NO_OVERRIDE) {
                        parts.add("ScreenSize=${screenSize.name}")
                    }
                    if (parts.isEmpty()) {
                        return method.name
                    }
                    return "${method.name}[${parts.joinToString(separator = ",")}]"
                }
            }
        }

        /**
         * Returns a list of screen sizes for the given test class.
         *
         * The screen sizes are retrieved from the @ParameterizeScreenSizes annotation on the test
         * class.
         */
        private fun getScreenSizes(klass: Class<*>): List<ScreenSize> {
            val args = InstrumentationRegistry.getArguments()
            if (args.getString("documentsui_force_screen_size_parameterization") == "true") {
                return listOf(ScreenSize.PHONE, ScreenSize.TABLET, ScreenSize.DESKTOP)
            }

            val pm = InstrumentationRegistry.getInstrumentation().context.packageManager
            if (!pm.hasSystemFeature(PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT)) {
                return listOf(ScreenSize.NO_OVERRIDE)
            }

            val screenSizesAnnotation = klass.getAnnotation(ParameterizeScreenSizes::class.java)

            val screenSizes = screenSizesAnnotation?.value ?: arrayOf(ScreenSize.NO_OVERRIDE)

            if (screenSizes.isEmpty()) {
                throw InitializationError(
                    "@ParameterizeScreenSizes must contain at least one screen size."
                )
            }
            return screenSizes.toList()
        }

        /**
         * Returns a list of synthetic targets for the given test class.
         *
         * The synthetic targets are retrieved from the @ParameterizeSyntheticTargets annotation on
         * the test class.
         */
        private fun getSyntheticTargets(klass: Class<*>): List<SyntheticTarget> {
            val args = InstrumentationRegistry.getArguments()
            if (args.getString("documentsui_force_flag_parameterization") == "true") {
                return listOf(
                    SyntheticTarget.STAGING,
                    SyntheticTarget.PROD,
                    SyntheticTarget.MAINLINE,
                )
            }

            val syntheticTargetsAnnotation =
                klass.getAnnotation(ParameterizeSyntheticTargets::class.java)

            val targets = syntheticTargetsAnnotation?.value ?: arrayOf(SyntheticTarget.NO_OVERRIDE)

            if (targets.isEmpty()) {
                throw InitializationError(
                    "@ParameterizeSyntheticTargets must contain at least one target."
                )
            }
            return targets.toList()
        }

        /** Executes a wm shell command and verifies the value is set. */
        private fun executeWmShellCommand(
            subcommand: String,
            value: String,
            retry: Boolean = true,
        ) {
            Log.d(TAG, "Running wm $subcommand $value")

            val uiAutomation = InstrumentationRegistry.getInstrumentation().uiAutomation

            // Set the value then wait for the effect. Retry setting the value once if the effect
            // doesn't appear after 10 attempts.
            for (i in 1..2) {
                uiAutomation.executeShellCommand("wm $subcommand $value")

                for (i in 1..10) {
                    val output = readFd(uiAutomation.executeShellCommand("wm $subcommand"))
                    if (output.contains(value)) {
                        return
                    }

                    Log.d(
                        TAG,
                        "Waiting for $subcommand to update to $value. Current output: $output",
                    )
                    SystemClock.sleep(100)
                }
            }

            throw RuntimeException("Failed to run wm $subcommand $value")
        }

        private fun readFd(fd: ParcelFileDescriptor): String {
            val inputStream = ParcelFileDescriptor.AutoCloseInputStream(fd)
            val reader = BufferedReader(InputStreamReader(inputStream))
            return reader.use { r -> r.readText() }
        }
    }
}
