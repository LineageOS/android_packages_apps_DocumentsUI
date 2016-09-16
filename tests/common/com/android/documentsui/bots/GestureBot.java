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

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.test.uiautomator.Configurator;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.view.MotionEvent;

/**
 * A test helper class that provides support for controlling directory list
 * and making assertions against the state of it.
 */
public class GestureBot extends Bots.BaseBot {
    private static final String DIR_CONTAINER_ID = "com.android.documentsui:id/container_directory";
    private static final String DIR_LIST_ID = "com.android.documentsui:id/dir_list";
    private static final int LONGPRESS_STEPS = 60;
    private static final int TRAVELING_STEPS = 20;
    private static final int BAND_SELECTION_DEFAULT_STEPS = 100;
    private static final int STEPS_INBETWEEN_POINTS = 2;

    public GestureBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    public void gestureSelectFiles(String startLabel, String endLabel) throws Exception {
        int toolType = Configurator.getInstance().getToolType();
        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_FINGER);
        Rect startCoord = findDocument(startLabel).getBounds();
        Rect endCoord = findDocument(endLabel).getBounds();
        double diffX = endCoord.centerX() - startCoord.centerX();
        double diffY = endCoord.centerY() - startCoord.centerY();
        Point[] points = new Point[LONGPRESS_STEPS + TRAVELING_STEPS];

        // First simulate long-press by having a bunch of MOVE events in the same coordinate
        for (int i = 0; i < LONGPRESS_STEPS; i++) {
            points[i] = new Point(startCoord.centerX(), startCoord.centerY());
        }

        // Next put the actual drag/move events
        for (int i = 0; i < TRAVELING_STEPS; i++) {
            int newX = startCoord.centerX() + (int) (diffX / TRAVELING_STEPS * i);
            int newY = startCoord.centerY() + (int) (diffY / TRAVELING_STEPS * i);
            points[i + LONGPRESS_STEPS] = new Point(newX, newY);
        }
        mDevice.swipe(points, STEPS_INBETWEEN_POINTS);
        Configurator.getInstance().setToolType(toolType);
    }

    public void bandSelection(Point start, Point end) throws Exception {
        bandSelection(start, end, BAND_SELECTION_DEFAULT_STEPS);
    }

    public void bandSelection(Point start, Point end, int steps) throws Exception {
        int toolType = Configurator.getInstance().getToolType();
        Configurator.getInstance().setToolType(MotionEvent.TOOL_TYPE_MOUSE);
        mDevice.swipe(start.x, start.y, end.x, end.y, steps);
        Configurator.getInstance().setToolType(toolType);
    }

    public UiObject findDocument(String label) throws UiObjectNotFoundException {
        final UiSelector docList = new UiSelector().resourceId(
                DIR_CONTAINER_ID).childSelector(
                        new UiSelector().resourceId(DIR_LIST_ID));

        // Wait for the first list item to appear
        new UiObject(docList.childSelector(new UiSelector())).waitForExists(mTimeout);

        return mDevice.findObject(docList.childSelector(new UiSelector().text(label)));
    }
}
