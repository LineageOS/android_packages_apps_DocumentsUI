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
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isPlatformPopup;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static junit.framework.Assert.assertNotNull;

import android.app.UiAutomation;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import androidx.test.InstrumentationRegistry;
import androidx.test.espresso.NoMatchingViewException;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.documentsui.actions.DoNothingAction;
import com.android.documentsui.utils.LayoutUtilsKt;

import junit.framework.AssertionFailedError;

import org.hamcrest.Matcher;

/** Handy collection of bots for working with Files app. */
public final class Bots {

    private static final String TAG = "Bots";
    private static final int TIMEOUT = 15000;

    public final BreadBot breadcrumb;
    public final DirectoryListBot directory;
    public final SortBot sort;
    public final KeyboardBot keyboard;
    public final SidebarBot roots;
    public final SearchBot search;
    public final GestureBot gesture;
    public final MenuBot menu;
    public final UiBot main;
    public final InspectorBot inspector;
    public final NotificationsBot notifications;
    public final PickerBot picker;

    public Bots(UiDevice device, UiAutomation automation, Context context, int timeout) {
        main = new UiBot(device, context, TIMEOUT);
        breadcrumb = new BreadBot(device, context, TIMEOUT);
        roots = new SidebarBot(device, automation, context, main, TIMEOUT);
        directory = new DirectoryListBot(device, automation, context, TIMEOUT);
        sort = new SortBot(device, context, TIMEOUT, main);
        keyboard = new KeyboardBot(device, context, TIMEOUT);
        search = new SearchBot(device, context, TIMEOUT);
        gesture = new GestureBot(device, automation, context, TIMEOUT);
        menu = new MenuBot(device, context, TIMEOUT);
        inspector = new InspectorBot(device, context, TIMEOUT);
        notifications = new NotificationsBot(device, context, TIMEOUT);
        picker = new PickerBot(device, context, TIMEOUT);
    }

    /**
     * A test helper class that provides support for controlling directory list
     * and making assertions against the state of it.
     */
    public static abstract class BaseBot {
        public final UiDevice mDevice;
        public final String mTargetPackage;
        final Context mContext;
        final int mTimeout;

        BaseBot(UiDevice device, Context context, int timeout) {
            mDevice = device;
            mContext = context;
            mTimeout = timeout;
            mTargetPackage =
                    InstrumentationRegistry.getInstrumentation()
                            .getTargetContext().getPackageName();
        }

        /**
         * Returns a `MotionEvent` that mocks a right click.
         * There are 2 ways right clicks are intercepted throughout DocumentsUI:
         *   1. Via an onClickListener and thus the actions can simply be one of ACTION_DOWN and
         *      ACTION_UP.
         *   2. Via an onGenericMotionListener and therefore there needs to be 4 actions,
         *      ACTION_DOWN, ACTION_BUTTON_PRESS, ACTION_BUTTON_RELEASE and ACTION_UP.
         */
        protected static MotionEvent getTestRightClickMotionEvent(int action, int x, int y) {
            long eventTime = SystemClock.uptimeMillis();

            MotionEvent.PointerProperties[] pp = {new MotionEvent.PointerProperties()};
            pp[0].clear();
            pp[0].id = 0;
            pp[0].toolType = MotionEvent.TOOL_TYPE_MOUSE;

            MotionEvent.PointerCoords[] pointerCoords = {new MotionEvent.PointerCoords()};
            pointerCoords[0].clear();
            pointerCoords[0].x = x;
            pointerCoords[0].y = y;
            pointerCoords[0].pressure = 0;
            pointerCoords[0].size = 1;

            MotionEvent event =
                    MotionEvent.obtain(
                            eventTime,
                            eventTime,
                            action,
                            1, // pointerCount.
                            pp,
                            pointerCoords,
                            0, // metaState.
                            MotionEvent.BUTTON_SECONDARY,
                            1f, // xPrecision.
                            1f, // yPrecision.
                            0, // deviceId.
                            0, // edgeFlags.
                            android.view.InputDevice.SOURCE_MOUSE,
                            0 // flags.
                    );

            if (action == MotionEvent.ACTION_BUTTON_PRESS
                    || action == MotionEvent.ACTION_BUTTON_RELEASE) {
                event.setActionButton(MotionEvent.BUTTON_SECONDARY);
            }

            return event;
        }

        /**
         * Attempts a click, retrying if a long press occurs by mistake.
         */
        protected ViewAction clickAndRetryOnLongPress() {
            return clickAndRetryOnLongPress(new DoNothingAction());
        }

        /**
         * Attempts a click, retrying if a long press occurs by mistake. Executes the rollbackAction
         * to undo the effect of the long press before retrying.
         *
         * @param rollbackAction is the action to be performed before retrying the click, if a long
         *                       press was accidentally executed.
         */
        protected ViewAction clickAndRetryOnLongPress(ViewAction rollbackAction) {
            return ViewActions.click(rollbackAction);
        }

        /**
         * Asserts that the specified view or one of its descendents has focus.
         */
        protected void assertHasFocus(String resourceName) {
            UiObject2 candidate = mDevice.findObject(By.res(resourceName));
            assertNotNull("Expected " + resourceName + " to have focus, but it didn't.",
                    candidate.findObject(By.focused(true)));
        }

        protected UiObject2 find(BySelector selector) {
            mDevice.wait(Until.findObject(selector), mTimeout);
            return mDevice.findObject(selector);
        }

        protected UiObject findObject(String resourceId) {
            final UiSelector object = new UiSelector().resourceId(resourceId);
            return mDevice.findObject(object);
        }

        protected UiObject findObject(String parentResourceId, String childResourceId) {
            final UiSelector selector = new UiSelector()
                    .resourceId(parentResourceId)
                    .childSelector(new UiSelector().resourceId(childResourceId));
            return mDevice.findObject(selector);
        }

        protected void waitForIdle() {
            mDevice.waitForIdle(mTimeout);
        }

        /**
         * (Poll) wait up until the maximum timeout for a view to be displayed (using Espresso).
         *
         * @param viewMatcher describes the view to wait for.
         * @param viewIsInPopup is true if the view is in a system popup view (eg. context menu).
         *
         * @return the ViewInteraction or null if it wasn't found in the time specified.
         */
        protected ViewInteraction waitForViewToBeDisplayed(Matcher<View> viewMatcher,
                boolean viewIsInPopup) {
            ViewInteraction view = null;
            final long waitUntilTime = System.currentTimeMillis() + mTimeout;

            while (System.currentTimeMillis() < waitUntilTime) {
                try {
                    if (viewIsInPopup) {
                        view = onView(viewMatcher)
                                .inRoot(isPlatformPopup()).check(matches(isDisplayed()));
                    } else {
                        view = onView(viewMatcher).check(matches(isDisplayed()));
                    }
                    break;
                } catch (NoMatchingViewException | AssertionFailedError e) {
                    // View not found or not displayed yet, wait and retry.
                    SystemClock.sleep(100);
                }
            }

            if (view == null) {
                Log.w(TAG, viewMatcher.toString() + " did not appear within " + mTimeout + "ms");
            }

            return view;
        }

        /**
         * (Poll) wait up until the maximum timeout for an item to be displayed in the context menu.
         *
         * @param popupItemName the name of the context menu item.
         *
         * @return the ViewInteraction for the view or null if it didn't appear.
         */
        protected ViewInteraction waitForContextMenuItemToAppear(String menuItemName) {
            return waitForViewToBeDisplayed(withText(menuItemName), true);
        }

        /** Check if the app is running in fixed_layout. */
        public boolean inFixedLayout() {
            return LayoutUtilsKt.inFixedLayout(mContext);
        }

        /** Check if the app is running in nav_rail_layout. */
        public boolean inNavRailLayout() {
            return LayoutUtilsKt.inNavRailLayout(mContext);
        }

        /** Check if the app is running in drawer_layout. */
        public boolean inDrawerLayout() {
            return LayoutUtilsKt.inDrawerLayout(mContext);
        }
    }
}
