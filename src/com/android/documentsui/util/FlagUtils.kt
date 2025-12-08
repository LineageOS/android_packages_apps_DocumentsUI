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

package com.android.documentsui.util

import android.provider.Flags.enableDocumentsTrashApi
import android.util.Log
import com.android.documentsui.flags.Flags
import com.android.modules.utils.build.SdkLevel

/** Wrap aconfig generated flags to allow us to override flags in tests. */
class FlagUtils
private constructor(private val overrides: MutableMap<String, Boolean> = mutableMapOf()) {
    companion object {
        private const val TAG = "FlagUtils"
        @Volatile private var instance: FlagUtils = FlagUtils()
        private val overridableFlags =
            listOf(
                Flags.FLAG_DESKTOP_FILE_HANDLING_RO,
                Flags.FLAG_DESKTOP_UX_PHASE_2_RO,
                Flags.FLAG_ENABLE_TRASH_FLOW_RO,
                Flags.FLAG_SINGLE_CLICK_TO_SELECT,
                Flags.FLAG_USE_MATERIAL3,
                // TODO(b/433858983): Make peek overridable once all flag evaluations use FlagUtils.
                // Tests need to use RequiresFlagsEnabled and CheckFlagsRule until then.
                // Flags.FLAG_USE_PEEK_PREVIEW_RO,
                Flags.FLAG_USE_SEARCH_V2_READ_ONLY,
                Flags.FLAG_VISUAL_SIGNALS_RO,
                Flags.FLAG_ZIP_NG_RO,
                Flags.FLAG_HOME_SCREEN_FILES_RO,
            )

        @JvmStatic
        fun getInstance(): FlagUtils {
            return instance
        }

        @JvmStatic
        fun isUseMaterial3FlagEnabled(): Boolean {
            // Material3 flag is special, since it has resources behind the flag we can never enable
            // it when it was disabled at build time (i.e. at build time the assets were stripped).
            if (!Flags.useMaterial3()) {
                return false
            }
            return getInstance().overrides.getOrDefault(Flags.FLAG_USE_MATERIAL3, false)
        }

        @JvmStatic
        fun isZipNgFlagEnabled(): Boolean {
            val flag = getInstance().overrides.getOrDefault(Flags.FLAG_ZIP_NG_RO, Flags.zipNgRo())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isSearchV2Enabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_USE_SEARCH_V2_READ_ONLY, Flags.useSearchV2ReadOnly())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isDesktopFileHandlingFlagEnabled(): Boolean {
            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_DESKTOP_FILE_HANDLING_RO, Flags.desktopFileHandlingRo())
        }

        @JvmStatic
        fun isDesktopUxPhase2FlagEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_DESKTOP_UX_PHASE_2_RO, Flags.desktopUxPhase2Ro())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isSingleClickToSelectEnabled(): Boolean {
            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_SINGLE_CLICK_TO_SELECT, Flags.singleClickToSelect())
        }

        @JvmStatic
        fun isVisualSignalsFlagEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_VISUAL_SIGNALS_RO, Flags.visualSignalsRo())
            return flag && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isUsePeekPreviewFlagEnabled(): Boolean {
            // TODO(b/433858983): Make peek overridable once all flag evaluations use FlagUtils
            // i.e. once the Flags.usePeekPreviewRo() condition in FilesActivity is removed.
            return Flags.usePeekPreviewRo() && isUseMaterial3FlagEnabled()
        }

        @JvmStatic
        fun isTrashFlowEnabled(): Boolean {
            // Check if the platform SDK is newer than Android Baklava (SDK 36).
            // The Trash feature relies on DocumentsContract APIs introduced in the
            // Android release after Baklava.
            // This specific Trash feature is NOT backward compatible with platforms
            // at or below Baklava because the required APIs are missing.
            // This check ensures the feature is only considered enabled on
            // supported platform versions, preventing runtime errors if the module
            // runs on an older base OS.
            if (!VersionUtils.isGreaterThanB()) {
                return false
            }
            // If API flag is not enabled, then trash flow will be disabled
            if (!enableDocumentsTrashApi()) {
                return false
            }
            return getInstance()
                .overrides
                .getOrDefault(Flags.FLAG_ENABLE_TRASH_FLOW_RO, Flags.enableTrashFlowRo())
        }

        @JvmStatic
        fun isMovingContentIntoPrivateSpaceEnabled(): Boolean {
            return SdkLevel.isAtLeastB() &&
                android.multiuser.Flags.enableMovingContentIntoPrivateSpace()
        }

        @JvmStatic
        fun isSupportVisibleBackgroundUserFlagEnabled(): Boolean {
            return Flags.supportVisibleBackgroundUser()
        }

        @JvmStatic
        fun isHomeScreenFilesFlagEnabled(): Boolean {
            val flag =
                getInstance()
                    .overrides
                    .getOrDefault(Flags.FLAG_HOME_SCREEN_FILES_RO, Flags.homeScreenFilesRo())
            return flag && isUseMaterial3FlagEnabled()
        }
    }

    fun setOverride(flag: String, state: Boolean) {
        if (!overridableFlags.contains(flag)) {
            throw Exception("Flag not supported: $flag")
        }
        if (flag == Flags.FLAG_USE_MATERIAL3 && state && !Flags.useMaterial3()) {
            throw Exception("Cannot enable FLAG_USE_MATERIAL3 when it's disabled at build time.")
        }
        overrides[flag] = state
        Log.d(TAG, "override flag $flag to $state")
    }

    fun restoreOverrideState(state: Map<String, Boolean>) {
        overrides.clear()
        overrides.putAll(state)
        Log.d(TAG, "restore flag overrides to $overrides")
    }

    fun copyOverrideState(): Map<String, Boolean> {
        return overrides.toMap()
    }
}
