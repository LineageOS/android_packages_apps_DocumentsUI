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

import com.android.documentsui.DocsSelectionManager;
import com.android.documentsui.dirlist.DocumentsAdapter;
import com.android.documentsui.dirlist.TestDocumentsAdapter;
import com.android.documentsui.selection.DefaultSelectionManager;
import com.android.documentsui.selection.DefaultSelectionManager.SelectionMode;
import com.android.documentsui.selection.SelectionManager.SelectionPredicate;

import java.util.Collections;
import java.util.List;

public class SelectionManagers {

    public static final SelectionPredicate CAN_SET_ANYTHING = new SelectionPredicate() {
        @Override
        public boolean canSetStateForId(String id, boolean nextState) {
            return true;
        }

        @Override
        public boolean canSetStateAtPosition(int position, boolean nextState) {
            return true;
        }
    };

    private SelectionManagers() {}

    public static DocsSelectionManager createTestInstance() {
        return createTestInstance(Collections.emptyList());
    }

    public static DocsSelectionManager createTestInstance(List<String> docs) {
        return createTestInstance(docs, DefaultSelectionManager.MODE_MULTIPLE);
    }

    public static DocsSelectionManager createTestInstance(
            List<String> docs, @SelectionMode int mode) {
        return createTestInstance(new TestDocumentsAdapter(docs), mode, CAN_SET_ANYTHING);
    }

    public static DocsSelectionManager createTestInstance(
            DocumentsAdapter adapter, @SelectionMode int mode, SelectionPredicate canSetState) {
        DocsSelectionManager manager = mode == DefaultSelectionManager.MODE_SINGLE
                ? DocsSelectionManager.createSingleSelect()
                : DocsSelectionManager.createMultiSelect();
        manager.reset(adapter, adapter, canSetState);

        return manager;
    }
}
