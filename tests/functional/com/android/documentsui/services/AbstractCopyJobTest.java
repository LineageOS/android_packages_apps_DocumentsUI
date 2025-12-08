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

import static com.google.common.collect.Lists.newArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.text.format.DateUtils;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.services.FileOperationService.OpType;

import java.text.NumberFormat;
import java.util.List;
import java.util.stream.IntStream;

public abstract class AbstractCopyJobTest<T extends CopyJob> extends AbstractJobTest<T> {

    private final @OpType int mOpType;

    AbstractCopyJobTest(@OpType int opType) {
        mOpType = opType;
    }

    private String getVerb() {
        switch (mOpType) {
            case FileOperationService.OPERATION_COPY:
            case FileOperationService.OPERATION_EXTRACT:
                return "Copying";
            case FileOperationService.OPERATION_COMPRESS:
                return "Zipping";
            case FileOperationService.OPERATION_MOVE:
                return "Moving";

            // DeleteJob does not inherit from CopyJob
            case FileOperationService.OPERATION_DELETE:
            case FileOperationService.OPERATION_UNPACK:
            case FileOperationService.OPERATION_UNKNOWN:
            default:
                return "";
        }
    }

    protected void runCopyFilesTest() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        createJob(newArrayList(testFile1, testFile2)).run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mDestRoot, 2);
        mDocs.assertHasFile(mDestRoot, "test1.txt");
        mDocs.assertHasFile(mDestRoot, "test2.txt");
        mDocs.assertFileContents(mDestRoot.documentId, "test1.txt", HAM_BYTES);
        mDocs.assertFileContents(mDestRoot.documentId, "test2.txt", FRUITY_BYTES);
    }

    protected void runCopyFilesTestWithJobProgress() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        CopyJob job = createJob(newArrayList(testFile1, testFile2));
        JobProgress progress = job.getJobProgress();
        assertEquals(job.id, progress.id);
        assertEquals(mOpType, progress.operationType);
        assertEquals(Job.STATE_CREATED, progress.state);
        assertEquals(getVerb() + " 2 files to “" + mDestRoot.title + "”", progress.msg);
        assertFalse(progress.hasFailures);

        job.run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mDestRoot, 2);
        mDocs.assertHasFile(mDestRoot, "test1.txt");
        mDocs.assertHasFile(mDestRoot, "test2.txt");
        mDocs.assertFileContents(mDestRoot.documentId, "test1.txt", HAM_BYTES);
        mDocs.assertFileContents(mDestRoot.documentId, "test2.txt", FRUITY_BYTES);

        progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(mOpType, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals(getVerb() + " 2 files to “" + mDestRoot.title + "”", progress.msg);
        assertEquals(HAM_BYTES.length + FRUITY_BYTES.length, progress.currentBytes);
        assertEquals(HAM_BYTES.length + FRUITY_BYTES.length, progress.requiredBytes);
    }

    protected void runCopyVirtualTypedFileTest() throws Exception {
        Uri testFile = mDocs.createVirtualFile(
                mSrcRoot, "/virtual.sth", "virtual/mime-type",
                FRUITY_BYTES, "application/pdf", "text/html");

        createJob(newArrayList(testFile)).run();
        waitForJobFinished();

        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasFile(mDestRoot, "virtual.sth.pdf");  // copy should convert file to PDF.
        mDocs.assertFileContents(mDestRoot.documentId, "virtual.sth.pdf", FRUITY_BYTES);
    }

    protected void runCopyVirtualTypedFileTestWithJobProgress() throws Exception {
        Uri testFile = mDocs.createVirtualFile(
                mSrcRoot, "/virtual.sth", "virtual/mime-type",
                FRUITY_BYTES, "application/pdf", "text/html");

        CopyJob job = createJob(newArrayList(testFile));
        JobProgress progress = job.getJobProgress();
        assertEquals(job.id, progress.id);
        assertEquals(mOpType, progress.operationType);
        assertEquals(Job.STATE_CREATED, progress.state);
        assertEquals("Copying “virtual.sth” to “" + mDestRoot.title + "”", progress.msg);
        assertFalse(progress.hasFailures);

        job.run();
        waitForJobFinished();

        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasFile(mDestRoot, "virtual.sth.pdf");  // copy should convert file to PDF.
        mDocs.assertFileContents(mDestRoot.documentId, "virtual.sth.pdf", FRUITY_BYTES);

        progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(mOpType, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals("Copying “virtual.sth” to “" + mDestRoot.title + "”", progress.msg);
        assertEquals(FRUITY_BYTES.length, progress.currentBytes);
        assertEquals(FRUITY_BYTES.length, progress.requiredBytes);
    }

    protected void runCopyVirtualNonTypedFileTest() throws Exception {
        Uri testFile = mDocs.createVirtualFile(
                mSrcRoot, "/virtual.sth", "virtual/mime-type",
                FRUITY_BYTES);

        createJob(newArrayList(testFile)).run();
        waitForJobFinished();

        mJobListener.assertFailed();
        mJobListener.assertFilesFailed(newArrayList("virtual.sth"));

        mDocs.assertChildCount(mDestRoot, 0);
    }

    protected void runCopyVirtualNonTypedFileTestWithJobProgress() throws Exception {
        Uri testFile = mDocs.createVirtualFile(
                mSrcRoot, "/virtual.sth", "virtual/mime-type",
                FRUITY_BYTES);

        CopyJob job = createJob(newArrayList(testFile));
        job.run();
        waitForJobFinished();

        mJobListener.assertFailed();
        mJobListener.assertFilesFailed(newArrayList("virtual.sth"));

        mDocs.assertChildCount(mDestRoot, 0);

        JobProgress progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(mOpType, progress.operationType);
        assertTrue(progress.hasFailures);
        assertEquals(getVerb() + " “virtual.sth” to “" + mDestRoot.title + "”", progress.msg);
        assertEquals(0, progress.currentBytes);
        assertEquals(FRUITY_BYTES.length, progress.requiredBytes);
    }

    protected void runCopyEmptyDirTest() throws Exception {
        Uri testDir = mDocs.createFolder(mSrcRoot, "emptyDir");

        CopyJob job = createJob(newArrayList(testDir));
        job.run();
        waitForJobFinished();

        Notification progressNotification = job.getProgressNotification();
        String copyPercentage = progressNotification.extras.getString(Notification.EXTRA_SUB_TEXT);

        // the percentage representation should not be NaN.
        assertNotEquals("Percentage representation should not be NaN.",
                NumberFormat.getPercentInstance().format(Double.NaN), copyPercentage);

        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasDirectory(mDestRoot, "emptyDir");
    }

    protected void runCopyEmptyDirTestWithJobProgress() throws Exception {
        Uri testDir = mDocs.createFolder(mSrcRoot, "emptyDir");

        CopyJob job = createJob(newArrayList(testDir));
        job.run();
        waitForJobFinished();

        Notification progressNotification = job.getProgressNotification();
        String copyPercentage = progressNotification.extras.getString(Notification.EXTRA_SUB_TEXT);

        // the percentage representation should not be NaN.
        assertNotEquals("Percentage representation should not be NaN.",
                NumberFormat.getPercentInstance().format(Double.NaN), copyPercentage);

        mDocs.assertChildCount(mDestRoot, 1);
        mDocs.assertHasDirectory(mDestRoot, "emptyDir");

        JobProgress progress = job.getJobProgress();
        assertEquals(Job.STATE_COMPLETED, progress.state);
        assertEquals(mOpType, progress.operationType);
        assertFalse(progress.hasFailures);
        assertEquals(getVerb() + " “emptyDir” to “" + mDestRoot.title + "”", progress.msg);
        assertEquals(-1, progress.currentBytes);
        assertEquals(-1, progress.requiredBytes);
    }

    protected void runCopyDirRecursivelyTest() throws Exception {

        Uri testDir1 = mDocs.createFolder(mSrcRoot, "dir1");
        mDocs.createDocument(testDir1, "text/plain", "test1.txt");

        Uri testDir2 = mDocs.createFolder(testDir1, "dir2");
        mDocs.createDocument(testDir2, "text/plain", "test2.txt");

        createJob(newArrayList(testDir1)).run();
        waitForJobFinished();

        DocumentInfo dir1Copy = mDocs.findDocument(mDestRoot.documentId, "dir1");

        mDocs.assertChildCount(dir1Copy.derivedUri, 2);
        mDocs.assertHasDirectory(dir1Copy.derivedUri, "dir2");
        mDocs.assertHasFile(dir1Copy.derivedUri, "test1.txt");

        DocumentInfo dir2Copy = mDocs.findDocument(dir1Copy.documentId, "dir2");
        mDocs.assertChildCount(dir2Copy.derivedUri, 1);
        mDocs.assertHasFile(dir2Copy.derivedUri, "test2.txt");
    }

    protected void runNoCopyDirToSelfTest() throws Exception {
        Uri testDir = mDocs.createFolder(mSrcRoot, "someDir");

        createJob(mOpType,
                newArrayList(testDir),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId),
                testDir).run();

        waitForJobFinished();
        mJobListener.assertFailed();
        mJobListener.assertFilesFailed(newArrayList("someDir"));

        mDocs.assertChildCount(mDestRoot, 0);
    }

    protected void runNoCopyDirToDescendentTest() throws Exception {
        Uri testDir = mDocs.createFolder(mSrcRoot, "someDir");
        Uri destDir = mDocs.createFolder(testDir, "theDescendent");

        createJob(mOpType,
                newArrayList(testDir),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId),
                destDir).run();

        waitForJobFinished();
        mJobListener.assertFailed();
        mJobListener.assertFilesFailed(newArrayList("someDir"));

        mDocs.assertChildCount(mDestRoot, 0);
    }

    protected void runCopyFileWithReadErrorsTest() throws Exception {
        Uri testFile = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile, HAM_BYTES);

        String testId = DocumentsContract.getDocumentId(testFile);
        mDocs.simulateReadErrorsForFile(testId, null);

        createJob(newArrayList(testFile)).run();

        waitForJobFinished();
        mJobListener.assertFailed();
        mJobListener.assertFilesFailed(newArrayList("test1.txt"));

        mDocs.assertChildCount(mDestRoot, 0);
    }

    protected void runCopyProgressForFileCountTest() throws Exception {
        // Init FileCountProgressTracker with 10 docs required to copy.
        TestCopyJobProcessTracker<CopyJob.FileCountProgressTracker> tracker =
                new TestCopyJobProcessTracker(CopyJob.FileCountProgressTracker.class, 10,
                        createJob(newArrayList(mDocs.createFolder(mSrcRoot, "tempDir"))),
                        (completed) -> NumberFormat.getPercentInstance().format(completed),
                        (time) -> mContext.getString(R.string.copy_remaining,
                                DateUtils.formatDuration((Long) time)));

        // Assert init progress is 0 & default remaining time is -1.
        tracker.getProcessTracker().start();
        tracker.assertProgressTrackStarted();
        tracker.assertStartedProgressEquals(0);
        tracker.assertStartedRemainingTimeEquals(-1);

        // Progress 20%: 2 docs processed after 1 sec, no remaining time since first sample.
        IntStream.range(0, 2).forEach(__ -> tracker.getProcessTracker().onDocumentCompleted());
        tracker.updateProgressAndRemainingTime(1000);
        tracker.assertProgressEquals(0.2);
        tracker.assertNoRemainingTime();

        // Progress 40%: 4 docs processed after 2 secs, expect remaining time is 3 secs.
        IntStream.range(2, 4).forEach(__ -> tracker.getProcessTracker().onDocumentCompleted());
        tracker.updateProgressAndRemainingTime(2000);
        tracker.assertProgressEquals(0.4);
        tracker.assertReminingTimeEquals(3000L);

        // progress 100%: 10 doc processed after 5 secs, expect no remaining time shown.
        IntStream.range(4, 10).forEach(__ -> tracker.getProcessTracker().onDocumentCompleted());
        tracker.updateProgressAndRemainingTime(5000);
        tracker.assertProgressEquals(1.0);
        tracker.assertNoRemainingTime();
    }

    protected void runCopyProgressForByteCountTest() throws Exception {
        // Init ByteCountProgressTracker with 100 KBytes required to copy.
        TestCopyJobProcessTracker<CopyJob.ByteCountProgressTracker> tracker =
                new TestCopyJobProcessTracker(CopyJob.ByteCountProgressTracker.class, 100000,
                        createJob(newArrayList(mDocs.createFolder(mSrcRoot, "tempDir"))),
                        (completed) -> NumberFormat.getPercentInstance().format(completed),
                        (time) -> mContext.getString(R.string.copy_remaining,
                                DateUtils.formatDuration((Long) time)));

        // Assert init progress is 0 & default remaining time is -1.
        tracker.getProcessTracker().start();
        tracker.assertProgressTrackStarted();
        tracker.assertStartedProgressEquals(0);
        tracker.assertStartedRemainingTimeEquals(-1);

        // Progress 25%: 25 KBytes processed after 1 sec, no remaining time since first sample.
        tracker.getProcessTracker().onBytesCopied(25000);
        tracker.updateProgressAndRemainingTime(1000);
        tracker.assertProgressEquals(0.25);
        tracker.assertNoRemainingTime();

        // Progress 50%: 50 KBytes processed after 2 secs, expect remaining time is 2 secs.
        tracker.getProcessTracker().onBytesCopied(25000);
        tracker.updateProgressAndRemainingTime(2000);
        tracker.assertProgressEquals(0.5);
        tracker.assertReminingTimeEquals(2000L);

        // Progress 100%: 100 KBytes processed after 4 secs, expect no remaining time shown.
        tracker.getProcessTracker().onBytesCopied(50000);
        tracker.updateProgressAndRemainingTime(4000);
        tracker.assertProgressEquals(1.0);
        tracker.assertNoRemainingTime();
    }

    void waitForJobFinished() throws Exception {
        mJobListener.waitForFinished();
        mDocs.waitForWrite();
    }

    /**
     * Creates a job with a stack consisting to the default source and destination.
     * TODO: Clean up, as mDestRoot.documentInfo may not really be the parent of
     * srcs.
     */
    final T createJob(List<Uri> srcs) throws Exception {
        Uri srcParent = DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId);
        return createJob(srcs, srcParent);
    }

    final T createJob(List<Uri> srcs, Uri srcParent) throws Exception {
        Uri destination = DocumentsContract.buildDocumentUri(AUTHORITY, mDestRoot.documentId);
        return createJob(mOpType, srcs, srcParent, destination);
    }
}
