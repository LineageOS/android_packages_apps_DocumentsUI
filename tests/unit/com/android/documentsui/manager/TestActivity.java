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

package com.android.documentsui.manager;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import android.content.Intent;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.dirlist.Model;

import org.mockito.Mockito;

import java.util.List;

import javax.annotation.Nullable;

public abstract class TestActivity extends com.android.documentsui.testing.TestActivity
        implements ActionHandler.Addons {

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
        assertEquals(expected, mLastStarted);
    }

    public void assertSomethingStarted() {
        assertNotNull(mLastStarted);
    }

    public void assertNothingStarted() {
        assertNull(mLastStarted);
    }

    @Override
    public void onRootPicked(RootInfo root) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void onDocumentPicked(DocumentInfo doc, Model model) {
    }

    @Override
    public void onDocumentsPicked(List<DocumentInfo> docs) {
    }

    @Override
    public boolean viewDocument(DocumentInfo doc) {
        return false;
    }

    @Override
    public boolean previewDocument(DocumentInfo doc, Model model) {
        return false;
    }
}
