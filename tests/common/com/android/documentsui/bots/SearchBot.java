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

package com.android.documentsui.bots;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.util.Material3Config.getRes;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;

import android.content.Context;
import android.view.View;

import androidx.annotation.IdRes;
import androidx.annotation.StringRes;
import androidx.test.espresso.ViewInteraction;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.documentsui.R;
import com.android.documentsui.actions.RelaxedClickAction;
import com.android.documentsui.actions.WaitUntilVisible;

import org.hamcrest.Matcher;

/**
 * A test helper class that provides support for controlling the search UI
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class SearchBot extends Bots.BaseBot {

    // Base search layout changes substantially between Ryu and Angler.
    @SuppressWarnings("unchecked")
    private static final Matcher<View> SEARCH_WIDGET = allOf(
            withId(R.id.option_menu_search),
            anyOf(isClickable(), hasDescendant(isClickable())));

    public SearchBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    /**
     * @return Whether or not the search icon (magnifying glass) is present and enabled.
     */
    public UiObject2 getSearchIcon() {
        String resId = mTargetPackage + (showsDockedSearch() ? ":id/docked_search_toolbar"
                : ":id/option_menu_search");
        return find(By.res(resId));
    }

    public void expand() throws UiObjectNotFoundException {
        if (!showsDockedSearch()) {
            UiObject searchView = findObject(mTargetPackage + ":id/option_menu_search");
            searchView.click();
        } else {
            findDockedSearchInput().click();
        }
    }

    public void clickSearchViewClearButton() throws UiObjectNotFoundException {
        if (!showsDockedSearch()) {
            UiObject clear = findObject(mTargetPackage + ":id/option_menu_search",
                    mTargetPackage + ":id/search_close_btn");
            clear.click();
        } else {
            UiObject clear = findObject(mTargetPackage + ":id/docked_search_toolbar",
                    mTargetPackage + ":id/docked_search_clear");
            clear.click();
        }
    }

    // Click on the search history item with specified queryText, if exists.
    public void clickSearchHistory(String queryText) throws UiObjectNotFoundException {
        UiObject history = findSearchHistoryView();
        UiSelector historyItemSelector = new UiSelector().text(queryText);
        mDevice.findObject(history.getSelector().childSelector(historyItemSelector)).click();
    }

    public void setInputText(String query) throws UiObjectNotFoundException {
        if (!showsDockedSearch()) {
            BySelector selector = By.res(mTargetPackage + ":id/search_src_text");
            mDevice.wait(Until.findObject(selector), 5000);
            onView(allOf(withId(androidx.appcompat.R.id.search_src_text), isDisplayed()))
                    .perform(typeText(query));
        } else {
            BySelector selector = By.res(mTargetPackage + ":id/docked_search_text");
            mDevice.wait(Until.findObject(selector), 5000);
            onView(allOf(withId(R.id.docked_search_text), isDisplayed())).perform(typeText(query));
        }
    }

    public void assertIsExpanded(boolean expanded) throws UiObjectNotFoundException {
        if (!showsDockedSearch()) {
            assertIconVisible(!expanded);
            assertEquals(expanded, findSearchViewTextField().exists());
        } else {
            assertTrue(findDockedSearchInput().exists());
        }
    }

    public void assertIsVisible(boolean visible) throws UiObjectNotFoundException {
        if (!showsDockedSearch()) {
            assertIconVisible(visible);
            // Only look for input when asserting invisible. Input visibility is checked with
            // assertIsExpanded.
            if (!visible) {
                assertFalse(findSearchViewTextField().exists());
            }
        } else {
            assertEquals(visible, findDockedSearchInput().exists());
        }
    }

    public void assertIconVisible(boolean visible) {
        if (visible) {
            assertTrue(
                    "Search icon should be visible.",
                    Matchers.present(SEARCH_WIDGET));
        } else {
            assertFalse(
                    "Search icon should not be visible.",
                    Matchers.present(SEARCH_WIDGET));
        }
    }

    public void assertSearchHistoryVisible(boolean visible) {
        if (visible) {
            assertTrue(
                    "Search fragment should be shown.",
                    findSearchHistoryView().exists());
        } else {
            assertFalse(
                    "Search fragment should be dismissed.",
                    findSearchHistoryView().exists());
        }
    }

    public void assertInputEquals(String query)
            throws UiObjectNotFoundException {
        UiObject textField;
        if (showsDockedSearch()) {
            textField = findDockedSearchInput();
        } else {
            textField = findSearchViewTextField();
        }

        assertTrue(textField.exists());
        assertEquals(query, textField.getText());
    }

    public void assertInputFocused(boolean focused)
            throws UiObjectNotFoundException {
        UiObject textField;
        if (showsDockedSearch()) {
            textField = findDockedSearchInput();
        } else {
            textField = findSearchViewTextField();
        }

        assertTrue(textField.exists());
        assertEquals(focused, textField.isFocused());
    }

    private UiObject findSearchHistoryView() {
        return findObject(mTargetPackage + ":id/history_list");
    }

    private UiObject findSearchViewTextField() {
        return findObject(mTargetPackage + ":id/option_menu_search",
                mTargetPackage + ":id/search_src_text");
    }

    private UiObject findDockedSearchInput() {
        return findObject(mTargetPackage + ":id/docked_search_text");
    }

    /** Whether the UI is using the docked search. */
    public boolean showsDockedSearch() {
        return mContext.getResources().getBoolean(getRes(R.bool.show_docked_search));
    }

    /** Whether or not the UI is using full bar for search view. */
    public boolean isFullBarSearchViewEnabled() {
        return mContext.getResources().getBoolean(getRes(R.bool.full_bar_search_view));
    }

    /**
     * Returns the view interaction for the chip with the given text, specified by the ID. Chips
     * and dropdowns are dynamically added, so we wait for the chip to become visible.
     * @param chipTextId The string ID of the chip text.
     * @return The view interaction corresponding to the chip with the given ID.
     */
    public ViewInteraction findChip(@StringRes int chipTextId) {
        return onView(allOf(withText(chipTextId),
                isDescendantOfA(withId(R.id.search_chip_group)))).perform(
                        new WaitUntilVisible(mTimeout)).perform(scrollTo());
    }

    /**
     * Waits for a chip to become visible, scrolls to it, and then performs a relaxed click.
     * @param chipTextId The ID of the text associated with the chip.
     * @return The view interaction corresponding to the chip with the given ID.
     */
    public ViewInteraction clickChip(@StringRes int chipTextId) {
        return findChip(chipTextId).perform(new RelaxedClickAction());
    }

    /**
     * Finds the dropdown trigger with the given ID. This method waits until the dropdown becomes
     * visible, and scrolls to it so that it is fully displayed.
     * @param dropdownId The ID of the dropdown.
     * @return The view interaction associated with this dropdown.
     */
    public ViewInteraction findDropdownTrigger(@IdRes int dropdownId) {
        return onView(withId(dropdownId)).perform(new WaitUntilVisible(mTimeout)).perform(
                scrollTo());
    }

    /**
     * Clicks the dropdown with the given ID. This method uses #findDropdownTrigger to make sure
     * that the dropdown is visible and displayed.
     * @param dropdownId The ID of the dropdown.
     */
    public void clickDropdownTrigger(@IdRes int dropdownId) {
        findDropdownTrigger(dropdownId).perform(new RelaxedClickAction());
    }

    /**
     * Finds the menu item with text with the given ID. This method waits until the tem becomes
     * visible, and scrolls to it so that it is fully displayed.
     * @param menuId The ID of the text shown in the menu item.
     * @return The view interaction associated with this menu item.
     */
    public ViewInteraction findMenuItem(@StringRes int menuId) {
        return onView(withText(menuId)).inRoot(isPlatformPopup()).perform(
                new WaitUntilVisible(mTimeout)).perform(scrollTo());
    }

    /**
     * Clicks the menu item with text with the given ID. This method uses #findMenuItem to make sure
     * that the menu item is visible and displayed.
     * @param menuId The ID of the text shown in the menu item.
     */
    public void clickMenuItem(@StringRes int menuId) {
        findMenuItem(menuId).perform(new RelaxedClickAction());
    }

    /**
     * Clears the search query and, if in a drawer layout, closes the search view.
     * @throws UiObjectNotFoundException If it is unable to find the clear button.
     */
    public void closeSearch() throws UiObjectNotFoundException {
        clickSearchViewClearButton();
        if (inDrawerLayout()) {
            // If the search is not docked, we also need to click the button that collapses
            // the search view.
            onView(allOf(withContentDescription("Collapse"), isDescendantOfA(withId(R.id.toolbar))))
                    .perform(click());
        }
    }
}
