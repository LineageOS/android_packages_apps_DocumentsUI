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
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.useMaterial3
import com.android.documentsui.util.FlagUtils
import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/*
 * Special Rule to override DocumentsUI flags. We cannot use the platform SetFlagsRule it's not
 * supported in Android test suits (xTS) and DocumentsUI tests are included in MTS.
 */
class OverrideFlagsRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement {
        return object : Statement() {
            override fun evaluate() {
                val flagAnnotations = AnnotationsRetriever.getFlagAnnotations(description)
                val flagUtils = FlagUtils.getInstance()
                val beforeState = flagUtils.copyOverrideState()
                try {
                    for (pair in flagAnnotations.mSetFlagValues.entries) {
                        // Material3 flag is special. We cannot enable it if it's disabled at build
                        // time. So when we encounter a SetFlag trying to do this, we skip the test.
                        Assume.assumeFalse(
                            "Cannot enable FLAG_USE_MATERIAL3 when it's disabled at build time.",
                            pair.key == FLAG_USE_MATERIAL3 && pair.value && !useMaterial3()
                        )

                        flagUtils.setOverride(pair.key, pair.value)
                    }

                    base.evaluate()
                } finally {
                    flagUtils.restoreOverrideState(beforeState)
                }
            }
        }
    }
}
