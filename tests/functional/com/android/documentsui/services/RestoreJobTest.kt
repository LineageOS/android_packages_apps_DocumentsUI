/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.documentsui.services

import android.app.Notification.CATEGORY_ERROR
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TITLE
import android.net.Uri
import android.os.Build
import android.platform.test.annotations.EnableFlags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.CheckFlagsRule
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import android.provider.DocumentsContract.buildDocumentUri
import android.provider.Flags.FLAG_ENABLE_DOCUMENTS_TRASH_API
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.android.documentsui.TrashDocumentHelper
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.FileOperationService.OPERATION_RESTORE
import com.android.documentsui.util.VersionUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/**
 * Tests the functionality of RestoreJob.
 *
 * When a file is trashed, its parent directory structure is recreated inside the .trash-storage
 * directory. A file or directory must be prefixed with ".trashed-<timestamp>-" to be considered a
 * trashed item.
 *
 * For example:
 * - A file at "<root>/<parent_1>/<parent_2>/<file>" is moved to trash.
 * - Its path in trash becomes: "<root>/.trash-storage/<parent_1>/<parent_2>/.trashed-<ts>-file"
 *
 * When the file is restored, it is moved back to its original path. The now-empty parent
 * directories (<parent_1>, <parent_2>) inside ".trash-storage" are then removed.
 */
@MediumTest
@RequiresFlagsEnabled(FLAG_ENABLE_DOCUMENTS_TRASH_API)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
internal class RestoreJobTest : AbstractJobTest<TrashJob>() {
    @get:Rule val setFlags = OverrideFlagsRule()

    @get:Rule val checkFlagsRule: CheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule()

    override fun setUp() {
        // Skip test if the platform SDK is not newer than Android Baklava (SDK 36).
        // The Trash feature under test relies on DocumentsContract APIs introduced in the
        // Android release after Baklava (SDK 36).
        // As DocumentsUI is a Mainline module, it's subject to MTS testing, which runs on
        // older Android base builds to verify backward compatibility. However, this specific
        // Trash feature lacks backward compatibility with platforms at or below Baklava.
        // This assumption prevents failures when the test runs on an older base OS
        // without the necessary APIs.
        assumeTrue(VersionUtils.isGreaterThanB())
        super.setUp()
    }

    /**
     * Tests the restoration of a single file. Verifies that the file is moved to its original path
     * and that the parent directories within the trash are removed.
     */
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testRestoreSingleFile() {
        val trashStorageDir = mDocs.createFolder(mSrcRoot, TrashDocumentHelper.TRASH_LOCATION)
        val testDirUri = mDocs.createFolder(trashStorageDir, "testDir")
        val trashedFileName = ".trashed-12345-document.txt"
        val trashedFileUri = mDocs.createDocument(testDirUri, "text/plain", trashedFileName)

        val job = createRestoreJob(listOf(trashedFileUri))

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        job.run()
        mJobListener.assertFinished()

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Restoring .trashed-12345-document.txt")
        }

        // Verify filesystem changes: trash is empty, and the file is restored to the source root.
        mDocs.assertChildCount(trashStorageDir, 0)
        mDocs.assertHasDirectory(mSrcRoot, "testDir")

        // Verify the restored file is now in its original location within mSrcRoot.
        val testDirInSrcRoot = mDocs.findDocument(mSrcRoot.documentId, "testDir")
        val children = mDocs.listChildren(testDirInSrcRoot.documentId)
        assertThat(children.any { it.displayName.contains("document.txt") }).isTrue()
    }

    /**
     * Tests the restoration of multiple files in a single operation. Verifies that all files are
     * moved to their original location and the trash directory is cleaned up.
     */
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testRestoreMultipleFiles() {
        // Create a trash directory and place two trashed files inside a subdirectory
        val trashStorageDir = mDocs.createFolder(mSrcRoot, TrashDocumentHelper.TRASH_LOCATION)
        val testDirUri = mDocs.createFolder(trashStorageDir, "testDir")
        val trashedFileName1 = ".trashed-12345-document1.txt"
        val trashedFileName2 = ".trashed-67890-document2.txt"
        val trashedFile1Uri = mDocs.createDocument(testDirUri, "text/plain", trashedFileName1)
        val trashedFile2Uri = mDocs.createDocument(testDirUri, "text/plain", trashedFileName2)

        val job = createRestoreJob(listOf(trashedFile1Uri, trashedFile2Uri))

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        job.run()
        mJobListener.assertFinished()

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Restoring 2 files")
        }

        // Verify filesystem changes: trash is empty, files are in the source root
        mDocs.assertChildCount(trashStorageDir, 0)
        mDocs.assertHasDirectory(mSrcRoot, "testDir")

        // Verify the restored files are now in their original location within mSrcRoot.
        val testDirInSrcRoot = mDocs.findDocument(mSrcRoot.documentId, "testDir")
        mDocs.assertChildCount(testDirInSrcRoot.derivedUri, 2)
        val children = mDocs.listChildren(testDirInSrcRoot.documentId)
        assertThat(children.any { it.displayName.contains("document1.txt") }).isTrue()
        assertThat(children.any { it.displayName.contains("document2.txt") }).isTrue()
    }

    /**
     * Tests the restoration of an entire folder. Verifies that the folder and all its contents are
     * moved back to the original location.
     */
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testRestoreFolder() {
        // Create a trash directory, then create a folder with two files inside it.
        val trashStorageDir = mDocs.createFolder(mSrcRoot, TrashDocumentHelper.TRASH_LOCATION)
        val trashedFolderName = ".trashed-12345-dir1"
        val trashedFileName1 = ".trashed-12345-document1.txt"
        val trashedFileName2 = ".trashed-12345-document2.txt"
        val trashedDirUri = mDocs.createFolder(trashStorageDir, trashedFolderName)
        mDocs.createDocument(trashedDirUri, "text/plain", trashedFileName1)
        mDocs.createDocument(trashedDirUri, "text/plain", trashedFileName2)

        // Create a restore job for the trashed folder.
        val job = createRestoreJob(listOf(trashedDirUri))

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        job.run()
        mJobListener.assertFinished()

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Restoring .trashed-12345-dir1")
        }

        // Verify the changes: trash should be empty, and the folder should be restored.
        mDocs.assertChildCount(trashStorageDir, 0)
        mDocs.assertHasDirectory(mSrcRoot, "dir1")

        // Verify the contents of the restored folder.
        val restoredDirInfo = mDocs.findDocument(mSrcRoot.documentId, "dir1")
        mDocs.assertChildCount(restoredDirInfo.derivedUri, 2)
        mDocs.assertHasFile(restoredDirInfo.derivedUri, "document1.txt")
        mDocs.assertHasFile(restoredDirInfo.derivedUri, "document2.txt")
    }

    /**
     * Tests that the restore job fails when attempting to restore a file that is not in the trash.
     * Verifies that the job status is marked as failed and an appropriate failure notification is
     * generated.
     */
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testRestoreFailsForNonTrashedFile() {
        // Create a document in the source root. Attempting to restore a file that isn't
        // in the trash directory should cause a failure.
        val uri = mDocs.createDocument(mSrcRoot, "text/plain", "document.txt")

        // Create the restore job
        val job = createRestoreJob(listOf(uri))

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        // Run the job and assert that it fails
        job.run()
        mJobListener.assertFailed()

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
        }

        with(job.failureNotification) {
            assertThat(category).isEqualTo(CATEGORY_ERROR)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Couldn’t restore 1 item")
                assertThat(getCharSequence(EXTRA_TEXT)).isEqualTo("Tap to view details")
            }
        }
    }

    /**
     * Tests that the restore job fails for a file that exists in the trash directory but does not
     * follow the required naming convention for trashed files (i.e., it lacks the `.trashed-`
     * prefix).
     */
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testRestoreFailsForImproperlyNamedFileInTrash() {
        // Create a file directly in the trash directory without the ".trashed-" prefix.
        // The restore operation should fail because it's not a valid trashed file.
        val trashStorageDir = mDocs.createFolder(mSrcRoot, TrashDocumentHelper.TRASH_LOCATION)
        val fileUri = mDocs.createDocument(trashStorageDir, "text/plain", "document.txt")

        // Create the restore job
        val job = createRestoreJob(listOf(fileUri))

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        // Run the job and assert that it fails
        job.run()
        mJobListener.assertFailed()

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
        }

        with(job.failureNotification) {
            assertThat(category).isEqualTo(CATEGORY_ERROR)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Couldn’t restore 1 item")
                assertThat(getCharSequence(EXTRA_TEXT)).isEqualTo("Tap to view details")
            }
        }
    }

    /**
     * Tests that the restore operation fails when a file with the same name already exists in the
     * target destination. The [android.provider.DocumentsContract.restoreDocumentFromTrash] call
     * throws an exception, which is then caught and handled to prevent the app from crashing.
     */
    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testRestoreFailsWhenFileAlreadyExistsAtDestination() {
        // Setup: Create a directory structure in the trash and add a trashed file to it.
        val trashStorageDir = mDocs.createFolder(mSrcRoot, TrashDocumentHelper.TRASH_LOCATION)
        val testDirName = "testDir"
        val fileName = "document.txt"
        val testDirUri = mDocs.createFolder(trashStorageDir, testDirName)
        val trashedFileName = ".trashed-12345-$fileName"
        val trashedFileUri = mDocs.createDocument(testDirUri, "text/plain", trashedFileName)

        // Create a conflicting file at the expected restore destination.
        val testDir = mDocs.createFolder(mSrcRoot, "testDir")
        mDocs.createDocument(testDir, "text/plain", fileName)

        // Create the restore job.
        val job = createRestoreJob(listOf(trashedFileUri))

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        // Run the job and assert that it fails
        job.run()
        mJobListener.assertFailed()

        with(job.jobProgress) {
            assertThat(operationType).isEqualTo(OPERATION_RESTORE)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
        }

        with(job.failureNotification) {
            assertThat(category).isEqualTo(CATEGORY_ERROR)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Couldn’t restore 1 item")
                assertThat(getCharSequence(EXTRA_TEXT)).isEqualTo("Tap to view details")
            }
        }
    }

    /**
     * Creates a test job to restore files from the trash.
     *
     * @param src A list of URIs for the files to restore.
     * @return A new RestoreJob instance.
     */
    private fun createRestoreJob(src: List<Uri>): RestoreJob {
        // Set the destination for the restored files to always be the root directory.
        val dest = buildDocumentUri(AUTHORITY, mSrcRoot.documentId)

        // Create and return the RestoreJob.
        return createJob(OPERATION_RESTORE, src, dest, dest) as RestoreJob
    }

    companion object {
        const val TAG = "RestoreJobTest"
    }
}
