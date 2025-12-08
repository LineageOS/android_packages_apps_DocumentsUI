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
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;
import static com.android.documentsui.conditions.HasChildCountCondition.hasMoreThanOneChild;
import static com.android.documentsui.conditions.HasChildCountCondition.hasNoChildren;
import static com.android.documentsui.conditions.HasChildCountCondition.hasOneChild;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.os.RemoteException;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.Settings;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.filters.LargeTest;
import androidx.test.filters.Suppress;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.Until;

import com.android.documentsui.actions.WaitForCheckState;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class SearchViewUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule();

    // UI timeout to wait for elements to appear, set to 5 seconds.
    private final int mTimeout = 5000;

    private String getDeviceLabel() {
        return Settings.Global.getString(context.getContentResolver(), Settings.Global.DEVICE_NAME);
    }

    @Before
    public void setUpTest() throws UiObjectNotFoundException, RemoteException {
        bots.roots.closeDrawer();

        // wait for a file to be present in default dir.
        bots.directory.waitForDocument(TestFilesRule.FILE_NAME_1);
    }

    @After
    public void tearDownTest() {
        // manually close activity to avoid SearchFragment show when Activity close. ref b/142840883
        Assert.assertNotNull(device);
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
    @DisableFlags({FLAG_USE_MATERIAL3}) // Enable when b/397315793 is fixed.
    public void testSearchView_ShouldHideOptionMenuOnExpanding() throws Exception {
        bots.search.expand();
        device.waitForIdle();

        bots.search.assertIsExpanded(true);
        bots.search.assertInputFocused(true);
        device.waitForIdle();

        assertFalse(bots.menu.hasMenuItem("Grid view"));
        assertFalse(bots.menu.hasMenuItem("List view"));
        assertEquals(!bots.search.isFullBarSearchViewEnabled(),
                bots.menu.hasMenuItemByDesc("More options"));
    }

    @Test
    public void testSearchView_CollapsesOnBack() throws Exception {
        bots.search.expand();
        device.pressBack();

        bots.search.assertIsExpanded(false);
    }

    @Test
    // TODO(b/414507592): Remove once recent searches is enabled again.
    @DisableFlags(FLAG_USE_MATERIAL3)
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
        if (!bots.search.showsDockedSearch()) {
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
        if (!bots.search.showsDockedSearch()) {
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
        bots.directory.assertDocumentsVisible(TestFilesRule.FILE_NAME_1, TestFilesRule.FILE_NAME_2);
    }

    @Test
    public void testSearch_NoResults() throws Exception {
        bots.search.expand();
        bots.search.setInputText("chocolate");

        bots.keyboard.pressEnter();
        device.waitForIdle(3000);

        bots.directory.waitAndAssertPlaceholderMessageText(
                String.format(context.getString(R.string.no_results), "TEST_ROOT_0"));
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
    @DisableFlags(FLAG_USE_MATERIAL3)
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
    @DisableFlags(FLAG_USE_MATERIAL3)
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
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchDropdowns() throws Exception {
        bots.search.expand();
        bots.search.setInputText("foo");
        // Verify that menu triggers (chips) are showing.
        bots.main.assertLocationTriggerShows();
        bots.main.assertLastModifiedTriggerShows();
        bots.main.assertFileTypeTriggerShows();
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2FileTypeDropdown() throws Exception {
        // Start search with term "file1" limiting results to images only.
        bots.search.expand();
        bots.search.setInputText("file");
        bots.keyboard.pressEnter();
        // Select images files only.
        bots.search.clickDropdownTrigger(R.id.search_file_type_trigger);
        bots.search.clickMenuItem(R.string.chip_title_images);

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);

        // There should be only file12.png left.
        device.waitForIdle();
        device.wait(Until.findObject(By.text(TestFilesRule.FILE_NAME_2).selected(false)), 5000);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastModifiedDropdown() throws Exception {
        // Start search with term "file1" limiting results modified in the last 30 days.
        bots.search.expand();
        bots.search.setInputText("file");
        bots.keyboard.pressEnter();
        bots.search.clickDropdownTrigger(R.id.search_last_modified_trigger);
        bots.search.clickMenuItem(R.string.search_last_modified_30_days);

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);
        device.waitForIdle();
        bots.directory.assertDocumentsCountOnList(true, 3);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2SearchLocationDropdown() throws Exception {
        // Start search with term "fred-dog", but rather than searching locally, search everywhere.
        bots.search.expand();
        bots.search.setInputText("fred-dog.jpg");
        bots.keyboard.pressEnter();
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);

        // Click Everywhere, to search everywhere.
        bots.search.clickMenuItem(R.string.search_location_everywhere);

        // Silence subsequent warnings about device being potentially null.
        Assert.assertNotNull(device);
        device.waitForIdle();
        bots.directory.assertDocumentsCountOnList(true, 1);
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2RootNameIsAdjusted() throws Exception {
        // The test starts in TEST_ROOT_0
        bots.search.expand();
        bots.search.setInputText("-no-such-file-");
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);
        // Check that the text in the dropdown window.
        bots.search.findMenuItem(R.string.search_location_everywhere).check(
                matches(isDisplayed()));
        onView(withText("TEST_ROOT_0")).inRoot(isPlatformPopup()).check(matches(isDisplayed()));
        // Click the "Everywhere" entry to hide the popup. This is needed for the bots to be able
        // to open the new root. But we also test that user choices are remembered.
        bots.search.clickMenuItem(R.string.search_location_everywhere);

        // Close the search view, to make sure that the directory drawer button becomes visible.
        bots.search.closeSearch();
        // Move to a different root.
        bots.roots.openRoot("Paging Root");

        // Start search, again.
        bots.search.expand();
        bots.search.setInputText("-no-such-file-");

        // Verify that that the location still shows "Everywhere".
        bots.search.findDropdownTrigger(R.id.search_location_trigger).check(
                matches(withText(R.string.search_location_everywhere)));

        // Click location trigger, and check that the root folder option is updated to Downloads.
        bots.search.clickDropdownTrigger(R.id.search_location_trigger);
        // Verify the dropdown menu to be updated.
        bots.search.findMenuItem(R.string.search_location_everywhere).check(
                matches(isDisplayed()));
        onView(withText("Paging Root")).inRoot(isPlatformPopup()).check(matches(isDisplayed()));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastModifiedDropdownVisibility() throws Exception {
        // Starts in TEST_ROOT_0. Start search and expect last modified dropdown to be visible.
        bots.search.expand();
        bots.search.setInputText("-no-such-file-");
        bots.search.findDropdownTrigger(R.id.search_last_modified_trigger).check(
                matches(isDisplayed()));

        // Close the search view, to make sure that the directory drawer button becomes visible.
        bots.search.closeSearch();
        // Move to the Recents view and expect the last modified to be gone.
        bots.roots.openRoot("Recent");
        bots.search.expand();
        bots.search.setInputText("-no-such-file-");
        onView(withId(R.id.search_last_modified_trigger)).check(matches(withEffectiveVisibility(
                ViewMatchers.Visibility.GONE)));

        // Close the search view, to make sure that the directory drawer button becomes visible.
        bots.search.closeSearch();
        // Move Downloads, repeat search, and expect the last modified trigger to be again visible.
        bots.roots.openRoot("Downloads");
        bots.search.expand();
        bots.search.setInputText("-no-such-file-");
        bots.search.findDropdownTrigger(R.id.search_last_modified_trigger).check(
                matches(isDisplayed()));
    }

    @Test
    @EnableFlags({FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testSearchV2LastUsedChipCopiedToFileTypeDropdown() throws Exception {
        // Click "Images" chip and wait until the chip becomes selected.
        bots.search.clickChip(R.string.chip_title_images)
                .perform(new WaitForCheckState(true, mTimeout));

        // Start search. Search text is not important.
        final String query = "irrelevant";
        bots.search.expand();
        bots.search.setInputText(query);

        // Verify that File Type trigger shows "Images" text.
        bots.search.findDropdownTrigger(R.id.search_file_type_trigger).check(
                matches(withText(R.string.chip_title_images)));

        // Clear the search text, to go back to directory listing, and wait for chips to show.
        bots.search.clickSearchViewClearButton();

        // Select Documents and Audio chips, in this order.
        bots.search.clickChip(R.string.chip_title_documents)
                .perform(new WaitForCheckState(true, mTimeout));
        bots.search.clickChip(R.string.chip_title_audio)
                .perform(new WaitForCheckState(true, mTimeout));

        bots.search.expand();
        bots.search.setInputText(query);
        bots.search.findDropdownTrigger(R.id.search_file_type_trigger).check(
                matches(withText(R.string.chip_title_audio)));

        // Clear the query again, to go back to chips.
        bots.search.clickSearchViewClearButton();
        // Uncheck Audio, and expect now Documents to be checked in file type dropdowns.
        bots.search.clickChip(R.string.chip_title_audio)
                .perform(new WaitForCheckState(false, mTimeout));

        // Enter the search query again, and verify that Documents file type is selected.
        bots.search.expand();
        bots.search.setInputText(query);
        bots.search.findDropdownTrigger(R.id.search_file_type_trigger).check(
                matches(withText(R.string.chip_title_documents)));
    }

    @Test
    @EnableFlags(FLAG_USE_MATERIAL3)
    @DisableFlags(FLAG_USE_SEARCH_V2_READ_ONLY)
    public void testSearchView_TogglingASearchChipClearsSelection() throws Exception {
        // Get the label of the device (this will be used to navigate to the ExternalStorageProvider
        // as the custom roots added for test do not show the search chips).
        String deviceLabel = getDeviceLabel();

        // Open the root and select the DCIM folder for selection.
        bots.roots.openRoot(deviceLabel);
        bots.directory.selectDocument("DCIM", 1);

        // Click on the Images search chips.
        bots.search.clickChip(R.string.chip_title_images);

        // Ensure the selection has cleared and the "1 file selected" text is not displayed.
        device.wait(Until.findObject(By.text(TestFilesRule.FILE_NAME_2).selected(false)), mTimeout);
        device.wait(Until.gone(By.text("1 selected")), mTimeout);
    }

    @Test
    @DisableFlags(
            FLAG_USE_MATERIAL3) // TODO(b/412895530): Enable for `use_material3` once fixed.
    public void testSelectionWhileSearchingHidesSearchBar() throws UiObjectNotFoundException {
        String pkg = bots.directory.mTargetPackage;

        // The `mTestFilesRule` creates more than 1 file, so ensure that that is the case before
        // proceeding.
        UiObject2 directoryList = device.findObject(By.res(pkg + ":id/dir_list"));
        directoryList.wait(hasMoreThanOneChild(), mTimeout);

        // Click the search icon and wait until the only result is the file that was searched for.
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

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testUnhandledRootsReturnEmptyCursors() throws UiObjectNotFoundException {
        String pkg = bots.directory.mTargetPackage;

        // Use Paging Root, as it throws:
        //     java.lang.UnsupportedOperationException: Search not supported
        bots.roots.openRoot("Paging Root");
        bots.search.expand();
        bots.search.setInputText("00");
        UiObject2 directoryList = device.findObject(By.res(pkg + ":id/dir_list"));
        directoryList.wait(hasNoChildren(), mTimeout);
        device.wait(Until.gone(By.displayId(R.id.progressbar)), mTimeout);
    }

    /**
     * Checks that we do not start searching until a non-null, not empty query is entered. This test
     * is limited to Search V2, as V1 shows a view with past search queries that hides the directory
     * listing. So while both searches behave in the same way, we can reliably verify it only in V2.
     */
    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testEmptyQueryShowsDirectoryListing() throws UiObjectNotFoundException {
        // Assert that we are in the correct location.
        bots.breadcrumb.assertItemsPresent(ROOT_0_ID);
        // Check the default content.
        assertDefaultContentOfTestDir0();
        // Open search, make sure query input field has focus.
        bots.search.expand();
        bots.search.assertInputFocused(true);
        // Try to hide the virtual keyboard; on small devices it can hide some files.
        Espresso.closeSoftKeyboard();
        // Check that the content of the current directory has not changed.
        assertDefaultContentOfTestDir0();
        // Enter an empty query.
        bots.search.setInputText("");
        Espresso.closeSoftKeyboard();
        // Check that the content of the current directory has not changed.
        assertDefaultContentOfTestDir0();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchVisibleInSearchableRootsOnly() throws UiObjectNotFoundException {
        // Starting in ROOT_ID_0, which is searchable.
        assertNotNull("Icon should be visible in ROOT_0_ID", bots.search.getSearchIcon());
        // Broken root cannot be searched.
        bots.roots.openRoot("Broken Root Doc");
        assertNull("Icon should not be visible ini Broken Root Doc", bots.search.getSearchIcon());
        // Device root should be searchable.
        String deviceLabel = getDeviceLabel();
        bots.roots.openRoot(deviceLabel);
        assertNotNull("Icon should be visible in " + deviceLabel, bots.search.getSearchIcon());
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchViewCollapsedOnSmallScreen() {
        assertNotNull(mActivityScenario);
        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity);
            Rect bounds = TestUtils.Companion.getActivityBounds(activity);
            Assume.assumeTrue(
                    "Skipping test: window size " + bounds.width() + "dp x " + bounds.height()
                            + "dp  is larger than 900dp x 600dp",
                    bounds.width() < 900.0 && bounds.height() < 600.0
            );
        });

        String pkg = bots.directory.mTargetPackage;
        UiObject2 searchBar = device.findObject(By.res(pkg + ":id/search_bar"));
        assertNull(searchBar);
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSearchViewExpandedOnLargeScreen() {
        assertNotNull(mActivityScenario);
        mActivityScenario.onActivity(activity -> {
            assertNotNull(activity);
            Rect bounds = TestUtils.Companion.getActivityBounds(activity);
            Assume.assumeTrue(
                    "Skipping test: window size " + bounds.width() + "dp x " + bounds.height()
                            + "dp  is smaller than 1000dp x 700dp",
                    bounds.width() >= 1000.0 && bounds.height() >= 700.0
            );
        });

        String pkg = bots.directory.mTargetPackage;
        UiObject2 searchBar = device.findObject(By.res(pkg + ":id/docked_search_text"));
        assertNotNull(searchBar);
        assertTrue(searchBar.isEnabled());
    }
}
