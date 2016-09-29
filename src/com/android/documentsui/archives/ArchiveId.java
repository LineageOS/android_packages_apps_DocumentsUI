/*
 * Copyright (C) 2016 The Android Open Source Project
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

import android.net.Uri;

class ArchiveId {
    private final static char DELIMITER = '#';

    public final Uri mArchiveUri;
    public final String mPath;

    public ArchiveId(Uri archiveUri, String path) {
        mArchiveUri = archiveUri;
        mPath = path;
        assert(!mPath.isEmpty());
    }

    static public ArchiveId fromDocumentId(String documentId) {
        final int delimiterPosition = documentId.indexOf(DELIMITER);
        assert(delimiterPosition != -1);
        return new ArchiveId(Uri.parse(documentId.substring(0, delimiterPosition)),
                documentId.substring((delimiterPosition + 1)));
    }

    public String toDocumentId() {
        return mArchiveUri.toString() + DELIMITER + mPath;
    }
};
