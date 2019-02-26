/*
 * Copyright (C) 2019 The Android Open Source Project
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
 *
 */

package com.android.documentsui;

import android.graphics.Bitmap;
import android.util.Log;

import androidx.test.runner.screenshot.BasicScreenCaptureProcessor;
import androidx.test.runner.screenshot.ScreenCapture;
import androidx.test.runner.screenshot.Screenshot;

import java.io.IOException;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * When the test fail happen, the screen capture will help the developer to judge
 * what does the UI looks like, where is the asserted view, and why test fail.
 */
public class ScreenshotCaptureRule extends TestWatcher {
    private static final String TAG = ScreenshotCaptureRule.class.getSimpleName();

    @Override
    protected void failed(Throwable e, Description description) {
        super.failed(e, description);

        ScreenCapture capture = Screenshot.capture();
        capture.setFormat(Bitmap.CompressFormat.PNG);
        capture.setName(description.getMethodName());

        try {
            new BasicScreenCaptureProcessor().process(capture);
        } catch (IOException e1) {
            Log.e(TAG, "Can't handle the capture. " + e1.getMessage());
        }
    }
}
