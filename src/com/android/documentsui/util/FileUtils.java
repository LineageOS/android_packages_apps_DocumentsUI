/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.documentsui.util;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.annotation.NonNull;

import com.android.documentsui.base.DocumentInfo;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

public class FileUtils {

    /**
     * Returns the canonical pathname string of the provided abstract pathname.
     *
     * @return The canonical pathname string denoting the same file or directory as this abstract
     *         pathname.
     * @see File#getCanonicalPath()
     */
    @NonNull
    public static String getCanonicalPath(@NonNull String path) throws IOException {
        Objects.requireNonNull(path);
        return new File(path).getCanonicalPath();
    }

    /**
     * This is basically a very slightly tweaked fork of
     * {@link com.android.externalstorage.ExternalStorageProvider#getPathFromDocId(String)}.
     * The difference between this fork and the "original" method is that here we do not strip
     * the leading and trailing "/"s (because we don't worry about those).
     *
     * @return canonicalized file path.
     */
    public static String getPathFromStorageDocId(String docId) throws IOException {
        // Remove the root tag from the docId, e.g. "primary:", which should leave with the file
        // path.
        final String docIdPath = docId.substring(docId.indexOf(':', 1) + 1);

        return getCanonicalPath(docIdPath);
    }

    /**
     * Returns the number of opening apps for the given file type.
     *
     * @param doc File MIME type.
     * @param pm PackageManager (usually Activity.getPackageManager())
     * @return Number of opening apps.
     */
    public static int countOpeningApps(DocumentInfo doc, PackageManager pm) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(doc.getDocumentUri(), doc.mimeType);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);
        return resolveInfos.size();
    }

    private FileUtils() {
    }
}
