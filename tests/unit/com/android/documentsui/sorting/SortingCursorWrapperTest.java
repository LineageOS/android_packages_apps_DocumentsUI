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

package com.android.documentsui.sorting;

import static com.android.documentsui.base.DocumentInfo.getCursorString;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.testing.SortModels;
import com.android.documentsui.testing.TestFileTypeLookup;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class SortingCursorWrapperTest {
    private static final boolean ENABLE_MANUAL_BENCHMARKS = false;

    private static final int ITEM_COUNT = 10;
    private static final String AUTHORITY = "test_authority";

    private static final String[] COLUMNS = new String[]{
            RootCursorWrapper.COLUMN_AUTHORITY,
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_FLAGS,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_MIME_TYPE
    };

    private static final String[] NAMES = new String[] {
            "4",
            "foo",
            "1",
            "bar",
            "*(Ljifl;a",
            "0",
            "baz",
            "2",
            "3",
            "%$%VD"
    };

    private static final String[] MIMES = new String[] {
            "application/zip",
            "video/3gp",
            "image/png",
            "text/plain",
            "application/msword",
            "text/html",
            "application/pdf",
            "image/png",
            "audio/flac",
            "audio/mp3"
    };

    private static final String[] TYPES = new String[] {
            "Zip archive",
            "3GP video",
            "PNG image",
            "Plain text",
            "Word document",
            "HTML document",
            "PDF document",
            "PNG image",
            "FLAC audio",
            "MP3 audio"
    };

    private TestFileTypeLookup fileTypeLookup;
    private SortModel sortModel;
    private Cursor cursor;

    @Before
    public void setUp() {
        sortModel = SortModels.createTestSortModel();
        fileTypeLookup = new TestFileTypeLookup();

        Random rand = new Random();
        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_DELETE);
            // Generate random document names and sizes. This forces the model's internal sort code
            // to actually do something.
            row.add(Document.COLUMN_DISPLAY_NAME, NAMES[i]);
            row.add(Document.COLUMN_SIZE, rand.nextInt());
            row.add(Document.COLUMN_MIME_TYPE, MIMES[i]);
        }

        cursor = c;
    }

    // Tests sorting ascending by item name.
    @Test
    public void testSort_names_ascending() {
        BitSet seen = new BitSet(ITEM_COUNT);
        List<String> names = new ArrayList<>();

        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_TITLE,
                SortDimension.SORT_DIRECTION_ASCENDING);

        final Cursor cursor = createSortingCursorWrapper();

        for (int i = 0; i < cursor.getCount(); ++i) {
            cursor.moveToPosition(i);
            seen.set(Integer.parseInt(
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID)));
            names.add(getCursorString(cursor, Document.COLUMN_DISPLAY_NAME));
        }

        assertEquals(ITEM_COUNT, seen.cardinality());
        for (int i = 0; i < names.size()-1; ++i) {
            assertTrue(Shared.compareToIgnoreCaseNullable(names.get(i), names.get(i+1)) <= 0);
        }
    }

    // Tests sorting descending by item name.
    @Test
    public void testSort_names_descending() {
        BitSet seen = new BitSet(ITEM_COUNT);
        List<String> names = new ArrayList<>();

        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_TITLE,
                SortDimension.SORT_DIRECTION_DESCENDING);

        final Cursor cursor = createSortingCursorWrapper();

        for (int i = 0; i < cursor.getCount(); ++i) {
            cursor.moveToPosition(i);
            seen.set(Integer.parseInt(
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID)));
            names.add(getCursorString(cursor, Document.COLUMN_DISPLAY_NAME));
        }

        assertEquals(ITEM_COUNT, seen.cardinality());
        for (int i = 0; i < names.size()-1; ++i) {
            assertTrue(Shared.compareToIgnoreCaseNullable(names.get(i), names.get(i+1)) >= 0);
        }
    }

    // Tests sorting by item size.
    @Test
    public void testSort_sizes_ascending() {
        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_SIZE,
                SortDimension.SORT_DIRECTION_ASCENDING);

        final Cursor cursor = createSortingCursorWrapper();

        BitSet seen = new BitSet(ITEM_COUNT);
        int previousSize = Integer.MIN_VALUE;
        for (int i = 0; i < cursor.getCount(); ++i) {
            cursor.moveToPosition(i);
            seen.set(Integer.parseInt(
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID)));
            // Check sort order - descending numerical
            int size = DocumentInfo.getCursorInt(cursor, Document.COLUMN_SIZE);
            assertTrue(previousSize <= size);
            previousSize = size;
        }
        // Check that all items were accounted for.
        assertEquals(ITEM_COUNT, seen.cardinality());
    }

    // Tests sorting by item size.
    @Test
    public void testSort_sizes_descending() {
        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_SIZE,
                SortDimension.SORT_DIRECTION_DESCENDING);

        Cursor cursor = createSortingCursorWrapper();

        BitSet seen = new BitSet(ITEM_COUNT);
        int previousSize = Integer.MAX_VALUE;
        for (int i = 0; i < cursor.getCount(); ++i) {
            cursor.moveToPosition(i);
            seen.set(Integer.parseInt(
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID)));
            // Check sort order - descending numerical
            int size = DocumentInfo.getCursorInt(cursor, Document.COLUMN_SIZE);
            assertTrue(previousSize >= size);
            previousSize = size;
        }
        // Check that all items were accounted for.
        assertEquals(ITEM_COUNT, seen.cardinality());
    }

    // Tests that directories and files are properly bucketed when sorting by size
    @Test
    public void testSort_sizesWithBucketing_ascending() {
        MatrixCursor c = new MatrixCursor(COLUMNS);

        for (int i = 0; i < ITEM_COUNT; ++i) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_SIZE, i);
            // Interleave directories and text files.
            String mimeType =(i % 2 == 0) ? Document.MIME_TYPE_DIR : "text/*";
            row.add(Document.COLUMN_MIME_TYPE, mimeType);
        }

        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_SIZE,
                SortDimension.SORT_DIRECTION_ASCENDING);

        final Cursor cursor = createSortingCursorWrapper(c);

        boolean seenAllDirs = false;
        int previousSize = Integer.MIN_VALUE;
        BitSet seen = new BitSet(ITEM_COUNT);
        // Iterate over items in sort order. Once we've encountered a document (i.e. not a
        // directory), all subsequent items must also be documents. That is, all directories are
        // bucketed at the front of the list, sorted by size, followed by documents, sorted by size.
        for (int i = 0; i < cursor.getCount(); ++i) {
            cursor.moveToPosition(i);
            seen.set(Integer.parseInt(
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID)));

            String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            if (seenAllDirs) {
                assertFalse(Document.MIME_TYPE_DIR.equals(mimeType));
            } else {
                if (!Document.MIME_TYPE_DIR.equals(mimeType)) {
                    seenAllDirs = true;
                    // Reset the previous size seen, because documents are bucketed separately by
                    // the sort.
                    previousSize = Integer.MIN_VALUE;
                }
            }
            // Check sort order - descending numerical
            int size = DocumentInfo.getCursorInt(c, Document.COLUMN_SIZE);
            assertTrue(previousSize <= size);
            previousSize = size;
        }

        // Check that all items were accounted for.
        assertEquals(ITEM_COUNT, seen.cardinality());
    }

    // Tests that directories and files are properly bucketed when sorting by size
    @Test
    public void testSort_sizesWithBucketing_descending() {
        MatrixCursor c = new MatrixCursor(COLUMNS);

        for (int i = 0; i < ITEM_COUNT; ++i) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_SIZE, i);
            // Interleave directories and text files.
            String mimeType =(i % 2 == 0) ? Document.MIME_TYPE_DIR : "text/*";
            row.add(Document.COLUMN_MIME_TYPE, mimeType);
        }

        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_SIZE,
                SortDimension.SORT_DIRECTION_DESCENDING);

        final Cursor cursor = createSortingCursorWrapper(c);

        boolean seenAllDirs = false;
        int previousSize = Integer.MAX_VALUE;
        BitSet seen = new BitSet(ITEM_COUNT);
        // Iterate over items in sort order. Once we've encountered a document (i.e. not a
        // directory), all subsequent items must also be documents. That is, all directories are
        // bucketed at the front of the list, sorted by size, followed by documents, sorted by size.
        for (int i = 0; i < cursor.getCount(); ++i) {
            cursor.moveToPosition(i);
            seen.set(cursor.getPosition());

            String mimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            if (seenAllDirs) {
                assertFalse(Document.MIME_TYPE_DIR.equals(mimeType));
            } else {
                if (!Document.MIME_TYPE_DIR.equals(mimeType)) {
                    seenAllDirs = true;
                    // Reset the previous size seen, because documents are bucketed separately by
                    // the sort.
                    previousSize = Integer.MAX_VALUE;
                }
            }
            // Check sort order - descending numerical
            int size = DocumentInfo.getCursorInt(c, Document.COLUMN_SIZE);
            assertTrue(previousSize >= size);
            previousSize = size;
        }

        // Check that all items were accounted for.
        assertEquals(ITEM_COUNT, seen.cardinality());
    }

    @Test
    public void testSort_time_ascending() {
        final int DL_COUNT = 3;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        Set<String> currentDownloads = new HashSet<>();

        // Add some files
        for (int i = 0; i < ITEM_COUNT; i++) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        }
        // Add some current downloads (no timestamp)
        for (int i = ITEM_COUNT; i < ITEM_COUNT + DL_COUNT; i++) {
            MatrixCursor.RowBuilder row = c.newRow();
            String id = Integer.toString(i);
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, id);
            currentDownloads.add(id);
        }

        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_DATE,
                SortDimension.SORT_DIRECTION_ASCENDING);

        final Cursor cursor = createSortingCursorWrapper(c);

        // Check that all items were accounted for
        assertEquals(ITEM_COUNT + DL_COUNT, cursor.getCount());

        // Check that active downloads are sorted to the bottom.
        for (int i = ITEM_COUNT; i < ITEM_COUNT + DL_COUNT; i++) {
            assertTrue(currentDownloads.contains(
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID)));
        }
    }

    @Test
    public void testSort_time_descending() {
        final int DL_COUNT = 3;
        MatrixCursor c = new MatrixCursor(COLUMNS);
        Set<String> currentDownloads = new HashSet<>();

        // Add some files
        for (int i = 0; i < ITEM_COUNT; i++) {
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, Integer.toString(i));
            row.add(Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis());
        }
        // Add some current downloads (no timestamp)
        for (int i = ITEM_COUNT; i < ITEM_COUNT + DL_COUNT; i++) {
            MatrixCursor.RowBuilder row = c.newRow();
            String id = Integer.toString(i);
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, id);
            currentDownloads.add(id);
        }

        sortModel.sortByUser(SortModel.SORT_DIMENSION_ID_DATE,
                SortDimension.SORT_DIRECTION_DESCENDING);

        final Cursor cursor = createSortingCursorWrapper(c);

        // Check that all items were accounted for
        assertEquals(ITEM_COUNT + DL_COUNT, cursor.getCount());

        // Check that active downloads are sorted to the top.
        for (int i = 0; i < DL_COUNT; i++) {
            assertTrue(currentDownloads.contains(
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID)));
        }
    }

    @Test
    public void testSort_type_ascending() {
        populateTypeMap();

        sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE, SortDimension.SORT_DIRECTION_ASCENDING);

        final Cursor cursor = createSortingCursorWrapper();

        assertEquals(ITEM_COUNT, cursor.getCount());
        final BitSet seen = new BitSet(ITEM_COUNT);
        List<String> types = new ArrayList<>(ITEM_COUNT);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            cursor.moveToPosition(i);
            final String mime =
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            final String type = fileTypeLookup.lookup(mime);
            types.add(type);

            seen.set(DocumentInfo.getCursorInt(cursor, Document.COLUMN_DOCUMENT_ID));
        }

        // Check all items were accounted for
        assertEquals(ITEM_COUNT, seen.cardinality());
        for (int i = 0; i < ITEM_COUNT - 1; ++i) {
            final String lhs = types.get(i);
            final String rhs = types.get(i + 1);
            assertTrue(lhs + " is not smaller than " + rhs,
                    Shared.compareToIgnoreCaseNullable(lhs, rhs) <= 0);
        }
    }

    @Test
    public void testSort_type_descending() {
        populateTypeMap();

        sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE, SortDimension.SORT_DIRECTION_DESCENDING);

        final Cursor cursor = createSortingCursorWrapper();

        assertEquals(ITEM_COUNT, cursor.getCount());
        final BitSet seen = new BitSet(ITEM_COUNT);
        List<String> types = new ArrayList<>(ITEM_COUNT);
        for (int i = 0; i < ITEM_COUNT; ++i) {
            cursor.moveToPosition(i);
            final String mime =
                    DocumentInfo.getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            final String type = fileTypeLookup.lookup(mime);
            types.add(type);

            seen.set(DocumentInfo.getCursorInt(cursor, Document.COLUMN_DOCUMENT_ID));
        }

        // Check all items were accounted for
        assertEquals(ITEM_COUNT, seen.cardinality());
        for (int i = 0; i < ITEM_COUNT - 1; ++i) {
            final String lhs = types.get(i);
            final String rhs = types.get(i + 1);
            assertTrue(lhs + " is not smaller than " + rhs,
                    Shared.compareToIgnoreCaseNullable(lhs, rhs) >= 0);
        }
    }

    @Test
    public void testSort_type_descending_with_directories() {
        do_testSort_type_descending_with_directories(new String[] {
                // ID,     MimeType
                "file___T, text/plain",
                "doc____H, text/html",
                "image__J, image/jpeg",
                "dir____F, vnd.android.document/directory",
                "folder_F, vnd.android.document/directory",
        });

        do_testSort_type_descending_with_directories(new String[] {
                // ID,     MimeType
                "image__J, image/jpeg",
                "file___T, text/plain",
                "dir____F, vnd.android.document/directory",
                "doc____H, text/html",
                "folder_F, vnd.android.document/directory",
        });
    }

    // This unit test captures the sort-some-data essence of the SortDocumentUiTest
    // testSortByType_Descending_gridMode functional test, without all of the UI machinery.
    //
    // "Functional" is in the "integration test, involving UI, running on hardware" sense. It's not
    // in the "pure, no side effects" sense.
    //
    // testData (the input) should hold the same 5 elements on each call, although they may be
    // permuted differently. The after-sorting output should be identical regardless of the
    // before-sorting input.
    //
    // If not, and different permutations of the same input can result in different outputs, then
    // the SortDocumentUiTest will be flaky.
    private void do_testSort_type_descending_with_directories(String[] testData) {
        TestFileTypeLookup lookup = new TestFileTypeLookup();
        lookup.fileTypes.put(Document.MIME_TYPE_DIR, "Folder");
        lookup.fileTypes.put("image/jpeg", "JPG image");
        lookup.fileTypes.put("text/html", "HTML document");
        lookup.fileTypes.put("text/plain", "TXT document");

        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (int i = 0; i < testData.length; ++i) {
            String[] fields = testData[i].split(",\\s+");
            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, fields[0]);
            row.add(Document.COLUMN_MIME_TYPE, fields[1]);
        }

        sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE,
                SortDimension.SORT_DIRECTION_DESCENDING);

        final Cursor cursor = new SortingCursorWrapper(
                c, sortModel.getDimensionById(sortModel.getSortedDimensionId()), lookup);

        String[] expectedIds = new String[]{
            // Directories (mapped to "Folder") should sort before non-directories, even with
            // SORT_DIRECTION_DESCENDING, and ties are broken in *ascending* ID order.
            "dir____F", // "Folder"
            "folder_F", // "Folder"
            "file___T", // "TXT document"
            "image__J", // "JPG image"
            "doc____H", // "HTML document"
        };

        assertEquals(expectedIds.length, cursor.getCount());

        String[] actualIds = new String[expectedIds.length];
        for (int i = 0; i < actualIds.length; ++i) {
            cursor.moveToPosition(i);
            actualIds[i] = DocumentInfo.getCursorString(cursor, Document.COLUMN_DOCUMENT_ID);
        }

        String expected = String.join(" ", expectedIds);
        String actual = String.join(" ", actualIds);
        assertEquals(expected, actual);
    }

    @Test
    public void manualBenchmarkSort_type_descending_with_directories() {
        // Disabled by default, but enable (by changing the ENABLE_MANUAL_BENCHMARKS constant's
        // value) to Log a crude measure of how long the SortingCursorWrapper constructor takes to
        // run (on some synthetic data). This can be useful knowledge when changing the
        // SortingCursorWrapper sorting algorithm.
        if (!ENABLE_MANUAL_BENCHMARKS) {
            return;
        }

        TestFileTypeLookup lookup = new TestFileTypeLookup();
        lookup.fileTypes.put(Document.MIME_TYPE_DIR, "Folder");
        lookup.fileTypes.put("image/jpeg", "JPG image");
        lookup.fileTypes.put("image/png", "PNG image");
        lookup.fileTypes.put("text/html", "HTML document");
        lookup.fileTypes.put("text/plain", "TXT document");

        // Create 5000 directories and 5000 non-directories.
        Random rand = new Random(123456);
        MatrixCursor c = new MatrixCursor(COLUMNS);
        for (int i = 0; i < 10000; ++i) {
            String mimeType = "";
            switch (rand.nextInt() & 7) {
                case 0:
                    mimeType = "image/jpeg";
                    break;
                case 1:
                    mimeType = "image/png";
                    break;
                case 2:
                    mimeType = "text/html";
                    break;
                case 3:
                    mimeType = "text/plain";
                    break;
                default:
                    mimeType = Document.MIME_TYPE_DIR;
                    break;
            }

            MatrixCursor.RowBuilder row = c.newRow();
            row.add(RootCursorWrapper.COLUMN_AUTHORITY, AUTHORITY);
            row.add(Document.COLUMN_DOCUMENT_ID, "ID" + i);
            row.add(Document.COLUMN_MIME_TYPE, mimeType);
        }

        sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_FILE_TYPE,
                SortDimension.SORT_DIRECTION_DESCENDING);

        long[] timeTakenMillis = new long[5];
        for (int i = 0; i < timeTakenMillis.length; ++i) {
            long t0 = System.currentTimeMillis();
            new SortingCursorWrapper(
                    c, sortModel.getDimensionById(sortModel.getSortedDimensionId()), lookup);
            long t1 = System.currentTimeMillis();
            timeTakenMillis[i] = t1 - t0;
        }

        Arrays.sort(timeTakenMillis);
        for (int i = 0; i < timeTakenMillis.length; ++i) {
            Log.d("SortingCursorWrapperTest",
                    "manualBenchmarkSort_type_descending_with_directories" + timeTakenMillis[i]);
        }
    }

    private void populateTypeMap() {
        for (int i = 0; i < ITEM_COUNT; ++i) {
            fileTypeLookup.fileTypes.put(MIMES[i], TYPES[i]);
        }
    }

    @Test
    public void testReturnsWrappedExtras() {
        MatrixCursor c = new MatrixCursor(COLUMNS);
        Bundle extras = new Bundle();
        extras.putBoolean(DocumentsContract.EXTRA_LOADING, true);
        extras.putString(DocumentsContract.EXTRA_INFO, "cheddar");
        extras.putString(DocumentsContract.EXTRA_ERROR, "flop");
        c.setExtras(extras);

        // set sorting to avoid an NPE.
        sortModel.sortByUser(
                SortModel.SORT_DIMENSION_ID_DATE,
                SortDimension.SORT_DIRECTION_DESCENDING);

        Bundle actual = createSortingCursorWrapper(c).getExtras();

        assertTrue(actual.getBoolean(DocumentsContract.EXTRA_LOADING, false));
        assertEquals("cheddar", actual.getString(DocumentsContract.EXTRA_INFO));
        assertEquals("flop", actual.getString(DocumentsContract.EXTRA_ERROR));
    }

    private Cursor createSortingCursorWrapper() {
        return createSortingCursorWrapper(cursor);
    }

    private Cursor createSortingCursorWrapper(Cursor c) {
        final int id = sortModel.getSortedDimensionId();
        return new SortingCursorWrapper(c, sortModel.getDimensionById(id), fileTypeLookup);
    }
}
