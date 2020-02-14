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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.InstrumentationRegistry;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestProvidersAccess;

import com.google.android.collect.Lists;
import com.google.android.material.tabs.TabLayout;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ProfileTabsTest {

    private final UserId systemUser = UserId.of(UserHandle.SYSTEM);
    private final UserId managedUser = UserId.of(100);

    private ProfileTabs mProfileTabs;

    private Context mContext;
    private TabLayout mTabLayout;
    private TestEnvironment mTestEnv;
    private State mState;
    private TestUserIdManager mTestUserIdManager;

    @Before
    public void setUp() {
        TestEnv.create();
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mContext.setTheme(R.style.DocumentsTheme);
        mContext.getTheme().applyStyle(R.style.DocumentsDefaultTheme, false);
        LayoutInflater layoutInflater = LayoutInflater.from(mContext);
        mState = new State();
        mState.action = State.ACTION_GET_CONTENT;
        mState.stack.changeRoot(TestProvidersAccess.DOWNLOADS);
        mState.stack.push(TestEnv.FOLDER_0);
        View view = layoutInflater.inflate(R.layout.directory_header, null);

        mTabLayout = view.findViewById(R.id.tabs);
        mTestEnv = new TestEnvironment();
        mTestEnv.isSearching = false;

        mTestUserIdManager = new TestUserIdManager();
    }

    @Test
    public void testUpdateView_singleUser_shouldHide() {
        initializeWithUsers(systemUser);

        assertThat(mTabLayout.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(0);
    }

    @Test
    public void testUpdateView_twoUsers_shouldShow() {
        initializeWithUsers(systemUser, managedUser);

        assertThat(mTabLayout.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);

        TabLayout.Tab tab1 = mTabLayout.getTabAt(0);
        assertThat(tab1.getTag()).isEqualTo(systemUser);
        assertThat(tab1.getText()).isEqualTo(mContext.getString(R.string.personal_tab));

        TabLayout.Tab tab2 = mTabLayout.getTabAt(1);
        assertThat(tab2.getTag()).isEqualTo(managedUser);
        assertThat(tab2.getText()).isEqualTo(mContext.getString(R.string.work_tab));
    }

    @Test
    public void testUpdateView_twoUsers_browse_shouldHide() {
        initializeWithUsers(systemUser, managedUser);

        mState.action = State.ACTION_BROWSE;
        mProfileTabs.updateView();

        assertThat(mTabLayout.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void testUpdateView_twoUsers_subFolder_shouldHide() {
        initializeWithUsers(systemUser, managedUser);

        // Push 1 more folder. Now the stack has size of 2.
        mState.stack.push(TestEnv.FOLDER_1);

        mProfileTabs.updateView();
        assertThat(mTabLayout.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_twoUsers_recents_subFolder_shouldHide() {
        initializeWithUsers(systemUser, managedUser);

        mState.stack.changeRoot(TestProvidersAccess.RECENTS);
        // This(stack of size 2 in Recents) may not happen in real world.
        mState.stack.push((TestEnv.FOLDER_0));

        mProfileTabs.updateView();
        assertThat(mTabLayout.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_twoUsers_thirdParty_shouldHide() {
        initializeWithUsers(systemUser, managedUser);

        mState.stack.changeRoot(TestProvidersAccess.PICKLES);
        mState.stack.push((TestEnv.FOLDER_0));

        mProfileTabs.updateView();
        assertThat(mTabLayout.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_twoUsers_isSearching_shouldHide() {
        mTestEnv.isSearching = true;
        initializeWithUsers(systemUser, managedUser);

        assertThat(mTabLayout.getVisibility()).isEqualTo(View.GONE);
        assertThat(mTabLayout.getTabCount()).isEqualTo(2);
    }

    @Test
    public void testUpdateView_getSelectedUser_afterUsersChanged() {
        initializeWithUsers(systemUser, managedUser);
        mProfileTabs.updateView();
        mTabLayout.selectTab(mTabLayout.getTabAt(1));
        assertThat(mTabLayout.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(managedUser);

        mTestUserIdManager.userIds = Collections.singletonList(systemUser);
        mProfileTabs.updateView();
        assertThat(mTabLayout.getVisibility()).isEqualTo(View.GONE);
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(systemUser);
    }

    @Test
    public void testgetSelectedUser_twoUsers() {
        initializeWithUsers(systemUser, managedUser);

        mTabLayout.selectTab(mTabLayout.getTabAt(0));
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(systemUser);

        mTabLayout.selectTab(mTabLayout.getTabAt(1));
        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(managedUser);
    }

    @Test
    public void testgetSelectedUser_singleUsers() {
        initializeWithUsers(systemUser);

        assertThat(mProfileTabs.getSelectedUser()).isEqualTo(systemUser);
    }

    private void initializeWithUsers(UserId... userIds) {
        mTestUserIdManager.userIds = Lists.newArrayList(userIds);
        for (UserId userId : userIds) {
            if (userId.isSystem()) {
                mTestUserIdManager.systemUser = userId;
            } else {
                mTestUserIdManager.managedUser = userId;
            }
        }

        mProfileTabs = new ProfileTabs(mTabLayout, mState, mTestUserIdManager, mTestEnv);
        mProfileTabs.updateView();
    }

    /**
     * A test implementation of {@link NavigationViewManager.Environment}.
     */
    private static class TestEnvironment implements NavigationViewManager.Environment {

        public boolean isSearching = false;

        @Override
        public RootInfo getCurrentRoot() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public String getDrawerTitle() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public void refreshCurrentRootAndDirectory(int animation) {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isSearchExpanded() {
            throw new UnsupportedOperationException("not implemented");
        }

        @Override
        public boolean isSearching() {
            return isSearching;
        }

    }

    private static class TestUserIdManager implements UserIdManager {
        List<UserId> userIds = new ArrayList<>();
        UserId systemUser = null;
        UserId managedUser = null;

        @Override
        public List<UserId> getUserIds() {
            return userIds;
        }

        @Override
        public UserId getSystemUser() {
            return systemUser;
        }

        @Override
        public UserId getManagedUser() {
            return managedUser;
        }
    }
}

