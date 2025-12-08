/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.flags.Flags.FLAG_SUPPORT_VISIBLE_BACKGROUND_USER;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.TestConfigStore;
import com.android.documentsui.TestUserManagerState;
import com.android.documentsui.ThumbnailCache;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@SmallTest
@RunWith(Parameterized.class)
public final class IconHelperTest {
    private final UserId mSystemUser = UserId.of(UserHandle.SYSTEM);
    private final UserId mManagedUser = UserId.of(100);
    private final UserId mPrivateUser = UserId.of(101);
    private Context mContext;
    private IconHelper mIconHelper;
    private ThumbnailCache mThumbnailCache = new ThumbnailCache(1000);
    private TestUserManagerState mTestUserManagerState;
    private final TestConfigStore mTestConfigStore = new TestConfigStore();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Parameter(0)
    public boolean isPrivateSpaceEnabled;

    /**
     * Parametrize values for {@code isPrivateSpaceEnabled} to run all the tests twice once with
     * private space flag enabled and once with it disabled.
     */
    @Parameters(name = "privateSpaceEnabled={0}")
    public static Iterable<?> data() {
        return Lists.newArrayList(true, false);
    }

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        isPrivateSpaceEnabled = SdkLevel.isAtLeastS() && isPrivateSpaceEnabled;
        if (SdkLevel.isAtLeastS()) {
            mTestUserManagerState = new TestUserManagerState();
            mTestUserManagerState.userIds = SdkLevel.isAtLeastV()
                    ? Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser)
                    : Lists.newArrayList(mSystemUser, mManagedUser);
        }
        mIconHelper = isPrivateSpaceEnabled
                ? new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, null, mTestUserManagerState, mTestConfigStore)
                : new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                        mThumbnailCache, mManagedUser, null, mTestConfigStore);
        if (isPrivateSpaceEnabled) {
            mTestConfigStore.enablePrivateSpaceInPhotoPicker();
        }
    }

    @Test
    public void testShouldShowBadge_returnFalse_onSystemUser() {
        // In HSUM setups the system user will not be a real user and thus DocumentsUI won't be used
        // in this situation. Let's assume the current user is a system user (thus the test is
        // running on a non-HSUM device).
        assume().that(UserId.CURRENT_USER.isSystem()).isTrue();
        assertThat(mIconHelper.shouldShowBadge(mSystemUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnTrue_onManagedUser() {
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isTrue();
    }

    @Test
    public void testShouldShowBadge_returnTrue_onPrivateUser() {
        if (!SdkLevel.isAtLeastV() || !isPrivateSpaceEnabled) return;
        assertThat(mIconHelper.shouldShowBadge(mPrivateUser.getIdentifier())).isTrue();
    }

    /*
     * This test verifies that the badge is not shown for a visible background user.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_SUPPORT_VISIBLE_BACKGROUND_USER})
    public void testShouldShowBadge_returnFalse_onVisibleBackgroundUser() throws Exception {
        // This is a test to verify the functionality of visible background non-profile users.
        // The feature for visible background non-profile users has been supported since U-OS.
        if (!SdkLevel.isAtLeastU()) return;

        Context mockContext = mock(Context.class);
        UserManager mockUserManager = mock(UserManager.class);
        when(mockUserManager.isUserForeground()).thenReturn(false);
        when(mockUserManager.isProfile()).thenReturn(false);
        when(mockUserManager.isUserVisible()).thenReturn(true);

        when(mockContext.createPackageContextAsUser(anyString(), anyInt(), any(UserHandle.class)))
                .thenReturn(mockContext);
        when(mockContext.getSystemServiceName(UserManager.class)).thenReturn("mockUserManager");
        when(mockContext.getSystemService(UserManager.class)).thenReturn(mockUserManager);
        when(mockContext.getResources()).thenReturn(mContext.getResources());

        UserId visibleBackgroundUser = UserId.of(102);
        TestUserManagerState testUserManagerState = new TestUserManagerState();
        testUserManagerState.userIds = Lists.newArrayList(mSystemUser, visibleBackgroundUser);

        mIconHelper = new IconHelper(mockContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, mManagedUser, testUserManagerState, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(visibleBackgroundUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalse_onManagedUser_doNotShowBadge() {
        if (isPrivateSpaceEnabled) return;
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ false,
                mThumbnailCache, mManagedUser, null, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnPrivateUser_doNotShowBadge() {
        if (!isPrivateSpaceEnabled) return;
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ false,
                mThumbnailCache, null, mTestUserManagerState, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mPrivateUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnManagedUser_withoutManagedUser() {
        if (isPrivateSpaceEnabled) return;
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, /* mManagedUser= */ null, null, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnManagedUser_withoutMultipleUsers() {
        if (!isPrivateSpaceEnabled) return;
        if (SdkLevel.isAtLeastS()) {
            mTestUserManagerState.userIds = Lists.newArrayList(mManagedUser);
        }
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, /* mManagedUser= */ null, mTestUserManagerState, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mManagedUser.getIdentifier())).isFalse();
    }

    @Test
    public void testShouldShowBadge_returnFalseOnPrivateUser_withoutMultipleUsers() {
        if (!SdkLevel.isAtLeastV() || !isPrivateSpaceEnabled) return;
        mTestUserManagerState.userIds = Lists.newArrayList(mPrivateUser);
        mIconHelper = new IconHelper(mContext, State.MODE_LIST, /* maybeShowBadge= */ true,
                mThumbnailCache, /* mManagedUser= */ null, mTestUserManagerState, mTestConfigStore);
        assertThat(mIconHelper.shouldShowBadge(mPrivateUser.getIdentifier())).isFalse();
    }
}
