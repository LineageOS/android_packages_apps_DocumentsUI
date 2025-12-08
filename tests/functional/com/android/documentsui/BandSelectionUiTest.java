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

import android.graphics.Point;
import android.graphics.Rect;
import android.platform.test.annotations.DesktopTest;
import android.view.View;

import androidx.test.filters.LargeTest;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.TestFilesRule;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@LargeTest
public class BandSelectionUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public TestFilesRule mTestFiles = new TestFilesRule();

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
    }

    @DesktopTest(cujs = {"b/434067616"})
    @Test
    public void testBandSelection_allFiles() throws Exception {
        bots.main.switchToGridMode();
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Rect startDir = bots.directory.findDocument(TestFilesRule.DIR_NAME_1).getBounds();
        Point start = new Point(dirListBounds.right - 1, startDir.centerY());
        Point end = new Point(dirListBounds.left + 1, dirListBounds.bottom - 1);
        bots.gesture.bandSelection(start, end);

        bots.directory.assertSelection(4);
    }

    @DesktopTest(cujs = {"b/434067616"})
    @Test
    public void testBandSelection_someFiles() throws Exception {
        // Switch to Grid mode and ensure the list is sorted by title.
        bots.main.switchToGridMode();
        bots.sort.sortBy(SortModel.SORT_DIMENSION_ID_TITLE, SortDimension.SORT_DIRECTION_ASCENDING);

        // Get all the documents to enumerate through and find all the documents in the top row.
        RootInfo root = mTestFiles.docsHelper.getRootList().get(0);
        List<DocumentInfo> createdDocs = mTestFiles.docsHelper.listAllChildren(root);

        // Work out the start and end of the drag selection to go select the top row of documents
        // if this is done in the opposite direction it will trigger the drawer to be swiped open.
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Rect startDoc = bots.directory.findDocument(createdDocs.get(0).displayName).getBounds();
        Point endPoint = new Point(dirListBounds.left + 1, startDoc.centerY());
        Point startPoint = new Point(dirListBounds.right - 1, startDoc.centerY());

        // Count the number of documents in the top row. This can differ based on the screen size so
        // calculate it dynamically to avoid failing on different devices.
        int docsInTopRow = 0;
        for (DocumentInfo doc : createdDocs) {
            Rect docBounds = bots.directory.findDocument(doc.displayName).getBounds();
            if (docBounds.centerY() != startDoc.centerY()) {
                break;
            }
            docsInTopRow++;
        }

        // Perform drag and assert that the number of docs in the top row is selected.
        if (context.getResources().getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL) {
            bots.gesture.bandSelection(endPoint, startPoint);
        } else {
            bots.gesture.bandSelection(startPoint, endPoint);
        }
        bots.directory.assertSelection(docsInTopRow);
    }
}
