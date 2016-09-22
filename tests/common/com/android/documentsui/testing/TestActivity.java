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

package com.android.documentsui.testing;

import android.app.Activity;
import android.content.Intent;

import junit.framework.Assert;

import org.mockito.Mockito;

import javax.annotation.Nullable;

public abstract class TestActivity extends Activity {

    private @Nullable Intent mLastStarted;

    public static TestActivity create() {
        return Mockito.mock(TestActivity.class);
    }

    @Override
    public String getPackageName() {
        return "TestActivity";
    }

    @Override
    public void startActivity(Intent intent) {
        mLastStarted = intent;
    }

    public void assertStarted(Intent expected) {
        Assert.assertEquals(expected, mLastStarted);
    }
}
