/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.documentsui.base;

import android.content.res.Resources;

import com.android.documentsui.R;

/**
 * Provides access to feature flags configured in config.xml.
 */
public interface Features {

    // technically we want to check >= O, but we'd need to patch back the O version code :|
    public static final boolean OMC_RUNTIME =
            android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES.N_MR1;

    boolean isArchiveCreationEnabled();
    boolean isRemoteActionsEnabled();
    boolean isContentPagingEnabled();
    boolean isFoldersInSearchResultsEnabled();
    boolean isSystemKeyboardNavigationEnabled();
    boolean isLaunchToDocumentEnabled();
    boolean isVirtualFilesSharingEnabled();
    boolean isContentRefreshEnabled();

    public static Features create(Resources resources) {
        return new RuntimeFeatures(resources);
    }

    final class RuntimeFeatures implements Features {
        private final Resources mRes;

        public RuntimeFeatures(Resources resources) {
            mRes = resources;
        }

        @Override
        public boolean isArchiveCreationEnabled() {
            return mRes.getBoolean(R.bool.feature_archive_creation);
        }

        @Override
        public boolean isRemoteActionsEnabled() {
            return mRes.getBoolean(R.bool.feature_remote_actions);
        }

        @Override
        public boolean isContentPagingEnabled() {
            return mRes.getBoolean(R.bool.feature_content_paging);
        }

        @Override
        public boolean isFoldersInSearchResultsEnabled() {
            return mRes.getBoolean(R.bool.feature_folders_in_search_results);
        }

        @Override
        public boolean isSystemKeyboardNavigationEnabled() {
            return mRes.getBoolean(R.bool.feature_system_keyboard_navigation);
        }

        @Override
        public boolean isLaunchToDocumentEnabled() {
            return mRes.getBoolean(R.bool.feature_launch_to_document);
        }

        @Override
        public boolean isContentRefreshEnabled() {
            return mRes.getBoolean(R.bool.feature_content_refresh);
        }

        @Override
        public boolean isVirtualFilesSharingEnabled() {
            return mRes.getBoolean(R.bool.feature_virtual_files_sharing);
        }
    }
}
