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

package com.android.documentsui.picker;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import static com.google.common.truth.Truth.assertThat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.espresso.ViewAssertion;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import androidx.test.runner.screenshot.ScreenCapture;
import androidx.test.runner.screenshot.Screenshot;

import com.android.documentsui.FuzzyColor;
import com.android.documentsui.R;
import com.android.documentsui.ScreenshotCaptureRule;

import java.util.ArrayList;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class PickActivityTest {
    @Rule
    public ActivityTestRule mActivityTestRule = new ActivityTestRule(PickActivity.class,
            false, false);

    @Rule
    public RuleChain mRuleChain = RuleChain.outerRule(mActivityTestRule)
            .around(new ScreenshotCaptureRule());
    @Rule
    public TestName mTestName = new TestName();

    private static List<FuzzyColor> getFuzzyHistogram(Bitmap bitmap,
            FuzzyColor.Threshold threshold) {
        List<FuzzyColor> histogram = new ArrayList<>();
        FuzzyColor.setThreshold(threshold);

        final int size = bitmap.getWidth() * bitmap.getHeight();
        final int [] colorPixels = new int[size];
        bitmap.getPixels(colorPixels, 0, bitmap.getWidth(), 0, 0,
                bitmap.getWidth(), bitmap.getHeight());
        for (int color : colorPixels) {
            if (Color.alpha(color) < 127) {
                continue;
            }

            FuzzyColor target = null;
            for (FuzzyColor matcher : histogram) {
                if (matcher.equals(color)) {
                    matcher.add(1);
                    target = matcher;
                }
            }

            if (target == null) {
                target = new FuzzyColor(color);
                target.add(1);
                histogram.add(target);
            }
        }

        histogram.sort((o1, o2) -> o2.getCount() - o1.getCount());

        return histogram;
    }

    static ViewAssertion assertColorCorrect(TestName testName, FuzzyColor.Threshold threshold) {
        return (view, noViewFoundException) -> {
            ScreenCapture capture = Screenshot.capture(view);
            capture.setName(testName.getMethodName());
            capture.setFormat(Bitmap.CompressFormat.PNG);
            Bitmap bitmap = capture.getBitmap();

            List<FuzzyColor> fuzzyHistogram = getFuzzyHistogram(bitmap, threshold);

            assertThat(fuzzyHistogram.size()).named("The number of dominant color")
                    .isGreaterThan(1);
        };
    }

    @Test
    public void onCreate_actionCreate_shouldLaunchSuccess() throws InterruptedException {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "foobar.txt");

        mActivityTestRule.launchActivity(intent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        onView(withText(R.string.menu_save)).check(assertColorCorrect(mTestName, null));
    }
}
