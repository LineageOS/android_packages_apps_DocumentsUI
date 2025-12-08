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

import android.net.Uri
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.provider.DocumentsContract.buildDocumentUri
import android.util.Log
import androidx.test.filters.MediumTest
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_ZIP_NG_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.CompressJob.getArchiveName
import com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS
import com.android.documentsui.util.FlagUtils.Companion.isZipNgFlagEnabled
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test

/** Tests CompressJob. */
@MediumTest
internal class CompressJobTest : AbstractJobTest<CompressJob>() {
    @get:Rule val setFlags = OverrideFlagsRule()

    private data class Entry(val size: Long, val mimeType: String)

    /* Tests CompressJob.getArchiveName(). */
    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO)
    fun archiveName() {
        assertThat(getArchiveName("Test", false)).isEqualTo("Test.zip")
        assertThat(getArchiveName("Test.", false)).isEqualTo("Test..zip")
        assertThat(getArchiveName("Test.a", false)).isEqualTo("Test.zip")
        assertThat(getArchiveName("Test.a.b.c", false)).isEqualTo("Test.a.b.zip")
        assertThat(getArchiveName("Test.txt", false)).isEqualTo("Test.zip")
        assertThat(getArchiveName("Test.TooLongToBeAnExtension", false))
            .isEqualTo("Test.TooLongToBeAnExtension.zip")
        assertThat(getArchiveName("Test....txt", false)).isEqualTo("Test....zip")
        assertThat(getArchiveName("Test...", false)).isEqualTo("Test....zip")
        assertThat(getArchiveName(".Test", false)).isEqualTo(".Test.zip")
        assertThat(getArchiveName(".Test.", false)).isEqualTo(".Test..zip")
        assertThat(getArchiveName(".Test.txt", false)).isEqualTo(".Test.zip")
        assertThat(getArchiveName("...", false)).isEqualTo("....zip")
        assertThat(getArchiveName("...Test", false)).isEqualTo("...zip")

        assertThat(getArchiveName("Test", true)).isEqualTo("Test.zip")
        assertThat(getArchiveName("Test.", true)).isEqualTo("Test..zip")
        assertThat(getArchiveName("Test.a", true)).isEqualTo("Test.a.zip")
        assertThat(getArchiveName("Test.a.b.c", true)).isEqualTo("Test.a.b.c.zip")
        assertThat(getArchiveName("Test.txt", true)).isEqualTo("Test.txt.zip")
        assertThat(getArchiveName("Test.ToLongToBeAnExtension", true))
            .isEqualTo("Test.ToLongToBeAnExtension.zip")
        assertThat(getArchiveName("Test....txt", true)).isEqualTo("Test....txt.zip")
        assertThat(getArchiveName("Test...", true)).isEqualTo("Test....zip")
        assertThat(getArchiveName(".Test", true)).isEqualTo(".Test.zip")
        assertThat(getArchiveName(".Test.", true)).isEqualTo(".Test..zip")
        assertThat(getArchiveName(".Test.txt", true)).isEqualTo(".Test.txt.zip")
        assertThat(getArchiveName("...", true)).isEqualTo("....zip")
        assertThat(getArchiveName("...Test", true)).isEqualTo("...Test.zip")
    }

    /* Tests CompressJob.getArchiveName(). */
    @Test
    @DisableFlags(FLAG_ZIP_NG_RO)
    fun archiveNameOld() {
        assertThat(getArchiveName("Test", false)).isEqualTo("Test.zip")
        assertThat(getArchiveName("Test.", false)).isEqualTo("Test..zip")
        assertThat(getArchiveName("Test.a", false)).isEqualTo("Test.a.zip")
        assertThat(getArchiveName("Test.a.b.c", false)).isEqualTo("Test.a.b.c.zip")
        assertThat(getArchiveName("Test.txt", false)).isEqualTo("Test.txt.zip")
        assertThat(getArchiveName("Test.ToLongToBeAnExtension", false))
            .isEqualTo("Test.ToLongToBeAnExtension.zip")
        assertThat(getArchiveName("Test....txt", false)).isEqualTo("Test....txt.zip")
        assertThat(getArchiveName("Test...", false)).isEqualTo("Test....zip")
        assertThat(getArchiveName(".Test", false)).isEqualTo(".Test.zip")
        assertThat(getArchiveName(".Test.", false)).isEqualTo(".Test..zip")
        assertThat(getArchiveName(".Test.txt", false)).isEqualTo(".Test.txt.zip")
        assertThat(getArchiveName("...", false)).isEqualTo("....zip")
        assertThat(getArchiveName("...Test", false)).isEqualTo("...Test.zip")

        assertThat(getArchiveName("Test", true)).isEqualTo("Test.zip")
        assertThat(getArchiveName("Test.", true)).isEqualTo("Test..zip")
        assertThat(getArchiveName("Test.a", true)).isEqualTo("Test.a.zip")
        assertThat(getArchiveName("Test.a.b.c", true)).isEqualTo("Test.a.b.c.zip")
        assertThat(getArchiveName("Test.txt", true)).isEqualTo("Test.txt.zip")
        assertThat(getArchiveName("Test.ToLongToBeAnExtension", true))
            .isEqualTo("Test.ToLongToBeAnExtension.zip")
        assertThat(getArchiveName("Test....txt", true)).isEqualTo("Test....txt.zip")
        assertThat(getArchiveName("Test...", true)).isEqualTo("Test....zip")
        assertThat(getArchiveName(".Test", true)).isEqualTo(".Test.zip")
        assertThat(getArchiveName(".Test.", true)).isEqualTo(".Test..zip")
        assertThat(getArchiveName(".Test.txt", true)).isEqualTo(".Test.txt.zip")
        assertThat(getArchiveName("...", true)).isEqualTo("....zip")
        assertThat(getArchiveName("...Test", true)).isEqualTo("...Test.zip")
    }

    /** Tests zipping one file. */
    @Test
    fun zipOneFile() {
        val uri = mDocs.createDocument(mSrcRoot, "text/plain", "Test.txt")
        mDocs.writeDocument(uri, HAM_BYTES)

        assertTreeIs(mutableMapOf("/Test.txt" to textEntry(14)))

        val job = createJob(listOf(uri))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(-1)
            assertThat(requiredBytes).isEqualTo(-1)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Zipping “Test.txt”")
            assertThat(currentBytes).isEqualTo(14)
            assertThat(requiredBytes).isEqualTo(14)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("TEST_ROOT_0")
        }

        val archivePath = if (isZipNgFlagEnabled()) "/Test.zip" else "/Test.txt.zip"
        assertTreeIs(mutableMapOf("/Test.txt" to textEntry(14), archivePath to zipEntry(146)))
    }

    /** Tests zipping one folder. */
    @Test
    fun zipOneFolder() {
        val uri = mDocs.createDocument(mSrcRoot, MIME_TYPE_DIR, "Test.a")

        assertTreeIs(mutableMapOf("/Test.a" to dirEntry))

        val job = createJob(listOf(uri))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(-1)
            assertThat(requiredBytes).isEqualTo(-1)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Zipping “Test.a”")
            assertThat(currentBytes).isEqualTo(-1)
            assertThat(requiredBytes).isEqualTo(-1)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("TEST_ROOT_0")
        }

        assertTreeIs(mutableMapOf("/Test.a" to dirEntry, "/Test.a.zip" to zipEntry(130)))
    }

    /** Tests zipping two files. */
    @Test
    fun zipTwoFiles() {
        val uri1 = mDocs.createDocument(mSrcRoot, "text/plain", "Test1.txt")
        mDocs.writeDocument(uri1, HAM_BYTES)

        val uri2 = mDocs.createDocument(mSrcRoot, "text/plain", "Test2.txt")
        mDocs.writeDocument(uri2, FRUITY_BYTES)

        assertTreeIs(mutableMapOf("/Test1.txt" to textEntry(14), "/Test2.txt" to textEntry(19)))

        val job = createJob(listOf(uri1, uri2))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(-1)
            assertThat(requiredBytes).isEqualTo(-1)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Zipping 2 files")
            assertThat(currentBytes).isEqualTo(33)
            assertThat(requiredBytes).isEqualTo(33)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("TEST_ROOT_0")
        }

        val archivePath = if (isZipNgFlagEnabled()) "/Archive.zip" else "/archive.zip"
        assertTreeIs(
            mutableMapOf(
                "/Test1.txt" to textEntry(14),
                "/Test2.txt" to textEntry(19),
                archivePath to zipEntry(279),
            )
        )
    }

    /** Tests when there is a collision for the destination archive. */
    @Test
    fun collision() {
        val uri1 = mDocs.createDocument(mSrcRoot, "text/plain", "Test1.txt")
        mDocs.writeDocument(uri1, HAM_BYTES)

        val uri2 = mDocs.createDocument(mSrcRoot, "text/plain", "Test2.txt")
        mDocs.writeDocument(uri2, FRUITY_BYTES)

        mDocs.createDocument(mSrcRoot, "application/zip", "archive.zip")
        mDocs.createDocument(mSrcRoot, "application/zip", "Archive.zip")

        assertTreeIs(
            mutableMapOf(
                "/Test1.txt" to textEntry(14),
                "/Test2.txt" to textEntry(19),
                "/archive.zip" to zipEntry(0),
                "/Archive.zip" to zipEntry(0),
            )
        )

        val job = createJob(listOf(uri1, uri2))

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(-1)
            assertThat(requiredBytes).isEqualTo(-1)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()

        // The destination document provider used in this test does not automatically rename a
        // file in case of name collision.
        // As a consequence, CompressJob fails to create the archive.
        mJobListener.assertFailed()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_COMPRESS)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            // TODO(b/439976731) This might have to change to "Couldn't zip 2 files"
            assertThat(msg).isEqualTo("Zipping 2 files")
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(33)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("TEST_ROOT_0")
        }

        assertTreeIs(
            mutableMapOf(
                "/Test1.txt" to textEntry(14),
                "/Test2.txt" to textEntry(19),
                "/archive.zip" to zipEntry(0),
                "/Archive.zip" to zipEntry(0),
            )
        )
    }

    private fun createJob(srcs: List<Uri>): CompressJob {
        val dest = buildDocumentUri(AUTHORITY, mSrcRoot.documentId)
        return createJob(OPERATION_COMPRESS, srcs, dest, dest)
    }

    private fun assertTreeIs(
        wantEntries: MutableMap<String, Entry>?,
        parentPath: String,
        info: DocumentInfo,
    ) {
        val path = "$parentPath/${info.displayName}"

        if (wantEntries != null) {
            val wantEntry = wantEntries.remove(path)
            if (wantEntry == null) {
                throw AssertionError("Found unexpected file '$path'")
            }

            assertWithMessage("For file '%s'", path)
                .that(info.mimeType)
                .isEqualTo(wantEntry.mimeType)

            // Since the underlying DocumentProvider might report an incorrect size in
            // `info.size` (b/429262410), we ignore the reported size and we don't compare it
            // to `wantEntry`. So, we don't do:
            // assertWithMessage("For file '%s'", path).that(info.size).isEqualTo(wantEntry.size)
        } else {
            Log.i(TAG, "\"$path\" to Entry(${info.size}, \"${info.mimeType}\"),")
        }

        if (info.isDirectory) {
            for (childInfo in mDocs.listChildren(info.documentId)) {
                assertTreeIs(wantEntries, path, childInfo)
            }
        }
    }

    /**
     * Recursively asserts that the document provider contains the specified files and directories,
     * and that the contained files have the given sizes.
     */
    private fun assertTreeIs(wantEntries: MutableMap<String, Entry>?) {
        for (info in mDocs.listAllChildren(mSrcRoot)) {
            assertTreeIs(wantEntries, "", info)
        }

        if (wantEntries != null) assertThat(wantEntries).isEmpty()
    }

    companion object {
        private const val TAG = "CompressJobTest"
        private val dirEntry = Entry(-1, MIME_TYPE_DIR)

        private fun textEntry(size: Long) = Entry(size, "text/plain")

        private fun zipEntry(size: Long = -1) = Entry(size, "application/zip")
    }
}
