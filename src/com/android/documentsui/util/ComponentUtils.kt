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
package com.android.documentsui.util

import android.content.ComponentName
import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.ext.SdkExtensions
import android.provider.MediaStore.ACTION_PICK_IMAGES
import android.util.Log
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.picker.TrampolineActivity

/**
 * Returns the ComponentName for the Photopicker on the device that handles the GET_CONTENT action.
 * Uses the PICK_IMAGES action to get the proper component name then attempts to find the
 * GET_CONTENT handler for that explicit component.
 */
fun getPhotopickerGetContentComponentNameForType(
    packageManager: PackageManager,
    type: String?
): ComponentName? {
    // Intent.ACTION_PICK_IMAGES is only available from SdkExtensions v2 onwards. Prior to that
    // the Photopicker was not available, so in those cases should always send to DocumentsUI.
    if (SdkExtensions.getExtensionVersion(Build.VERSION_CODES.R) < 2) {
        return null
    }

    // Attempt to resolve the `ACTION_PICK_IMAGES` intent to get the Photopicker package.
    // On T+ devices this is is a standalone package, whilst prior to T it is part of the
    // MediaProvider module.
    val pickImagesIntent = Intent(
        ACTION_PICK_IMAGES
    ).apply { addCategory(Intent.CATEGORY_DEFAULT) }
    val photopickerComponentName: ComponentName? = pickImagesIntent.resolveActivity(
        packageManager
    )

    // For certain devices the activity that handles ACTION_GET_CONTENT can be disabled (when
    // the ACTION_PICK_IMAGES is enabled) so double check by explicitly checking the
    // ACTION_GET_CONTENT activity on the same activity that handles ACTION_PICK_IMAGES.
    val photopickerGetContentIntent = Intent(ACTION_GET_CONTENT).apply {
        setType(type)
        setPackage(photopickerComponentName?.packageName)
    }
    val photopickerGetContentComponent: ComponentName? =
        photopickerGetContentIntent.resolveActivity(packageManager)

    // Ensure the `ACTION_GET_CONTENT` activity is enabled.
    if (!isComponentEnabled(packageManager, photopickerGetContentComponent)) {
        if (DEBUG) {
            Log.d(
                TrampolineActivity.Companion.TAG,
                "Photopicker PICK_IMAGES component has no enabled GET_CONTENT handler"
            )
        }
        return null
    }

    return photopickerGetContentComponent
}

/**
 * Private method to check if the supplied ComponentName is enabled or not.
 * Photopicker dynamically disables itself in some instances.
 */
private fun isComponentEnabled(
    packageManager: PackageManager,
    componentName: ComponentName?
): Boolean {
    if (componentName == null) {
        return false
    }

    return when (packageManager.getComponentEnabledSetting(componentName)) {
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
            // DEFAULT is a state that essentially defers to the state defined in the
            // AndroidManifest which can be either enabled or disabled.
            packageManager.getPackageInfo(
                componentName.packageName,
                PackageManager.GET_ACTIVITIES
            )?.let { packageInfo: PackageInfo ->
                if (packageInfo.activities == null) {
                    return false
                }
                for (val info in packageInfo.activities) {
                    if (info.name == componentName.className) {
                        return info.enabled
                    }
                }
            }
            return false
        }

        // Everything else is considered disabled.
        else -> false
    }
}
