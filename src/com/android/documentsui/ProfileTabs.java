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

import static androidx.core.util.Preconditions.checkNotNull;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;

import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;

import com.google.android.material.tabs.TabLayout;

import java.util.Collections;
import java.util.List;

/**
 * A manager class to control UI on a {@link TabLayout} for cross-profile purpose.
 */
public class ProfileTabs implements ProfileTabsAddons {
    private static final float DISABLED_TAB_OPACITY = 0.38f;

    private final TabLayout mTabs;
    private final State mState;
    private final NavigationViewManager.Environment mEnv;
    private final UserIdManager mUserIdManager;
    private List<UserId> mUserIds;
    @Nullable
    private Listener mListener;

    public ProfileTabs(TabLayout tabLayout, State state, UserIdManager userIdManager,
            NavigationViewManager.Environment env) {
        mTabs = checkNotNull(tabLayout);
        mState = checkNotNull(state);
        mEnv = checkNotNull(env);
        mUserIdManager = checkNotNull(userIdManager);
        mTabs.removeAllTabs();
        mUserIds = Collections.singletonList(UserId.CURRENT_USER);
        mTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (mListener != null) {
                    mListener.onUserSelected((UserId) tab.getTag());
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    /**
     * Update the tab layout based on conditions.
     */
    public void updateView() {
        updateTabsIfNeeded();
        mTabs.setVisibility(shouldShow() ? View.VISIBLE : View.GONE);
    }

    public void setListener(@Nullable Listener listener) {
        mListener = listener;
    }

    private void updateTabsIfNeeded() {
        List<UserId> userIds = mUserIdManager.getUserIds();
        // Add tabs if the userIds is not equals to cached mUserIds.
        // Given that mUserIds was initialized with only the current user, if getUserIds()
        // returns just the current user, we don't need to do anything on the tab layout.
        if (!userIds.equals(mUserIds)) {
            mUserIds = userIds;
            mTabs.removeAllTabs();
            if (mUserIds.size() > 1) {
                // set setSelected to false otherwise it will trigger callback.
                mTabs.addTab(createTab(R.string.personal_tab,
                        mUserIdManager.getSystemUser()), /* setSelected= */false);
                mTabs.addTab(createTab(R.string.work_tab,
                        mUserIdManager.getManagedUser()), /* setSelected= */false);
            }
            mTabs.selectTab(mTabs.getTabAt(mUserIds.indexOf(UserId.CURRENT_USER)));
        }
    }

    /**
     * Returns the user represented by the selected tab. If there is no tab, return the
     * current user.
     */
    public UserId getSelectedUser() {
        if (mTabs.getTabCount() > 1 && mTabs.getSelectedTabPosition() >= 0) {
            return (UserId) mTabs.getTabAt(mTabs.getSelectedTabPosition()).getTag();
        }
        return UserId.CURRENT_USER;
    }

    private boolean shouldShow() {
        // Only show tabs when:
        // 1. state supports cross profile, and
        // 2. more than one tab, and
        // 3. not in search mode, and
        // 4. not in sub-folder, and
        // 5. the root supports cross profile.
        return mState.supportsCrossProfile()
                && mTabs.getTabCount() > 1
                && !mEnv.isSearching()
                && mState.stack.size() <= 1
                && mState.stack.getRoot() != null && mState.stack.getRoot().supportsCrossProfile();
    }

    private TabLayout.Tab createTab(int resId, UserId userId) {
        return mTabs.newTab().setText(resId).setTag(userId);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (mTabs.getChildCount() > 0) {
            View view = mTabs.getChildAt(0);
            if (view instanceof ViewGroup) {
                ViewGroup tabs = (ViewGroup) view;
                for (int i = 0; i < tabs.getChildCount(); i++) {
                    View tabView = tabs.getChildAt(i);
                    tabView.setEnabled(enabled);
                    tabView.setAlpha((enabled || mTabs.getSelectedTabPosition() == i) ? 1f
                            : DISABLED_TAB_OPACITY);
                }
            }
        }
    }

    /**
     * Interface definition for a callback to be invoked.
     */
    interface Listener {
        /**
         * Called when a user tab has been selected.
         */
        void onUserSelected(UserId userId);
    }
}
