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
package com.android.documentsui.inspector;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.view.View.OnClickListener;

import com.android.documentsui.inspector.InspectorController.TableDisplay;

import java.util.HashMap;
import java.util.Map;

/**
 * Friendly testable table implementation.
 */
class TestTable implements TableDisplay {

    private Map<Integer, CharSequence> calledBundleKeys;
    private boolean mVisible;

    public TestTable() {
        calledBundleKeys = new HashMap<>();
    }

    @Override
    public void setTitle(int title) {

    }

    public void assertRowContains(int keyId, String expected) {
        assertTrue(String.valueOf(calledBundleKeys.get(keyId)).contains(expected));
    }

    public void assertHasRow(int keyId, String expected) {
        assertEquals(expected, calledBundleKeys.get(keyId));
    }

    @Override
    public void put(int keyId, CharSequence value) {
        calledBundleKeys.put(keyId, value);
    }

    @Override
    public void put(int keyId, CharSequence value, OnClickListener callback) {
        calledBundleKeys.put(keyId, value);
    }

    @Override
    public void setVisible(boolean visible) {
        mVisible = visible;
    }

    @Override
    public boolean isEmpty() {
        return calledBundleKeys.isEmpty();
    }

    void assertEmpty() {
        assertTrue(calledBundleKeys.isEmpty());
    }

    void assertVisible(boolean expected) {
        assertEquals(expected, mVisible);
    }
}