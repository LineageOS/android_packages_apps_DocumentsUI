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

import static junit.framework.Assert.assertEquals;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.testing.TestEventListener;
import com.android.documentsui.testing.android.TestPackageManager;
import com.android.documentsui.testing.android.TestResources;

import org.mockito.Mockito;

/**
 * Abstract to avoid having to implement unnecessary Activity stuff.
 * Instances are created using {@link #create()}.
 */
public abstract class TestActivity extends AbstractBase {

    public TestResources resources;
    public TestPackageManager packageMgr;
    public Intent intent;
    public RootInfo currentRoot;

    public TestEventListener<Intent> startActivity;
    public TestEventListener<Intent> startService;
    public TestEventListener<RootInfo> rootPicked;
    public TestEventListener<DocumentInfo> openContainer;
    public TestEventListener<Integer> refreshCurrentRootAndDirectory;

    public static TestActivity create() {
        TestActivity activity = Mockito.mock(TestActivity.class, Mockito.CALLS_REAL_METHODS);
        activity.init();
        return activity;
    }

   public void init() {
       resources = TestResources.create();
       packageMgr = TestPackageManager.create();
       intent = new Intent();

       startActivity = new TestEventListener<>();
       startService = new TestEventListener<>();
       rootPicked = new TestEventListener<>();
       openContainer = new TestEventListener<>();
       refreshCurrentRootAndDirectory =  new TestEventListener<>();
   }

    @Override
    public final String getPackageName() {
        return "Banarama";
    }

    @Override
    public final void startActivity(Intent intent) {
        startActivity.accept(intent);
    }

    public final void assertActivityStarted(String expectedAction) {
        assertEquals(expectedAction, startActivity.getLastValue().getAction());
    }

    @Override
    public final ComponentName startService(Intent intent) {
        startService.accept(intent);
        return null;
    }

    public final void assertServiceStarted(String expectedAction) {
        assertEquals(expectedAction, startService.getLastValue().getAction());
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
    public final PackageManager getPackageManager() {
        return packageMgr;
    }

    @Override
    public final void onRootPicked(RootInfo root) {
        rootPicked.accept(root);
    }

    @Override
    public final void onDocumentPicked(DocumentInfo doc) {
        throw new UnsupportedOperationException();
    }

    @Override
    public final void openContainerDocument(DocumentInfo doc) {
        openContainer.accept(doc);
    }

    @Override
    public final void refreshCurrentRootAndDirectory(int anim) {
        refreshCurrentRootAndDirectory.accept(anim);
    }

    @Override
    public final RootInfo getCurrentRoot() {
        return currentRoot;
    }
}

// Trick Mockito into finding our Addons methods correctly. W/o this
// hack, Mockito thinks Addons methods are not implemented.
abstract class AbstractBase extends Activity implements CommonAddons {}
