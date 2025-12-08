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

package com.android.documentsui;

import static android.multiuser.Flags.FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.PERSONAL_TAB;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_TAB;
import static com.android.documentsui.flags.Flags.FLAG_SUPPORT_VISIBLE_BACKGROUND_USER;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserProperties;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.util.ArraySet;

import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.UserManagers;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SmallTest
@SdkSuppress(minSdkVersion = 31, codeName = "S")
public class UserManagerStateTest {

    /**
     * Class that exposes the @hide api [targetUserId] in order to supply proper values for
     * reflection based code that is inspecting this field.
     *
     * @property targetUserId
     */
    private static class ReflectedResolveInfo extends ResolveInfo {

        public int targetUserId;

        ReflectedResolveInfo(int targetUserId) {
            this.targetUserId = targetUserId;
        }

        @Override
        public boolean isCrossProfileIntentForwarderActivity() {
            return true;
        }
    }

    private static final String PERSONAL = "Personal";
    private static final String WORK = "Work";
    private static final String PRIVATE = "Private";
    private static final String PACKAGE_NAME = "com.android.documentsui";

    /**
     * Assume that the current user is SYSTEM_USER. For HSUM targets, the primary user is set as the
     * system user.
     */
    private final int mCurrentUserId = UserHandle.myUserId();

    private final UserHandle mPrimaryUser = UserHandle.of(mCurrentUserId);
    private final UserHandle mSystemUser = mPrimaryUser == null ? UserHandle.SYSTEM : mPrimaryUser;
    private final UserHandle mManagedUser = UserHandle.of(mCurrentUserId + 10);
    private final UserHandle mPrivateUser = UserHandle.of(mCurrentUserId + 20);
    private final UserHandle mOtherUser = UserHandle.of(mCurrentUserId + 30);
    private final UserHandle mNormalUser = UserHandle.of(mCurrentUserId + 40);
    private final UserHandle mVisibleBackgroundUser = UserHandle.of(mCurrentUserId + 50);

    private final ResolveInfo mMockInfoPrimaryUser =
            new ReflectedResolveInfo(mPrimaryUser.getIdentifier());
    private final ResolveInfo mMockInfoManagedUser =
            new ReflectedResolveInfo(mManagedUser.getIdentifier());
    private final ResolveInfo mMockInfoPrivateUser =
            new ReflectedResolveInfo(mPrivateUser.getIdentifier());

    private final Context mMockContext = mock(Context.class);
    private final Intent mMockIntent = new Intent();
    private final UserManager mMockUserManager = UserManagers.create();
    private final PackageManager mMockPackageManager = mock(PackageManager.class);
    private final DevicePolicyManager mDevicePolicyManager = mock(DevicePolicyManager.class);
    private UserManagerState mUserManagerState;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setup() throws Exception {
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);
        when(mMockContext.createContextAsUser(any(UserHandle.class), anyInt()))
                .thenReturn(mMockContext);
        when(mMockContext.createPackageContextAsUser(any(), anyInt(), any(UserHandle.class)))
                .thenReturn(mMockContext);

        when(mMockUserManager.isManagedProfile(mManagedUser.getIdentifier())).thenReturn(true);
        when(mMockUserManager.isManagedProfile(mSystemUser.getIdentifier())).thenReturn(false);
        when(mMockUserManager.isManagedProfile(mPrivateUser.getIdentifier())).thenReturn(false);
        when(mMockUserManager.isManagedProfile(mOtherUser.getIdentifier())).thenReturn(false);

        if (SdkLevel.isAtLeastV()) {
            UserProperties systemUserProperties =
                    new UserProperties.Builder()
                            .setShowInSharingSurfaces(
                                    UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE)
                            .setCrossProfileContentSharingStrategy(
                                    UserProperties.CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION)
                            .build();
            UserProperties managedUserProperties =
                    new UserProperties.Builder()
                            .setShowInSharingSurfaces(
                                    UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE)
                            .setCrossProfileContentSharingStrategy(
                                    UserProperties.CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION)
                            .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_PAUSED)
                            .build();
            UserProperties privateUserProperties =
                    new UserProperties.Builder()
                            .setShowInSharingSurfaces(
                                    UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE)
                            .setCrossProfileContentSharingStrategy(
                                    UserProperties
                                            .CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                            .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_HIDDEN)
                            .build();
            UserProperties otherUserProperties =
                    new UserProperties.Builder()
                            .setShowInSharingSurfaces(
                                    UserProperties.SHOW_IN_SHARING_SURFACES_WITH_PARENT)
                            .setCrossProfileContentSharingStrategy(
                                    UserProperties
                                            .CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                            .build();
            UserProperties normalUserProperties =
                    new UserProperties.Builder()
                            .setShowInSharingSurfaces(UserProperties.SHOW_IN_SHARING_SURFACES_NO)
                            .setCrossProfileContentSharingStrategy(
                                    UserProperties
                                            .CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                            .build();
            UserProperties visibleBackgroundUserProperties =
                    new UserProperties.Builder()
                            .setShowInSharingSurfaces(UserProperties.SHOW_IN_SHARING_SURFACES_NO)
                            .setCrossProfileContentSharingStrategy(
                                    UserProperties
                                            .CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                            .build();
            when(mMockUserManager.getUserProperties(mSystemUser)).thenReturn(systemUserProperties);
            when(mMockUserManager.getUserProperties(mManagedUser))
                    .thenReturn(managedUserProperties);
            when(mMockUserManager.getUserProperties(mPrivateUser))
                    .thenReturn(privateUserProperties);
            when(mMockUserManager.getUserProperties(mOtherUser)).thenReturn(otherUserProperties);
            when(mMockUserManager.getUserProperties(mNormalUser)).thenReturn(normalUserProperties);
            when(mMockUserManager.getUserProperties(mVisibleBackgroundUser))
                    .thenReturn(visibleBackgroundUserProperties);
        }

        when(mMockUserManager.getProfileParent(mSystemUser)).thenReturn(null);
        when(mMockUserManager.getProfileParent(mManagedUser)).thenReturn(mPrimaryUser);
        when(mMockUserManager.getProfileParent(mPrivateUser)).thenReturn(mPrimaryUser);
        when(mMockUserManager.getProfileParent(mOtherUser)).thenReturn(mPrimaryUser);
        when(mMockUserManager.getProfileParent(mNormalUser)).thenReturn(null);
        when(mMockUserManager.getProfileParent(mVisibleBackgroundUser)).thenReturn(null);

        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getSystemServiceName(UserManager.class)).thenReturn("mMockUserManager");
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);
        when(mMockContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mMockContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mMockContext.getResources())
                .thenReturn(
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getResources());

        when(mMockContext.getPackageName()).thenReturn(PACKAGE_NAME);
        when(mMockContext.createPackageContextAsUser(PACKAGE_NAME, 0, mSystemUser))
                .thenReturn(mMockContext);
        when(mMockContext.createPackageContextAsUser(PACKAGE_NAME, 0, mManagedUser))
                .thenReturn(mMockContext);
        when(mMockContext.createPackageContextAsUser(PACKAGE_NAME, 0, mPrivateUser))
                .thenReturn(mMockContext);
        when(mMockContext.createPackageContextAsUser(PACKAGE_NAME, 0, mOtherUser))
                .thenReturn(mMockContext);
        when(mMockContext.createPackageContextAsUser(PACKAGE_NAME, 0, mNormalUser))
                .thenReturn(mMockContext);
        when(mMockContext.createPackageContextAsUser(PACKAGE_NAME, 0, mPrimaryUser))
                .thenReturn(mMockContext);
    }

    @Test
    public void testGetUserIds_onlySystemUser_returnsSystemUser() {
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserSystem_allShowInSharingSurfacesSeparate() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(
                currentUser,
                Lists.newArrayList(
                        mSystemUser, mManagedUser, mPrivateUser, mOtherUser, mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(
                        UserId.of(mSystemUser), UserId.of(mManagedUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserManaged_allShowInSharingSurfacesSeparate() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(
                currentUser,
                Lists.newArrayList(
                        mSystemUser, mManagedUser, mPrivateUser, mOtherUser, mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(
                        UserId.of(mSystemUser), UserId.of(mManagedUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserPrivate_allShowInSharingSurfacesSeparate() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(
                currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(
                        UserId.of(mSystemUser), UserId.of(mManagedUser), UserId.of(mPrivateUser));
    }

    /*
     * This test verifies that the UserManagerState returns the list of user ids
     * that only includes the visible background user when the display owner user is the
     * visible background user.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_SUPPORT_VISIBLE_BACKGROUND_USER})
    public void testGetUserIds_onlyVisibleBackgroundUser_returnsVisibleBackgroundUser() {
        // This is a test to verify the functionality of visible background non-profile users.
        // The feature for visible background non-profile users has been supported since U-OS.
        if (!SdkLevel.isAtLeastU()) return;

        UserId displayOwnerUser = UserId.of(mVisibleBackgroundUser);
        initializeUserManagerState(displayOwnerUser,
                Lists.newArrayList(mVisibleBackgroundUser));
        when(mMockUserManager.isUserForeground()).thenReturn(false);
        when(mMockUserManager.isProfile()).thenReturn(false);
        when(mMockUserManager.isUserVisible()).thenReturn(true);

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(displayOwnerUser);
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserSystem_returnsBoth() {
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser));
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserManaged_returnsBoth() {
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser));
    }

    @Test
    public void testGetUserIds_systemAndPrivateUserCurrentUserSystem_returnsBoth() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_systemAndPrivateUserCurrentUserPrivate_returnsBoth() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_unsupportedDeviceCurrent_returnsCurrentUser() {
        // This test only tests for Android R or later. This test case always passes
        // before R.
        if (VersionUtils.isAtLeastR()) {
            // When permission is denied, only returns the current user.
            when(mMockContext.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            UserId currentUser = UserId.of(mSystemUser);
            when(mMockUserManager.getUserProfiles())
                    .thenReturn(
                            Lists.newArrayList(
                                    mSystemUser, mManagedUser, mPrivateUser, mOtherUser));
            mUserManagerState = UserManagerState.create(mMockContext);
            assertWithMessage("Unsupported device should have returned only the current user")
                    .that(mUserManagerState.getUserIds())
                    .containsExactly(currentUser);
        }
    }

    @Test
    public void testGetUserIds_returnCachedList() {
        // Returns all three if there are system, managed and private users.
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(
                currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));
        assertWithMessage("getUserIds does not return cached instance")
                .that(mUserManagerState.getUserIds())
                .isSameInstanceAs(mUserManagerState.getUserIds());
    }

    @Test
    public void testGetCanForwardToProfileIdMap_systemUserCanForwardToManaged() {
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfoManagedUser);

        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        any(Intent.class), anyInt(), eq(mSystemUser)))
                .thenReturn(mMockResolveInfoList);

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_systemUserCanAlwaysForwardToPrivate() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_systemUserCanNotForwardToManagedUser() {
        UserId currentUser = UserId.of(mSystemUser);
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoPrivateUser, mMockInfoPrimaryUser);
        if (SdkLevel.isAtLeastV()) {
            initializeUserManagerState(
                    currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
            when(mMockPackageManager.queryIntentActivitiesAsUser(
                            mMockIntent, PackageManager.MATCH_DEFAULT_ONLY, mSystemUser))
                    .thenReturn(mMockResolveInfoList);
        } else {
            initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
            when(mMockPackageManager.queryIntentActivitiesAsUser(
                            mMockIntent, PackageManager.MATCH_DEFAULT_ONLY, mSystemUser))
                    .thenReturn(mMockResolveInfoList);
        }

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), false);
        if (SdkLevel.isAtLeastV()) {
            expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);
        }

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_managedCanForwardToAllVPlus() {
        assumeTrue(SdkLevel.isAtLeastV());

        UserId currentUser = UserId.of(mManagedUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfoPrimaryUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        any(Intent.class), anyInt(), eq(mManagedUser)))
                .thenReturn(mMockResolveInfoList);

        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_managedCanForwardToAllUMinus() {
        assumeFalse(SdkLevel.isAtLeastV());

        UserId currentUser = UserId.of(mManagedUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfoPrimaryUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        any(Intent.class), anyInt(), eq(mManagedUser)))
                .thenReturn(mMockResolveInfoList);

        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser));

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_managedCanNotForwardToAll() {
        UserId currentUser = UserId.of(mManagedUser);
        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfoPrimaryUser);

        if (SdkLevel.isAtLeastV()) {
            initializeUserManagerState(
                    currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
            when(mMockPackageManager.queryIntentActivitiesAsUser(
                            mMockIntent, PackageManager.MATCH_DEFAULT_ONLY, mSystemUser))
                    .thenReturn(mMockResolveInfoList);
        } else {
            initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
            when(mMockPackageManager.queryIntentActivities(
                            mMockIntent, PackageManager.MATCH_DEFAULT_ONLY))
                    .thenReturn(mMockResolveInfoList);
        }

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), false);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        if (SdkLevel.isAtLeastV()) {
            expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), false);
        }

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_privateCanForwardToAll() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoPrimaryUser, mMockInfoManagedUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        any(Intent.class), anyInt(), eq(mSystemUser)))
                .thenReturn(mMockResolveInfoList);

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_privateCanNotForwardToManagedUser() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoPrivateUser, mMockInfoPrimaryUser);
        when(mMockPackageManager.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(mMockResolveInfoList);

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), false);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testGetCanForwardToProfileIdMap_privateCanAlwaysForwardToSystemUser() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfoPrimaryUser);
        when(mMockPackageManager.queryIntentActivities(any(Intent.class), anyInt()))
                .thenReturn(mMockResolveInfoList);

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);

        assertWithMessage("getCanForwardToProfileIdMap returns incorrect mappings")
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    public void testOnProfileStatusChange_anyIntentActionForManagedProfile() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        // UserManagerState#mUserId and UserManagerState#mCanForwardToProfileIdMap will
        // empty
        // by default if the getters of these member variables have not been called
        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent =
                new HashMap<>(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        String action = "any_intent";
        mUserManagerState.onProfileActionStatusChange(action, UserId.of(mManagedUser));

        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds())
                .isEqualTo(userIdsBeforeIntent);
        assertWithMessage(
                        "Unexpected changes to canForwardToProfileIdMap on receiving intent: "
                                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(canForwardToProfileIdMapBeforeIntent);
    }

    @Test
    public void testOnProfileStatusChange_actionProfileUnavailableForPrivateProfile() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        UserId managedUser = UserId.of(mManagedUser);
        UserId privateUser = UserId.of(mPrivateUser);
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoManagedUser, mMockInfoPrivateUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        mMockIntent, PackageManager.MATCH_DEFAULT_ONLY, mSystemUser))
                .thenReturn(mMockResolveInfoList);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        // UserManagerState#mUserId and UserManagerState#mCanForwardToProfileIdMap will
        // empty by default if the getters of these member variables have not been called
        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent =
                new HashMap<>(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        List<UserId> expectedUserIdsAfterIntent = Lists.newArrayList(currentUser, managedUser);

        String action = Intent.ACTION_PROFILE_UNAVAILABLE;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                        "UserIds list should not be same before and after receiving intent: "
                                + action)
                .that(mUserManagerState.getUserIds())
                .isNotEqualTo(userIdsBeforeIntent);
        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds())
                .isEqualTo(expectedUserIdsAfterIntent);
        assertWithMessage(
                        "CanForwardToLabelMap should be same before and after receiving intent: "
                                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(canForwardToProfileIdMapBeforeIntent);
    }

    @Test
    public void testOnProfileStatusChange_actionProfileAvailable_profileInitialised() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        UserId managedUser = UserId.of(mManagedUser);
        UserId privateUser = UserId.of(mPrivateUser);
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoManagedUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        mMockIntent, PackageManager.MATCH_DEFAULT_ONLY, mSystemUser))
                .thenReturn(mMockResolveInfoList);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));

        // initialising the userIds list and canForwardToProfileIdMap
        mUserManagerState.getUserIds();
        mUserManagerState.getCanForwardToProfileIdMap(mMockIntent);

        // Making the private profile unavailable after it has been initialised
        mUserManagerState.onProfileActionStatusChange(
                Intent.ACTION_PROFILE_UNAVAILABLE, privateUser);

        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent =
                new HashMap<>(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        List<UserId> expectedUserIdsAfterIntent =
                Lists.newArrayList(currentUser, managedUser, privateUser);

        String action = Intent.ACTION_PROFILE_AVAILABLE;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                        "UserIds list should not be same before and after receiving intent: "
                                + action)
                .that(mUserManagerState.getUserIds())
                .isNotEqualTo(userIdsBeforeIntent);
        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds())
                .isEqualTo(expectedUserIdsAfterIntent);
        assertWithMessage(
                        "CanForwardToLabelMap should be same before and after receiving intent: "
                                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(canForwardToProfileIdMapBeforeIntent);
    }

    @Test
    public void testOnProfileStatusChange_actionProfileAdded() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        UserId managedUser = UserId.of(mManagedUser);
        UserId privateUser = UserId.of(mPrivateUser);

        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfoManagedUser);

        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        any(Intent.class), anyInt(), eq(mSystemUser)))
                .thenReturn(mMockResolveInfoList);

        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));

        mUserManagerState.setCurrentStateIntent(new Intent());

        // initialising the userIds list and canForwardToProfileIdMap
        mUserManagerState.getUserIds();
        mUserManagerState.getCanForwardToProfileIdMap(mMockIntent);

        String action = Intent.ACTION_PROFILE_ADDED;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                        "UserIds list should not be same before and after receiving intent: "
                                + action)
                .that(mUserManagerState.getUserIds())
                .containsExactly(currentUser, managedUser, privateUser);
        assertWithMessage(
                        "CanForwardToLabelMap should be same before and after receiving intent: "
                                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(
                        Map.ofEntries(
                                Map.entry(currentUser, true),
                                Map.entry(managedUser, true),
                                Map.entry(privateUser, true)));
    }

    @Test
    public void testOnProfileStatusChange_actionProfileAvailable_profileNotInitialised() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        UserId managedUser = UserId.of(mManagedUser);
        UserId privateUser = UserId.of(mPrivateUser);
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoManagedUser, mMockInfoPrivateUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                        any(Intent.class), anyInt(), eq(mSystemUser)))
                .thenReturn(mMockResolveInfoList);

        when(mMockUserManager.getProfileParent(UserHandle.of(privateUser.getIdentifier())))
                .thenReturn(mPrimaryUser);

        // Private user will not be initialised if it is in quiet mode
        when(mMockUserManager.isQuietModeEnabled(mPrivateUser)).thenReturn(true);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        mUserManagerState.setCurrentStateIntent(new Intent());
        // UserManagerState#mUserId and UserManagerState#mCanForwardToProfileIdMap will
        // be empty by default if the getters of these member variables have not been called
        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent =
                new HashMap<>(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        List<UserId> expectedUserIdsAfterIntent =
                Lists.newArrayList(currentUser, managedUser, privateUser);
        Map<UserId, Boolean> expectedCanForwardToProfileIdMapAfterIntent = new HashMap<>();
        expectedCanForwardToProfileIdMapAfterIntent.put(currentUser, true);
        expectedCanForwardToProfileIdMapAfterIntent.put(managedUser, true);
        expectedCanForwardToProfileIdMapAfterIntent.put(privateUser, true);

        String action = Intent.ACTION_PROFILE_AVAILABLE;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                        "UserIds list should not be same before and after receiving intent: "
                                + action)
                .that(mUserManagerState.getUserIds())
                .isNotEqualTo(userIdsBeforeIntent);
        assertWithMessage("Unexpected changes to user id list on receiving intent: " + action)
                .that(mUserManagerState.getUserIds())
                .isEqualTo(expectedUserIdsAfterIntent);
        assertWithMessage(
                        "CanForwardToLabelMap should not be same before and after receiving intent:"
                                + " "
                                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isNotEqualTo(canForwardToProfileIdMapBeforeIntent);
        assertWithMessage(
                        "Unexpected changes to canForwardToProfileIdMap on receiving intent: "
                                + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent))
                .isEqualTo(expectedCanForwardToProfileIdMapAfterIntent);
    }

    /*
     * This test verifies that the UserManagerState does not change its state
     * when the intent action is ACTION_PROFILE_AVAILABLE for a visible background user.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_SUPPORT_VISIBLE_BACKGROUND_USER})
    public void testOnProfileStatusChange_visibleBackgroundUserNotAffected() {
        // This is a test to verify the functionality of visible background non-profile users.
        // The feature for visible background non-profile users has been supported since U-OS.
        if (!SdkLevel.isAtLeastU()) return;

        UserId privateUser = UserId.of(mPrivateUser);
        UserId visibleBackgroundUserId = UserId.of(mVisibleBackgroundUser);

        final List<ResolveInfo> mMockResolveInfoList = Lists.newArrayList(mMockInfoManagedUser);
        when(mMockUserManager.getVisibleUsers())
                .thenReturn(new ArraySet(new UserHandle[]{mSystemUser, mVisibleBackgroundUser}));
        when(mMockPackageManager.queryIntentActivitiesAsUser(mMockIntent,
                PackageManager.MATCH_DEFAULT_ONLY, mSystemUser)).thenReturn(
                mMockResolveInfoList);

        initializeUserManagerState(visibleBackgroundUserId,
                Lists.newArrayList(mVisibleBackgroundUser));
        when(mMockUserManager.isUserForeground()).thenReturn(false);
        when(mMockUserManager.isProfile()).thenReturn(false);
        when(mMockUserManager.isUserVisible()).thenReturn(true);

        List<UserId> userIdsBeforeIntent = new ArrayList<>(mUserManagerState.getUserIds());
        Map<UserId, Boolean> canForwardToProfileIdMapBeforeIntent = new HashMap<>(
                mUserManagerState.getCanForwardToProfileIdMap(mMockIntent));

        String action = Intent.ACTION_PROFILE_AVAILABLE;
        mUserManagerState.onProfileActionStatusChange(action, privateUser);

        assertWithMessage(
                "UserIds list should be same before and after receiving intent: " + action)
                .that(mUserManagerState.getUserIds()).isEqualTo(userIdsBeforeIntent);
        assertWithMessage(
                "CanForwardToLabelMap should be same before and after receiving intent: "
                        + action)
                .that(mUserManagerState.getCanForwardToProfileIdMap(mMockIntent)).isEqualTo(
                        canForwardToProfileIdMapBeforeIntent);
    }

    @Test
    public void testGetUserIdToLabelMap_systemUserAndManagedUser_PreV() {
        assumeFalse(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        if (SdkLevel.isAtLeastT()) {
            DevicePolicyResourcesManager devicePolicyResourcesManager =
                    mock(DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(PERSONAL_TAB), any()))
                    .thenReturn(PERSONAL);
            when(devicePolicyResourcesManager.getString(eq(WORK_TAB), any())).thenReturn(WORK);
        }

        Map<UserId, String> userIdToLabelMap = mUserManagerState.getUserIdToLabelMap();

        assertWithMessage("Incorrect label returned for user id " + mSystemUser)
                .that(userIdToLabelMap.get(UserId.of(mSystemUser)))
                .isEqualTo(PERSONAL);
        assertWithMessage("Incorrect label returned for user id " + mManagedUser)
                .that(userIdToLabelMap.get(UserId.of(mManagedUser)))
                .isEqualTo(WORK);
    }

    @Test
    public void testGetUserIdToLabelMap_systemUserManagedUserPrivateUser_PostV() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        if (SdkLevel.isAtLeastT()) {
            DevicePolicyResourcesManager devicePolicyResourcesManager =
                    mock(DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(PERSONAL_TAB), any()))
                    .thenReturn(PERSONAL);
        }
        UserManager managedUserManager = getUserManagerForUser(mManagedUser);
        UserManager privateUserManager = getUserManagerForUser(mPrivateUser);
        when(managedUserManager.getProfileLabel()).thenReturn(WORK);
        when(privateUserManager.getProfileLabel()).thenReturn(PRIVATE);

        Map<UserId, String> userIdToLabelMap = mUserManagerState.getUserIdToLabelMap();

        assertWithMessage("Incorrect label returned for user id " + mSystemUser)
                .that(userIdToLabelMap.get(UserId.of(mSystemUser)))
                .isEqualTo(PERSONAL);
        assertWithMessage("Incorrect label returned for user id " + mManagedUser)
                .that(userIdToLabelMap.get(UserId.of(mManagedUser)))
                .isEqualTo(WORK);
        assertWithMessage("Incorrect label returned for user id " + mPrivateUser)
                .that(userIdToLabelMap.get(UserId.of(mPrivateUser)))
                .isEqualTo(PRIVATE);
    }

    /*
     * This test verifies that the UserManagerState returns the personal label
     * when the display owner user is the visible background user.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_SUPPORT_VISIBLE_BACKGROUND_USER})
    public void testGetUserIdToLabelMap_visibleBackgroundUser_PostV() {
        assumeTrue(SdkLevel.isAtLeastV());

        initializeUserManagerState(UserId.of(mVisibleBackgroundUser),
                Lists.newArrayList(mVisibleBackgroundUser));
        when(mMockUserManager.getVisibleUsers())
                .thenReturn(new ArraySet(new UserHandle[]{mSystemUser, mVisibleBackgroundUser}));
        when(mMockUserManager.isUserForeground()).thenReturn(false);
        when(mMockUserManager.isProfile()).thenReturn(false);
        when(mMockUserManager.isUserVisible()).thenReturn(true);

        DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                DevicePolicyResourcesManager.class);
        when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
        when(devicePolicyResourcesManager.getString(eq(PERSONAL_TAB), any())).thenReturn(
                PERSONAL);
        UserManager visibleBackgroundUserManager = getUserManagerForUser(mVisibleBackgroundUser);
        when(visibleBackgroundUserManager.getProfileLabel()).thenReturn(PERSONAL);

        Map<UserId, String> userIdToLabelMap = mUserManagerState.getUserIdToLabelMap();

        assertWithMessage("Incorrect label returned for user id " + mVisibleBackgroundUser)
                .that(userIdToLabelMap.get(UserId.of(mVisibleBackgroundUser)))
                .isEqualTo(PERSONAL);
    }


    @Test
    public void testGetUserIdToBadgeMap_systemUserManagedUser_PreV() {
        assumeFalse(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        Drawable workBadge = mock(Drawable.class);
        Resources resources = mock(Resources.class);
        when(mMockContext.getResources()).thenReturn(resources);
        when(mMockContext.getDrawable(R.drawable.ic_briefcase)).thenReturn(workBadge);
        if (SdkLevel.isAtLeastT()) {
            DevicePolicyResourcesManager devicePolicyResourcesManager =
                    mock(DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getDrawable(
                            eq(WORK_PROFILE_ICON), eq(SOLID_COLORED), any()))
                    .thenReturn(workBadge);
        }

        Map<UserId, Drawable> userIdToBadgeMap = mUserManagerState.getUserIdToBadgeMap();

        assertWithMessage("There should be no badge present for personal user")
                .that(userIdToBadgeMap.containsKey(UserId.of(mSystemUser)))
                .isFalse();
        assertWithMessage("Incorrect badge returned for user id " + mManagedUser)
                .that(userIdToBadgeMap.get(UserId.of(mManagedUser)))
                .isEqualTo(workBadge);
    }

    @Test
    public void testGetUserIdToBadgeMap_systemUserManagedUserPrivateUser_PostV() {
        assumeTrue(SdkLevel.isAtLeastV());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        Drawable workBadge = mock(Drawable.class);
        Drawable privateBadge = mock(Drawable.class);
        UserManager managedUserManager = getUserManagerForUser(mManagedUser);
        UserManager privateUserManager = getUserManagerForUser(mPrivateUser);
        when(managedUserManager.getUserBadge()).thenReturn(workBadge);
        when(privateUserManager.getUserBadge()).thenReturn(privateBadge);

        Map<UserId, Drawable> userIdToBadgeMap = mUserManagerState.getUserIdToBadgeMap();

        assertWithMessage("There should be no badge present for personal user")
                .that(userIdToBadgeMap.get(UserId.of(mSystemUser)))
                .isNull();
        assertWithMessage("Incorrect badge returned for user id " + mManagedUser)
                .that(userIdToBadgeMap.get(UserId.of(mManagedUser)))
                .isEqualTo(workBadge);
        assertWithMessage("Incorrect badge returned for user id " + mPrivateUser)
                .that(userIdToBadgeMap.get(UserId.of(mPrivateUser)))
                .isEqualTo(privateBadge);
    }

    /*
     * This test verifies that the UserManagerState does not return any badge
     * for the visible background user.
     */
    @Test
    @RequiresFlagsEnabled({FLAG_SUPPORT_VISIBLE_BACKGROUND_USER})
    public void testGetUserIdToBadgeMap_visibleBackgroundUser_PostV() {
        assumeTrue(SdkLevel.isAtLeastV());

        initializeUserManagerState(UserId.of(mVisibleBackgroundUser),
                Lists.newArrayList(mVisibleBackgroundUser));
        when(mMockUserManager.isUserForeground()).thenReturn(false);
        when(mMockUserManager.isProfile()).thenReturn(false);
        when(mMockUserManager.isUserVisible()).thenReturn(true);

        Map<UserId, Drawable> userIdToBadgeMap = mUserManagerState.getUserIdToBadgeMap();

        assertWithMessage("There should be no badge present for personal user")
                .that(userIdToBadgeMap.get(UserId.of(mVisibleBackgroundUser))).isNull();
    }

    @Test
    @Ignore
    @RequiresFlagsEnabled({FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE})
    public void testGetCanForwardToProfileIdMap_emptyExcludedUserList() {
        assumeTrue(SdkLevel.isAtLeastB());
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoPrimaryUser, mMockInfoManagedUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                any(Intent.class), anyInt(), eq(mSystemUser)))
                .thenReturn(mMockResolveInfoList);

        State state = new State();
        state.excludedUserIds = new java.util.HashSet<>();

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mPrivateUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);

        assertWithMessage("getCanForwardToProfileIdMapForAllowedUsers returns incorrect mappings")
                .that(
                        mUserManagerState.getCanForwardToProfileIdMapForAllowedUsers(
                                mMockIntent, state))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    @Test
    @Ignore
    @RequiresFlagsEnabled({FLAG_ENABLE_MOVING_CONTENT_INTO_PRIVATE_SPACE})
    public void testGetCanForwardToProfileIdMap_privateProfileExcluded_shouldUseNextUser() {
        assumeTrue(SdkLevel.isAtLeastB());
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(
                currentUser, Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser));
        final List<ResolveInfo> mMockResolveInfoList =
                Lists.newArrayList(mMockInfoPrimaryUser, mMockInfoManagedUser);
        when(mMockPackageManager.queryIntentActivitiesAsUser(
                any(Intent.class), anyInt(), eq(mSystemUser)))
                .thenReturn(mMockResolveInfoList);

        State state = new State();
        state.excludedUserIds = new java.util.HashSet<>();
        state.excludedUserIds.add(currentUser.getIdentifier());

        Map<UserId, Boolean> expectedCanForwardToProfileIdMap = new HashMap<>();
        expectedCanForwardToProfileIdMap.put(UserId.of(mManagedUser), true);
        expectedCanForwardToProfileIdMap.put(UserId.of(mSystemUser), true);

        assertWithMessage("getCanForwardToProfileIdMapForAllowedUsers returns incorrect mappings")
                .that(
                        mUserManagerState.getCanForwardToProfileIdMapForAllowedUsers(
                                mMockIntent, state))
                .isEqualTo(expectedCanForwardToProfileIdMap);
    }

    private void initializeUserManagerState(UserId current, List<UserHandle> usersOnDevice) {
        when(mMockUserManager.getUserProfiles()).thenReturn(usersOnDevice);
        TestConfigStore testConfigStore = new TestConfigStore();
        testConfigStore.enablePrivateSpaceInPhotoPicker();
        mUserManagerState =
                new UserManagerState.RuntimeUserManagerState(
                        mMockContext, current, true, testConfigStore);
    }

    private UserManager getUserManagerForUser(UserHandle user) {
        Context mockUserContext = mock(Context.class);
        when(mMockContext.createContextAsUser(user, 0)).thenReturn(mockUserContext);
        UserManager mockUserManager = mock(UserManager.class);
        when(mockUserContext.getSystemService(Context.USER_SERVICE)).thenReturn(mockUserManager);
        when(mockUserContext.getSystemServiceName(UserManager.class)).thenReturn(
                Context.USER_SERVICE);
        when(mockUserContext.getSystemService(UserManager.class)).thenReturn(mockUserManager);
        return mockUserManager;
    }
}
