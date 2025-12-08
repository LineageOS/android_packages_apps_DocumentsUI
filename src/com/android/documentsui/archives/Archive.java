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

package com.android.documentsui.archives;

import static com.android.documentsui.base.SharedMinimal.DEBUG;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.commons.compress.archivers.ArchiveEntry;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Provides basic implementation for creating, extracting and accessing
 * files within archives exposed by a document provider.
 *
 * <p>This class is thread safe.
 */
public abstract class Archive implements Closeable {
    private static final String TAG = "Archive";

    public static final String[] DEFAULT_PROJECTION = new String[]{
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE,
            Document.COLUMN_FLAGS
    };

    final Context mContext;
    final Uri mArchiveUri;
    final int mAccessMode;
    final Uri mNotificationUri;

    // The container as well as values are guarded by mEntries.
    @GuardedBy("mEntries")
    final Map<String, ArchiveEntry> mEntries;

    // The container as well as values and elements of values are guarded by mEntries.
    @GuardedBy("mEntries")
    final Map<String, List<ArchiveEntry>> mTree;

    Archive(
            Context context,
            Uri archiveUri,
            int accessMode,
            @Nullable Uri notificationUri) {
        mContext = context;
        mArchiveUri = archiveUri;
        mAccessMode = accessMode;
        mNotificationUri = notificationUri;

        mTree = new HashMap<>();
        mEntries = new HashMap<>();
    }

    /** Returns a valid, normalized path for an entry. */
    public static String getEntryPath(ArchiveEntry entry) {
        final List<String> parts = new ArrayList<String>();
        boolean isDir = true;

        // Get the path that will be decomposed and normalized
        final String in = entry.getName();

        decompose:
        for (int i = 0; i < in.length(); ) {
            // Skip separators
            if (in.charAt(i) == '/') {
                isDir = true;
                do {
                    if (++i == in.length()) break decompose;
                } while (in.charAt(i) == '/');
            }

            // Found the beginning of a part
            final int b = i;
            assert (b < in.length());
            assert (in.charAt(b) != '/');

            // Find the end of the part
            do {
                ++i;
            } while (i < in.length() && in.charAt(i) != '/');

            // Extract part
            final String part = in.substring(b, i);
            assert (!part.isEmpty());

            // Special case if part is "."
            if (part.equals(".")) {
                isDir = true;
                continue;
            }

            // Special case if part is ".."
            if (part.equals("..")) {
                isDir = true;
                if (!parts.isEmpty()) parts.remove(parts.size() - 1);
                continue;
            }

            // The part is either a directory or a file name
            isDir = false;
            parts.add(part);
        }

        // If the decomposed path looks like a directory but the archive entry says that it is not
        // a directory entry, append "?" for the file name
        if (isDir && !entry.isDirectory()) {
            isDir = false;
            parts.add("?");
        }

        if (parts.isEmpty()) return "/";

        // Construct the normalized path
        final StringBuilder sb = new StringBuilder(in.length() + 3);

        for (final String part : parts) {
            sb.append('/');
            sb.append(part);
        }

        if (entry.isDirectory()) {
            sb.append('/');
        }

        final String out = sb.toString();
        if (DEBUG) Log.d(TAG, "getEntryPath(" + in + ") -> " + out);
        return out;
    }

    /**
     * Returns true if the file descriptor is seekable.
     *
     * @param descriptor File descriptor to check.
     */
    public static boolean canSeek(ParcelFileDescriptor descriptor) {
        try {
            return Os.lseek(descriptor.getFileDescriptor(), 0,
                    OsConstants.SEEK_CUR) == 0;
        } catch (ErrnoException e) {
            return false;
        }
    }

    /**
     * Lists child documents of an archive or a directory within an
     * archive. Must be called only for archives with supported mime type,
     * or for documents within archives.
     *
     * @see DocumentsProvider.queryChildDocuments(String, String[], String)
     */
    public Cursor queryChildDocuments(@NonNull String documentId, @Nullable String[] projection,
            @Nullable String sortOrder) throws FileNotFoundException {
        final ArchiveId parsedParentId = ArchiveId.fromDocumentId(documentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedParentId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");

        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_PROJECTION);
        if (mNotificationUri != null) {
            result.setNotificationUri(mContext.getContentResolver(), mNotificationUri);
        }

        synchronized (mEntries) {
            final List<ArchiveEntry> parentList = mTree.get(parsedParentId.mPath);
            if (parentList == null) {
                throw new FileNotFoundException();
            }
            for (final ArchiveEntry entry : parentList) {
                addCursorRow(result, entry);
            }
        }
        return result;
    }

    /**
     * Returns a MIME type of a document within an archive.
     *
     * @see DocumentsProvider.getDocumentType(String)
     */
    public String getDocumentType(@NonNull String documentId) throws FileNotFoundException {
        final ArchiveId parsedId = ArchiveId.fromDocumentId(documentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");

        synchronized (mEntries) {
            final ArchiveEntry entry = mEntries.get(parsedId.mPath);
            if (entry == null) {
                throw new FileNotFoundException();
            }
            return getMimeTypeForEntry(entry);
        }
    }

    /**
     * Returns true if a document within an archive is a child or any descendant of the archive
     * document or another document within the archive.
     *
     * @see DocumentsProvider.isChildDocument(String, String)
     */
    public boolean isChildDocument(@NonNull String parentDocumentId, @NonNull String documentId) {
        final ArchiveId parsedParentId = ArchiveId.fromDocumentId(parentDocumentId);
        final ArchiveId parsedId = ArchiveId.fromDocumentId(documentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedParentId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");

        synchronized (mEntries) {
            final ArchiveEntry entry = mEntries.get(parsedId.mPath);
            if (entry == null) {
                return false;
            }

            final ArchiveEntry parentEntry = mEntries.get(parsedParentId.mPath);
            if (parentEntry == null || !parentEntry.isDirectory()) {
                return false;
            }

            // Add a trailing slash even if it's not a directory, so it's easy to check if the
            // entry is a descendant.
            String pathWithSlash = entry.isDirectory() ? getEntryPath(entry)
                    : getEntryPath(entry) + "/";

            return pathWithSlash.startsWith(parsedParentId.mPath) &&
                    !parsedParentId.mPath.equals(pathWithSlash);
        }
    }

    /**
     * Returns metadata of a document within an archive.
     *
     * @see DocumentsProvider.queryDocument(String, String[])
     */
    public Cursor queryDocument(@NonNull String documentId, @Nullable String[] projection)
            throws FileNotFoundException {
        final ArchiveId parsedId = ArchiveId.fromDocumentId(documentId);
        MorePreconditions.checkArgumentEquals(mArchiveUri, parsedId.mArchiveUri,
                "Mismatching archive Uri. Expected: %s, actual: %s.");

        synchronized (mEntries) {
            final ArchiveEntry entry = mEntries.get(parsedId.mPath);
            if (entry == null) {
                throw new FileNotFoundException();
            }

            final MatrixCursor result = new MatrixCursor(
                    projection != null ? projection : DEFAULT_PROJECTION);
            if (mNotificationUri != null) {
                result.setNotificationUri(mContext.getContentResolver(), mNotificationUri);
            }
            addCursorRow(result, entry);
            return result;
        }
    }

    /**
     * Creates a file within an archive.
     *
     * @see DocumentsProvider.createDocument(String, String, String))
     */
    public String createDocument(String parentDocumentId, String mimeType, String displayName)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Creating documents not supported.");
    }

    /**
     * Opens a file within an archive.
     *
     * @see DocumentsProvider.openDocument(String, String, CancellationSignal))
     */
    public ParcelFileDescriptor openDocument(
            String documentId, String mode, @Nullable final CancellationSignal signal)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Opening not supported.");
    }

    /**
     * Opens a thumbnail of a file within an archive.
     *
     * @see DocumentsProvider.openDocumentThumbnail(String, Point, CancellationSignal))
     */
    public AssetFileDescriptor openDocumentThumbnail(
            String documentId, Point sizeHint, final CancellationSignal signal)
            throws FileNotFoundException {
        throw new UnsupportedOperationException("Thumbnails not supported.");
    }

    /**
     * Creates an archive id for the passed path.
     */
    public ArchiveId createArchiveId(@NonNull String path) {
        return new ArchiveId(mArchiveUri, mAccessMode, path);
    }

    /**
     * Not thread safe.
     */
    void addCursorRow(MatrixCursor cursor, ArchiveEntry entry) {
        final MatrixCursor.RowBuilder row = cursor.newRow();
        final ArchiveId parsedId = createArchiveId(getEntryPath(entry));
        row.add(Document.COLUMN_DOCUMENT_ID, parsedId.toDocumentId());

        final File file = new File(entry.getName());
        row.add(Document.COLUMN_DISPLAY_NAME, file.getName());
        row.add(Document.COLUMN_SIZE, entry.getSize());

        final String mimeType = getMimeTypeForEntry(entry);
        row.add(Document.COLUMN_MIME_TYPE, mimeType);

        int flags = mimeType.startsWith("image/") ? Document.FLAG_SUPPORTS_THUMBNAIL : 0;
        if (MetadataReader.isSupportedMimeType(mimeType)) {
            flags |= Document.FLAG_SUPPORTS_METADATA;
        }
        row.add(Document.COLUMN_FLAGS, flags);
    }

    public static @NonNull String getMimeTypeForEntry(@NonNull ArchiveEntry entry) {
        if (entry.isDirectory()) {
            return Document.MIME_TYPE_DIR;
        }

        final int lastDot = entry.getName().lastIndexOf('.');
        if (lastDot >= 0) {
            final String extension = entry.getName().substring(lastDot + 1).toLowerCase(Locale.US);
            final String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }

        return "application/octet-stream";
    }

    // TODO: Upstream to the Preconditions class.
    // TODO: Move to a separate file.
    public static class MorePreconditions {
        static void checkArgumentEquals(String expected, @Nullable String actual,
                String message) {
            if (!TextUtils.equals(expected, actual)) {
                throw new IllegalArgumentException(String.format(message,
                        String.valueOf(expected), String.valueOf(actual)));
            }
        }

        static void checkArgumentEquals(Uri expected, @Nullable Uri actual,
                String message) {
            checkArgumentEquals(expected.toString(), actual.toString(), message);
        }
    }
}
