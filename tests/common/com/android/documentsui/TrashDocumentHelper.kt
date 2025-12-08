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

package com.android.documentsui

import android.text.format.DateUtils
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.util.ArrayList
import java.util.Locale

/**
 * Provides support for document trash operations
 */
object TrashDocumentHelper {

    const val TRASH_LOCATION: String = ".trash-storage"

    private const val PREFIX_TRASHED: String = "trashed"

    private const val TAG: String = "TrashDocumentHelper"

    private const val DEFAULT_DURATION_TRASHED: Long = 30 * DateUtils.DAY_IN_MILLIS

    /**
     * Moves a file or directory to the trash.
     *
     * <p>The item is moved into a designated trash folder within the root and renamed to
     * include an expiration timestamp.
     *
     * @param originalFile The file or directory to trash.
     * @param rootDir The root directory where the trash folder is located.
     * @return A list with the trash directory and the newly trashed file.
     * @throws FileNotFoundException If creating the trash directory or moving the file fails.
     */
    @Throws(FileNotFoundException::class)
    fun moveToTrash(originalFile: File, rootDir: File): List<File> {
        val trashStorageDir = File(rootDir, TRASH_LOCATION)
        if (!trashStorageDir.exists()) {
            if (!trashStorageDir.mkdirs()) {
                Log.e(
                    TAG,
                    "Failed to create trash storage directory: " + trashStorageDir.path
                )
                throw FileNotFoundException("Failed to create trash storage directory.")
            }
        }

        val dateExpires =
            (System.currentTimeMillis() + DEFAULT_DURATION_TRASHED) / 1000

        val destinationFile = prepareDestinationFile(
            originalFile,
            trashStorageDir,
            rootDir,
            dateExpires
        )

        if (!originalFile.renameTo(destinationFile)) {
            Log.e(
                TAG,
                "Failed to move file to trash: " + originalFile.path + " to " +
                        destinationFile.path
            )
            throw FileNotFoundException("Failed to trash document: $originalFile")
        }

        if (destinationFile.isDirectory) {
            prefixChildrenOnDisk(destinationFile, dateExpires)
        }

        val files: MutableList<File> = ArrayList()
        files.add(trashStorageDir)
        files.add(destinationFile)
        return files
    }

    /**
     * Prepares the full destination path for the file within the trash directory.
     *
     * @param originalFile       The file to be trashed.
     * @param trashBaseDirectory The base trash directory.
     * @param rootDir The root directory where the trash folder is located.
     * @param dateExpires The `dateExpires` timestamp (in seconds) of the item
     * @return The File object representing the final destination of the trashed item.
     * @throws IllegalStateException if the destination parent directory cannot be created.
     */
    @Throws(IllegalStateException::class)
    private fun prepareDestinationFile(
        originalFile: File,
        trashBaseDirectory: File,
        rootDir: File,
        dateExpires: Long
    ): File {
        val updatedDisplayName = getTrashFileName(dateExpires, originalFile.name)

        var relPath = originalFile.absolutePath.substring(rootDir.path.length)
        if (relPath.startsWith(File.separator)) {
            relPath = relPath.substring(1)
        }

        var destParent = trashBaseDirectory

        if (relPath.isNotEmpty()) {
            val parentFile = File(relPath).parentFile
            if (parentFile != null) {
                destParent = File(trashBaseDirectory, parentFile.path)
            }
        }

        if (!destParent.mkdirs()) {
            check(destParent.exists()) {
                "Failed to create trash sub-directory: " +
                        destParent.absolutePath
            }
        }

        return File(destParent, updatedDisplayName)
    }

    /**
     * Recursively prefixes the names of all children (files and directories)
     * within a given directory on disk, ensuring they reflect the trashed state.
     * This method is intended to be called on a directory that has just been
     * moved to trash, and its children need consistent naming.
     *
     * @param parentDir                The directory whose children need to be prefixed.
     * @param parentTrashedDateExpires The `dateExpires` timestamp (in seconds) of the parent
     * trashed directory. Children will inherit this expiration.
     */
    private fun prefixChildrenOnDisk(parentDir: File, parentTrashedDateExpires: Long) {
        val children = parentDir.listFiles() ?: return

        for (child in children) {
            val newChildName = getTrashFileName(parentTrashedDateExpires, child.name)

            val renamedChildFile = File(parentDir, newChildName)

            if (!child.renameTo(renamedChildFile)) {
                Log.w(
                    TAG,
                    "Failed to rename child: " + child.absolutePath + " to " +
                            renamedChildFile.absolutePath
                )
            }

            // If the renamedChildFile item is a directory, recurse into it
            if (renamedChildFile.isDirectory) {
                prefixChildrenOnDisk(renamedChildFile, parentTrashedDateExpires)
            }
        }
    }

    /**
     * Generates a filename for a trashed item.
     *
     * @param dateExpires The expiration timestamp of the item.
     * @param name The original name of the item.
     * @return The formatted filename to be used for the trashed item.
     */
    private fun getTrashFileName(dateExpires: Long, name: String): String {
        return String.format(
            Locale.US,
            ".%s-%d-%s",
            PREFIX_TRASHED,
            dateExpires,
            name
        )
    }
}
