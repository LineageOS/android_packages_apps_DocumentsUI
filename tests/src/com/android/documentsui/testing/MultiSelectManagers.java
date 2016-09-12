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

import com.android.documentsui.dirlist.MultiSelectManager;
import com.android.documentsui.dirlist.MultiSelectManager.SelectionMode;
import com.android.documentsui.dirlist.MultiSelectManager.SelectionPredicate;
import com.android.documentsui.dirlist.TestDocumentsAdapter;

import java.util.Collections;
import java.util.List;

public class MultiSelectManagers {
    private MultiSelectManagers() {}

    public static MultiSelectManager createTestInstance() {
        return createTestInstance(Collections.emptyList());
    }

    public static MultiSelectManager createTestInstance(List<String> docs) {
        return createTestInstance(docs, MultiSelectManager.MODE_MULTIPLE);
    }

    public static MultiSelectManager createTestInstance(
            List<String> docs, @SelectionMode int mode) {
        return createTestInstance(
                docs,
                mode,
                (String id, boolean nextState) -> true);
    }

    public static MultiSelectManager createTestInstance(
            List<String> docs, @SelectionMode int mode, SelectionPredicate canSetState) {
        TestDocumentsAdapter adapter = new TestDocumentsAdapter(docs);
        MultiSelectManager manager = new MultiSelectManager(
                adapter,
                mode,
                canSetState);

        return manager;
    }
}
