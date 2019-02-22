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
 */

package com.android.documentsui;

import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Locale;

/**
 * To help the image verification by using RGB, HSV, and HSL color space to make sure whether the
 * pixels in the image over the threshold or not. Not only to use color ARGB match but also to
 * fuzzy color match(RGB, HSV, HSL).
 */
public class FuzzyColor {
    public static class Threshold {
        public int getColor() {
            return 20;
        }

        public float getHue() {
            return 8f;
        }

        public float getSaturation() {
            return 0.25f;
        }

        public float getValue() {
            return 0.25f;
        }

        public float getLuminance() {
            return 0.1f;
        }
    }

    private static Threshold sThreshold = new Threshold();

    /**
     * To set the threshold adjust the verify algorithm.
     *
     * @param threshold the threshold that want to set
     */
    public static synchronized void setThreshold(Threshold threshold) {
        if (threshold == null) {
            return;
        }
        sThreshold = threshold;
    }

    private final int mColor;
    private final float [] mHsv;
    private final float mLuminance;
    private final int mRed;
    private final int mGreen;
    private final int mBlue;
    private int mCount;

    /**
     * To initial a FuzzyColor according to color parameter.
     *
     * @param color the ARGB color for the pixel in the image
     */
    public FuzzyColor(int color) {
        mColor = color;

        mRed = Color.red(color);
        mGreen = Color.green(color);
        mBlue = Color.blue(color);
        mHsv = new float[3];
        mLuminance = Color.luminance(color);
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), mHsv);
    }

    public void add(int count) {
        mCount += count;
    }

    public int getCount() {
        return mCount;
    }

    public int getColor() {
        return mColor;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj instanceof FuzzyColor) {
            FuzzyColor other = (FuzzyColor) obj;
            return compare(this, other);
        } else if (obj instanceof Integer) {
            int color = (int) obj;
            return compare(this, new FuzzyColor(color));
        } else {
            return false;
        }
    }

    private static boolean compare(FuzzyColor c1, FuzzyColor c2) {
        if (Math.abs(c1.mRed - c2.mRed) < sThreshold.getColor()
                && Math.abs(c1.mGreen - c2.mGreen) < sThreshold.getColor()
                && Math.abs(c1.mBlue - c2.mBlue) < sThreshold.getColor()) {
            return true;
        }

        float hueDiff = Math.abs(c1.mHsv[0] - c2.mHsv[0]);
        if (hueDiff > sThreshold.getHue() && hueDiff < (360f - sThreshold.getHue())) {
            return false;
        }

        float saturationDiff = Math.abs(c1.mHsv[1] - c2.mHsv[1]);
        if (saturationDiff > sThreshold.getSaturation()) {
            return false;
        }

        float valueDiff = Math.abs(c1.mHsv[2] - c2.mHsv[2]);
        if (valueDiff > sThreshold.getValue()) {
            return false;
        }

        float luminanceDiff = Math.abs(c1.mLuminance - c2.mLuminance);
        if (luminanceDiff > sThreshold.getLuminance()) {
            return false;
        }

        return true;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,
                "[#%08x] count = %d, hue = %f, saturation = %f, value = %f, luminance = %f",
                mColor, mCount, mHsv[0], mHsv[1],  mHsv[2], mLuminance);
    }
}
