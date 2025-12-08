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

package com.android.documentsui.loaders

import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.net.Uri
import android.provider.DocumentsContract
import com.android.documentsui.CrossProfileNoPermissionException
import com.android.documentsui.CrossProfileQuietModeException
import com.android.documentsui.DirectoryResult
import com.android.documentsui.MultiRootDocumentsLoader
import com.android.documentsui.base.Lookup
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.State
import com.android.documentsui.base.UserId
import com.android.documentsui.roots.ProvidersAccess
import com.android.documentsui.roots.RootCursorWrapper
import java.util.concurrent.Executor

/**
 * A loader that queries and merges documents from the trash across multiple providers for a
 * specific user.
 *
 * <p>This loader handles cross-profile permission and quiet mode checks before loading. It ensures
 * that each content provider authority is queried only once, even if multiple roots from the same
 * provider are present. It uses a custom cursor to modify the flags of trashed items, preventing
 * standard move and remove operations.
 */
class TrashFileLoader(
    context: Context?,
    providers: ProvidersAccess?,
    state: State?,
    executors: Lookup<String?, Executor?>?,
    fileTypeMap: Lookup<String?, String?>?,
    private val mUserId: UserId,
) : MultiRootDocumentsLoader(context, providers, state, executors, fileTypeMap) {
    private val mCalledAuthoritiesSet: MutableSet<String> = HashSet()

    /**
     * <p>Before loading, this method checks if interaction with the specified user is permitted and
     * if the user's profile is in quiet mode. If either of these conditions is not met, it returns
     * a {@link DirectoryResult} with the appropriate exception.
     *
     * @return A {@link DirectoryResult} containing the merged cursor of trashed documents or an
     *   exception if loading failed.
     */
    override fun loadInBackground(): DirectoryResult? {
        if (!mState.canInteractWith(mUserId)) {
            val result = DirectoryResult()
            result.exception = CrossProfileNoPermissionException()
            return result
        } else if (mUserId.isQuietModeEnabled(getContext())) {
            val result = DirectoryResult()
            result.exception = CrossProfileQuietModeException(mUserId)
            return result
        }
        return super.loadInBackground()
    }

    /**
     * Determines whether a root should be ignored to avoid redundant queries for trashed documents.
     *
     * <p>The trash content is fetched per-authority using {@link
     * android.provider.DocumentsContract#buildTrashDocumentsUri(String)}. If a single authority
     * provides multiple roots, querying each would result in the same set of trashed documents
     * being returned, leading to duplicate entries. This method prevents this by ensuring that each
     * authority is queried only once.
     *
     * @param root The {@link RootInfo} to check.
     * @return {@code true} if the root's authority is null or has already been queried, {@code
     *   false} otherwise.
     */
    public override fun shouldIgnoreRoot(root: RootInfo): Boolean {
        // A single provider may have multiple roots, but trashed documents are queried
        // at the authority level. We track called authorities to avoid duplicate queries.
        if (root.authority == null || mCalledAuthoritiesSet.contains(root.authority)) {
            return true
        }
        mCalledAuthoritiesSet.add(root.authority)
        return false
    }

    /**
     * Returns a {@link QueryTask} specifically for querying trashed documents.
     *
     * @param authority The authority of the content provider to query.
     * @param rootInfos The list of roots associated with the authority.
     * @return A new {@link TrashTask} instance.
     */
    override fun getQueryTask(authority: String, rootInfos: List<RootInfo>): QueryTask {
        return TrashTask(authority, rootInfos)
    }

    /** Returns {@code false} to ensure hidden files in the trash are displayed. */
    override fun shouldFilterHiddenFiles(): Boolean {
        return false
    }

    /**
     * Wraps the merged cursor to mask certain document flags.
     *
     * @param mergedCursor The final cursor containing documents from all providers.
     * @return A {@link NonRemoveAndMoveMaskCursor} that filters out flags related to moving and
     *   removing items.
     */
    override fun getMaskCursor(mergedCursor: Cursor): Cursor? {
        return NonRemoveAndMoveMaskCursor(mergedCursor)
    }

    /** A {@link QueryTask} that builds a URI for querying trashed documents. */
    private inner class TrashTask(authority: String?, rootInfos: List<RootInfo>?) :
        QueryTask(authority, rootInfos) {

        /** Returns the URI for querying trashed documents for the given authority. */
        override fun getQueryUri(rootInfo: RootInfo): Uri {
            return DocumentsContract.buildTrashDocumentsUri(authority)
        }

        override fun generateResultCursor(
            rootInfo: RootInfo,
            oriCursor: Cursor,
        ): RootCursorWrapper {
            return RootCursorWrapper(rootInfo.userId, authority, rootInfo.rootId, oriCursor, -1)
        }
    }

    /**
     * A {@link CursorWrapper} that masks the {@link DocumentsContract.Document#COLUMN_FLAGS} to
     * disable moving and removing items from the trash view.
     */
    class NonRemoveAndMoveMaskCursor(cursor: Cursor) : CursorWrapper(cursor) {

        /**
         * If the requested column is {@link DocumentsContract.Document#COLUMN_FLAGS}, this method
         * applies a bitmask to remove the {@link DocumentsContract.Document#FLAG_SUPPORTS_MOVE} and
         * {@link DocumentsContract.Document#FLAG_SUPPORTS_REMOVE} flags.
         */
        override fun getInt(index: Int): Int {
            val flagIndex = wrappedCursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)
            val value = super.getInt(index)
            return if (index == flagIndex) (value and NOT_MOVABLE_MASK) else value
        }

        companion object {
            private const val NOT_MOVABLE_MASK =
                (DocumentsContract.Document.FLAG_SUPPORTS_REMOVE or
                        DocumentsContract.Document.FLAG_SUPPORTS_MOVE)
                    .inv()
        }
    }
}
