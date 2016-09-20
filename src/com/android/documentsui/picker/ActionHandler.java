/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.picker;

import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.provider.Settings;

import com.android.documentsui.Metrics;

/**
 * Provides {@link PickActivity} action specializations to fragments.
 */
class ActionHandler extends com.android.documentsui.ActionHandler<PickActivity> {

    ActionHandler(PickActivity activity) {
        super(activity);
    }

    @Override
    public void showAppDetails(ResolveInfo info) {
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", info.activityInfo.packageName, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        mActivity.startActivity(intent);
    }

    @Override
    public void open(ResolveInfo info) {
        Metrics.logAppVisited(mActivity, info);
        mActivity.onAppPicked(info);
    }
}
