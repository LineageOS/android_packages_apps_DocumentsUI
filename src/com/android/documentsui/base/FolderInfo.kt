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
package com.android.documentsui.base

/**
 * Represents core information about folder that is to be searched or listed. The reason this
 * class exists is so that we can represent root or nested folders for search. The existing
 * classes, RootInfo and DocumentInfo do not share common ancestor, and thus cannot be used
 * interchangeably. Additionally the "folderId" is rootId for the RootInfo, but documentId for
 * DocumentInfo.
 */
data class FolderInfo(
    val folderId: String,
    val authority: String,
    // Specifies whether the Root this folder exists on supports search result limiting: this can
    // help to find more search results (increasing the limit) or improve performance (decreasing
    // the limit). We could just send the limit regardless of whether the root says it supports this
    // but this allows SearchLoader to be judicious and not starting sending new parameters that
    // it hasn't done in the past.
    //
    // TODO(b:419704219) remove this property when RootInfo/DocumentInfo have a shared interface.
    val supportsSearchResultLimiting: Boolean
) {
    constructor(rootInfo: RootInfo) : this(
        rootInfo.rootId,
        rootInfo.authority,
        rootInfo.supportsSearchResultLimit()
    )
    constructor(folder: DocumentInfo, folderRoot: RootInfo) : this(
        folder.documentId,
        folder.authority,
        folderRoot.supportsSearchResultLimit()
    )
}
