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

package com.android.documentsui.testing.android;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;

import com.android.documentsui.base.RootInfo;

import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract to avoid having to implement unnecessary Activity stuff.
 * Instances are created using {@link #create()}.
 */
public abstract class TestPackageManager extends PackageManager {

    public Map<String, ResolveInfo> contentProviders;

    private TestPackageManager() {}

    public void addStubContentProviderForRoot(RootInfo... roots) {
        for (RootInfo root : roots) {
            // only one entry per authority is required.
            if (!contentProviders.containsKey(root.authority)) {
                ResolveInfo info = new ResolveInfo();
                contentProviders.put(root.authority, info);
                info.providerInfo = new ProviderInfo();
                info.providerInfo.authority = root.authority;
            }
        }
    }

    public static TestPackageManager create() {
        TestPackageManager pm = Mockito.mock(
                TestPackageManager.class, Mockito.CALLS_REAL_METHODS);
        pm.contentProviders = new HashMap<>();
        return pm;
    }

    @Override
    public List<ResolveInfo> queryIntentContentProviders(Intent intent, int flags) {
        List<ResolveInfo> result = new ArrayList<>();
        result.addAll(contentProviders.values());
        return result;
    }
}
