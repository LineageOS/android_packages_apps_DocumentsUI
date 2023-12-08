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

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.OUTLINE;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_OFF_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_WORK_FILES_MESSAGE;
import static com.android.documentsui.DevicePolicyResources.Strings.CANT_SELECT_WORK_FILES_TITLE;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_PROFILE_OFF_ENABLE_BUTTON;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_PROFILE_OFF_ERROR_TITLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.app.admin.DevicePolicyResourcesManager;
import android.content.Context;
import android.content.pm.UserProperties;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.core.util.Preconditions;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.CrossProfileNoPermissionException;
import com.android.documentsui.CrossProfileQuietModeException;
import com.android.documentsui.Model;
import com.android.documentsui.R;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.TestActionHandler;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.testing.UserManagers;
import com.android.documentsui.util.FeatureFlagUtils;
import com.android.modules.utils.build.SdkLevel;

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

@SmallTest
public final class MessageTest {

    private UserId mUserId = UserId.of(100);
    private Message mInflateMessage;
    private Context mContext;
    private Runnable mDefaultCallback = () -> {
    };
    private UserManager mUserManager;
    private DevicePolicyManager mDevicePolicyManager;
    private TestActionHandler mTestActionHandler;

    @Before
    public void setUp() {
        mContext = mock(Context.class);
        mUserManager = UserManagers.create();
        mTestActionHandler = new TestActionHandler();
        mDevicePolicyManager = mock(DevicePolicyManager.class);
        when(mContext.getSystemServiceName(UserManager.class)).thenReturn("mUserManager");
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE))
                .thenReturn(mDevicePolicyManager);
        when(mContext.getResources()).thenReturn(
                InstrumentationRegistry.getInstrumentation().getTargetContext().getResources());
        DocumentsAdapter.Environment env =
                new TestEnvironment(mContext, TestEnv.create(), mTestActionHandler);
        env.getDisplayState().action = State.ACTION_GET_CONTENT;
        if (SdkLevel.isAtLeastV()) {
            UserProperties userProperties = new UserProperties.Builder()
                    .setShowInQuietMode(UserProperties.SHOW_IN_QUIET_MODE_PAUSED)
                    .build();
            UserHandle userHandle = UserHandle.of(mUserId.getIdentifier());
            when(mUserManager.getUserProperties(userHandle)).thenReturn(userProperties);
        }
        if (FeatureFlagUtils.isPrivateSpaceEnabled()) {
            String personalLabel = mContext.getString(R.string.personal_tab);
            String workLabel = mContext.getString(R.string.work_tab);
            Map<UserId, String> userIdToLabelMap = new HashMap<>();
            userIdToLabelMap.put(TestProvidersAccess.USER_ID, personalLabel);
            userIdToLabelMap.put(mUserId, workLabel);
            mInflateMessage = new Message.InflateMessage(env, mDefaultCallback,
                    TestProvidersAccess.USER_ID, mUserId, userIdToLabelMap, mUserManager);
        } else {
            mInflateMessage = new Message.InflateMessage(env, mDefaultCallback);
        }
    }

    @Test
    public void testInflateMessage_updateToCrossProfileNoPermission() {
        // Make sure this test is running on system user.
        Preconditions.checkArgument(UserId.CURRENT_USER.isSystem());
        Model.Update error = new Model.Update(
                new CrossProfileNoPermissionException(),
                /* isRemoteActionsEnabled= */ true);
        if (SdkLevel.isAtLeastT()) {
            String title = mContext.getString(R.string.cant_select_work_files_error_title);
            String message = mContext.getString(R.string.cant_select_work_files_error_message);
            DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                    DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(CANT_SELECT_WORK_FILES_TITLE), any()))
                    .thenReturn(title);
            when(devicePolicyResourcesManager.getString(eq(CANT_SELECT_WORK_FILES_MESSAGE), any()))
                    .thenReturn(message);
        }

        mInflateMessage.update(error);

        assertThat(mInflateMessage.getLayout())
                .isEqualTo(InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR);
        assertThat(mInflateMessage.getTitleString())
                .isEqualTo(mContext.getString(R.string.cant_select_work_files_error_title));
        assertThat(mInflateMessage.getMessageString())
                .isEqualTo(mContext.getString(R.string.cant_select_work_files_error_message));
        // No button for this error
        assertThat(mInflateMessage.getButtonString()).isNull();
    }

    @Test
    public void testInflateMessage_updateToCrossProfileQuietMode() {
        if (SdkLevel.isAtLeastV()) return;
        Model.Update error = new Model.Update(
                new CrossProfileQuietModeException(mUserId),
                /* isRemoteActionsEnabled= */ true);
        if (SdkLevel.isAtLeastT()) {
            String title = mContext.getString(R.string.quiet_mode_error_title);
            String text = mContext.getString(R.string.quiet_mode_button);
            DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                    DevicePolicyResourcesManager.class);
            when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ERROR_TITLE), any()))
                    .thenReturn(title);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ENABLE_BUTTON), any()))
                    .thenReturn(text);
        }

        mInflateMessage.update(error);

        assertThat(mInflateMessage.getLayout())
                .isEqualTo(InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR);
        assertThat(mInflateMessage.getTitleString())
                .isEqualTo(mContext.getString(R.string.quiet_mode_error_title));
        assertThat(mInflateMessage.getButtonString()).isEqualTo(
                mContext.getString(R.string.quiet_mode_button));
        assertThat(mInflateMessage.mCallback).isNotNull();
        mInflateMessage.mCallback.run();

        assertThat(mTestActionHandler.mRequestDisablingQuietModeHappened).isTrue();
    }

    @Test
    public void testInflateMessage_updateToCrossProfileQuietMode_PostV() {
        if (!SdkLevel.isAtLeastV()) return;
        Model.Update error = new Model.Update(
                new CrossProfileQuietModeException(mUserId),
                /* isRemoteActionsEnabled= */ true);

        DevicePolicyResourcesManager devicePolicyResourcesManager = mock(
                DevicePolicyResourcesManager.class);
        when(mDevicePolicyManager.getResources()).thenReturn(devicePolicyResourcesManager);

        if (FeatureFlagUtils.isPrivateSpaceEnabled()) {
            Drawable icon = mContext.getDrawable(R.drawable.work_off);
            when(devicePolicyResourcesManager.getDrawable(eq(WORK_PROFILE_OFF_ICON), eq(OUTLINE),
                    any()))
                    .thenReturn(icon);
        } else {
            String title = mContext.getString(R.string.quiet_mode_error_title);
            String text = mContext.getString(R.string.quiet_mode_button);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ERROR_TITLE), any()))
                    .thenReturn(title);
            when(devicePolicyResourcesManager.getString(eq(WORK_PROFILE_OFF_ENABLE_BUTTON), any()))
                    .thenReturn(text);
        }
        mInflateMessage.update(error);

        assertThat(mInflateMessage.getLayout())
                .isEqualTo(InflateMessageDocumentHolder.LAYOUT_CROSS_PROFILE_ERROR);

        if (!FeatureFlagUtils.isPrivateSpaceEnabled()) {
            assert mInflateMessage.getTitleString() != null;
            assertThat(mInflateMessage.getTitleString().toString())
                    .isEqualTo(mContext.getString(R.string.quiet_mode_error_title));
            assert mInflateMessage.getButtonString() != null;
            assertThat(mInflateMessage.getButtonString().toString()).isEqualTo(
                    mContext.getString(R.string.quiet_mode_button));
        }
        assertThat(mInflateMessage.mCallback).isNotNull();
        mInflateMessage.mCallback.run();

        assertThat(mTestActionHandler.mRequestDisablingQuietModeHappened).isTrue();
    }
}
