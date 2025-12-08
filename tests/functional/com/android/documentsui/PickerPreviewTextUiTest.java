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

package com.android.documentsui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Intent;
import android.provider.DocumentsContract;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;

import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.rules.TestFilesRule;

import org.junit.Rule;
import org.junit.Test;

@LargeTest
public class PickerPreviewTextUiTest extends ActivityTestJunit4<PickActivity> {

    @Rule public final TestFilesRule mTestFilesRule = new TestFilesRule();

    @Override
    protected void launchActivity() {
        final Intent intent = new Intent(context, PickActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_GET_CONTENT);
        if (getInitialRoot() != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, getInitialRoot().getUri());
        }
        intent.setType("text/*");
        mActivityScenario = ActivityScenario.launch(intent);
    }

    @Test
    public void testPreviewInvisible_directory_listMode() throws Exception {
        bots.main.switchToListMode();
        assertTrue(bots.directory.findDocument(TestFilesRule.DIR_NAME_1).isEnabled());
        assertFalse(bots.directory.hasDocumentPreview(TestFilesRule.DIR_NAME_1));
    }

    @Test
    public void testPreviewVisible_enabled_gridMode() throws Exception {
        bots.main.switchToGridMode();
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_1).isEnabled());
        assertTrue(bots.directory.hasDocumentPreview(TestFilesRule.FILE_NAME_1));
    }

    @Test
    public void testPreviewVisible_enabled_listMode() throws Exception {
        bots.main.switchToListMode();
        assertTrue(bots.directory.findDocument(TestFilesRule.FILE_NAME_1).isEnabled());
        assertTrue(bots.directory.hasDocumentPreview(TestFilesRule.FILE_NAME_1));
    }
}
