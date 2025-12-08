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

import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Helper object for restoring files and directories from the trash.
 *
 * This object provides functionality to move trashed items back to their original or a specified
 * location.
 */
object RestoreDocumentHelper {

    const val TRASH_LOCATION: String = ".trash-storage"

    private const val PREFIX_TRASHED: String = "trashed"

    private const val TAG: String = "RestoreDocumentHelper"

    private val PATTERN_EXPIRES_FILE = Pattern.compile("(?i)^\\.(pending|trashed)-(\\d+)-([^/]+)$")

    /**
     * Restores a file or directory from the trash storage to a target location.
     *
     * The trashed file is expected to be within the `.trash-storage` directory and its name must
     * follow the `.trashed-TIMESTAMP-originalName` pattern.
     *
     * @param rootDir The root directory of the storage where `.trash-storage` is located.
     * @param trashedFilePath The absolute path to the trashed file or directory within
     *   `.trash-storage`.
     * @param targetParentPath An optional absolute path to the parent directory where the item
     *   should be restored. If `null`, the original parent directory is inferred from the
     *   `trashedFilePath`.
     * @return The absolute path of the restored file or directory.
     * @throws IllegalArgumentException if the `trashedFilePath` is not within `.trash-storage` or
     *   if the file name does not match the expected trashed pattern.
     * @throws FileNotFoundException if the `trashedFilePath` does not exist.
     * @throws IllegalStateException if parent directories for restoration cannot be created or if
     *   the file renaming fails.
     */
    @Throws(
        IllegalArgumentException::class,
        FileNotFoundException::class,
        IllegalStateException::class,
    )
    fun restoreFileFromTrash(
        rootDir: File,
        trashedFilePath: String,
        targetParentPath: String?,
    ): String? {
        val trashedFile = File(trashedFilePath)
        val trashedFileName = trashedFile.name
        var prefixToUnprefix: String
        val originalFileName: String?

        val trashStorageRoot = File(rootDir, TrashDocumentHelper.TRASH_LOCATION).absolutePath

        // trashed file should be descendant of .trash-storage location
        if (!trashedFile.absolutePath.startsWith(trashStorageRoot)) {
            Log.w(TAG, "trashed file not a descendant of .trash-storage")
            throw IllegalArgumentException("Not allowed to restore file")
        }

        // Extract the prefix and original name using the defined pattern
        val matcher: Matcher = PATTERN_EXPIRES_FILE.matcher(trashedFileName)
        if (matcher.matches() && matcher.group(1).equals(PREFIX_TRASHED)) {
            // Group 1 is "trashed"
            // Group 2 is the timestamp
            // Group 3 is the original file name
            prefixToUnprefix =
                trashedFileName.substring(0, matcher.start(3)) // Get the ".trashed-TIMESTAMP-" part
            originalFileName = matcher.group(3) // Get the original file name part
            Log.d(
                TAG,
                ("Extracted prefix '" +
                    prefixToUnprefix +
                    "', original name '" +
                    originalFileName +
                    "'"),
            )
        } else {
            throw IllegalArgumentException(
                "File name does not indicate a trashed item: $trashedFileName"
            )
        }

        val targetPath: String? = targetParentPath ?: getTargetPath(rootDir, trashedFile)

        val validTargetPath: String? = getValidTargetPath(targetPath)
        Log.d(TAG, "Restoring: $trashedFilePath to $validTargetPath")

        val targetParent = File(validTargetPath)

        if (!targetParent.exists()) {
            if (!targetParent.mkdirs()) {
                if (!targetParent.exists()) {
                    Log.e(
                        TAG,
                        "Failed to create parent directory for restore: " +
                            targetParent.absolutePath,
                    )
                    throw IllegalStateException(
                        "Failed to create parent directory for restoration."
                    )
                }
            }
        }

        val originalLocation = File(targetParent, originalFileName)

        if (originalLocation.exists()) {
            throw IllegalStateException("Failed to restore: $originalLocation already exists")
        }

        if (!trashedFile.renameTo(originalLocation)) {
            Log.e(
                TAG,
                ("Failed to rename during restore: " +
                    trashedFilePath +
                    " -> " +
                    originalLocation.absolutePath),
            )
            throw IllegalStateException("Failed to restore: Could not move file.")
        }

        // If the restored item is a directory and we successfully extracted a prefix,
        // unprefix its children recursively.
        if (originalLocation.isDirectory && !prefixToUnprefix.isEmpty()) {
            unprefixChildrenOnDisk(originalLocation, prefixToUnprefix)
        }

        // Clean up empty parent directories in trash location
        deleteAllParentIfNonTrashed(rootDir, trashedFile)

        return originalLocation.absolutePath
    }

    /**
     * Determines the default target path for restoration based on the trashed file's location
     * within the `.trash-storage` directory. This method take account the trash structure as the
     * original file system relative to the external storage root.
     *
     * @param rootDir The root directory where the trash folder is located.
     * @param file The trashed file for which to determine the default restore path.
     * @return The absolute path to the default restoration target directory.
     */
    private fun getTargetPath(rootDir: File, file: File): String {
        val volumeRootPath: String = rootDir.path
        val trashStorageRoot = File(rootDir, TrashDocumentHelper.TRASH_LOCATION).absolutePath
        val pathInTrash = file.absolutePath

        var relativePathInTrash = pathInTrash.substring(trashStorageRoot.length)

        // Remove the filename itself to get the parent path
        val lastSeparator = relativePathInTrash.lastIndexOf(File.separator)
        if (lastSeparator != -1) {
            relativePathInTrash = relativePathInTrash.substring(0, lastSeparator)
        } else {
            relativePathInTrash = "" // If it's a file directly under .trash-storage
        }

        val defaultRestoreParent = File(volumeRootPath, relativePathInTrash)

        return defaultRestoreParent.absolutePath
    }

    /**
     * Cleans a segment by removing trash prefixes.
     *
     * @param segment The path segment to clean.
     * @return The cleaned segment, or the original if no matching prefix was found.
     */
    private fun cleanSegment(segment: String?): String? {
        if (segment == null || segment.isEmpty()) {
            return segment
        }

        val matcher: Matcher = PATTERN_EXPIRES_FILE.matcher(segment)
        if (matcher.matches() && matcher.group(1) == PREFIX_TRASHED) {
            return matcher.group(3) // Return the original name part
        }

        return segment
    }

    /**
     * Cleans a full path by removing trash prefixes from all segments.
     *
     * @param targetPath The path string to clean.
     * @return The cleaned path string.
     */
    private fun getValidTargetPath(targetPath: String?): String? {
        if (targetPath == null || targetPath.isEmpty()) {
            return targetPath
        }

        val segments: Array<String?> =
            targetPath.split(File.separator.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        val cleanedSegments: MutableList<String?> = ArrayList<String?>()
        for (segment in segments) {
            cleanedSegments.add(cleanSegment(segment))
        }

        // Reconstruct path, handling leading/trailing slashes if present
        var cleanedPath = java.lang.String.join(File.separator, cleanedSegments)
        if (targetPath.startsWith(File.separator) && !cleanedPath.startsWith(File.separator)) {
            cleanedPath = File.separator + cleanedPath
        }
        if (targetPath.endsWith(File.separator) && !cleanedPath.endsWith(File.separator)) {
            cleanedPath = cleanedPath + File.separator
        }

        return cleanedPath
    }

    /**
     * Recursively unprefixes the names of all children (files and directories) within a given
     * directory on disk, effectively restoring their original names.
     *
     * @param dir The directory whose children need to be unprefixed.
     * @param originalPrefix The exact prefix to remove (e.g., ".trashed-TIMESTAMP-").
     */
    private fun unprefixChildrenOnDisk(dir: File, originalPrefix: String) {
        val children = dir.listFiles()
        if (children == null) {
            return
        }

        for (child in children) {
            val childName = child.name
            if (childName.startsWith(originalPrefix)) {
                val newName = childName.substring(originalPrefix.length)
                val renamed = File(child.parent, newName)

                if (child.renameTo(renamed)) {
                    if (renamed.isDirectory) {
                        // Recurse for subdirectories
                        unprefixChildrenOnDisk(renamed, originalPrefix)
                    }
                } else {
                    Log.w(TAG, "Failed to unprefix child: " + child.absolutePath)
                }
            }
        }
    }

    /**
     * Recursively deletes empty parent directories in the trash location after a file is restored.
     * It stops when it encounters the `.trash-storage` root or a directory that is not empty or
     * does not follow the trashed naming pattern.
     *
     * @param rootDir The root directory where the trash folder is located.
     * @param trashedFile The file that was just restored. Its parent directories will be checked.
     */
    private fun deleteAllParentIfNonTrashed(rootDir: File, trashedFile: File?) {
        if (trashedFile == null) {
            return
        }

        var parent = trashedFile.parentFile
        val trashBase = File(rootDir, TRASH_LOCATION)
        while (parent != null && parent != trashBase) { // Stop at .trash-storage root
            // If the parent is not an empty directory.
            if (!parent.isDirectory || parent.list().size != 0) {
                break
            }

            // Check if the directory name matches the trash pattern. If it does,
            // it's part of the trash structure and shouldn't be deleted.
            val matcher: Matcher = PATTERN_EXPIRES_FILE.matcher(parent.name)
            if (matcher.matches() && matcher.group(1) == PREFIX_TRASHED) {
                break
            }

            // At this point, we know the directory is empty and not a trashed folder.
            // This implies it was created to hold a trashed file and can now be deleted.
            val nextParent = parent.parentFile
            if (!parent.delete()) {
                Log.w(TAG, "Failed to delete empty trash parent: " + parent.absolutePath)
            }
            parent = nextParent // Continue to the next parent
        }
    }
}
