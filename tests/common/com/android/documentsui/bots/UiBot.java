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

package com.android.documentsui.bots;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasFocus;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withClassName;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.endsWith;

import android.content.Context;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.BoundedMatcher;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.documentsui.R;

import com.google.android.material.appbar.MaterialToolbar;

import org.hamcrest.Description;
import org.hamcrest.Matcher;

import java.util.Iterator;
import java.util.List;

/**
 * A test helper class that provides support for controlling DocumentsUI activities
 * programmatically, and making assertions against the state of the UI.
 * <p>
 * Support for working directly with Roots and Directory view can be found in the respective bots.
 */
public class UiBot extends Bots.BaseBot {

    @SuppressWarnings("unchecked")
    private static final Matcher<View> TOOLBAR = allOf(
            isAssignableFrom(Toolbar.class),
            withId(R.id.toolbar));
    @SuppressWarnings("unchecked")
    private static final Matcher<View> TEXT_ENTRY = allOf(
            withClassName(endsWith("EditText")));
    @SuppressWarnings("unchecked")
    private static final Matcher<View> TOOLBAR_OVERFLOW = allOf(
            withClassName(endsWith("OverflowMenuButton")),
            ViewMatchers.isDescendantOfA(TOOLBAR));
    @SuppressWarnings("unchecked")

    public static String targetPackageName;

    public UiBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
        targetPackageName =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    }

    private static Matcher<Object> withToolbarTitle(final Matcher<CharSequence> textMatcher) {
        return new BoundedMatcher<Object, Toolbar>(Toolbar.class) {
            @Override
            public boolean matchesSafely(Toolbar toolbar) {
                return textMatcher.matches(toolbar.getTitle());
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("with toolbar title: ");
                textMatcher.describeTo(description);
            }

            @Override
            public void describeMismatch(Object item, Description description) {
                super.describeMismatch(item, description);
                if (item != null && item instanceof Toolbar) {
                    Toolbar toolbar = (Toolbar) item;
                    description.appendText(
                            "unexpected toolbar title: \"" + toolbar.getTitle() + "\"");
                }
            }
        };
    }

    public void assertWindowTitle(String expected) {
        mDevice.waitForIdle(mTimeout);
        if (!isUseMaterial3FlagEnabled() && expected.equals("Recent")) {
            // When m3 is off and `show_search_bar` is on, the toolbar on the "recent files" screen
            // on phones contains a search bar instead of a title, so let's check the `header_title`
            // instead. NOTE: header_title doesn't exist in m3.
            onView(withId(R.id.header_title)).check(matches(withText("Recent files")));
            return;
        }
        onView(TOOLBAR).check(matches(withToolbarTitle(is(expected))));
    }

    /**
     * Checks that the search bar is visible.
     */
    public void assertSearchBarShow() {
        onView(withId(R.id.searchbar_title)).check(matches(isDisplayed()));
    }

    /**
     * Checks that the docked search bar is visible.
     */
    public void assertDockedSearchBarShow() {
        onView(withId(R.id.docked_search_toolbar)).check(matches(isDisplayed()));
    }

    /**
     * Checks that the search menu item is visible.
     */
    public void assertOptionsMenuSearchShow() {
        onView(withId(R.id.option_menu_search)).check(matches(isDisplayed()));
    }

    /**
     * Checks that the search bar is not visible.
     */
    public void assertSearchBarGone() {
        onView(withId(R.id.searchbar_title)).check(
                matches(withEffectiveVisibility(ViewMatchers.Visibility.GONE)));
    }

    /**
     * Checks that the UI chip that toggles location search menu is visible.
     */
    public void assertLocationTriggerShows() {
        onView(withId(R.id.search_location_trigger)).check(matches(isDisplayed()));
    }

    /**
     * Checks that the UI chip that toggles last modified menu is visible.
     */
    public void assertLastModifiedTriggerShows() {
        onView(withId(R.id.search_last_modified_trigger)).check(matches(isDisplayed()));
    }

    /**
     * Checks that the UI chip that toggles file type menu is visible.
     */
    public void assertFileTypeTriggerShows() {
        onView(withId(R.id.search_file_type_trigger)).check(matches(isDisplayed()));
    }

    public void assertMenuEnabled(int id, boolean enabled) {
        UiObject2 menu = findMenuWithName(mContext.getString(id));
        if (enabled) {
            assertNotNull(menu);
            assertEquals(enabled, menu.isEnabled());
        } else {
            assertNull(menu);
        }
    }

    public void assertInActionMode(boolean inActionMode) {
        assertEquals(inActionMode, waitForActionModeBarToAppear());
    }

    public UiObject openOverflowMenu() throws UiObjectNotFoundException {
        UiObject obj = findMenuMoreOptions();
        obj.click();
        mDevice.waitForIdle(mTimeout);
        return obj;
    }

    public void setDialogText(String text) throws UiObjectNotFoundException {
        onView(TEXT_ENTRY)
                .perform(ViewActions.replaceText(text));
    }

    public void assertDialogText(String expected) throws UiObjectNotFoundException {
        onView(TEXT_ENTRY)
                .check(matches(withText(is(expected))));
    }

    /**
     * Checks that the current view state is in list mode.
     */
    public void assertInListMode() {
        // In list mode, there should be the grid mode button that is visible.
        final UiObject2 gridModeBtn = menuGridMode();
        assertNotNull(gridModeBtn);
    }

    /**
     * Checks that the current view state is in grid mode.
     */
    public void assertInGridMode() {
        // In grid mode, there should be the list mode button that is visible.
        final UiObject2 listModeBtn = menuListMode();
        assertNotNull(listModeBtn);
    }

    public void switchToListMode() {
        final UiObject2 listMode = menuListMode();
        if (listMode != null) {
            listMode.click();
        }
    }

    public void clickActionItem(String label) throws UiObjectNotFoundException {
        if (!waitForActionModeBarToAppear()) {
            throw new UiObjectNotFoundException("ActionMode bar not found");
        }
        clickActionbarOverflowItem(label);
        mDevice.waitForIdle();
    }

    public void switchToGridMode() {
        final UiObject2 gridMode = menuGridMode();
        if (gridMode != null) {
            gridMode.click();
        }
    }

    UiObject2 menuGridMode() {
        // Note that we're using By.desc rather than By.res, because of b/25285770
        return find(By.desc("Grid view"));
    }

    UiObject2 menuListMode() {
        // Note that we're using By.desc rather than By.res, because of b/25285770
        return find(By.desc("List view"));
    }

    public void clickToolbarItem(int id) {
        onView(withId(id)).perform(clickAndRetryOnLongPress());
    }

    private Matcher<View> getActionbarOverflow() {
        final Matcher<View> actionBar =
                isUseMaterial3FlagEnabled()
                        ? allOf(isAssignableFrom(MaterialToolbar.class), withId(R.id.selection_bar))
                        : allOf(withClassName(endsWith("ActionBarContextView")));
        return allOf(
                withClassName(endsWith("OverflowMenuButton")),
                ViewMatchers.isDescendantOfA(actionBar));
    }

    public void clickActionbarOverflowItem(String label) {
        onView(getActionbarOverflow()).perform(clickAndRetryOnLongPress());
        mDevice.waitForIdle();
        // Click the item by label, since Espresso doesn't support lookup by id on overflow.
        onView(withText(label)).perform(click());
    }

    public void clickToolbarOverflowItem(String label) {
        onView(TOOLBAR_OVERFLOW).perform(clickAndRetryOnLongPress());
        // Click the item by label, since Espresso doesn't support lookup by id on overflow.
        onView(withText(label)).perform(click());
    }

    public boolean waitForActionModeBarToAppear() {
        String actionModeId = isUseMaterial3FlagEnabled() ? "selection_bar" : "action_mode_bar";
        UiObject2 bar =
                mDevice.wait(
                        Until.findObject(By.res(mTargetPackage + ":id/" + actionModeId)), mTimeout);
        return (bar != null);
    }

    /**
     * Waits for the job progress toolbar icon to become visible.
     */
    public boolean waitForJobProgressToolbarIconToAppear() {
        return mDevice.wait(
                Until.findObject(By.res(mTargetPackage + ":id/job_progress_toolbar_indicator")),
                mTimeout
        ) != null;
    }

    public void clickRename() throws UiObjectNotFoundException {
        if (!waitForActionModeBarToAppear()) {
            throw new UiObjectNotFoundException("ActionMode bar not found");
        }
        clickActionbarOverflowItem(mContext.getString(R.string.menu_rename));
        mDevice.waitForIdle();
    }

    public void clickDelete() throws UiObjectNotFoundException {
        if (!waitForActionModeBarToAppear()) {
            throw new UiObjectNotFoundException("ActionMode bar not found");
        }
        if (isTrashFlowEnabled()) {
            clickActionItem("Delete permanently");
        } else {
            clickToolbarItem(R.id.action_menu_delete);
        }
        mDevice.waitForIdle();
    }

    public UiObject findDownloadRetryDialog() {
        UiSelector selector = new UiSelector().text("Couldn't download");
        UiObject title = mDevice.findObject(selector);
        title.waitForExists(mTimeout);
        return title;
    }

    public UiObject findFileRenameDialog() {
        UiSelector selector = new UiSelector().text("Rename");
        UiObject title = mDevice.findObject(selector);
        title.waitForExists(mTimeout);
        return title;
    }

    public UiObject findRenameErrorMessage() {
        UiSelector selector = new UiSelector().text(mContext.getString(R.string.name_conflict));
        UiObject title = mDevice.findObject(selector);
        title.waitForExists(mTimeout);
        return title;
    }

    @SuppressWarnings("unchecked")
    public void assertDialogOkButtonFocused() {
        onView(withId(android.R.id.button1)).check(matches(hasFocus()));
    }

    /** Clicks the OK button on a dialog. */
    public void clickDialogOkButton(boolean closeSoftKeyboard) throws UiObjectNotFoundException {
        // On dialogs with no text input, a soft keyboard doesn't show up at all and attempting to
        // close it causes failures. Let's be intentional about the closure only on dialogs which
        // have text input.
        if (closeSoftKeyboard) {
            // Espresso has flaky results when keyboard shows up, so hiding it for now
            // before trying to click on any dialog button
            Espresso.closeSoftKeyboard();
        }

        final UiObject2 button = mDevice.wait(Until.findObject(By.res("android:id/button1")),
                mTimeout);
        if (button == null) throw new UiObjectNotFoundException("Cannot find an 'OK' button");
        button.click();
    }

    /** Clicks the Cancel button on a dialog. */
    public void clickDialogCancelButton(boolean closeSoftKeyboard)
            throws UiObjectNotFoundException {
        // On dialogs with no text input, a soft keyboard doesn't show up at all and attempting to
        // close it causes failures. Let's be intentional about the closure only on dialogs which
        // have text input.
        if (closeSoftKeyboard) {
            // Espresso has flaky results when keyboard shows up, so hiding it for now
            // before trying to click on any dialog button
            Espresso.closeSoftKeyboard();
        }

        final UiObject2 button = mDevice.wait(Until.findObject(By.res("android:id/button2")),
                mTimeout);
        if (button == null) throw new UiObjectNotFoundException("Cannot find a 'Cancel' button");
        button.click();
    }

    public UiObject findMenuLabelWithName(String label) {
        UiSelector selector = new UiSelector().text(label);
        return mDevice.findObject(selector);
    }

    UiObject2 findMenuWithName(String label) {
        UiObject2 list = mDevice.findObject(By.clazz("android.widget.ListView"));
        List<UiObject2> menuItems = list.getChildren();
        Iterator<UiObject2> it = menuItems.iterator();

        UiObject2 menuItem = null;
        while (it.hasNext()) {
            menuItem = it.next();
            UiObject2 text = menuItem.findObject(By.text(label));
            if (text != null) {
                return menuItem;
            }
        }
        return null;
    }

    boolean hasMenuWithName(String label) {
        return findMenuWithName(label) != null;
    }

    UiObject findMenuMoreOptions() {
        UiSelector selector = new UiSelector().className("android.widget.ImageView")
                .descriptionContains("More options");
        // TODO: use the system string ? android.R.string.action_menu_overflow_description
        return mDevice.findObject(selector);
    }
}
