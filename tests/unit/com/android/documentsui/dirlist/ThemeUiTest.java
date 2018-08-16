/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import android.app.UiModeManager;
import android.content.res.TypedArray;
import android.graphics.Color;

import android.support.test.filters.SmallTest;

import com.android.documentsui.ActivityTest;
import com.android.documentsui.R;
import com.android.documentsui.files.FilesActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
* This class test default Light Theme (Night Mode Disable)
* Verify ActionBar background, Window background, and GridItem background to meet Light style
*/
@SmallTest
public class ThemeUiTest extends ActivityTest<FilesActivity> {
    public ThemeUiTest() {
        super(FilesActivity.class);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        setSystemUiModeNight(UiModeManager.MODE_NIGHT_NO);
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        setSystemUiModeNight(UiModeManager.MODE_NIGHT_NO);
    }

    @Test
    public void testThemeNightModeEnable_actionBarColor() {
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.ActionBarView);
        int actionBarBackground = ta.getColor(R.styleable.ActionBarView_android_colorPrimary,
                Color.RED);
        ta.recycle();
        assertEquals(getActivity().getColor(android.R.color.white), actionBarBackground);
    }

    @Test
    public void testThemeNightModeEnable_gridItemBackgroundColor() {
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.GridItem);
        int gridItemColor = ta.getColor(R.styleable.GridItem_gridItemColor, Color.RED);
        ta.recycle();
        assertEquals(getActivity().getColor(R.color.item_doc_background), gridItemColor);
    }

    @Test
    public void testThemeNightModeEnable_lightNavigationBar() {
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.SystemWindow);
        boolean isLightNavigationBar = ta.getBoolean(
                R.styleable.SystemWindow_android_windowLightNavigationBar, false);
        ta.recycle();
        assertTrue(isLightNavigationBar);
    }

    @Test
    public void testThemeNightModeEnable_lightStatusBar() {
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.SystemWindow);
        boolean isLightStatusBar = ta.getBoolean(
                R.styleable.SystemWindow_android_windowLightNavigationBar, false);
        ta.recycle();
        assertTrue(isLightStatusBar);
    }

    @Test
    public void testThemeNightModeEnable_navigationBarColor() {
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.SystemWindow);
        int navigationBarColor = ta.getColor(R.styleable.SystemWindow_android_navigationBarColor,
                Color.RED);
        ta.recycle();
        assertEquals(getActivity().getColor(android.R.color.white), navigationBarColor);
    }

    @Test
    public void testThemeNightModeEnable_windowBackgroundColor() {
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.SystemWindow);
        int windowBackground = ta.getColor(
                R.styleable.SystemWindow_android_windowBackground, Color.RED);
        ta.recycle();
        assertEquals(getActivity().getColor(android.R.color.white), windowBackground);
    }

    @Test
    public void testThemeNightModeEnable_statusBarColor() {
        TypedArray ta = getActivity().obtainStyledAttributes(R.styleable.SystemWindow);
        int statusBarColor = ta.getColor(R.styleable.SystemWindow_android_statusBarColor,
                Color.RED);
        ta.recycle();
        assertEquals(getActivity().getColor(android.R.color.white), statusBarColor);
    }
}
