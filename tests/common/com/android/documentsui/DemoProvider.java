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

import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.MatrixCursor.RowBuilder;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;

/**
 * Provides data view that exercises some of the more esoteric functionality...like
 * display of INFO and ERROR messages.
 *
 * <p>Do not use this provider for automated testing.
 */
public class DemoProvider extends DocumentsProvider {

    private static final String ROOT_ID = "demo-root";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_FLAGS,
            Root.COLUMN_TITLE,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES
    };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
    };

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        final RowBuilder row = c.newRow();
        row.add(Root.COLUMN_ROOT_ID, ROOT_ID);
        row.add(Root.COLUMN_FLAGS, 0);
        row.add(Root.COLUMN_TITLE, "Demo Root");
        row.add(Root.COLUMN_DOCUMENT_ID, "root0");
        row.add(Root.COLUMN_AVAILABLE_BYTES, 1024 * 1024 * 100);
        return c;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection)
            throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
            Bundle extras = new Bundle();
            c.setExtras(extras);
            extras.putString(
                    DocumentsContract.EXTRA_INFO,
                    "This provider is for feature demos only. Do not use from automated tests.");
        addFolder(c, documentId);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(
            String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException {
        MatrixCursor c = new MatrixCursor(
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION);
        Bundle extras = new Bundle();
        c.setExtras(extras);

        switch (parentDocumentId) {
            case "show info":
                extras.putString(
                        DocumentsContract.EXTRA_INFO,
                        "I'm a synthetic INFO. Don't judge me.");
                addFolder(c, "folder");
                addFile(c, "zzz");
                for (int i = 0; i < 100; i++) {
                    addFile(c, "" + i);
                }
                break;

            case "show error":
                extras.putString(
                        DocumentsContract.EXTRA_ERROR,
                        "I'm a synthetic ERROR. Don't judge me.");
                break;

            case "show both error and info":
                extras.putString(
                        DocumentsContract.EXTRA_INFO,
                        "INFO: I'm confused. I've show both ERROR and INFO.");
                extras.putString(
                        DocumentsContract.EXTRA_ERROR,
                        "ERROR: I'm confused. I've show both ERROR and INFO.");
                break;

            case "throw a nice exception":
                throw new RuntimeException();

            default:
                addFolder(c, "show info");
                addFolder(c, "show error");
                addFolder(c, "show both error and info");
                addFolder(c, "throw a nice exception");
                break;
        }

        return c;
    }

    private void addFolder(MatrixCursor c, String id) {
        final RowBuilder row = c.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id);
        row.add(Document.COLUMN_DISPLAY_NAME, id);
        row.add(Document.COLUMN_SIZE, 0);
        row.add(Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR);
        row.add(Document.COLUMN_FLAGS, 0);
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
    }

    private void addFile(MatrixCursor c, String id) {
        final RowBuilder row = c.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id);
        row.add(Document.COLUMN_DISPLAY_NAME, id);
        row.add(Document.COLUMN_SIZE, 0);
        row.add(Document.COLUMN_MIME_TYPE, "text/plain");
        row.add(Document.COLUMN_FLAGS, 0);
        row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode,
            CancellationSignal signal) throws FileNotFoundException {
        throw new UnsupportedOperationException("Nope!");
    }

    @Override
    public boolean onCreate() {
        return true;
    }

}
