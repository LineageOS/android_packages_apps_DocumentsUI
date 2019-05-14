/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.theme;

import android.annotation.TargetApi;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.os.Build;
import android.os.UserHandle;
import android.util.Log;

import java.util.List;

/**
 * ThemeOverlayManager manage runtime resource overlay packages of DocumentsUI
 */
public class ThemeOverlayManager {
    private static final String TAG = ThemeOverlayManager.class.getSimpleName();

    private static final boolean DEBUG = false;

    private OverlayManager mOverlayManager;
    private String mTargetPackageId;
    private UserHandle mUserHandle;

    @TargetApi(Build.VERSION_CODES.M)
    public ThemeOverlayManager(OverlayManager overlayManager, String targetPackageId) {
        mOverlayManager = overlayManager;
        mTargetPackageId = targetPackageId;
        mUserHandle = UserHandle.of(UserHandle.myUserId());
    }

    /**
     * Apply runtime overlay package
     *
     * @param enabled whether or not enable overlay package
     */
    public void applyOverlays(boolean enabled) {
        setOverlaysEnabled(getOverlayInfo(), enabled);
    }

    private List<OverlayInfo> getOverlayInfo() {
        if (mOverlayManager != null) {
            return mOverlayManager.getOverlayInfosForTarget(mTargetPackageId, mUserHandle);
        }
        return null;
    }

    private void setOverlaysEnabled(List<OverlayInfo> overlayInfos, boolean enabled) {
        if (mOverlayManager != null && overlayInfos != null) {
            for (OverlayInfo info : overlayInfos) {
                if (info.isEnabled() != enabled) {
                    setEnabled(info.getPackageName(), mUserHandle, enabled);
                } else {
                    Log.w(TAG, "Overlay package:" + info.getPackageName()
                            + ", already enabled, UserHandle:");
                }
            }
        }
    }

    private void setEnabled(String pkg, UserHandle userHandle, boolean enabled) {
        if (DEBUG) {
            Log.d(TAG, String.format("setEnabled: %s %s %b", pkg, userHandle, enabled));
        }
        mOverlayManager.setEnabled(pkg, enabled, userHandle);
    }
}
