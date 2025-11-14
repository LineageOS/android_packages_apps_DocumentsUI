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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.conditions.HasChildCountCondition.hasMoreThanOneChild;
import static com.android.documentsui.conditions.HasChildCountCondition.hasOneChild;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import static org.hamcrest.CoreMatchers.allOf;
import static org.junit.Assert.assertFalse;

import android.os.RemoteException;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.Settings;

import androidx.test.filters.LargeTest;
import androidx.test.filters.Suppress;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.documentsui.actions.RelaxedClickAction;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.CheckAndForceMaterial3Flag;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class SearchViewUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule();

    // UI timeout to wait for elements to appear, set to 5 seconds.
    private final int mTimeout = 5000;

    @Before
    public void setUpTest() throws UiObjectNotFoundException, RemoteException {
        bots.roots.closeDrawer();

        // wait for a file to be present in default dir.
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
    }

    @After
    public void tearDownTest() {
        // manually close activity to avoid SearchFragment show when Activity close. ref b/142840883
        device.waitForIdle();
        device.pressBack();
        device.pressBack();
        device.pressBack();
    }

    private void assertDefaultContentOfTestDir0() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_2);
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_NO_RENAME);
        bots.directory.assertDocumentsCount(4);
    }

    private void assertDefaultContentOfTestDir1() throws UiObjectNotFoundException {
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_3);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_4);
        bots.directory.assertDocumentsCount(2);
    }

    @Test
    public void testSearchIconVisible() throws Exception {
        // The default root (root 0) supports search
        bots.search.assertIsExpanded(false);
    }

    @Test
    @HugeLongTest
    public void testSearchIconHidden() throws Exception {
        bots.roots.openRoot(ROOT_1_ID);  // root 1 doesn't support search

        bots.search.assertIsVisible(false);
    }

    @Test
    public void testSearchView_ExpandsOnClick() throws Exception {
        bots.search.expand();
        device.waitForIdle();

        bots.search.assertIsExpanded(true);
        bots.search.assertInputFocused(true);

        // FIXME: Matchers fail the not-present check if we've ever clicked this.
        // bots.search.assertIconVisible(false);
    }

    @Test
    @RequiresFlagsDisabled({FLAG_USE_MATERIAL3}) // Enable when b/397315793 is fixed.
    public void testSearchView_ShouldHideOptionMenuOnExpanding() throws Exception {
        bots.search.expand();
        device.waitForIdle();

        bots.search.assertIsExpanded(true);
        bots.search.assertInputFocused(true);
        device.waitForIdle();

        assertFalse(bots.menu.hasMenuItem("Grid view"));
        assertFalse(bots.menu.hasMenuItem("List view"));
        assertFalse(bots.menu.hasMenuItemByDesc("More options"));
    }

    @Test
    public void testSearchView_CollapsesOnBack() throws Exception {
        bots.search.expand();
        device.pressBack();

        bots.search.assertIsExpanded(false);
    }

    @Test
    // TODO(b/414507592): Remove once recent searches is enabled again.
    @RequiresFlagsDisabled(FLAG_USE_MATERIAL3)
    public void testSearchFragment_DismissedOnCloseAfterCancel() throws Exception {
        bots.search.expand();
        bots.search.setInputText("query text");

        // Cancel search
        device.pressBack();
        device.waitForIdle();

        // Close search
        device.pressBack();
        device.waitForIdle();

        bots.search.assertIsExpanded(false);
        bots.search.assertSearchHistoryVisible(false);
    }

    @Test
    public void testSearchView_ClearsTextOnBack() throws Exception {
        bots.search.expand();
        bots.search.setInputText("file2");

        device.pressBack();
        // When docked search is enable pressing back twice will kill the activity.
        if (!context.getResources().getBoolean(R.bool.show_docked_search)) {
            device.pressBack();
        }

        // Wait for a file in the default directory to be listed.
        bots.directory.waitForDocument(TestFilesRule.DIR_NAME_1);

        bots.search.assertIsExpanded(false);
    }

    @Test
    public void testSearchView_ClearsSearchOnBack() throws Exception {
        bots.search.expand();
        bots.search.setInputText("file1");
        bots.keyboard.pressEnter();
        device.waitForIdle();

        device.pressBack();

        bots.search.assertIsExpanded(false);
    }

    @Test
    public void testSearchView_ClearsAutoSearchOnBack() throws Exception {
        bots.search.expand();
        bots.search.setInputText("chocolate");
        //Wait for auto search result, it should be no results and show holder message.
        bots.directory.waitForHolderMessage();

        device.pressBack();
        // When docked search is enable pressing back twice will kill the activity.
        if (!context.getResources().getBoolean(R.bool.show_docked_search)) {
            device.pressBack();
        }

        bots.search.assertIsExpanded(false);
    }

    @Test
    public void testSearchView_StateAfterSearch() throws Exception {
        bots.search.expand();
        bots.search.setInputText("file1");
        bots.keyboard.pressEnter();
        device.waitForIdle();

        bots.search.assertInputEquals("file1");
    }

    @Test
    public void testSearch_ResultsFound() throws Exception {
        bots.search.expand();
        bots.search.setInputText("file1");
        bots.keyboard.pressEnter();

        bots.directory.assertDocumentsCountOnList(true, 2);
        bots.directory.assertDocumentsPresent(TestFilesRule.FILE_NAME_1, TestFilesRule.FILE_NAME_2);
    }

    @Test
    public void testSearch_NoResults() throws Exception {
        bots.search.expand();
        bots.search.setInputText("chocolate");

        bots.keyboard.pressEnter();
        device.waitForIdle(3000);

        String msg = String.valueOf(context.getString(R.string.no_results));
        bots.directory.assertPlaceholderMessageText(String.format(msg, "TEST_ROOT_0"));
    }

    @Suppress
    public void testSearchResultsFound_ClearsOnBack() throws Exception {
        bots.search.expand();
        bots.search.setInputText(TestFilesRule.FILE_NAME_1);

        bots.keyboard.pressEnter();
        device.pressBack();
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Suppress
    public void testSearchNoResults_ClearsOnBack() throws Exception {
        bots.search.expand();
        bots.search.setInputText("chocolate bunny");

        bots.keyboard.pressEnter();
        device.pressBack();
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Suppress
    public void testSearchResultsFound_ClearsOnDirectoryChange() throws Exception {
        // Skipping this test for phones since currently there's no way to open the drawer on
        // phones after doing a search (it's a back button instead of a hamburger button)
        if (!bots.main.inFixedLayout()) {
            return;
        }

        bots.search.expand();

        bots.search.setInputText(TestFilesRule.FILE_NAME_1);

        bots.keyboard.pressEnter();

        bots.roots.openRoot(ROOT_1_ID);
        device.waitForIdle();
        assertDefaultContentOfTestDir1();

        bots.roots.openRoot(ROOT_0_ID);
        device.waitForIdle();

        assertDefaultContentOfTestDir0();
    }

    @Test
    // TODO(b/414507592): Remove once recent searches is enabled again.
    @RequiresFlagsDisabled(FLAG_USE_MATERIAL3)
    public void testSearchHistory_showAfterSearchViewClear() throws Exception {
        bots.search.expand();
        bots.search.setInputText("chocolate");

        bots.keyboard.pressEnter();
        device.waitForIdle();

        bots.search.clickSearchViewClearButton();
        device.waitForIdle();

        bots.search.assertInputFocused(true);
        bots.search.assertSearchHistoryVisible(true);
    }

    @Test
    // TODO(b/414507592): Remove once recent searches is enabled again.
    @RequiresFlagsDisabled(FLAG_USE_MATERIAL3)
    public void testSearchView_focusClearedAfterSelectingSearchHistory() throws Exception {
        String queryText = "history";
        bots.search.expand();
        bots.search.setInputText(queryText);
        bots.keyboard.pressEnter();
        device.waitForIdle();

        bots.search.clickSearchViewClearButton();
        device.waitForIdle();
        bots.search.assertInputFocused(true);
        bots.search.assertSearchHistoryVisible(true);

        bots.search.clickSearchHistory(queryText);
        bots.search.assertInputFocused(false);
        bots.search.assertSearchHistoryVisible(false);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchDropdowns() throws Exception {
        bots.search.expand();
        bots.search.setInputText("foo");
        // Verify that menu triggers (chips) are showing.
        bots.main.assertLocationTriggerShows();
        bots.main.assertLastModifiedTriggerShows();
        bots.main.assertFileTypeTriggerShows();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2FileTypeDropdown() throws Exception {
        // Start search with term "file1" limiting results to images only.
        bots.search.expand();
        bots.search.setInputText("file");
        bots.keyboard.pressEnter();
        // Select images files only.
        onView(withId(R.id.search_file_type_trigger)).perform(click());
        onView(withText(R.string.chip_title_images)).inRoot(isPlatformPopup()).perform(click());

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);

        // There should be only file12.png left.
        device.waitForIdle();
        device.wait(Until.findObject(By.text(TestFilesRule.FILE_NAME_2).selected(false)), 5000);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastModifiedDropdown() throws Exception {
        // Start search with term "file1" limiting results modified in the last 30 days.
        bots.search.expand();
        bots.search.setInputText("file");
        bots.keyboard.pressEnter();
        onView(withId(R.id.search_last_modified_trigger)).perform(click());
        onView(withText(R.string.search_last_modified_30_days)).inRoot(isPlatformPopup()).perform(
                click());

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);
        device.waitForIdle();
        bots.directory.assertDocumentsCountOnList(true, 3);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2SearchLocationDropdown() throws Exception {
        // Start search with term "file1", but rather than searching locally, search everywhere.
        bots.search.expand();
        bots.search.setInputText("fred-dog.jpg");
        bots.keyboard.pressEnter();
        onView(withId(R.id.search_location_trigger)).perform(click());
        onView(withText(R.string.search_location_everywhere)).inRoot(isPlatformPopup()).perform(
                click());

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);
        device.waitForIdle();
        bots.directory.assertDocumentsCountOnList(true, 1);
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_MATERIAL3)
    @RequiresFlagsDisabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    public void testSearchView_TogglingASearchChipClearsSelection() throws Exception {
        // Get the label of the device (this will be used to navigate to the ExternalStorageProvider
        // as the custom roots added for test do not show the search chips).
        String deviceLabel =
                Settings.Global.getString(
                        context.getContentResolver(), Settings.Global.DEVICE_NAME);

        // Open the root and select the DCIM folder for selection.
        bots.roots.openRoot(deviceLabel);
        bots.directory.selectDocument("DCIM", 1);

        // Click on the Images search chips.
        onView(
                allOf(
                        withText("Images"),
                        isDescendantOfA(withId(R.id.search_chip_group)),
                        isDisplayed()))
                .perform(new RelaxedClickAction());

        // Ensure the selection has cleared and the "1 file selected" text is not displayed.
        device.wait(Until.findObject(By.text(TestFilesRule.FILE_NAME_2).selected(false)), mTimeout);
        device.wait(Until.gone(By.text("1 selected")), mTimeout);
    }

    @Test
    @RequiresFlagsDisabled(
            FLAG_USE_MATERIAL3) // TODO(b/412895530): Enable for `use_material3` once fixed.
    public void testSelectionWhileSearchingHidesSearchBar() throws UiObjectNotFoundException {
        String pkg = bots.directory.mTargetPackage;

        // The `mTestFilesRule` creates more than 1 file, so ensure that that is the case before
        // proceeding.
        UiObject2 directoryList = device.findObject(By.res(pkg + ":id/dir_list"));
        directoryList.wait(hasMoreThanOneChild(), mTimeout);

        // Click the search icon and wait until the only result is the file that was searced for.
        bots.search.expand();
        bots.search.setInputText(TestFilesRule.FILE_NAME_1);
        directoryList.wait(hasOneChild(), mTimeout);
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);

        // Select the document. This implicitly verifies that the bar at the top shows the text "1
        // selected" which, in all conditions, occludes the search input box.
        bots.directory.selectDocument(TestFilesRule.FILE_NAME_1, 1);

        // Deselect the document and click the clear button on the search view (if the selection bar
        // at the top is visible this won't be possible).
        bots.directory.clearSelection();
        bots.search.clickSearchViewClearButton();
        device.wait(Until.findObject(By.res(pkg + ":id/history_list")), mTimeout);
    }
}
