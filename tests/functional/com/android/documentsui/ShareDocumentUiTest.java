/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.StubProvider.ROOT_0_ID;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.LargeTest;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class ShareDocumentUiTest extends ActivityTestJunit4<FilesActivity> {
    private static final String TEST_FILE_NAME = "amazingchair.pdf";

    @Rule
    public final TestFilesRule mTestFilesRule =
            new TestFilesRule()
                    .createTestFiles(
                            (docsHelper) -> {
                                final RootInfo root = docsHelper.getRoot(ROOT_0_ID);
                                docsHelper.createDocument(root, "application/pdf", TEST_FILE_NAME);
                            });

    @Before
    public void setUpTest() {
        bots.roots.closeDrawer();
    }

    // Ignore test as it's currently failing on some devices due to animation and timing.
    // TODO: b/423793930 make the test robust to UI timing
    @Ignore
    @Test
    public void testShareSheet_showsSelectedFilename() throws Exception {
        bots.directory.rightClickDocument(TEST_FILE_NAME);

        // Click the context menu item to open Share Sheet
        onView(withText("Share")).perform(click());

        // Ensure that Share Sheet opens and contains the filename preview
        UiObject2 textView = device.findObject(
                By.res("com.android.intentresolver:id/content_preview_filename")
        );
        assertNotNull(textView);

        // Ensure the filename preview is of the file we're sharing
        assertTrue(textView.wait(Until.textContains(TEST_FILE_NAME), 5000));
    }
}
