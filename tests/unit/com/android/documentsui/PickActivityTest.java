/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static com.android.documentsui.base.Providers.AUTHORITY_STORAGE;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.testing.TestProvidersAccess;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SmallTest
public class PickActivityTest {

    private Intent intentGetContent;

    @Rule
    public final ActivityTestRule<PickActivity> mRule =
            new ActivityTestRule<>(PickActivity.class, false, false);

    @Before
    public void setUp() throws Exception {
        intentGetContent = new Intent(Intent.ACTION_GET_CONTENT);
        intentGetContent.addCategory(Intent.CATEGORY_OPENABLE);
        intentGetContent.setType("*/*");
        Uri hintUri = DocumentsContract.buildRootUri(AUTHORITY_STORAGE, "primary");
        intentGetContent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, hintUri);
    }

    @Test
    public void testOnDocumentPicked() {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = TestProvidersAccess.USER_ID;
        doc.authority = "authority";
        doc.documentId = "documentId";

        PickActivity pickActivity = mRule.launchActivity(intentGetContent);
        pickActivity.mState.canShareAcrossProfile = true;
        pickActivity.onDocumentPicked(doc);
        SystemClock.sleep(3000);

        Instrumentation.ActivityResult result = mRule.getActivityResult();
        assertThat(pickActivity.isFinishing()).isTrue();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        assertThat(result.getResultData().getData()).isEqualTo(doc.getDocumentUri());
    }

    @Test
    public void testOnDocumentPicked_otherUser() {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = TestProvidersAccess.OtherUser.USER_ID;
        doc.authority = "authority";
        doc.documentId = "documentId";

        PickActivity pickActivity = mRule.launchActivity(intentGetContent);
        pickActivity.mState.canShareAcrossProfile = true;
        pickActivity.onDocumentPicked(doc);
        SystemClock.sleep(3000);

        Instrumentation.ActivityResult result = mRule.getActivityResult();
        assertThat(result.getResultCode()).isEqualTo(Activity.RESULT_OK);
        assertThat(result.getResultData().getData()).isEqualTo(doc.getDocumentUri());
    }

    @Test
    public void testOnDocumentPicked_otherUserDoesNotReturn() {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = TestProvidersAccess.OtherUser.USER_ID;
        doc.authority = "authority";
        doc.documentId = "documentId";

        PickActivity pickActivity = mRule.launchActivity(intentGetContent);
        pickActivity.mState.canShareAcrossProfile = false;
        pickActivity.onDocumentPicked(doc);
        SystemClock.sleep(3000);

        assertThat(pickActivity.isFinishing()).isFalse();
    }
}
