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

import static android.view.InputDevice.SOURCE_MOUSE;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;

import android.platform.test.annotations.DesktopTest;
import android.view.MotionEvent;

import androidx.test.filters.LargeTest;
import androidx.test.filters.Suppress;
import androidx.test.uiautomator.UiObjectNotFoundException;

import com.android.documentsui.bots.EspressoBotsKt;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@Ignore
public class SidebarUiTest extends ActivityTestJunit4<FilesActivity> {

    private static final String TAG = "RootUiTest";

    @Rule public final TestFilesRule mTestFilesRule = new TestFilesRule();

    void assertDefaultContentOfTestDir0() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_2);
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_NO_RENAME);
        bots.directory.assertDocumentsCount(4);
    }

    @DesktopTest(cujs = {"b/434065338", "b/434066029", "b/434065378"})
    @HugeLongTest
    public void testRootTapped_GoToRootFromChildDir() throws Exception {
        bots.directory.openDocument(TestFilesRule.DIR_NAME_1);
        bots.main.assertWindowTitle(TestFilesRule.DIR_NAME_1);
        bots.roots.openRoot(ROOT_0_ID);
        bots.main.assertWindowTitle(ROOT_0_ID);
        assertDefaultContentOfTestDir0();
    }

    @Suppress
    public void testRootChanged_ClearSelection() throws Exception {
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);
        bots.main.assertInActionMode(true);

        bots.roots.openRoot(ROOT_1_ID);
        bots.main.assertInActionMode(false);
    }

    @Test
    public void testPasteIntoFolderOnRoot() throws UiObjectNotFoundException {
        bots.main.switchToListMode();

        // Right click a file and copy it.
        onView(withText("file1.log")).perform(click(SOURCE_MOUSE, MotionEvent.BUTTON_SECONDARY));
        onView(withText("Copy")).perform(click());

        // Right click a root and try to paste the copied file into it.
        bots.roots.rightClickRootAndClickMenuOption(ROOT_1_ID, "Paste into folder");

        // Navigate to the root and ensure the file has been copied successfully.
        bots.roots.openRoot(ROOT_1_ID);
        bots.directory.waitForDocument("file1.log");
    }

    @Test
    public void testOpenInNewWindow_preservesFiles() throws Exception {
        // Select Recents in the existing window and open ROOT_0 in the new window so we can
        // distinguish the two windows by checking the title.
        bots.roots.openRoot("Recent");
        bots.main.assertWindowTitle("Recent");

        // Open the ROOT_0 node in a new window.
        bots.roots.rightClickRootAndClickMenuOption(ROOT_0_ID, "Open in new window");

        // Check in the new window the ROOT_0 is selected and the files inside matches the original
        // contents.
        bots.main.assertWindowTitle(ROOT_0_ID);
        assertDefaultContentOfTestDir0();
    }

    @Test
    public void testOpenInNewWindow_preservesFiles_espresso() throws Exception {
        // Select Recents in the existing window and open ROOT_0 in the new window so we can
        // distinguish the two windows by checking the title.
        EspressoBotsKt.openRoot(context, "Recent");
        bots.main.assertWindowTitle("Recent");

        // Open the ROOT_0 node in a new window.
        EspressoBotsKt.rightClickRootAndClickMenuOption(context, ROOT_0_ID, "Open in new window");

        // Check in the new window the ROOT_0 is selected and the files inside matches the original
        // contents.
        bots.main.assertWindowTitle(ROOT_0_ID);
        assertDefaultContentOfTestDir0();
    }
}
