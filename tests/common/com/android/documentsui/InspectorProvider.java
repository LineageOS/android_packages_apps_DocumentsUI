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
package com.android.documentsui;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.DocumentsContract.Root;
import java.io.FileNotFoundException;

/**
 * Content Provider for testing the Document Inspector.
 */
public class InspectorProvider extends TestRootProvider {

    private static final String AUTHORITY = "com.android.documentsui.inspectorprovider";

    private static final String ROOT_ID = "inspector-root";
    private static final String ROOT_DOC_ID = "root0";
    private static final int ROOT_FLAGS = 0;

    public InspectorProvider() {
        super("Inspector Root", ROOT_ID, ROOT_FLAGS, ROOT_DOC_ID);
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {

        MatrixCursor c = createDocCursor(projection);
        addFolder(c, documentId);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String s, String[] projection, String s1)
        throws FileNotFoundException {

        MatrixCursor c = createDocCursor(projection);
        addFile(c, "test.txt");
        addFile(c, "test1.txt");
        return c;
    }
}
