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

package com.android.documentsui.rules

import android.platform.test.flag.junit.AnnotationsRetriever
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.platform.test.flag.junit.IFlagsValueProvider
import com.android.documentsui.flags.Flags
import com.android.documentsui.util.Material3Config
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * A TestRule that checks for the flag like the `CheckFlagsRule`, but this also overrides the config
 * `force_material3` to sync with the desired state of the flag `use_material3`.
 *
 * @RequiresFlagsEnabled(FLAG_USE_MATERIAL3) => forces the config `force_material3` to true.
 * @RequiresFlagsDisabled(FLAG_USE_MATERIAL3) => forces the config `force_material3` to false.
 */
class CheckAndForceMaterial3Flag : TestRule {
    private val flagsValueProvider: IFlagsValueProvider = DeviceFlagsValueProvider()

    override fun apply(base: Statement, description: Description?): Statement? {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                val flagAnnotations = AnnotationsRetriever.getFlagAnnotations(description)
                val isMaterial3 = flagAnnotations.mRequiredFlagValues[Flags.FLAG_USE_MATERIAL3]

                flagsValueProvider.setUp()
                try {
                    flagAnnotations.assumeAllRequiredFlagsMatchProvider(flagsValueProvider)
                } finally {
                    flagsValueProvider.tearDownBeforeTest()
                }

                // The try/finally above takes care of checking the state of the DeviceFlag, so the
                // code only reaches here if the flag is in the desired state.
                if (isMaterial3 != null) {
                    // Only force if the use_material3 flag is in use (aka not-null).
                    Material3Config.setEnabledForTest(isMaterial3)
                }
                base.evaluate()
            }
        }
    }
}
