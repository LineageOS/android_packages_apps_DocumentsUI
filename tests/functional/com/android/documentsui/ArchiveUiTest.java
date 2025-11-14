/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.filters.LargeTest;

import com.android.documentsui.files.FilesActivity;
import com.android.documentsui.rules.CheckAndForceMaterial3Flag;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class ArchiveUiTest extends ActivityTestJunit4<FilesActivity> {

    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    @Rule
    public final TestFilesRule mTestFilesRule = new TestFilesRule();

    private void archiveValid() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.openDocument("archive.zip");
        bots.directory.waitForDocument("file1.txt");
        bots.directory.assertDocumentsPresent("dir1", "dir2", "file1.txt");
        bots.directory.openDocument("dir1");
        bots.directory.waitForDocument("cherries.txt");
    }

    @Test
    @RequiresFlagsDisabled({FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testArchive_valid() throws Exception {
        archiveValid();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testArchive_valid_searchV2() throws Exception {
        archiveValid();
    }

    private void archiveInvalid() throws Exception {
        bots.roots.openRoot("ResourcesProvider");
        bots.directory.openDocument("broken.zip");

        final String msg = String.valueOf(context.getString(R.string.empty));
        bots.directory.waitForHolderMessage();
        bots.directory.assertPlaceholderMessageText(msg);
    }

    @Test
    @RequiresFlagsDisabled({FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testArchive_invalid() throws Exception {
        archiveInvalid();
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testArchive_invalid_searchV2() throws Exception {
        archiveInvalid();
    }
}
