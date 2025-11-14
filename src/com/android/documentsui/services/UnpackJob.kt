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

import android.app.Notification
import android.content.Context
import android.icu.text.MessageFormat
import android.net.Uri
import android.os.FileUtils.copy
import android.os.OperationCanceledException
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.DocumentsContract
import android.text.BidiFormatter
import android.text.TextUtils
import android.util.Log
import com.android.documentsui.DocumentsApplication
import com.android.documentsui.R
import com.android.documentsui.archives.ReadableArchive
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.DocumentStack
import com.android.documentsui.base.Features
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.base.SharedMinimal.VERBOSE
import com.android.documentsui.base.SharedMinimal.redact
import com.android.documentsui.clipping.UrisSupplier
import com.android.documentsui.util.FormatUtils
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.common.io.Files
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.NumberFormat
import java.util.LinkedList
import java.util.Locale
import org.apache.commons.compress.archivers.ArchiveEntry

/**
 * UnpackJob extracts all the files from a supported archive. It only works on one archive at a
 * time. Given an archive to unpack, it:
 * - Opens the archive
 * - Lists all the entries of the archive
 * - Estimates the number of directories, files and bytes to extract
 * - Checks the free space in the destination directory
 * - Creates a unique extraction directory in the destination directory
 * - Creates all the intermediate directories in the extraction directory
 * - Extracts all the files from the archive
 * - Tracks and reports its own progress
 * - Records file extraction errors
 * - Deletes any partially extracted file in case of error
 * - Handles cancellation gracefully
 */
class UnpackJob(
    service: Context,
    listener: Listener,
    id: String,
    destination: DocumentStack,
    srcs: UrisSupplier,
    features: Features
) : ResolvedResourcesJob(
    service,
    listener,
    id,
    FileOperationService.OPERATION_UNPACK,
    destination,
    srcs,
    features
) {
    private val dirPathToUri: MutableMap<String, Uri> = mutableMapOf()
    private val tracker = ProgressTracker()
    private val dstInfo: DocumentInfo = destination.peek()
    private var archive: ReadableArchive? = null

    override fun createProgressBuilder(): Notification.Builder {
        return super.createProgressBuilder(
            service.getString(getRes(R.string.extract_notification_title)),
            getRes(R.drawable.ic_menu_extract),
            service.getString(android.R.string.cancel),
            getRes(R.drawable.ic_cab_cancel)
        )
    }

    override fun getSetupNotification(): Notification {
        return getSetupNotification(service.getString(getRes(R.string.extract_preparing)))
    }

    /** This method is called on a different thread than the thread running the extraction. */
    override fun getProgressNotification(): Notification {
        val absoluteProgress: Double
        val absoluteTarget: Double
        val remainingTime: Long

        synchronized(this) {
            absoluteProgress = tracker.absoluteProgress
            absoluteTarget = tracker.absoluteTarget
            remainingTime = tracker.getRemainingTimeEstimate(absoluteProgress, absoluteTarget)
            if (DEBUG) Log.d(TAG, "$tracker, ${remainingTime / 1000} seconds left")
        }

        if (absoluteTarget > 0) {
            val relativeProgress = absoluteProgress / absoluteTarget
            mProgressBuilder.setProgress(100, (relativeProgress * 100).toInt(), false)
            mProgressBuilder.setSubText(NumberFormat.getPercentInstance().format(relativeProgress))
        } else {
            mProgressBuilder.setProgress(100, 0, true)
            mProgressBuilder.setSubText(null)
        }

        mProgressBuilder.setContentText(
            if (remainingTime > 0) {
                service.getString(
                    getRes(R.string.copy_remaining),
                    FormatUtils.formatDuration(remainingTime)
                )
            } else {
                null
            }
        )

        return mProgressBuilder.build()
    }

    override fun finish() {
        if (DEBUG) Log.d(TAG, "Finished with $tracker")
        archive?.close()
        super.finish()
    }

    override fun getFailureNotification(): Notification {
        return getFailureNotification(
            getRes(R.plurals.copy_error_notification_title),
            getRes(R.drawable.ic_menu_extract)
        )
    }

    /** This method is called on a different thread than the thread running the extraction. */
    @Synchronized
    public override fun getJobProgress(): JobProgress {
        val args: MutableMap<String, Any> = mutableMapOf(
            "directory" to BidiFormatter.getInstance().unicodeWrap(dstInfo.displayName),
            "count" to mResolvedDocs.size,
        )

        if (mResolvedDocs.size == 1) {
            args.put(
                "filename",
                BidiFormatter.getInstance().unicodeWrap(archiveInfo.displayName)
            )
        }

        val message = MessageFormat(
            service.getString(getRes(R.string.extract_in_progress)),
            Locale.getDefault()
        ).format(args)

        return JobProgress(
            id,
            operationType,
            state,
            message,
            hasFailures(),
            stack,
            tracker.bytesCopied,
            tracker.bytesRequired,
            tracker.remainingTimeEstimate
        )
    }

    private val resolver = appContext.getContentResolver()

    private val archiveInfo: DocumentInfo
        get() {
            assert(mResolvedDocs.size == 1)
            return mResolvedDocs.first()
        }

    override fun setUp(): Boolean {
        synchronized(this) {
            if (!super.setUp()) return false
        }

        if (DEBUG) Log.d(TAG, "Unpacking ${archiveInfo.derivedUri}")

        try {
            openArchive()
            checkFreeSpace()
            createExtractionDirectory()
            return true
        } catch (_: OperationCanceledException) {
            if (DEBUG) Log.d(TAG, "Canceled unpacking of ${archiveInfo.derivedUri}")
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot unpack ${redact(archiveInfo.derivedUri)}", t)
            onFileFailed(archiveInfo)
        }

        return false
    }

    private fun createExtractionDirectory() {
        mSignal.throwIfCanceled()

        // Create the extraction directory with the same base name as the archive.
        // This lets the destination document provider deal with name collisions, if necessary.
        val dirName = Files.getNameWithoutExtension(archiveInfo.displayName)
        val dirUri = DocumentsContract.createDocument(
            resolver,
            dstInfo.derivedUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            dirName
        )

        if (dirUri == null) {
            throw IOException(
                "Cannot create extraction dir ${redact(dirName)} in ${redact(dstInfo.derivedUri)}"
            )
        }

        dirPathToUri.put("/", dirUri)
        val dirInfo = DocumentInfo.fromUri(resolver, dirUri, dstInfo.userId)
        if (VERBOSE) Log.v(TAG, "Created extraction dir ${redact(dirInfo)}")
        stack.push(dirInfo)
    }

    private fun openArchive() {
        mSignal.throwIfCanceled()

        // Open the archive.
        archive = ReadableArchive.createForParcelFileDescriptor(
            appContext,
            resolver.openFileDescriptor(archiveInfo.derivedUri, "r", null),
            archiveInfo.derivedUri,
            archiveInfo.mimeType,
            ParcelFileDescriptor.MODE_READ_ONLY,
            null
        )

        mSignal.throwIfCanceled()

        // Count all the directories to create, all the files to extract, and all the bytes
        // to copy.
        val dirs = mutableSetOf("/")
        for (entry in archive!!.entries) {
            var path = File(ReadableArchive.getEntryPath(entry))

            if (!entry.isDirectory()) {
                // The entry represents a file. Get the path of its containing directory.
                path = path.getParentFile()!!
                synchronized(this) {
                    tracker.bytesRequired += entry.getSize()
                    tracker.filesRequired++
                }
            }

            while (dirs.add(path.toString())) {
                path = path.getParentFile()!!
                synchronized(this) {
                    tracker.dirsRequired++
                }
            }
        }
    }

    override fun start() {
        synchronized(this) {
            tracker.addPoint()
        }

        try {
            // Create all the directories first, to ensure that no directory will be renamed
            // because of a collision with a file name.
            createAllDirectories()
            extractAllFiles()
        } catch (_: OperationCanceledException) {
            if (DEBUG) Log.d(TAG, "Canceled unpacking of ${redact(archiveInfo.derivedUri)}")
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot unpack ${redact(archiveInfo.derivedUri)}", t)
            onFileFailed(archiveInfo)
        }
    }

    private fun extractAllFiles() {
        for (entry in archive!!.entries) {
            mSignal.throwIfCanceled()
            if (!entry.isDirectory()) processFileEntry(entry)
        }
    }

    private fun createAllDirectories() {
        for (entry in archive!!.entries) {
            mSignal.throwIfCanceled()
            var path = File(ReadableArchive.getEntryPath(entry))
            if (!entry.isDirectory()) path = path.getParentFile()!!
            ensureDirectoryExists(path)
        }
    }

    private fun processFileEntry(entry: ArchiveEntry) {
        val path = File(ReadableArchive.getEntryPath(entry))
        val dirUri = ensureDirectoryExists(path.getParentFile()!!)
        val fileName = path.getName()

        try {
            extractFile(entry, dirUri, fileName)
        } catch (e: OperationCanceledException) {
            // Propagate cancellation request.
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot extract ${redact(path)} from ${redact(archiveInfo.derivedUri)}", t)
            synchronized(this) {
                tracker.filesRequired--
            }
            onPathFailed(path.toString())
        }

        // Adjust progress expectations after extracting a file.
        synchronized(this) {
            tracker.bytesRequired -= entry.getSize() - tracker.bytesCopiedInCurrentFile
            tracker.bytesCopiedInPreviousFiles += tracker.bytesCopiedInCurrentFile
            tracker.bytesCopiedInCurrentFile = 0
            tracker.addPoint()
        }
    }

    private fun extractFile(entry: ArchiveEntry, dirUri: Uri, fileName: String) {
        // Open input stream serving the archive entry's contents.
        archive!!.getInputStream(entry).use { inputStream ->
            mSignal.throwIfCanceled()
            // Create output file.
            val fileUri = DocumentsContract.createDocument(resolver, dirUri, "", fileName)!!
            if (VERBOSE) Log.v(TAG, "Created file $fileUri")
            try {
                copyFile(inputStream, fileUri)
            } catch (t: Throwable) {
                // Error or cancellation while copying a file.
                deletePartialFile(fileUri)
                throw t
            }
        }
    }

    @Synchronized
    private fun trackBytesInCurrentFile(bytes: Long) {
        tracker.bytesCopiedInCurrentFile = bytes
        tracker.addPoint()
    }

    private fun copyFile(inputStream: InputStream, outputFile: Uri) {
        val client = getClient(outputFile)
        // Open output file for writing.
        try {
            client.openFile(outputFile, "w", mSignal).use { fd ->
                ParcelFileDescriptor.AutoCloseOutputStream(fd).use { outputStream ->
                    // Copy bytes.
                    val bytes = copy(
                        inputStream,
                        outputStream,
                        mSignal,
                        Runnable::run,
                        ::trackBytesInCurrentFile
                    )
                    trackBytesInCurrentFile(bytes)
                }
            }
        } finally {
            releaseClient(outputFile)
        }

        synchronized(this) {
            tracker.filesCopied++
            tracker.addPoint()
        }
    }

    private fun deletePartialFile(uri: Uri) {
        try {
            if (DocumentsContract.deleteDocument(resolver, uri)) {
                if (DEBUG) Log.d(TAG, "Deleted partial file ${redact(uri)}")
            } else {
                Log.e(TAG, "Cannot delete partial file ${redact(uri)}")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot delete partial file ${redact(uri)}", t)
        }
    }

    @Throws(IOException::class)
    private fun ensureDirectoryExists(path: File): Uri {
        val uri = dirPathToUri[path.toString()]
        if (uri != null) return uri

        val parentUri = ensureDirectoryExists(path.getParentFile()!!)
        val name = path.getName()
        if (TextUtils.isEmpty(name)) return parentUri

        mSignal.throwIfCanceled()
        val newDirUri = DocumentsContract.createDocument(
            resolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name
        )

        if (newDirUri == null) {
            throw IOException("Cannot create dir ${redact(name)} in ${redact(parentUri)}")
        }

        if (VERBOSE) Log.v(TAG, "Created dir ${redact(newDirUri)}")
        dirPathToUri.put(path.toString(), newDirUri)

        synchronized(this) {
            tracker.dirsCreated++
            tracker.addPoint()
        }

        return newDirUri
    }

    /** Checks whether the destination directory has enough free space.  */
    private fun checkFreeSpace() {
        mSignal.throwIfCanceled()
        val bytesRequired = tracker.bytesRequired
        if (DEBUG) Log.d(TAG, "Need at least $bytesRequired bytes of free space")

        var root = stack.root
        if (root == null) {
            Log.w(TAG, "No root info for destination dir ${redact(dstInfo.derivedUri)}")
            return
        }

        // Query root info again instead of using stack.root because the numbers may be stale.
        root = DocumentsApplication.getProvidersCache(appContext).getRootOneshot(
            root.userId, root.authority, root.rootId, true
        )

        if (root == null || root.availableBytes < 0) {
            Log.w(TAG, "Root $root does not provide its free space amount")
            return
        }

        if (DEBUG) Log.d(TAG, "Root $root has ${root.availableBytes} bytes of free space")

        if (bytesRequired > root.availableBytes) {
            throw IOException(
                "Not enough free space in ${redact(dstInfo.derivedUri)}: " +
                        "Need $bytesRequired bytes, " +
                        "but only got ${root.availableBytes} bytes of free space"
            )
        }
    }

    override fun toString(): String {
        return "UnpackJob {id=$id, uris=$mResourceUris, docs=$mResolvedDocs, dest=$dstInfo}"
    }

    private class ProgressTracker : Job.ProgressTracker {
        var bytesRequired: Long = 0
        var bytesCopiedInPreviousFiles: Long = 0
        var bytesCopiedInCurrentFile: Long = 0
        var filesRequired = 0
        var filesCopied = 0
        var dirsRequired = 0
        var dirsCreated = 0

        /** A progress sample. */
        private data class Point(val time: Long, val progress: Double)

        /** Circular queue of the most recent progress samples. */
        val points = LinkedList<Point>()

        /** Records a progress sample if the latest one was recorded more than a second ago. */
        fun addPoint() {
            val now = SystemClock.uptimeMillis()
            val last = points.peekLast()
            if (last != null && now - last.time < 1000) return
            points.addLast(Point(now, absoluteProgress))
            // Only keep a handful of the most recent samples.
            while (points.size > 20) points.removeFirst()
        }

        val bytesCopied: Long
            get() = bytesCopiedInPreviousFiles + bytesCopiedInCurrentFile

        val absoluteProgress: Double
            get() = getLinear(dirsCreated, filesCopied, bytesCopied)

        val absoluteTarget: Double
            get() = getLinear(dirsRequired, filesRequired, bytesRequired)

        override fun getProgress(): Double {
            return absoluteProgress / absoluteTarget
        }

        override fun getRemainingTimeEstimate(): Long {
            return getRemainingTimeEstimate(absoluteProgress, absoluteTarget)
        }

        fun getRemainingTimeEstimate(progress: Double, target: Double): Long {
            val first = points.peekFirst()
            if (first == null) return -1
            val now = SystemClock.uptimeMillis()
            val elapsedTime = now - first.time
            val t: Double = elapsedTime * (target - progress) / (progress - first.progress)
            return if (t > 0) t.toLong() else -1
        }

        override fun toString(): String {
            return "Progress %.0f%%, %,d/%,d dirs, %,d/%,d files, %,d/%,d bytes".format(
                progress * 100,
                dirsCreated,
                dirsRequired,
                filesCopied,
                filesRequired,
                bytesCopied,
                bytesRequired
            )
        }

        companion object {
            /**
             * Gets a linear approximation of the time taken to create the given number of
             * directories, create the given number of files and write the given number of bytes.
             * The unit of measure of the returned value is the time taken to write one byte.
             *
             * The constants used in this formula have been empirically determined to
             * approximate a smooth progress tracking with a test device. The time taken to
             * create an empty file and open it for writing matches the time taken to transfer
             * 2.9e6 bytes. The time taken to create a directory matches the time taken to
             * transfer 2.0e7 bytes.
             */
            private fun getLinear(dirs: Int, files: Int, bytes: Long): Double {
                return bytes.toDouble() + 2.9e6 * files + 2.0e7 * dirs
            }
        }
    }

    companion object {
        private const val TAG = "UnpackJob"
    }
}
