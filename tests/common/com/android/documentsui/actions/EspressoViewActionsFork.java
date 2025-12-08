/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui.actions;

import static androidx.test.internal.util.Checks.checkNotNull;

import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import androidx.test.espresso.InjectEventSecurityException;
import androidx.test.espresso.PerformException;
import androidx.test.espresso.UiController;
import androidx.test.espresso.action.MotionEvents;
import androidx.test.espresso.action.Tapper;

import java.util.Locale;

/**
 * A fork of some parts of Espresso which are necessary to send a better emulation of right click.
 *
 * <p>Espresso click() only sends ACTION_DOWN and ACTION_UP events. The absence of
 * ACTION_BUTTON_PRESS and ACTION_BUTTON_RELEASE events causes right click implemented using
 * GenericMotion listener to fail (e.g. right click on root list).
 *
 * <p>See b/436682861 for more information.
 */
public class EspressoViewActionsFork {
    private static final String TAG = "EspressoViewActionsFork";
    private static final int MAX_CLICK_ATTEMPTS = 3;
    // android.view.MotionEvent uses 0 to represent no buttons in actionButton and buttonState.
    private static final int NO_BUTTONS = 0;

    /** Copied from espresso/core/java/androidx/test/espresso/action/Tap.java */
    public static class SingleClick implements Tapper {
        @Override
        public Tapper.Status sendTap(
                UiController uiController, float[] coordinates, float[] precision) {
            return sendTap(uiController, coordinates, precision, 0, NO_BUTTONS);
        }

        @Override
        public Tapper.Status sendTap(
                UiController uiController,
                float[] coordinates,
                float[] precision,
                int inputDevice,
                int buttonState) {
            Tapper.Status status =
                    sendSingleClick(uiController, coordinates, precision, inputDevice, buttonState);
            if (status == Tapper.Status.SUCCESS) {
                // Wait until the touch event was processed by the main thread.
                long singlePressTimeout = (long) (ViewConfiguration.getTapTimeout() * 1.5f);
                uiController.loopMainThreadForAtLeast(singlePressTimeout);
            }
            return status;
        }
    }

    /**
     * Send a single click by emulating the event sequence that a real mouse click emits:
     * ACTION_DOWN -> ACTION_BUTTON_PRESS -> ACTION_BUTTON_RELEASE -> ACTION_UP.
     *
     * <p>See b/436682861 for more information.
     *
     * <p>Copied from espresso/core/java/androidx/test/espresso/action/Tap.java
     */
    private static Tapper.Status sendSingleClick(
            UiController uiController,
            float[] coordinates,
            float[] precision,
            int inputDevice,
            int buttonState) {
        checkNotNull(uiController);
        checkNotNull(coordinates);
        checkNotNull(precision);
        /*
         * There isn't an official doc for the sequence of MotionEvents but b/436682861#comment2
         * gives us a starting point, e.g. for a right click it should look like:
         *
         * | Action           | Action button | Button state |
         * |------------------|---------------|--------------|
         * | `DOWN`           | -             | `SECONDARY`  |
         * | `BUTTON_PRESS`   | `SECONDARY`   | `SECONDARY`  |
         * | `BUTTON_RELEASE` | `SECONDARY`   | -            |
         * | `UP`             | -             | -            |
         *
         * I wanted include this in the javadoc but the formatter kept mangling the table :/
         */
        DownResultHolder touchResult =
                sendDown(
                        uiController,
                        coordinates,
                        precision,
                        MotionEvent.ACTION_DOWN,
                        inputDevice,
                        buttonState,
                        NO_BUTTONS); // ACTION_DOWN shouldn't have actionButton
        DownResultHolder buttonResult =
                sendDown(
                        uiController,
                        coordinates,
                        precision,
                        MotionEvent.ACTION_BUTTON_PRESS,
                        inputDevice,
                        buttonState,
                        buttonState); // actionButton same as buttonState
        try {
            // The up events use NO_BUTTONS for buttonState and copy actionButton from downEvent.
            boolean touchFailed =
                    !sendUp(
                            uiController,
                            buttonResult.mDown,
                            MotionEvent.ACTION_BUTTON_RELEASE);
            boolean buttonFailed =
                    !sendUp(
                            uiController,
                            touchResult.mDown,
                            MotionEvent.ACTION_UP);
            if (touchFailed || buttonFailed) {
                Log.d(
                        TAG,
                        "Injection of up event as part of the click failed. Send cancel "
                                + "event.");
                // ACTION_BUTTON_* doesn't have a cancel action type, so we just send a cancel event
                // for the touch event.
                MotionEvents.sendCancel(uiController, touchResult.mDown);
                return Tapper.Status.FAILURE;
            }
        } finally {
            touchResult.mDown.recycle();
            buttonResult.mDown.recycle();
        }
        return touchResult.mLongPress ? Tapper.Status.WARNING : Tapper.Status.SUCCESS;
    }

    /**
     * Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java with
     * additional arguments for action and actionButton so we can send ACTION_DOWN and
     * ACTION_BUTTON_PRESS events.
     */
    private static DownResultHolder sendDown(
            UiController uiController,
            float[] coordinates,
            float[] precision,
            int action,
            int inputDevice,
            int buttonState,
            int actionButton) {
        checkNotNull(uiController);
        checkNotNull(coordinates);
        checkNotNull(precision);

        for (int retry = 0; retry < MAX_CLICK_ATTEMPTS; retry++) {
            MotionEvent motionEvent;
            try {
                motionEvent =
                        obtainDownEvent(
                                coordinates,
                                precision,
                                action,
                                inputDevice,
                                buttonState,
                                actionButton);
                // The down event should be considered a tap if it is long enough to be detected
                // but short enough not to be a long-press. Assume that TapTimeout is set at
                // least twice the detection time for a tap (no need to sleep for the whole
                // TapTimeout since we aren't concerned about scrolling here).
                long downTime = motionEvent.getDownTime();
                long isTapAt = downTime + (ViewConfiguration.getTapTimeout() / 2);

                boolean injectEventSucceeded = uiController.injectMotionEvent(motionEvent);

                while (true) {
                    long delayToBeTap = isTapAt - SystemClock.uptimeMillis();
                    if (delayToBeTap <= 10) {
                        break;
                    }
                    // Sleep only a fraction of the time, since there may be other events in the
                    // UI queue that could cause us to start sleeping late, and then oversleep.
                    uiController.loopMainThreadForAtLeast(delayToBeTap / 4);
                }

                boolean longPress = false;
                if (SystemClock.uptimeMillis()
                        > (downTime + ViewConfiguration.getLongPressTimeout())) {
                    longPress = true;
                    Log.w(TAG, "Overslept and turned a tap into a long press");
                }

                if (!injectEventSucceeded) {
                    motionEvent.recycle();
                    continue;
                }

                return new DownResultHolder(motionEvent, longPress);
            } catch (InjectEventSecurityException e) {
                throw new PerformException.Builder()
                        .withActionDescription("Send down motion event")
                        .withViewDescription("unknown") // likely to be replaced by FailureHandler
                        .withCause(e)
                        .build();
            }
        }
        throw new PerformException.Builder()
                .withActionDescription(
                        String.format(Locale.ROOT, "click (after %s attempts)", MAX_CLICK_ATTEMPTS))
                .withViewDescription("unknown") // likely to be replaced by FailureHandler
                .build();
    }

    /**
     * Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java with
     * additional arguments for action and actionButton so we can send ACTION_DOWN and
     * ACTION_BUTTON_PRESS events.
     */
    private static MotionEvent obtainDownEvent(
            float[] coordinates,
            float[] precision,
            int action,
            int inputDevice,
            int buttonState,
            int actionButton) {
        checkNotNull(coordinates);
        checkNotNull(precision);

        long downTime = SystemClock.uptimeMillis();
        return obtain(
                downTime,
                downTime,
                action,
                coordinates,
                precision[0],
                precision[1],
                inputDevice,
                buttonState,
                actionButton);
    }

    /**
     * Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java with an new
     * argument for action to support ACTION_BUTTON_RELEASE and ACTION_UP events.
     */
    private static boolean sendUp(UiController uiController, MotionEvent downEvent, int action) {
        checkNotNull(uiController);
        checkNotNull(downEvent);

        MotionEvent motionEvent = null;
        try {
            motionEvent = obtainUpEvent(downEvent, action);
            boolean injectEventSucceeded = uiController.injectMotionEvent(motionEvent);

            if (!injectEventSucceeded) {
                Log.e(
                        TAG,
                        String.format(
                                Locale.ROOT,
                                "Injection of up event failed (corresponding down event: %s)",
                                downEvent));
                return false;
            }
        } catch (InjectEventSecurityException e) {
            throw new PerformException.Builder()
                    .withActionDescription(
                            String.format(
                                    Locale.ROOT,
                                    "inject up event (corresponding down event: %s)",
                                    downEvent))
                    .withViewDescription("unknown") // likely to be replaced by FailureHandler
                    .withCause(e)
                    .build();
        } finally {
            if (null != motionEvent) {
                motionEvent.recycle();
            }
        }
        return true;
    }

    /**
     * Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java with a new
     * argument for action to support ACTION_BUTTON_RELEASE and ACTION_UP events.
     */
    public static MotionEvent obtainUpEvent(MotionEvent downEvent, int action) {
        checkNotNull(downEvent);
        // The up event is mostly the same as the down event except for the different action and
        // buttonState, see the table in sendSingleClick.
        return obtain(
                downEvent.getDownTime(),
                SystemClock.uptimeMillis(),
                action,
                new float[] {downEvent.getX(), downEvent.getY()},
                downEvent.getXPrecision(),
                downEvent.getYPrecision(),
                downEvent.getSource(),
                downEvent.getToolType(0),
                NO_BUTTONS, // buttonState is always empty for ACTION_UP and ACTION_BUTTON_RELEASE
                downEvent.getActionButton());
    }

    /**
     * Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java with a new
     * argument for actionButton to properly support ACTION_BUTTON_{PRESS,RELEASE}.
     */
    private static MotionEvent obtain(
            long downTime,
            long eventTime,
            int action,
            float[] coordinates,
            float xPrecision,
            float yPrecision,
            int source,
            int buttonState,
            int actionButton) {
        return obtain(
                downTime,
                eventTime,
                action,
                coordinates,
                xPrecision,
                yPrecision,
                source,
                mapInputDeviceToToolType(source),
                buttonState,
                actionButton);
    }

    /**
     * Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java with a new
     * argument for actionButton to properly support ACTION_BUTTON_{PRESS,RELEASE}.
     */
    private static MotionEvent obtain(
            long downTime,
            long eventTime,
            int action,
            float[] coordinates,
            float xPrecision,
            float yPrecision,
            int source,
            int toolType,
            int buttonState,
            int actionButton) {
        final MotionEvent.PointerCoords[] pointerCoords = {new MotionEvent.PointerCoords()};
        final MotionEvent.PointerProperties[] pointerProperties = getPointerProperties(toolType);
        pointerCoords[0].clear();
        pointerCoords[0].x = coordinates[0];
        pointerCoords[0].y = coordinates[1];
        pointerCoords[0].pressure = 0;
        pointerCoords[0].size = 1;

        MotionEvent e =
                MotionEvent.obtain(
                        downTime,
                        eventTime,
                        action,
                        1, // pointerCount
                        pointerProperties,
                        pointerCoords,
                        0, // metaState
                        buttonState,
                        xPrecision,
                        yPrecision,
                        0, // deviceId
                        0, // edgeFlags
                        source,
                        0); // flags
        if (actionButton != NO_BUTTONS) {
            e.setActionButton(actionButton);
        }
        return e;
    }

    /** Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java. */
    private static MotionEvent.PointerProperties[] getPointerProperties(int toolType) {
        MotionEvent.PointerProperties[] pointerProperties = {new MotionEvent.PointerProperties()};
        pointerProperties[0].clear();
        pointerProperties[0].id = 0;
        pointerProperties[0].toolType = toolType;
        return pointerProperties;
    }

    /** Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java. */
    private static int mapInputDeviceToToolType(int inputDevice) {
        int toolType;
        switch (inputDevice) {
            case InputDevice.SOURCE_MOUSE:
                toolType = MotionEvent.TOOL_TYPE_MOUSE;
                break;
            case InputDevice.SOURCE_STYLUS:
                toolType = MotionEvent.TOOL_TYPE_STYLUS;
                break;
            case InputDevice.SOURCE_TOUCHSCREEN:
                toolType = MotionEvent.TOOL_TYPE_FINGER;
                break;
            default:
                toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
                break;
        }
        return toolType;
    }

    /** Copied from espresso/core/java/androidx/test/espresso/action/MotionEvents.java. */
    private static class DownResultHolder {
        final MotionEvent mDown;
        final boolean mLongPress;

        DownResultHolder(MotionEvent down, boolean longPress) {
            this.mDown = down;
            this.mLongPress = longPress;
        }
    }
}
