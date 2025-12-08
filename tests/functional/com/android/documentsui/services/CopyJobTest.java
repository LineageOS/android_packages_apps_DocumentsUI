/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.services;

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO;
import static com.android.documentsui.services.FileOperationService.OPERATION_COPY;

import static com.google.common.collect.Lists.newArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.net.Uri;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract.Document;

import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;

import com.android.documentsui.rules.OverrideFlagsRule;

import org.junit.Rule;
import org.junit.Test;

@MediumTest
public class CopyJobTest extends AbstractCopyJobTest<CopyJob> {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    public CopyJobTest() {
        super(OPERATION_COPY);
    }

    @Test
    public void testCopyFiles() throws Exception {
        runCopyFilesTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyFilesWithProgress() throws Exception {
        runCopyFilesTestWithJobProgress();
    }

    @Test
    public void testCopyVirtualTypedFile() throws Exception {
        runCopyVirtualTypedFileTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyVirtualTypedFileWithJobProgress() throws Exception {
        runCopyVirtualTypedFileTestWithJobProgress();
    }

    @Test
    public void testCopyVirtualNonTypedFile() throws Exception {
        runCopyVirtualNonTypedFileTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyVirtualNonTypedFileWithProgress() throws Exception {
        runCopyVirtualNonTypedFileTestWithJobProgress();
    }

    @Test
    public void testCopy_BackendSideVirtualTypedFile_Fallback() throws Exception {
        mDocs.assertChildCount(mDestRoot, 0);

        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", "tokyo.sth",
                Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_SUPPORTS_COPY
                        | Document.FLAG_SUPPORTS_MOVE, "application/pdf");

        createJob(newArrayList(testFile)).run();

        waitForJobFinished();
        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasFile(mDestRoot, "tokyo.sth.pdf");  // Copy should convert file to PDF.
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyWithJobProgress_BackendSideVirtualTypedFile_Fallback() throws Exception {
        mDocs.assertChildCount(mDestRoot, 0);

        Uri testFile = mDocs.createDocumentWithFlags(
                mSrcRoot.documentId, "virtual/mime-type", "tokyo.sth",
                Document.FLAG_VIRTUAL_DOCUMENT | Document.FLAG_SUPPORTS_COPY
                        | Document.FLAG_SUPPORTS_MOVE, "application/pdf");

        CopyJob job = createJob(newArrayList(testFile));

        JobProgress progress = job.getJobProgress();
        assertEquals(job.id, progress.id);
        assertEquals(Job.STATE_CREATED, progress.state);
        assertEquals("Copying “tokyo.sth” to “" + mDestRoot.title + "”", progress.msg);
        assertFalse(progress.hasFailures);

        job.run();
        waitForJobFinished();
        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasFile(mDestRoot, "tokyo.sth.pdf");  // Copy should convert file to PDF.

        progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(OPERATION_COPY, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("Copying “tokyo.sth” to “" + mDestRoot.title + "”", progress.msg);
        assertEquals(-1, progress.currentBytes);
        assertEquals(-1, progress.requiredBytes);
    }

    @Test
    public void testCopyEmptyDir() throws Exception {
        runCopyEmptyDirTest();
    }

    @Test
    @EnableFlags({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testCopyEmptyDirWithJobProgress() throws Exception {
        runCopyEmptyDirTestWithJobProgress();
    }

    @Test
    public void testCopyDirRecursively() throws Exception {
        runCopyDirRecursivelyTest();
    }

    // This test sometimes takes >1 minute to run.
    @LargeTest
    @Test
    public void testCopyDirRecursively_loadingInFirstCursor() throws Exception {
        mDocs.setLoadingDuration(500);
        testCopyDirRecursively();
    }

    @Test
    public void testNoCopyDirToSelf() throws Exception {
        runNoCopyDirToSelfTest();
    }

    @Test
    public void testNoCopyDirToDescendent() throws Exception {
        runNoCopyDirToDescendentTest();
    }

    @Test
    public void testCopyFileWithReadErrors() throws Exception {
        runCopyFileWithReadErrorsTest();
    }

    @Test
    public void testCopyProgressWithFileCount() throws Exception {
        runCopyProgressForFileCountTest();
    }

    @Test
    public void testCopyProgressWithByteCount() throws Exception {
        runCopyProgressForByteCountTest();
    }
}
