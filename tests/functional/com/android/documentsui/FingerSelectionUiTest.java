/*
 * Copyright (C) 2019 The Android Open Source Project
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

import androidx.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class FingerSelectionUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule();

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
    }

    @Test
    public void testFingerSelection_outOfRange() throws Exception {
        bots.main.switchToGridMode();
        Rect dirListBounds = bots.directory.findDocumentsList().getBounds();
        Rect firstDoc = bots.directory.findDocument(TestFilesRule.FILE_NAME_1).getBounds();
        // Start from list right bottom.
        Point start = new Point(firstDoc.centerX(), firstDoc.centerY());
        // End is center of file1
        Point end = new Point(dirListBounds.right, dirListBounds.bottom);
        bots.gesture.fingerSelection(start, end);

        bots.directory.assertSelection(3);
    }
}
