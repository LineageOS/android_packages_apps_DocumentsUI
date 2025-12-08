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
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.withContentDescription;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.hamcrest.Matchers.allOf;

import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;
import androidx.test.uiautomator.Until;

import com.android.documentsui.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A test helper class that provides support for controlling directory list
 * and making assertions against the state of it.
 */
public class DirectoryListBot extends Bots.BaseBot {

    private static final int MAX_LAYOUT_LEVEL = 10;

    private final String mDirContainerId;
    private final String mDirListId;
    private final String mItemRootId;
    private final String mPreviewId;
    private final String mGridSelectionRegionId;
    private final String mListSelectionRegionId;

    private final UiAutomation mAutomation;

    public DirectoryListBot(
            UiDevice device, UiAutomation automation, Context context, int timeout) {
        super(device, context, timeout);
        mAutomation = automation;
        mDirContainerId = mTargetPackage + ":id/container_directory";
        mDirListId = mTargetPackage + ":id/dir_list";
        mItemRootId = mTargetPackage + ":id/item_root";
        mPreviewId = mTargetPackage + ":id/preview_icon";
        mListSelectionRegionId = mTargetPackage + ":id/icon";
        mGridSelectionRegionId =
                mTargetPackage
                        + (isUseMaterial3FlagEnabled() ? ":id/selection_circle" : ":id/icon");
    }

    public void assertDocumentsCount(int count) throws UiObjectNotFoundException {
        UiObject docsList = findDocumentsList();
        assertEquals(count, docsList.getChildCount());
    }

    /**
     * Checks if the given set of file labels is visible, without scrolling.
     * @param labels The labels to be found in the current view.
     * @throws UiObjectNotFoundException If files with given labels do not exist.
     */
    public void assertDocumentsVisible(String... labels) throws UiObjectNotFoundException {
        assertDocumentsExistWithScroll(false, labels);
    }

    /**
     * Checks if the given set of file labels is visible, with scrolling.
     * @param labels The labels to be found in the current view, scrolling included.
     * @throws UiObjectNotFoundException If files with given labels do not exist.
     */
    public void assertDocumentsPresent(String... labels) throws UiObjectNotFoundException {
        assertDocumentsExistWithScroll(true, labels);
    }

    /**
     * Checks if the given set of file labels is exists. The scroll variable controls if the code
     * is allowed to scroll the file panel to try to locate the documents.
     * @param scroll If file view may be scrolled to find the specified file labels.
     * @param labels The labels to be found in the current view, scrolling included.
     * @throws UiObjectNotFoundException If files with given labels do not exist.
     */
    public void assertDocumentsExistWithScroll(boolean scroll, String... labels)
            throws UiObjectNotFoundException {
        List<String> absent = new ArrayList<>();
        for (String label : labels) {
            if (!findDocument(label, scroll).exists()) {
                absent.add(label);
            }
        }
        if (!absent.isEmpty()) {
            fail("Expected documents " + Arrays.asList(labels)
                    + ", but missing " + absent);
        }
    }

    public void assertDocumentsAbsent(String... labels) throws UiObjectNotFoundException {
        List<String> found = new ArrayList<>();
        for (String label : labels) {
            if (findDocument(label).exists()) {
                found.add(label);
            }
        }
        if (!found.isEmpty()) {
            fail("Expected documents not present" + Arrays.asList(labels)
                    + ", but present " + found);
        }
    }

    public void assertDocumentsCountOnList(boolean exists, int count) throws UiObjectNotFoundException {
        UiObject docsList = findDocumentsList();
        assertEquals(exists, docsList.exists());
        if(docsList.exists()) {
            assertEquals(count, docsList.getChildCount());
        }
    }

    public void assertHasMessage(String expected) throws UiObjectNotFoundException {
        UiObject messageTextView = findHeaderMessageTextView();
        String msg = String.valueOf(expected);
        assertEquals(msg, messageTextView.getText());
    }

    public void assertHasMessage(boolean expected) throws UiObjectNotFoundException {
        UiObject messageTextView = findHeaderMessageTextView();
        if (expected) {
            assertTrue(messageTextView.exists());
        } else {
            assertFalse(messageTextView.exists());
        }
    }

    public void assertHasMessageButtonText(String expected) throws UiObjectNotFoundException {
        UiObject button = findHeaderMessageButton();
        String msg = String.valueOf(expected);
        assertEquals(msg.toUpperCase(), button.getText().toUpperCase());
    }

    public void clickMessageButton() throws UiObjectNotFoundException {
        UiObject button = findHeaderMessageButton();
        button.click();
    }

    /**
     * Checks against placeholder text. Placeholder can be Empty page, No results page, or the
     * "Hourglass" page (ie. something-went-wrong page).
     */
    public void waitAndAssertPlaceholderMessageText(String message)
            throws UiObjectNotFoundException {
        final UiObject messageTextView = findPlaceholderMessageTextView();
        assertTrue(messageTextView.exists());
        assertEquals(message, messageTextView.getText());
    }

    private UiObject findHeaderMessageTextView() {
        return findObject(
                mDirContainerId,
                mTargetPackage + ":id/message_textview");
    }

    private UiObject findHeaderMessageButton() {
        return findObject(
                mDirContainerId,
                mTargetPackage + ":id/dismiss_button");
    }

    private UiObject findPlaceholderMessageTextView() throws UiObjectNotFoundException {
        final String childResourceId = mTargetPackage + ":id/message";
        new UiScrollable(new UiSelector().resourceId(mDirContainerId)).scrollIntoView(
                new UiSelector().text(childResourceId));
        return findObject(mDirContainerId, childResourceId);
    }

    public void waitForHolderMessage() throws UiObjectNotFoundException {
        findPlaceholderMessageTextView().waitForExists(mTimeout);
    }

    public void openDocument(String label) throws UiObjectNotFoundException {
        int toolType = Configurator.getInstance().getToolType();
        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_FINGER);
        UiObject doc = findDocument(label, true);
        doc.click();
        Configurator.getInstance().setToolType(toolType);
    }

    /**
     * @param label The filename of the document
     * @param number Which nth document it is. The number corresponding to "n selected"
     */
    public void selectDocument(String label, int number) throws UiObjectNotFoundException {
        waitForDocument(label);
        UiObject2 selectionHotspot = findSelectionHotspot(label);
        selectionHotspot.click();

        // Wait until selection is fully done: onSingleTapConfirmed, not just onSingleTapUp. This
        // also avoids a future click being registered as double clicking.
        SystemClock.sleep((ViewConfiguration.getDoubleTapTimeout() * 3) / 2);
        assertSelection(number);
    }

    private BySelector getSelectionRegionSelector() {
        BySelector selectionRegionSelector = By.res(mGridSelectionRegionId);
        if (mDevice.findObject(selectionRegionSelector) == null) {
            selectionRegionSelector = By.res(mListSelectionRegionId);
        }
        return selectionRegionSelector;
    }

    /** Select the first document that has a selectable region in the list or grid view. */
    public void selectFirstDocument() throws UiObjectNotFoundException {
        final BySelector list = By.res(mDirListId);
        final BySelector selectionRegionSelector = getSelectionRegionSelector();

        UiObject2 firstAvailableSelectionHotspot =
                mDevice.findObject(list).findObject(selectionRegionSelector);
        firstAvailableSelectionHotspot.click();
        assertSelection(1);
    }

    /** Finds a list item's (whose text has the given label) selection hotspot. */
    public UiObject2 findSelectionHotspot(String label) throws UiObjectNotFoundException {
        return findItemAndSelectionHotspot(label)[1];
    }

    /** Finds a list item (whose text has the given label) and the selection hotspot within it. */
    public UiObject2[] findItemAndSelectionHotspot(String label) throws UiObjectNotFoundException {
        final BySelector list = By.res(mDirListId);

        BySelector selector = By.hasChild(By.text(label));

        final UiSelector docList = findDocumentsListSelector();
        new UiScrollable(docList).scrollIntoView(new UiSelector().text(label));

        final BySelector selectionRegionSelector = getSelectionRegionSelector();
        UiObject2 parent = mDevice.findObject(list).findObject(selector);
        UiObject2 selectionHotspot = null;
        for (int i = 1; i <= MAX_LAYOUT_LEVEL; i++) {
            parent = parent.getParent();
            selectionHotspot = parent.findObject(selectionRegionSelector);
            if (selectionHotspot != null) {
                break;
            }
        }
        return new UiObject2[]{ parent, selectionHotspot };
    }

    /**
     * Clicks the "X" cancel selection button.
     */
    public void clearSelection() {
        int parentId = isUseMaterial3FlagEnabled()
                ? R.id.selection_bar : androidx.appcompat.R.id.action_mode_bar;
        int contentDescription = isUseMaterial3FlagEnabled()
                ? R.string.clear_selection : android.R.string.cancel;
        onView(allOf(withContentDescription(contentDescription),
                isDescendantOfA(withId(parentId)))).perform(clickAndRetryOnLongPress());
    }

    public void pasteFilesFromClipboard() {
        mDevice.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);
    }

    public UiObject2 getSnackbar(String message) {
        return mDevice.wait(Until.findObject(By.text(message)), mTimeout);
    }

    public void waitForDocument(String label) throws UiObjectNotFoundException {
        findDocument(label).waitForExists(mTimeout);
    }

    public UiObject findDocument(String label) throws UiObjectNotFoundException {
        return findDocument(label, false);
    }

    public UiObject findDocument(String label, boolean withScroll)
            throws UiObjectNotFoundException {
        final UiSelector docList = findDocumentsListSelector();

        // Wait for the first list item to appear
        new UiObject(docList.childSelector(new UiSelector())).waitForExists(mTimeout);

        if (withScroll) {
            new UiScrollable(docList).scrollIntoView(new UiSelector().text(label));
        }
        return mDevice.findObject(docList.childSelector(new UiSelector().text(label)));
    }

    public boolean hasDocuments(String... labels) throws UiObjectNotFoundException {
        for (String label : labels) {
            if (!findDocument(label).exists()) {
                return false;
            }
        }
        return true;
    }

    public boolean hasDocumentPreview(String label) {
        final BySelector list = By.res(mDirListId);

        UiObject2 parent = mDevice.findObject(list).findObject(By.text(label));
        for (int i = 1; i <= MAX_LAYOUT_LEVEL; i++) {
            parent = parent.getParent();
            if (mItemRootId.equals(parent.getResourceName())) {
                break;
            }
        }

        return parent.hasObject(By.res(mPreviewId));
    }

    public void assertFirstDocumentHasFocus() throws UiObjectNotFoundException {
        final UiSelector docList = findDocumentsListSelector();

        // Wait for the first list item to appear
        UiObject doc = new UiObject(docList.childSelector(new UiSelector()));
        doc.waitForExists(mTimeout);

        assertTrue(doc.isFocused());
    }

    public UiObject findDocumentsList() {
        return findObject(
                mDirContainerId,
                mDirListId);
    }

    private UiSelector findDocumentsListSelector() {
        return new UiSelector().resourceId(
                mDirContainerId).childSelector(
                new UiSelector().resourceId(mDirListId));
    }

    public void assertHasFocus() {
        assertHasFocus(mDirListId);
    }

    /** Assert that 0 things are selected. */
    public void assertNoSelection() {
        UiObject2 selectionText = mDevice.wait(
                Until.findObject(By.textContains("selected")), mTimeout / 10);
        assertNull(selectionText);
    }

    /** Assert that N things are selected, for positive N. */
    public void assertSelection(int numSelected) {
        String assertSelectionText = numSelected + " selected";
        UiObject2 selectionText = mDevice.wait(
                Until.findObject(By.text(assertSelectionText)), mTimeout);
        assertNotNull(selectionText);
    }

    public void assertOrder(String[] dirs, String[] files) throws UiObjectNotFoundException {
        int remaining = mTimeout;
        if (remaining < 0) {
            remaining = 0;
        }
        // 1048576 is (1 << 20), a power of two close to one million. The value is basically
        // arbitrary. We just want our sleeps to start as a small fraction of the default timeout,
        // but double in length each iteration.
        int retryTimeout = remaining / 1048576;
        if ((retryTimeout < 1) && (remaining != 0)) {
            retryTimeout = 1;
        }

        // Check that the bounding boxes for the (dirs ++ files) UI items are ordered. Use
        // exponential backoff in case we have to wait (without explicit synchronization) for a
        // worker thread to sort things (and having that trigger UI changes).
        //
        // Loop invariants:
        //  • (0 <= retryTimeout) and (retryTimeout <= remaining)
        //  • (0 < retryTimeout) unless (0 == remaining), in which case (0 == retryTimeout)
        //  • remaining decreases on each complete (no return or fail) iteration
        while (true) {
            try {
                for (int i = 0; i < dirs.length - 1; ++i) {
                    checkOrder(dirs[i], dirs[i + 1]);
                }

                if (dirs.length > 0 && files.length > 0) {
                    checkOrder(dirs[dirs.length - 1], files[0]);
                }

                for (int i = 0; i < files.length - 1; ++i) {
                    checkOrder(files[i], files[i + 1]);
                }

                return;
            } catch (NotInOrderException nioe) {
                if (remaining <= 0) {
                    fail(nioe.getMessage());
                }
                SystemClock.sleep(retryTimeout);

                remaining -= retryTimeout;
                retryTimeout *= 2;
                if ((retryTimeout > remaining) || (retryTimeout <= 0)) {
                    retryTimeout = remaining;
                }
            }
        }
    }

    public void rightClickDocument(String label) throws UiObjectNotFoundException {
        Rect startCoord = findDocument(label, true).getBounds();
        rightClickDocument(new Point(startCoord.centerX(), startCoord.centerY()));
    }

    public void rightClickDocument(Point point) throws UiObjectNotFoundException {
        // TODO: Use Espresso instead of doing the events mock ourselves
        MotionEvent motionDown =
                getTestRightClickMotionEvent(MotionEvent.ACTION_DOWN, point.x, point.y);
        mAutomation.injectInputEvent(motionDown, true);
        SystemClock.sleep(100);

        MotionEvent motionUp =
                getTestRightClickMotionEvent(MotionEvent.ACTION_UP, point.x, point.y);

        mAutomation.injectInputEvent(motionUp, true);
    }

    private void checkOrder(String first, String second) throws NotInOrderException,
            UiObjectNotFoundException {
        final UiObject firstObj = findDocument(first);
        final UiObject secondObj = findDocument(second);

        final int layoutDirection = mContext.getResources().getConfiguration().getLayoutDirection();
        final Rect firstBound = firstObj.getVisibleBounds();
        final Rect secondBound = secondObj.getVisibleBounds();
        if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            if (firstBound.bottom < secondBound.top || firstBound.right < secondBound.left) {
                return;
            }
        } else {
            if (firstBound.bottom < secondBound.top || firstBound.left > secondBound.right) {
                return;
            }
        }
        throw new NotInOrderException(first + " is not located before " + second);
    }

    private static class NotInOrderException extends Exception {
        NotInOrderException(String m) {
            super(m);
        }
    }
}
