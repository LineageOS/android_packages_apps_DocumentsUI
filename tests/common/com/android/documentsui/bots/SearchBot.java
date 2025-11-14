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
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;

import android.content.Context;
import android.view.View;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.documentsui.R;

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
            UiObject clear = findObject(mTargetPackage + ":id/option_menu_docked_search",
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

    private boolean showsDockedSearch() {
        return mContext.getResources().getBoolean(R.bool.show_docked_search);
    }
}
