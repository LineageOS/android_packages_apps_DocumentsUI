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

package com.android.documentsui.prefs;

import android.app.backup.BackupManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * A class that monitors changes to the default shared preferences file. If a preference which
 * should be backed up changed, schedule a backup.
 */
public final class PreferencesMonitor
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private Context mContext;

    public PreferencesMonitor(Context context) {
        mContext = context;
    }

    public void start() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .registerOnSharedPreferenceChangeListener(this);
    }

    public void stop() {
        PreferenceManager.getDefaultSharedPreferences(mContext)
                .unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (PrefsBackupHelper.shouldBackup(key)) {
            BackupManager.dataChanged(mContext.getPackageName());
        }
    }
}
