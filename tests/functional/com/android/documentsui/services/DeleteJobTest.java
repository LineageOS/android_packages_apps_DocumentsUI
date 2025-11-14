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

package com.android.documentsui.services;

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;
import static com.android.documentsui.flags.Flags.FLAG_VISUAL_SIGNALS_RO;
import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;

import static com.google.common.collect.Lists.newArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.net.Uri;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.provider.DocumentsContract;

import androidx.test.filters.MediumTest;

import com.android.documentsui.rules.CheckAndForceMaterial3Flag;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

@MediumTest
public class DeleteJobTest extends AbstractJobTest<DeleteJob> {

    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    @Test
    public void testDeleteFiles() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        createJob(newArrayList(testFile1, testFile2),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId))
                .run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testDeleteFilesWithProgress() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        DeleteJob job = createJob(newArrayList(testFile1, testFile2),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId));
        var progress = job.getJobProgress();
        assertEquals(job.id, progress.id);
        assertEquals(Job.STATE_CREATED, progress.state);
        assertEquals(OPERATION_DELETE, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("Deleting 2 files", progress.msg);

        job.run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mSrcRoot, 0);

        progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(OPERATION_DELETE, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("Deleting 2 files", progress.msg);
    }

    @Test
    public void testDeleteFiles_NoSrcParent() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        createJob(newArrayList(testFile1, testFile2), null).run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testDeleteFilesWithProgress_NoSrcParent() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        DeleteJob job = createJob(newArrayList(testFile1, testFile2), null);
        job.run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mSrcRoot, 0);
        var progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(OPERATION_DELETE, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("Deleting 2 files", progress.msg);
    }

    @Test
    @RequiresFlagsEnabled({FLAG_USE_MATERIAL3, FLAG_VISUAL_SIGNALS_RO})
    public void testDeleteSingleFile_ProgressMessage() throws Exception {
        Uri testFile = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile, HAM_BYTES);
        DeleteJob job = createJob(newArrayList(testFile), null);

        var progress = job.getJobProgress();
        assertEquals(job.id, progress.id);
        assertEquals(Job.STATE_CREATED, progress.state);
        assertEquals(OPERATION_DELETE, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("Deleting test1.txt", progress.msg);

        job.run();
        mJobListener.waitForFinished();

        progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(OPERATION_DELETE, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("Deleting test1.txt", progress.msg);
    }

    /**
     * Creates a job with a stack consisting to the default src directory.
     */
    private DeleteJob createJob(List<Uri> srcs, Uri srcParent) throws Exception {
        Uri stack = DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId);
        return createJob(OPERATION_DELETE, srcs, srcParent, stack);
    }
}
