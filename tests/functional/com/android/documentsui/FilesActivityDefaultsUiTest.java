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

package com.android.documentsui;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;

import android.platform.test.annotations.DesktopTest;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.filters.HugeLongTest;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class FilesActivityDefaultsUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule(/* skipCreation */ true);

    @Override
    protected RootInfo getInitialRoot() {
        return null;  // test the default, unaffected state of the app.
    }

    @Test
    @HugeLongTest
    public void testNavigate_FromEmptyDirectory() throws Exception {
        device.waitForIdle();

        bots.roots.openRoot(mTestFilesRule.getRoot(ROOT_0_ID).title);
        bots.directory.waitAndAssertPlaceholderMessageText(context.getString(R.string.empty));

        // Check to make sure back button is properly handled by non-Doc type DocHolders
        device.pressBack();
    }

    @DesktopTest(cujs = {"b/434066211"})
    @Test
    @HugeLongTest
    public void testDefaultRoots() throws Exception {
        device.waitForIdle();

        // Should also have Drive, but that requires pre-configuration of devices
        // We omit for now.
        bots.roots.assertRootsPresent(
                "Downloads",
                ROOT_0_ID,
                ROOT_1_ID);

        if (context.getResources().getBoolean(R.bool.show_media_roots)) {
            bots.roots.assertRootsPresent("Audio", "Images");
        }
    }
}
