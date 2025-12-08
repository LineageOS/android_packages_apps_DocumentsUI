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
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.FileOperationService.OPERATION_TRASH
import com.android.documentsui.util.VersionUtils
import com.google.common.truth.Truth.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Tests TrashJob. */
@MediumTest
@RequiresFlagsEnabled(FLAG_ENABLE_DOCUMENTS_TRASH_API)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.BAKLAVA, codeName = "B")
internal class TrashJobTest : AbstractJobTest<TrashJob>() {
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

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testTrashSingleFile() {
        val testDir1 = mDocs.createFolder(mSrcRoot, "dir1")
        val fileUri = mDocs.createDocument(testDir1, "text/plain", "document.txt")

        val dir1Info = mDocs.findDocument(mSrcRoot.documentId, "dir1")

        mDocs.assertHasDirectory(mSrcRoot, "dir1")
        mDocs.assertChildCount(dir1Info.derivedUri, 1)
        mDocs.assertHasFile(dir1Info.derivedUri, "document.txt")

        val job = createTrashJob(listOf(fileUri))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Trashing document.txt")
        }

        mDocs.assertHasDirectory(mSrcRoot, "dir1")
        mDocs.assertChildCount(dir1Info.derivedUri, 0)

        val trashBaseDocumentInfo =
            mDocs.findDocument(mSrcRoot.documentId, TrashDocumentHelper.TRASH_LOCATION)
        val dir1InsideTrashBase = mDocs.findDocument(trashBaseDocumentInfo.documentId, "dir1")

        mDocs.assertChildCount(dir1InsideTrashBase.derivedUri, 1)
        val children = mDocs.listChildren(dir1InsideTrashBase.documentId)
        assertThat(children.any { it.displayName.contains("document.txt") }).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testTrashMultipleFile() {
        val testDir1 = mDocs.createFolder(mSrcRoot, "dir1")
        val file1Uri = mDocs.createDocument(testDir1, "text/plain", "document1.txt")
        val file2Uri = mDocs.createDocument(testDir1, "text/plain", "document2.txt")

        val dir1Info = mDocs.findDocument(mSrcRoot.documentId, "dir1")

        mDocs.assertHasDirectory(mSrcRoot, "dir1")
        mDocs.assertChildCount(dir1Info.derivedUri, 2)
        mDocs.assertHasFile(dir1Info.derivedUri, "document1.txt")
        mDocs.assertHasFile(dir1Info.derivedUri, "document2.txt")

        val job = createTrashJob(listOf(file1Uri, file2Uri))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Trashing 2 files")
        }

        mDocs.assertHasDirectory(mSrcRoot, "dir1")
        mDocs.assertChildCount(dir1Info.derivedUri, 0)

        val trashBaseDocumentInfo =
            mDocs.findDocument(mSrcRoot.documentId, TrashDocumentHelper.TRASH_LOCATION)
        val dir1InsideTrashBase = mDocs.findDocument(trashBaseDocumentInfo.documentId, "dir1")

        mDocs.assertChildCount(dir1InsideTrashBase.derivedUri, 2)
        val children = mDocs.listChildren(dir1InsideTrashBase.documentId)
        assertThat(children.any { it.displayName.contains("document1.txt") }).isTrue()
        assertThat(children.any { it.displayName.contains("document2.txt") }).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testTrashFolder() {
        val testDir1 = mDocs.createFolder(mSrcRoot, "dir1")
        mDocs.createDocument(testDir1, "text/plain", "document1.txt")
        mDocs.createDocument(testDir1, "text/plain", "document2.txt")

        val dir1Info = mDocs.findDocument(mSrcRoot.documentId, "dir1")

        // only single child i.e. directory only
        mDocs.assertChildCount(mSrcRoot, 1)
        mDocs.assertHasDirectory(mSrcRoot, "dir1")
        mDocs.assertChildCount(dir1Info.derivedUri, 2)
        mDocs.assertHasFile(dir1Info.derivedUri, "document1.txt")
        mDocs.assertHasFile(dir1Info.derivedUri, "document2.txt")

        val job = createTrashJob(listOf(testDir1))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Trashing dir1")
        }

        // Parent root should not consist "dir1", only .trash-storage
        mDocs.assertChildCount(mSrcRoot, 1)
        mDocs.assertHasDirectory(mSrcRoot, TrashDocumentHelper.TRASH_LOCATION)

        val trashBaseDocumentInfo =
            mDocs.findDocument(mSrcRoot.documentId, TrashDocumentHelper.TRASH_LOCATION)

        var trashedDirInTrashBase: DocumentInfo? = null

        val trashedChildren = mDocs.listChildren(trashBaseDocumentInfo.derivedUri)

        for (child in trashedChildren) {
            if (child.displayName.contains("dir1")) {
                trashedDirInTrashBase = child
                break
            }
        }

        assertThat(trashedDirInTrashBase).isNotNull()

        mDocs.assertChildCount(trashedDirInTrashBase?.derivedUri, 2)
        val children = mDocs.listChildren(trashedDirInTrashBase?.documentId)
        assertThat(children.any { it.displayName.contains("document1.txt") }).isTrue()
        assertThat(children.any { it.displayName.contains("document2.txt") }).isTrue()
    }

    @Test
    @EnableFlags(Flags.FLAG_ENABLE_TRASH_FLOW_RO)
    fun testTrashFailedNoFileFound() {
        // create a document in mDestRoot, trashDocument only working for mSrcRoot,
        // so this will give FileNotFoundException
        val uri = mDocs.createDocument(mDestRoot, "text/plain", "document.txt")
        mDocs.writeDocument(uri, HAM_BYTES)

        val job = createTrashJob(listOf(uri))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
        }

        job.run()
        mJobListener.assertFailed()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_TRASH)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
        }

        with(job.getFailureNotification()) {
            assertThat(category).isEqualTo(CATEGORY_ERROR)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Couldn’t trash 1 item")
                assertThat(getCharSequence(EXTRA_TEXT)).isEqualTo("Tap to view details")
            }
        }
    }

    private fun createTrashJob(src: List<Uri>): TrashJob {
        val dest = buildDocumentUri(AUTHORITY, mSrcRoot.documentId)
        return createJob(OPERATION_TRASH, src, dest, dest)
    }

    companion object {
        const val TAG = "TrashJobTest"
    }
}
