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
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.swipeRight;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import static org.hamcrest.Matchers.allOf;

import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.espresso.ViewInteraction;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.documentsui.R;
import com.android.documentsui.actions.RelaxedClickAction;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A test helper class that provides support for controlling and asserting against the roots list
 * drawer.
 */
public class SidebarBot extends Bots.BaseBot {
    private static final String TAG = "SidebarBot";

    private final String mRootListId;
    private final UiAutomation mAutomation;
    private final UiBot mUiBot;

    public SidebarBot(
            UiDevice device, UiAutomation automation, Context context, UiBot uiBot, int timeout) {
        super(device, context, timeout);
        mAutomation = automation;
        mUiBot = uiBot;
        mRootListId = mTargetPackage + ":id/roots_list";
    }

    private UiSelector getRootsContainerSelector() {
        final String containerId =
                this.inNavRailLayout() ? ":id/nav_rail_container_roots" : ":id/container_roots";
        return new UiSelector()
                .resourceId(mTargetPackage + containerId)
                .childSelector(new UiSelector().resourceId(mRootListId));
    }

    private boolean toolbarHasTitle(String title) {
        try {
            mUiBot.assertWindowTitle(title);
            return true;
        } catch (AssertionFailedError e) {
            return false;
        }
    }

    private UiObject findRoot(String label) throws UiObjectNotFoundException {
        // We might need to expand drawer if not visible.
        openDrawer();

        final UiSelector rootsList = getRootsContainerSelector();

        // Wait for the first list item to appear.
        new UiObject(rootsList.childSelector(new UiSelector())).waitForExists(mTimeout);

        // Now scroll around to find our item.
        new UiScrollable(rootsList).scrollIntoView(new UiSelector().text(label));
        // If the list is scrolled to the bottom, there may be a bottom bounce animation, which
        // makes clicks fail. Wait for the animation to settle before returning the UI element so
        // the caller can interact with it reliably.
        SystemClock.sleep(500);
        return new UiObject(rootsList.childSelector(new UiSelector().text(label)));
    }

    /** Open navigation root either from the Drawer or the Navigation rail. */
    public void openRoot(String label) throws UiObjectNotFoundException {
        if (toolbarHasTitle(label)) {
            return;
        }
        assertTrue("Failed to click on root: " + label, findRoot(label).click());
        mUiBot.assertWindowTitle(label);
    }

    /**
     * Use openRoot above for general usage, which caters both the navigation rail and the drawer,
     * only use openNavRailRoot if you want to open root explicitly from the navigation rail.
     */
    public void openNavRailRoot(String label) throws UiObjectNotFoundException {
        // Use UiScrollable to scroll into the view.
        final UiSelector rootsList = getRootsContainerSelector();
        new UiObject(rootsList.childSelector(new UiSelector())).waitForExists(mTimeout);
        new UiScrollable(rootsList).scrollIntoView(new UiSelector().text(label));

        // Use Espresso to click.
        onView(allOf(withText(label), isDescendantOfA(withId(R.id.nav_rail_container_roots))))
                .perform(click());
    }

    /** Open navigation drawer from the burger menu button within the navigation rail layout. */
    public void openDrawerFromNavRail() {
        onView(withId(R.id.nav_rail_burger_menu)).perform(click());
    }

    public void openDrawer() throws UiObjectNotFoundException {
        // In drawer layout we explicitly open the drawer by clicking the burger menu in the
        // toolbar, in other layouts we do nothing because the nav sidebar is shown by default.
        if (!this.inDrawerLayout()) {
            return;
        }

        final UiSelector hamburgerSelector =
                new UiSelector()
                        .resourceId(mTargetPackage + ":id/toolbar")
                        .childSelector(
                                new UiSelector()
                                        .className("android.widget.ImageButton")
                                        .description(mContext.getString(R.string.drawer_open))
                                        .clickable(true));
        UiObject hamburgerButton = mDevice.findObject(hamburgerSelector);
        assertTrue("Hamburger button is NOT present",
                hamburgerButton.waitForExists(mTimeout));
        hamburgerButton.click();

        // Wait for the roots to appear and fail if it doesn't.
        assertTrue(mDevice.findObject(getRootsContainerSelector()).waitForExists(mTimeout));
    }

    public void closeDrawer() {
        // Espresso will try to close the drawer if it's opened
        // But if no drawer exists (Tablet devices), we will have to catch the exception
        // and continue on the test
        // Why can't we do something like .exist() first?
        // http://stackoverflow.com/questions/20807131/espresso-return-boolean-if-view-exists
        try {
            if (mContext.getResources().getConfiguration()
                    .getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                onView(withId(R.id.drawer_layout)).perform(swipeRight());
            } else {
                onView(withId(R.id.drawer_layout)).perform(swipeLeft());
            }
        } catch (Exception e) {
            Log.e(TAG, "Cannot close drawer", e);
        }
    }

    public void assertRootsPresent(String... labels) throws UiObjectNotFoundException {
        List<String> missing = new ArrayList<>();
        for (String label : labels) {
            if (!findRoot(label).exists()) {
                missing.add(label);
            }
        }
        if (!missing.isEmpty()) {
            Assert.fail(
                    "Expected roots " + Arrays.asList(labels) + ", but missing " + missing);
        }
    }

    public void assertRootsAbsent(String... labels) throws UiObjectNotFoundException {
        List<String> unexpected = new ArrayList<>();
        for (String label : labels) {
            if (findRoot(label).exists()) {
                unexpected.add(label);
            }
        }
        if (!unexpected.isEmpty()) {
            Assert.fail("Unexpected roots " + unexpected);
        }
    }

    public void assertHasFocus() {
        assertHasFocus(mRootListId);
    }

    /** Right clicks a root with `label` and then clicks the `menuOption`. */
    public void rightClickRootAndClickMenuOption(String rootLabel, String menuOption)
            throws UiObjectNotFoundException {
        Rect point = findRoot(rootLabel).getVisibleBounds();

        // The RootsFragment listens to right clicks in the GenericMotionListener. This is to allow
        // for a left and right click to be used interchangeably. This means to mock this behaviour,
        // 4 input events needs to be synthesized. A down, button press, button release and an up.
        MotionEvent motionDown =
                getTestRightClickMotionEvent(
                        MotionEvent.ACTION_DOWN, point.centerX(), point.centerY());
        mAutomation.injectInputEvent(motionDown, true);
        SystemClock.sleep(25);

        MotionEvent motionButtonPress =
                getTestRightClickMotionEvent(
                        MotionEvent.ACTION_BUTTON_PRESS, point.centerX(), point.centerY());
        mAutomation.injectInputEvent(motionButtonPress, true);
        SystemClock.sleep(25);

        MotionEvent motionButtonRelease =
                getTestRightClickMotionEvent(
                        MotionEvent.ACTION_BUTTON_RELEASE, point.centerX(), point.centerY());
        mAutomation.injectInputEvent(motionButtonRelease, true);
        SystemClock.sleep(25);

        MotionEvent motionUp =
                getTestRightClickMotionEvent(
                        MotionEvent.ACTION_UP, point.centerX(), point.centerY());
        mAutomation.injectInputEvent(motionUp, true);

        mDevice.waitForIdle();

        ViewInteraction menuItem = waitForContextMenuItemToAppear(menuOption);
        assertNotNull("Context menu item " + menuOption + " not found", menuItem);
        menuItem.perform(new RelaxedClickAction());
    }
}
