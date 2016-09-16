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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.BySelector;
import android.support.test.uiautomator.Configurator;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.support.test.uiautomator.Until;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A test helper class that provides support for controlling directory list
 * and making assertions against the state of it.
 */
public class DirectoryListBot extends Bots.BaseBot {
    private static final String DIR_CONTAINER_ID = "com.android.documentsui:id/container_directory";
    private static final String DIR_LIST_ID = "com.android.documentsui:id/dir_list";

    private static final BySelector SNACK_DELETE =
            By.desc(Pattern.compile("^Deleting [0-9]+ file.+"));

    public DirectoryListBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    public void assertDocumentsCount(int count) throws UiObjectNotFoundException {
        UiObject docsList = findDocumentsList();
        assertEquals(count, docsList.getChildCount());
    }

    public void assertDocumentsPresent(String... labels) throws UiObjectNotFoundException {
        List<String> absent = new ArrayList<>();
        for (String label : labels) {
            if (!findDocument(label).exists()) {
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

    public void assertMessageTextView(String message) throws UiObjectNotFoundException {
        UiObject messageTextView = findMessageTextView();
        assertTrue(messageTextView.exists());

        String msg = String.valueOf(message);
        assertEquals(String.format(msg, "TEST_ROOT_0"), messageTextView.getText());

    }

    private UiObject findMessageTextView() {
        return findObject(
                DIR_CONTAINER_ID,
                "com.android.documentsui:id/message");
    }

    public void assertSnackbar(int id) {
        assertNotNull(getSnackbar(mContext.getString(id)));
    }

    public void openDocument(String label) throws UiObjectNotFoundException {
        int toolType = Configurator.getInstance().getToolType();
        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_FINGER);
        UiObject doc = findDocument(label);
        doc.click();
        Configurator.getInstance().setToolType(toolType);
    }

    public void clickDocument(String label) throws UiObjectNotFoundException {
        findDocument(label).click();
    }

    public void selectDocument(String label) throws UiObjectNotFoundException {
        waitForDocument(label);
        UiObject2 selectionHotspot = findSelectionHotspot(label);
        selectionHotspot.click();
    }

    public UiObject2 findSelectionHotspot(String label) {
        final BySelector list = By.res(DIR_LIST_ID);

        BySelector selector = By.hasChild(By.text(label));
        UiObject2 parent = mDevice.findObject(list).findObject(selector);
        if (parent.getClassName().equals("android.widget.LinearLayout")) {
            // For list mode, the parent of the textView does not contain the selector icon, but the
            // grandparent of the textView does
            // Gotta go one more level up
            selector = By.hasDescendant(By.text(label).depth(2));
            parent = mDevice.findObject(list).findObject(selector);
        }
        return parent.findObject(By.clazz(ImageView.class));
    }

    public void copyFilesToClipboard(String...labels) throws UiObjectNotFoundException {
        for (String label: labels) {
            clickDocument(label);
        }
        mDevice.pressKeyCode(KeyEvent.KEYCODE_C, KeyEvent.META_CTRL_ON);
    }

    public void pasteFilesFromClipboard() {
        mDevice.pressKeyCode(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON);
    }

    public UiObject2 getSnackbar(String message) {
        return mDevice.wait(Until.findObject(By.text(message)), mTimeout);
    }

    public void waitForDeleteSnackbar() {
        mDevice.wait(Until.findObject(SNACK_DELETE), mTimeout);
    }

    public void waitForDeleteSnackbarGone() {
        // wait a little longer for snackbar to go away, as it disappears after a timeout.
        mDevice.wait(Until.gone(SNACK_DELETE), mTimeout * 2);
    }

    public void waitForDocument(String label) throws UiObjectNotFoundException {
        findDocument(label).waitForExists(mTimeout);
    }

    public UiObject findDocument(String label) throws UiObjectNotFoundException {
        final UiSelector docList = new UiSelector().resourceId(
                DIR_CONTAINER_ID).childSelector(
                        new UiSelector().resourceId(DIR_LIST_ID));

        // Wait for the first list item to appear
        new UiObject(docList.childSelector(new UiSelector())).waitForExists(mTimeout);

        // new UiScrollable(docList).scrollIntoView(new UiSelector().text(label));
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

    public void assertFirstDocumentHasFocus() throws UiObjectNotFoundException {
        final UiSelector docList = new UiSelector().resourceId(
                DIR_CONTAINER_ID).childSelector(
                        new UiSelector().resourceId(DIR_LIST_ID));

        // Wait for the first list item to appear
        UiObject doc = new UiObject(docList.childSelector(new UiSelector()));
        doc.waitForExists(mTimeout);

        assertTrue(doc.isFocused());
    }

    public UiObject findDocumentsList() {
        return findObject(
                DIR_CONTAINER_ID,
                DIR_LIST_ID);
    }

    public void assertHasFocus() {
        assertHasFocus(DIR_LIST_ID);
    }

    public void assertSelection(int numSelected) {
        String assertSelectionText = numSelected + " selected";
        UiObject2 selectionText = mDevice.wait(
                Until.findObject(By.text(assertSelectionText)), mTimeout);
        assertTrue(selectionText != null);
    }

    public void assertOrder(String[] dirs, String[] files) throws UiObjectNotFoundException {
        for (int i = 0; i < dirs.length - 1; ++i) {
            assertOrder(dirs[i], dirs[i + 1]);
        }

        if (dirs.length > 0 && files.length > 0) {
            assertOrder(dirs[dirs.length - 1], files[0]);
        }

        for (int i = 0; i < files.length - 1; ++i) {
            assertOrder(files[i], files[i + 1]);
        }
    }

    private void assertOrder(String first, String second) throws UiObjectNotFoundException {

        final UiObject firstObj = findDocument(first);
        final UiObject secondObj = findDocument(second);

        final int layoutDirection = mContext.getResources().getConfiguration().getLayoutDirection();
        final Rect firstBound = firstObj.getVisibleBounds();
        final Rect secondBound = secondObj.getVisibleBounds();
        if (layoutDirection == View.LAYOUT_DIRECTION_LTR) {
            assertTrue(
                    "\"" + first + "\" is not located above or to the left of \"" + second
                            + "\" in LTR",
                    firstBound.bottom < secondBound.top || firstBound.right < secondBound.left);
        } else {
            assertTrue(
                    "\"" + first + "\" is not located above or to the right of \"" + second +
                            "\" in RTL",
                    firstBound.bottom < secondBound.top || firstBound.left > secondBound.right);
        }
    }
}
