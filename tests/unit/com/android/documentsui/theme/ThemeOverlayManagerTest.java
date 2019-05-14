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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.os.UserHandle;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ThemeOverlayManagerTest {
    private static final String TEST_DISABLED_PREFIX = "com.example.";
    private static final String TEST_ENABLED_PREFIX = "com.example.enabled.";
    private static final String TEST_OVERLAY_PACKAGE = "test.overlay";
    private static final String TEST_TARGET_PACKAGE = "test.target";

    @Mock
    OverlayManager mOverlayManager;

    ThemeOverlayManager mThemeOverlayManager;

    UserHandle mUserHandle;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mUserHandle = UserHandle.of(UserHandle.myUserId());

        when(mOverlayManager.getOverlayInfosForTarget(getEnabledTargetPackageId(),
                mUserHandle)).thenReturn(Lists.newArrayList(
                createOverlayInfo(getOverlayPackageId(), getEnabledTargetPackageId(), true)));

        when(mOverlayManager.getOverlayInfosForTarget(getDisabledTargetPackageId(),
                mUserHandle)).thenReturn(Lists.newArrayList(
                createOverlayInfo(getOverlayPackageId(), getDisabledTargetPackageId(), false)));
    }

    @Test
    public void testOverlayPackagesForDocumentsUI_shouldBeNonStatic() {
        final String docsuiPkgId =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        final OverlayManager manager =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getSystemService(
                        OverlayManager.class);
        final List<OverlayInfo> infos = manager.getOverlayInfosForTarget(docsuiPkgId, mUserHandle);
        for (OverlayInfo info : infos) {
            assertThat(info.isStatic).isFalse();
        }
    }

    @Test
    public void testApplyOverlays_shouldSetEnabled() {
        final boolean enabled = true;

        mThemeOverlayManager = new ThemeOverlayManager(mOverlayManager,
                getDisabledTargetPackageId());
        mThemeOverlayManager.applyOverlays(enabled);

        verify(mOverlayManager, times(1)).setEnabled(getOverlayPackageId(), enabled,
                mUserHandle);
    }

    @Test
    public void testApplyOverlays_shouldGetOverlayInfo() {
        final boolean enabled = true;

        mThemeOverlayManager = new ThemeOverlayManager(mOverlayManager,
                getEnabledTargetPackageId());
        mThemeOverlayManager.applyOverlays(enabled);

        verify(mOverlayManager, times(1)).getOverlayInfosForTarget(getEnabledTargetPackageId(),
                mUserHandle);
    }

    @Test
    public void testApplyOverlays_shouldCheckEnabled_beforeSetEnabled() {
        final boolean enabled = true;

        mThemeOverlayManager = new ThemeOverlayManager(mOverlayManager,
                getEnabledTargetPackageId());
        mThemeOverlayManager.applyOverlays(enabled);

        verify(mOverlayManager, never()).setEnabled(getOverlayPackageId(), enabled,
                mUserHandle);
    }

    @Test
    public void testDefaultDisabled_applyOverlays_shouldEnabled() {
        final boolean enabled = true;

        assertThat(mOverlayManager.getOverlayInfosForTarget(getDisabledTargetPackageId(),
                mUserHandle).get(0).isEnabled()).isEqualTo(!enabled);

        mThemeOverlayManager = new ThemeOverlayManager(mOverlayManager,
                getDisabledTargetPackageId());
        mThemeOverlayManager.applyOverlays(enabled);

        verify(mOverlayManager, times(1)).setEnabled(getOverlayPackageId(), enabled,
                mUserHandle);
    }

    @Test
    public void testDefaultEnabled_applyOverlays_shouldDisabled() {
        final boolean enabled = false;

        assertThat(mOverlayManager.getOverlayInfosForTarget(getEnabledTargetPackageId(),
                mUserHandle).get(0).isEnabled()).isEqualTo(!enabled);

        mThemeOverlayManager = new ThemeOverlayManager(mOverlayManager,
                getEnabledTargetPackageId());
        mThemeOverlayManager.applyOverlays(enabled);

        verify(mOverlayManager, times(1)).setEnabled(getOverlayPackageId(), enabled,
                mUserHandle);
    }

    private static OverlayInfo createOverlayInfo(String packageName, String targetPackageName,
            boolean enabled) {
        return new OverlayInfo(packageName, targetPackageName, null, null, "",
                enabled ? OverlayInfo.STATE_ENABLED : OverlayInfo.STATE_DISABLED, 0, 0, false);
    }

    private static String getDisabledTargetPackageId() {
        return TEST_DISABLED_PREFIX + TEST_TARGET_PACKAGE;
    }

    private static String getEnabledTargetPackageId() {
        return TEST_ENABLED_PREFIX + TEST_TARGET_PACKAGE;
    }

    private static String getOverlayPackageId() {
        return TEST_DISABLED_PREFIX + TEST_OVERLAY_PACKAGE;
    }
}
