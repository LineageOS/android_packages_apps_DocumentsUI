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

import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserProperties;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.filters.SmallTest;

import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.UserManagers;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

@SmallTest
public class UserManagerStateTest {

    private static final int SHOW_IN_SHARING_SURFACES_WITH_PARENT = 0;
    private static final int SHOW_IN_SHARING_SURFACES_SEPARATE = 1;
    private static final int SHOW_IN_SHARING_SURFACES_NO = 2;
    private static final int CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION = 0;
    private static final int CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT = 1;
    private final UserHandle mSystemUser = UserHandle.SYSTEM;
    private final UserHandle mManagedUser = UserHandle.of(100);
    private final UserHandle mPrivateUser = UserHandle.of(101);
    private final UserHandle mOtherUser = UserHandle.of(102);
    private final UserHandle mNormalUser = UserHandle.of(103);

    private final Context mMockContext = mock(Context.class);
    private final UserManager mMockUserManager = UserManagers.create();
    private final PackageManager mMockPackageManager = mock(PackageManager.class);
    private UserManagerState mUserManagerState;

    @Before
    public void setup() throws Exception {
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);

        when(mMockUserManager.isManagedProfile(mManagedUser.getIdentifier())).thenReturn(true);
        when(mMockUserManager.isManagedProfile(mSystemUser.getIdentifier())).thenReturn(false);
        when(mMockUserManager.isManagedProfile(mPrivateUser.getIdentifier())).thenReturn(false);
        when(mMockUserManager.isManagedProfile(mOtherUser.getIdentifier())).thenReturn(false);

        if (SdkLevel.isAtLeastV()) {
            UserProperties systemUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(SHOW_IN_SHARING_SURFACES_SEPARATE)
                    .setCrossProfileContentSharingStrategy(
                            CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION)
                    .build();
            UserProperties managedUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(SHOW_IN_SHARING_SURFACES_SEPARATE)
                    .setCrossProfileContentSharingStrategy(
                            CROSS_PROFILE_CONTENT_SHARING_NO_DELEGATION)
                    .build();
            UserProperties privateUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(SHOW_IN_SHARING_SURFACES_SEPARATE)
                    .setCrossProfileContentSharingStrategy(
                            CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                    .build();
            UserProperties otherUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(SHOW_IN_SHARING_SURFACES_WITH_PARENT)
                    .setCrossProfileContentSharingStrategy(
                            CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                    .build();
            UserProperties normalUserProperties = new UserProperties.Builder()
                    .setShowInSharingSurfaces(SHOW_IN_SHARING_SURFACES_NO)
                    .setCrossProfileContentSharingStrategy(
                            CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT)
                    .build();
            when(mMockUserManager.getUserProperties(mSystemUser)).thenReturn(systemUserProperties);
            when(mMockUserManager.getUserProperties(mManagedUser)).thenReturn(
                    managedUserProperties);
            when(mMockUserManager.getUserProperties(mPrivateUser)).thenReturn(
                    privateUserProperties);
            when(mMockUserManager.getUserProperties(mOtherUser)).thenReturn(otherUserProperties);
            when(mMockUserManager.getUserProperties(mNormalUser)).thenReturn(normalUserProperties);
        }

        when(mMockUserManager.getProfileParent(mSystemUser)).thenReturn(mSystemUser);
        when(mMockUserManager.getProfileParent(mManagedUser)).thenReturn(mSystemUser);
        when(mMockUserManager.getProfileParent(mPrivateUser)).thenReturn(mSystemUser);
        when(mMockUserManager.getProfileParent(mOtherUser)).thenReturn(mSystemUser);
        when(mMockUserManager.getProfileParent(mNormalUser)).thenReturn(null);

        when(mMockContext.getPackageManager()).thenReturn(mMockPackageManager);
        when(mMockContext.getSystemServiceName(UserManager.class)).thenReturn("mMockUserManager");
        when(mMockContext.getSystemService(UserManager.class)).thenReturn(mMockUserManager);

    }

    @Test
    public void testGetUserIds_onlySystemUser_returnsSystemUser() {
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(UserId.of(mSystemUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserSystem_allShowInSharingSurfacesSeparate() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser,
                        mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser),
                        UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserManaged_allShowInSharingSurfacesSeparate() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser,
                        mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser),
                        UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_allProfilesCurrentUserPrivate_allShowInSharingSurfacesSeparate() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser),
                        UserId.of(mPrivateUser));
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
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_systemAndPrivateUserCurrentUserPrivate_returnsBoth() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mPrivateUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mPrivateUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mPrivateUser));
    }

    @Test
    public void testGetUserIds_systemAndOtherUserCurrentUserOtherPreV_returnsCurrentUser() {
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mOtherUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(currentUser);
    }

    @Test
    public void testGetUserIds_systemAndOtherUserCurrentUserOtherPostV_returnsSystemUser() {
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mOtherUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser));
    }

    @Test
    public void testGetUserIds_normalAndOtherUserCurrentUserNormal_returnsCurrentUser() {
        // since both users do not have show in sharing surfaces separate, returns current user
        UserId currentUser = UserId.of(mNormalUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mNormalUser));

        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mNormalUser));
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserSystem_returnsBothInOrder() {
        // Returns the both if there are system and managed users.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser)).inOrder();
    }

    @Test
    public void testGetUserIds_systemAndManagedUserCurrentUserManaged_returnsBothInOrder() {
        // Returns the both if there are system and managed users.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mSystemUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser)).inOrder();
    }

    @Test
    public void testGetUserIds_managedAndSystemUserCurrentUserSystem_returnsBothInOrder() {
        // Returns the both if there are system and managed users, regardless of input list order.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mManagedUser, mSystemUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds())
                .containsExactly(UserId.of(mSystemUser), UserId.of(mManagedUser)).inOrder();
    }

    @Test
    public void testGetUserIds_otherAndManagedUserCurrentUserOtherPreV_returnsCurrentUser() {
        // When there is no system user, returns the current user.
        // This is a case theoretically can happen but we don't expect. So we return the current
        // user only.
        if (SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(currentUser);
    }

    @Test
    public void testGetUserIds_otherAndManagedUserCurrentUserOtherPostV_returnsManagedUser() {
        // When there is no system user, returns the current user.
        // This is a case theoretically can happen but we don't expect. So we return the current
        // user only.
        if (!SdkLevel.isAtLeastV()) return;
        UserId currentUser = UserId.of(mOtherUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(UserId.of(mManagedUser));
    }

    @Test
    public void testGetUserIds_otherAndManagedUserCurrentUserManaged_returnsCurrentUser() {
        // When there is no system user, returns the current user.
        // This is a case theoretically can happen, but we don't expect. So we return the current
        // user only.
        UserId currentUser = UserId.of(mManagedUser);
        initializeUserManagerState(currentUser, Lists.newArrayList(mOtherUser, mManagedUser));
        assertWithMessage("getUserIds returns unexpected list of user ids")
                .that(mUserManagerState.getUserIds()).containsExactly(currentUser);
    }

    @Test
    public void testGetUserIds_unsupportedDeviceCurrent_returnsCurrentUser() {
        // This test only tests for Android R or later. This test case always passes before R.
        if (VersionUtils.isAtLeastR()) {
            // When permission is denied, only returns the current user.
            when(mMockContext.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS))
                    .thenReturn(PackageManager.PERMISSION_DENIED);
            UserId currentUser = UserId.of(mSystemUser);
            when(mMockUserManager.getUserProfiles()).thenReturn(
                    Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));
            mUserManagerState = UserManagerState.create(mMockContext);
            assertWithMessage("Unsupported device should have returned only the current user")
                    .that(mUserManagerState.getUserIds()).containsExactly(currentUser);
        }
    }

    @Test
    public void testGetUserIds_returnCachedList() {
        // Returns all three if there are system, managed and private users.
        UserId currentUser = UserId.of(mSystemUser);
        initializeUserManagerState(currentUser,
                Lists.newArrayList(mSystemUser, mManagedUser, mPrivateUser, mOtherUser));
        assertWithMessage("getUserIds does not return cached instance")
                .that(mUserManagerState.getUserIds())
                .isSameInstanceAs(mUserManagerState.getUserIds());
    }

    private void initializeUserManagerState(UserId current, List<UserHandle> usersOnDevice) {
        when(mMockUserManager.getUserProfiles()).thenReturn(usersOnDevice);
        mUserManagerState = new UserManagerState.RuntimeUserManagerState(mMockContext, current,
                true);
    }
}
