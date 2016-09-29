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

package com.android.documentsui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Resources;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.testing.TestEventListener;
import com.android.documentsui.testing.android.TestResources;

import org.mockito.Mockito;

/**
 * Abstract to avoid having to implement unnecessary Activity stuff.
 * Instances are created using {@link #create()}.
 */
public abstract class TestActivity extends AbstractBase {

    public TestResources resources;
    public Intent intent;

    public TestEventListener<Intent> startActivity;
    public TestEventListener<Intent> startService;
    public TestEventListener<RootInfo> rootPicked;

    public static TestActivity create() {
        TestActivity activity = Mockito.mock(TestActivity.class, Mockito.CALLS_REAL_METHODS);
        activity.init();
        return activity;
    }

   public void init() {
       resources = TestResources.create();
       intent = new Intent();

       startActivity = new TestEventListener<>();
       startService = new TestEventListener<>();
       rootPicked = new TestEventListener<>();
   }

    @Override
    public final String getPackageName() {
        return "Banarama";
    }

    @Override
    public final void startActivity(Intent intent) {
        startActivity.accept(intent);
    }

    @Override
    public final ComponentName startService(Intent intent) {
        startService.accept(intent);
        return null;
    }

    @Override
    public final Intent getIntent() {
        return intent;
    }

    @Override
    public final Resources getResources() {
        return resources;
    }

    @Override
    public final void onRootPicked(RootInfo root) {
        rootPicked.accept(root);
    }
}

// Trick Mockito into finding our Addons methods correctly. W/o this
// hack, Mockito thinks Addons methods are not implemented.
abstract class AbstractBase extends Activity implements CommonAddons {}
