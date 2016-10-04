/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import android.database.MatrixCursor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import com.android.documentsui.DirectoryResult;
import com.android.documentsui.roots.RootCursorWrapper;

import libcore.net.MimeUtils;

import java.util.Random;

public class TestModel extends Model {

    static final String[] COLUMNS = new String[]{
        RootCursorWrapper.COLUMN_AUTHORITY,
        Document.COLUMN_DOCUMENT_ID,
        Document.COLUMN_FLAGS,
        Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_SIZE,
        Document.COLUMN_MIME_TYPE
    };

    private final String mAuthority;
    private int mLastId = 0;
    private Random mRand = new Random();
    private MatrixCursor mCursor;

    public TestModel(String authority) {
        super();
        mAuthority = authority;
        reset();
    }

    public void reset() {
        mLastId = 0;
        mCursor = new MatrixCursor(COLUMNS);
    }

    public void update() {
        DirectoryResult r = new DirectoryResult();
        r.cursor = mCursor;
        super.update(r);
    }

    public void createFolders(String... names) {
        for (int i = 0; i < names.length; i++) {
            createFolder(i, names[i]);
        }
    }

    public void createFiles(String... names) {
        for (int i = 0; i < names.length; i++) {
            create(++mLastId, names[i], guessMimeType(names[i]));
        }
    }

    private void createFolder(int i, String name) {
        create(
                ++mLastId,
                name,
                DocumentsContract.Document.MIME_TYPE_DIR,
                Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_WRITE
                        | Document.FLAG_DIR_SUPPORTS_CREATE);
    }

    private void create(int id, String name, String mimeType) {
        create(id, name, mimeType, Document.FLAG_SUPPORTS_DELETE);
    }

    public void create(int id, String name, String mimeType, int flags) {
        MatrixCursor.RowBuilder row = mCursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(id));
        row.add(RootCursorWrapper.COLUMN_AUTHORITY, mAuthority);
        row.add(Document.COLUMN_DISPLAY_NAME, name);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);
        row.add(Document.COLUMN_FLAGS, flags);
        row.add(Document.COLUMN_SIZE, mRand.nextInt());
    }

    private static String guessMimeType(String name) {
        int i = name.indexOf('.');

        while(i != -1) {
            name = name.substring(i + 1);
            String type = MimeUtils.guessMimeTypeFromExtension(name);
            if (type != null) {
                return type;
            }
            i = name.indexOf('.');
        }

        return "text/plain";
    }
}
