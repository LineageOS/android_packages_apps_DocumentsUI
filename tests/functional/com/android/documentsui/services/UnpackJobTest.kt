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
import android.app.Notification.CATEGORY_PROGRESS
import android.app.Notification.EXTRA_PROGRESS_INDETERMINATE
import android.app.Notification.EXTRA_TEXT
import android.app.Notification.EXTRA_TITLE
import android.net.Uri
import android.platform.test.annotations.DisableFlags
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import android.provider.DocumentsContract.buildDocumentUri
import android.util.Log
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_ZIP_NG_RO
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.services.FileOperationService.OPERATION_UNPACK
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Tests UnpackJob. */
@MediumTest
internal class UnpackJobTest : AbstractJobTest<UnpackJob>() {
    @get:Rule
    val setFlags = OverrideFlagsRule()

    private data class Entry(val size: Long, val mimeType: String)

    /** Tests with a MIME type that is not a supported archive type. */
    @Test
    fun unsupportedMimeType() {
        val uri = mDocs.createDocument(mSrcRoot, "text/plain", "My Text File.txt")
        mDocs.writeDocument(uri, HAM_BYTES)
        assertTreeIs(mutableMapOf("/My Text File.txt" to textEntry(14)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFailed()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            assertThat(msg).isEqualTo("Extracting “My Text File.txt” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        // No extraction folder should have been created.
        assertTreeIs(mutableMapOf("/My Text File.txt" to textEntry(14)))
    }

    /** Tests with an invalid ZIP archive. */
    @Test
    fun invalidZip() {
        val uri = mDocs.createDocument(mSrcRoot, "application/zip", "My Archive.zip")
        mDocs.writeDocument(uri, HAM_BYTES)
        assertTreeIs(mutableMapOf("/My Archive.zip" to zipEntry(14)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFailed()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            assertThat(msg).isEqualTo("Extracting “My Archive.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        // No extraction folder should have been created.
        assertTreeIs(mutableMapOf("/My Archive.zip" to zipEntry(14)))
    }

    /** Tests with a valid ZIP archive. */
    @Test
    fun validZip() {
        val uri = createDocument("application/zip", "archives/zip/hello.zip")
        assertTreeIs(mutableMapOf("/hello.zip" to zipEntry(806)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Extracting “hello.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(110)
            assertThat(requiredBytes).isEqualTo(110)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("hello")
        }

        assertTreeIs(
            mutableMapOf(
                "/hello.zip" to zipEntry(806),
                "/hello" to dirEntry,
                "/hello/hello" to dirEntry,
                "/hello/hello/hello.txt" to textEntry(48),
                "/hello/hello/hello2.txt" to textEntry(48),
                "/hello/hello/inside_folder" to dirEntry,
                "/hello/hello/inside_folder/hello_insside.txt" to textEntry(14),
            )
        )
    }

    /** Tests with a valid 7Z archive. */
    @Test
    fun valid7Z() {
        val uri = createDocument("application/x-7z-compressed", "archives/7z/hello.7z")
        assertTreeIs(mutableMapOf("/hello.7z" to Entry(253, "application/x-7z-compressed")))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Extracting “hello.7z” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(110)
            assertThat(requiredBytes).isEqualTo(110)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("hello")
        }

        assertTreeIs(
            mutableMapOf(
                "/hello.7z" to Entry(253, "application/x-7z-compressed"),
                "/hello" to dirEntry,
                "/hello/hello" to dirEntry,
                "/hello/hello/hello.txt" to textEntry(48),
                "/hello/hello/hello2.txt" to textEntry(48),
                "/hello/hello/inside_folder" to dirEntry,
                "/hello/hello/inside_folder/hello_insside.txt" to textEntry(14),
            )
        )
    }

    /** Tests with a valid TAR archive. */
    @Test
    fun validTar() {
        val uri = createDocument("application/x-tar", "archives/tar/hello.tar")
        assertTreeIs(mutableMapOf("/hello.tar" to Entry(10240, "application/x-tar")))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Extracting “hello.tar” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(110)
            assertThat(requiredBytes).isEqualTo(110)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("hello")
        }

        assertTreeIs(
            mutableMapOf(
                "/hello.tar" to Entry(10240, "application/x-tar"),
                "/hello" to dirEntry,
                "/hello/hello" to dirEntry,
                "/hello/hello/hello.txt" to textEntry(48),
                "/hello/hello/hello2.txt" to textEntry(48),
                "/hello/hello/inside_folder" to dirEntry,
                "/hello/hello/inside_folder/hello_insside.txt" to textEntry(14),
            )
        )
    }

    /** Tests with a valid TGZ archive. */
    @Test
    fun validTgz() {
        val uri = createDocument("application/x-gtar-compressed", "archives/tar_gz/hello.tgz")
        assertTreeIs(mutableMapOf("/hello.tgz" to Entry(406, "application/x-gtar-compressed")))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Extracting “hello.tgz” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(110)
            assertThat(requiredBytes).isEqualTo(110)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("hello")
        }

        assertTreeIs(
            mutableMapOf(
                "/hello.tgz" to Entry(406, "application/x-gtar-compressed"),
                "/hello" to dirEntry,
                "/hello/hello" to dirEntry,
                "/hello/hello/hello.txt" to textEntry(48),
                "/hello/hello/hello2.txt" to textEntry(48),
                "/hello/hello/inside_folder" to dirEntry,
                "/hello/hello/inside_folder/hello_insside.txt" to textEntry(14),
            )
        )
    }

    /** Tests with a partially encrypted ZIP archive. */
    @Test
    fun partiallyEncryptedZip() {
        val uri = createDocument("application/zip", "archives/zip/different-encryptions.zip")
        assertTreeIs(mutableMapOf("/different-encryptions.zip" to zipEntry(1083)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFailed()
        mJobListener.assertFailureCount(4)
        assertThat(job.failedPaths).containsExactly(
            "/Encrypted AES-128.txt",
            "/Encrypted AES-192.txt",
            "/Encrypted AES-256.txt",
            "/Encrypted ZipCrypto.txt"
        )

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            assertThat(msg).isEqualTo("Extracting “different-encryptions.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(23)
            assertThat(requiredBytes).isEqualTo(23)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("different-encryptions")
        }

        // The archive should have been partially extracted.
        assertTreeIs(
            mutableMapOf(
                "/different-encryptions.zip" to zipEntry(1083),
                "/different-encryptions" to dirEntry,
                "/different-encryptions/ClearText.txt" to textEntry(23),
            )
        )
    }

    /** Tests when there is a collision for the extraction folder. */
    @Test
    fun collisionForExtractionFolder() {
        val uri = createDocument("application/zip", "archives/zip/hello.zip")
        mDocs.createFolder(mSrcRoot, "hello")
        assertTreeIs(
            mutableMapOf(
                "/hello.zip" to zipEntry(806),
                "/hello" to dirEntry,
            )
        )

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()

        // The destination document provider used in this test does not automatically rename a
        // folder in case of name collision.
        // As a consequence, UnpackJob fails to extract the archive.
        mJobListener.assertFailed()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            assertThat(msg).isEqualTo("Extracting “hello.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(110)
        }

        assertTreeIs(
            mutableMapOf(
                "/hello.zip" to zipEntry(806),
                "/hello" to dirEntry,
            )
        )
    }

    /** Tests with a ZIP archive containing colliding entries. */
    @Test
    fun collisionsInArchive() {
        val uri = createDocument("application/zip", "archives/zip/file-dir-same-name.zip")
        assertTreeIs(mutableMapOf("/file-dir-same-name.zip" to zipEntry(823)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()

        // The destination document provider used in this test does not automatically rename files
        // or folders in case of name collisions.
        // As a consequence, UnpackJob fails to extract some files from the archive.
        mJobListener.assertFailed()
        mJobListener.assertFailureCount(6)
        assertThat(job.failedPaths).containsExactly(
            "/pet/cat",
            "/pet",
            "/pet/cat/fish",
            "/pet/cat",
            "/pet",
            "/pet/cat/fish"
        )

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            assertThat(msg).isEqualTo("Extracting “file-dir-same-name.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("file-dir-same-name")
        }

        // The archive should have been partially extracted.
        assertTreeIs(
            mutableMapOf(
                "/file-dir-same-name.zip" to zipEntry(823),
                "/file-dir-same-name" to dirEntry,
                "/file-dir-same-name/pet" to dirEntry,
                "/file-dir-same-name/pet/cat" to dirEntry,
                "/file-dir-same-name/pet/cat/fish" to dirEntry,
            )
        )
    }

    /** Tests with a ZIP archive containing a corrupted file that can be detected via its CRC. */
    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO)
    fun badCrcChecked() {
        val uri = createDocument("application/zip", "archives/zip/bad-crc.zip")
        assertTreeIs(mutableMapOf("/bad-crc.zip" to zipEntry(234)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()

        // The file with a bad CRC should be detected.
        mJobListener.assertFailed()
        mJobListener.assertFailureCount(1)
        assertThat(job.failedPaths).containsExactly("/bad-crc.txt")

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            assertThat(msg).isEqualTo("Extracting “bad-crc.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("bad-crc")
        }

        // The partially extracted file with a bad CRC should have been removed.
        assertTreeIs(
            mutableMapOf(
                "/bad-crc.zip" to zipEntry(234),
                "/bad-crc" to dirEntry,
            )
        )
    }

    @Test
    @DisableFlags(FLAG_ZIP_NG_RO)
    fun badCrcUnchecked() {
        val uri = createDocument("application/zip", "archives/zip/bad-crc.zip")
        assertTreeIs(mutableMapOf("/bad-crc.zip" to zipEntry(234)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()

        // The file with a bad CRC should not be detected.
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Extracting “bad-crc.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(62)
            assertThat(requiredBytes).isEqualTo(62)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("bad-crc")
        }

        assertTreeIs(
            mutableMapOf(
                "/bad-crc.zip" to zipEntry(234),
                "/bad-crc" to dirEntry,
                "/bad-crc/bad-crc.txt" to textEntry(62),
            )
        )
    }

    /** Tests with a ZIP archive containing a corrupted repository with wrong file sizes. */
    @Test
    @EnableFlags(FLAG_USE_MATERIAL3, FLAG_ZIP_NG_RO)
    fun badSizesChecked() {
        val uri = createDocument("application/zip", "archives/zip/bad-sizes.zip")
        assertTreeIs(mutableMapOf("/bad-sizes.zip" to zipEntry(886)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()

        // The files with incorrect sizes should be detected.
        mJobListener.assertFailed()
        mJobListener.assertFailureCount(7)
        assertThat(job.failedPaths).containsExactly(
            "/0.txt",
            "/1.txt",
            "/2.txt",
            "/4.txt",
            "/5.txt",
            "/6.txt",
            "/7.txt"
        )

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isTrue()
            assertThat(msg).isEqualTo("Extracting “bad-sizes.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(3)
            assertThat(requiredBytes).isEqualTo(3)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("bad-sizes")
        }

        // Only the file with the correct size should have been extracted.
        assertTreeIs(
            mutableMapOf(
                "/bad-sizes.zip" to zipEntry(886),
                "/bad-sizes" to dirEntry,
                "/bad-sizes/d" to dirEntry,
                "/bad-sizes/3.txt" to textEntry(3),
            )
        )
    }

    @Test
    @DisableFlags(FLAG_ZIP_NG_RO)
    fun badSizesUnchecked() {
        val uri = createDocument("application/zip", "archives/zip/bad-sizes.zip")
        assertTreeIs(mutableMapOf("/bad-sizes.zip" to zipEntry(886)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()

        // The files with incorrect sizes should not be detected.
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Extracting “bad-sizes.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(28)
            assertThat(requiredBytes).isEqualTo(28)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("bad-sizes")
        }

        assertTreeIs(
            mutableMapOf(
                "/bad-sizes.zip" to zipEntry(886),
                "/bad-sizes" to dirEntry,
                "/bad-sizes/d" to dirEntry,
                "/bad-sizes/0.txt" to textEntry(0),
                "/bad-sizes/1.txt" to textEntry(1),
                "/bad-sizes/2.txt" to textEntry(2),
                "/bad-sizes/3.txt" to textEntry(3),
                "/bad-sizes/4.txt" to textEntry(4),
                "/bad-sizes/5.txt" to textEntry(5),
                "/bad-sizes/6.txt" to textEntry(6),
                "/bad-sizes/7.txt" to textEntry(7),
            )
        )
    }

    /** Tests with an empty ZIP archive. */
    @Test
    fun emptyZip() {
        val uri = createDocument("application/zip", "archives/zip/empty.zip")
        assertTreeIs(mutableMapOf("/empty.zip" to zipEntry(22)))

        val job = createJob(uri)

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_CREATED)
            assertThat(hasFailures).isFalse()
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
        }

        job.run()
        mJobListener.assertFinished()

        with(job.getJobProgress()) {
            assertThat(operationType).isEqualTo(OPERATION_UNPACK)
            assertThat(id).isEqualTo(job.id)
            assertThat(state).isEqualTo(Job.STATE_COMPLETED)
            assertThat(hasFailures).isFalse()
            assertThat(msg).isEqualTo("Extracting “empty.zip” to “TEST_ROOT_0”")
            assertThat(currentBytes).isEqualTo(0)
            assertThat(requiredBytes).isEqualTo(0)
            assertThat(msRemaining).isLessThan(0)
            assertThat(destination!!.peek().displayName).isEqualTo("empty")
        }

        with(job.getProgressNotification()) {
            assertThat(category).isEqualTo(CATEGORY_PROGRESS)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Extracting files")
                assertThat(getBoolean(EXTRA_PROGRESS_INDETERMINATE)).isTrue()
            }
        }

        assertTreeIs(
            mutableMapOf(
                "/empty.zip" to zipEntry(22),
                "/empty" to dirEntry,
            )
        )
    }

    /** Tests the various system notifications with a partially encrypted ZIP archive. */
    @Test
    fun notifications() {
        val uri = createDocument("application/zip", "archives/zip/different-encryptions.zip")
        assertTreeIs(mutableMapOf("/different-encryptions.zip" to zipEntry(1083)))

        val job = createJob(uri)

        with(job.getSetupNotification()) {
            assertThat(category).isEqualTo(CATEGORY_PROGRESS)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Extracting files")
                assertThat(getCharSequence(EXTRA_TEXT)).isEqualTo("Preparing...")
                assertThat(getBoolean(EXTRA_PROGRESS_INDETERMINATE)).isTrue()
            }
        }

        job.run()
        mJobListener.assertFailed()

        with(job.getProgressNotification()) {
            assertThat(category).isEqualTo(CATEGORY_PROGRESS)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Extracting files")
                assertThat(getBoolean(EXTRA_PROGRESS_INDETERMINATE)).isFalse()
            }
        }

        with(job.getFailureNotification()) {
            assertThat(category).isEqualTo(CATEGORY_ERROR)
            with(extras) {
                assertThat(getCharSequence(EXTRA_TITLE)).isEqualTo("Couldn’t extract 4 items")
                assertThat(getCharSequence(EXTRA_TEXT)).isEqualTo("Tap to view details")
            }
        }
    }

    private fun createJob(src: Uri): UnpackJob {
        val dest = buildDocumentUri(AUTHORITY, mSrcRoot.documentId)
        return createJob(OPERATION_UNPACK, listOf(src), dest, dest)
    }

    private fun createDocument(mimeType: String, assetPath: String): Uri {
        val uri = mDocs.createDocument(mSrcRoot, mimeType, File(assetPath).name)
        getInstrumentation().context.getAssets().open(assetPath).use {
            mDocs.writeDocument(uri, it)
        }
        return uri
    }

    private fun assertTreeIs(
        wantEntries: MutableMap<String, Entry>?,
        parentPath: String,
        info: DocumentInfo
    ) {
        val path = "$parentPath/${info.displayName}"

        if (wantEntries != null) {
            val wantEntry = wantEntries.remove(path)
            if (wantEntry == null) {
                throw AssertionError("Found unexpected file '$path'")
            }

            assertWithMessage("For file '%s'", path).that(info.mimeType)
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
        private const val TAG = "UnpackJobTest"
        private val dirEntry = Entry(-1, MIME_TYPE_DIR)
        private fun textEntry(size: Long) = Entry(size, "text/plain")
        private fun zipEntry(size: Long) = Entry(size, "application/zip")
    }
}
