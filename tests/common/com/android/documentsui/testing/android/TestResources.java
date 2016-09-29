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

import android.content.res.Resources;
import android.util.SparseBooleanArray;

import org.mockito.Mockito;

/**
 * Abstract to avoid having to implement unnecessary Activity stuff.
 * Instances are created using {@link #create()}.
 */
public abstract class TestResources extends Resources {

    public SparseBooleanArray bools;

    public TestResources() {
        super(ClassLoader.getSystemClassLoader());
    }

    public static TestResources create() {
        TestResources resources = Mockito.mock(
                TestResources.class, Mockito.CALLS_REAL_METHODS);
        resources.bools = new SparseBooleanArray();
        return resources;
    }

    @Override
    public boolean getBoolean(int id) throws NotFoundException {
        return bools.get(id);
    }
}
