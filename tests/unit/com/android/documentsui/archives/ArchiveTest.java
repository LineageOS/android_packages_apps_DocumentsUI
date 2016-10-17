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

package com.android.documentsui.archives;

import com.android.documentsui.archives.Archive;
import com.android.documentsui.tests.R;

import android.database.Cursor;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.support.test.InstrumentationRegistry;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.MediumTest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;

@MediumTest
public class ArchiveTest extends AndroidTestCase {
    private static final Uri ARCHIVE_URI = Uri.parse("content://i/love/strawberries");
    private static final String NOTIFICATION_URI = "content://notification-uri";
    private Archive mArchive = null;
    private ArchiveId mArchiveId;

    public static ArchiveId createArchiveId(String path) {
        return new ArchiveId(ARCHIVE_URI, path);
    }

    public void loadArchive(int resource) {
        // Extract the file from resources.
        File file = null;
        final Context context = InstrumentationRegistry.getTargetContext();
        final Context testContext = InstrumentationRegistry.getContext();
        try {
            file = File.createTempFile("com.android.documentsui.archives.tests{",
                    "}.zip", context.getCacheDir());
            try (
                final FileOutputStream outputStream =
                        new ParcelFileDescriptor.AutoCloseOutputStream(
                                ParcelFileDescriptor.open(
                                        file, ParcelFileDescriptor.MODE_WRITE_ONLY));
                final InputStream inputStream =
                        testContext.getResources().openRawResource(resource);
            ) {
                final byte[] buffer = new byte[32 * 1024];
                int bytes;
                while ((bytes = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytes);
                }
                outputStream.flush();

            }
            mArchive = Archive.createForParcelFileDescriptor(
                  context,
                  ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY),
                  ARCHIVE_URI,
                  Uri.parse(NOTIFICATION_URI));
        } catch (IOException e) {
            fail(String.valueOf(e));
        } finally {
            // On UNIX the file will be still available for processes which opened it, even
            // after deleting it. Remove it ASAP, as it won't be used by anyone else.
            if (file != null) {
                file.delete();
            }
        }
    }

    @Override
    public void tearDown() {
        if (mArchive != null) {
            mArchive.close();
        }
    }

    public void testQueryChildDocument() throws IOException {
        loadArchive(R.raw.archive);
        final Cursor cursor = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "/").toDocumentId(), null, null);

        assertTrue(cursor.moveToFirst());
        assertEquals(new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir1",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        assertTrue(cursor.moveToNext());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir2/").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir2",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        assertTrue(cursor.moveToNext());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "file1.txt").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("file1.txt",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals("text/plain",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(13,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        assertFalse(cursor.moveToNext());

        // Check if querying children works too.
        final Cursor childCursor = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId(), null, null);

        assertTrue(childCursor.moveToFirst());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir1/cherries.txt").toDocumentId(),
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertEquals("cherries.txt",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals("text/plain",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(17,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
    }

    public void testQueryChildDocument_NoDirs() throws IOException {
        loadArchive(R.raw.no_dirs);
        final Cursor cursor = mArchive.queryChildDocuments(
            new ArchiveId(ARCHIVE_URI, "/").toDocumentId(), null, null);

        assertTrue(cursor.moveToFirst());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir1",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        assertFalse(cursor.moveToNext());

        final Cursor childCursor = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId(), null, null);

        assertTrue(childCursor.moveToFirst());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir1/dir2/").toDocumentId(),
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir2",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        assertFalse(childCursor.moveToNext());

        final Cursor childCursor2 = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "dir1/dir2/").toDocumentId(),
                null, null);

        assertTrue(childCursor2.moveToFirst());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir1/dir2/cherries.txt").toDocumentId(),
                childCursor2.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertFalse(childCursor2.moveToNext());
    }

    public void testQueryChildDocument_EmptyDirs() throws IOException {
        loadArchive(R.raw.empty_dirs);
        final Cursor cursor = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "/").toDocumentId(), null, null);

        assertTrue(cursor.moveToFirst());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir1",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        assertFalse(cursor.moveToNext());

        final Cursor childCursor = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId(), null, null);

        assertTrue(childCursor.moveToFirst());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir1/dir2/").toDocumentId(),
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir2",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));

        assertTrue(childCursor.moveToNext());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir1/dir3/").toDocumentId(),
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DOCUMENT_ID)));
        assertEquals("dir3",
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_DISPLAY_NAME)));
        assertEquals(Document.MIME_TYPE_DIR,
                childCursor.getString(childCursor.getColumnIndexOrThrow(
                        Document.COLUMN_MIME_TYPE)));
        assertEquals(0,
                childCursor.getInt(childCursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
        assertFalse(cursor.moveToNext());

        final Cursor childCursor2 = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "dir1/dir2/").toDocumentId(),
                null, null);
        assertFalse(childCursor2.moveToFirst());

        final Cursor childCursor3 = mArchive.queryChildDocuments(
                new ArchiveId(ARCHIVE_URI, "dir1/dir3/").toDocumentId(),
                null, null);
        assertFalse(childCursor3.moveToFirst());
    }

    public void testGetDocumentType() throws IOException {
        loadArchive(R.raw.archive);
        assertEquals(Document.MIME_TYPE_DIR, mArchive.getDocumentType(
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId()));
        assertEquals("text/plain", mArchive.getDocumentType(
                new ArchiveId(ARCHIVE_URI, "file1.txt").toDocumentId()));
    }

    public void testIsChildDocument() throws IOException {
        loadArchive(R.raw.archive);
        final String documentId = new ArchiveId(ARCHIVE_URI, "/").toDocumentId();
        assertTrue(mArchive.isChildDocument(documentId,
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId()));
        assertFalse(mArchive.isChildDocument(documentId,
                new ArchiveId(ARCHIVE_URI, "this-does-not-exist").toDocumentId()));
        assertTrue(mArchive.isChildDocument(
                new ArchiveId(ARCHIVE_URI, "dir1/").toDocumentId(),
                new ArchiveId(ARCHIVE_URI, "dir1/cherries.txt").toDocumentId()));
        assertTrue(mArchive.isChildDocument(documentId,
                new ArchiveId(ARCHIVE_URI, "dir1/cherries.txt").toDocumentId()));
    }

    public void testQueryDocument() throws IOException {
        loadArchive(R.raw.archive);
        final Cursor cursor = mArchive.queryDocument(
                new ArchiveId(ARCHIVE_URI, "dir2/strawberries.txt").toDocumentId(),
                null);

        assertTrue(cursor.moveToFirst());
        assertEquals(
                new ArchiveId(ARCHIVE_URI, "dir2/strawberries.txt").toDocumentId(),
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)));
        assertEquals("strawberries.txt",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)));
        assertEquals("text/plain",
                cursor.getString(cursor.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)));
        assertEquals(21,
                cursor.getInt(cursor.getColumnIndexOrThrow(Document.COLUMN_SIZE)));
    }

    public void testOpenDocument() throws IOException {
        loadArchive(R.raw.archive);
        final ParcelFileDescriptor descriptor = mArchive.openDocument(
                new ArchiveId(ARCHIVE_URI, "dir2/strawberries.txt").toDocumentId(),
                "r", null /* signal */);
        try (final ParcelFileDescriptor.AutoCloseInputStream inputStream =
                new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            assertEquals("I love strawberries!", new Scanner(inputStream).nextLine());
        }
    }
}
