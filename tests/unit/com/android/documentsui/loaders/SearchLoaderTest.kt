/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.os.Bundle
import android.platform.test.annotations.RequiresFlagsEnabled
import android.provider.DocumentsContract
import androidx.test.filters.SmallTest
import com.android.documentsui.ContentLock
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.Model
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.FolderInfo
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.CheckAndForceMaterial3Flag
import com.android.documentsui.sorting.SortModel
import com.android.documentsui.testing.TestFeatures
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import com.google.common.truth.Expect
import java.time.Duration
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private const val TOTAL_FILE_COUNT = 8

fun createQueryArgs(vararg mimeTypes: String): Bundle {
    val args = Bundle()
    args.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES, arrayOf<String>(*mimeTypes))
    return args
}

@RunWith(Enclosed::class)
@SmallTest
class SearchLoaderTest {

    // Collection of tests that are parametrized by query, duration, and MIME type.
    @RunWith(Parameterized::class)
    class ParametrizedTests(private val testParams: LoaderTestParams) : BaseLoaderTest() {
        lateinit var mExecutor: ExecutorService
        val mContentLock = ContentLock()
        val mContentObserver = LockingContentObserver(mContentLock) {}

        companion object {
            @JvmStatic
            @Parameters(name = "with parameters {0}")
            fun data() = listOf(
                // Specifying ALL_RESULTS as the limit should return as many results as the root allows,
                // for TestDocumentsProvider this is 23 (to match FileSystemProvider's legacy default),
                // which our TOTAL_FILE_COUNT is well below (so expect all files to be returned).
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "sample",
                    null,
                    ALL_RESULTS,
                    Bundle(),
                    TOTAL_FILE_COUNT
                ),
                // Specifying a positive, non-zero search result limit should work.
                LoaderTestParams(TOTAL_FILE_COUNT, "sample", null, 2, Bundle(), 2),
                // Specifying a zero search results limit should return zero files.
                LoaderTestParams(TOTAL_FILE_COUNT, "sample", null, 0, Bundle(), 0),
                LoaderTestParams(TOTAL_FILE_COUNT, "txt", null, ALL_RESULTS, Bundle(), 2),
                LoaderTestParams(TOTAL_FILE_COUNT, "foozig", null, ALL_RESULTS, Bundle(), 0),
                // The first file is at NOW, the second at NOW - 1h; expect 2.
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "sample",
                    Duration.ofMinutes(60 + 1),
                    ALL_RESULTS,
                    Bundle(),
                    2
                ),
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "sample",
                    null,
                    ALL_RESULTS,
                    createQueryArgs("image/*"),
                    2
                ),
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "sample",
                    null,
                    ALL_RESULTS,
                    createQueryArgs("image/*", "video/*"),
                    6
                ),
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "sample",
                    null,
                    ALL_RESULTS,
                    createQueryArgs("application/pdf"),
                    0
                ),
            )
        }

        @get:Rule
        val checkFlags = CheckAndForceMaterial3Flag()

        @get:Rule
        val expect: Expect = Expect.create()

        @Before
        fun setUpTest() {
            mExecutor = Executors.newSingleThreadExecutor()
        }

        @Test
        @RequiresFlagsEnabled(FLAG_USE_SEARCH_V2_READ_ONLY)
        fun testLoadInBackground() {
            val mockProvider = mEnv.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
            val docs = createDocuments(testParams.fakeFileCount)
            mockProvider!!.setNextChildDocumentsReturns(*docs)
            val userIds = listOf(TestProvidersAccess.DOWNLOADS.userId)
            val queryOptions = QueryOptions(
                testParams.fakeFileCount + 1,
                testParams.maxResultsPerRoot,
                testParams.lastModifiedDelta,
                null,
                true,
                arrayOf("*/*"),
                testParams.otherArgs,
            )

            val folderInfo = listOf(
                FolderInfo(
                    TestProvidersAccess.DOWNLOADS.rootId,
                    TestProvidersAccess.DOWNLOADS.authority,
                    TestProvidersAccess.DOWNLOADS.supportsSearchResultLimit()
                )
            )

            val loader = SearchLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                mContentObserver,
                folderInfo,
                testParams.query,
                queryOptions,
                mEnv.state.sortModel,
                mExecutor,
            )
            val directoryResult = loader.loadInBackground()
            expect.that(getFileCount(directoryResult)).isEqualTo(testParams.expectedCount)
        }
    }

    // Collection of plain tests that do not use parameters.
    class PlainTests : BaseLoaderTest() {
        @get:Rule
        val checkFlags = CheckAndForceMaterial3Flag()

        @get:Rule
        val expect: Expect = Expect.create()

        lateinit var mExecutor: ExecutorService
        val mContentLock = ContentLock()
        val mContentObserver = LockingContentObserver(mContentLock) {}

        @Before
        fun setUpTest() {
            mExecutor = Executors.newSingleThreadExecutor()
        }

        fun generateDocuments(
            count: Int,
            suffixOffset: Int,
            extensions: Array<String>
        ): Array<DocumentInfo> {
            return Array(count) { i ->
                val suffix = String.format(Locale.US, "%05d", 2 * i + suffixOffset)
                val ext = extensions[i % extensions.size]
                mEnv.model.createFile("document-$suffix.$ext")
            }
        }

        /**
         * Checks that the merging, filtering and sorting of results works correctly. We set up
         * two providers: home and pickles storage. They get files with names that have a zipper
         * like pattern, when sorted. Here we are checking if merging two cursors, with filtering
         * produces the expected result.
         */
        @Test
        fun testValidateMergeFilterSort() {
            val fileCount = 200
            val maxCount = fileCount / 2
            mEnv.mockProviders.apply {
                // Pickles documents have IDs 0, 2, 4, .., 398. Half of the documents are images,
                // the other half are documents (PDFs).
                get(TestProvidersAccess.PICKLES.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(fileCount, 0, arrayOf("png", "pdf"))
                )
                // Home documents have IDs 1, 3, 5, ... 399. Half of the documents are images,
                // the other half are videos.
                get(TestProvidersAccess.HOME.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(fileCount, 1, arrayOf("png", "avi"))
                )
            }

            // Setup the sort model so that results are sorted by their name.
            val sortModel = SortModel.createModel()
            sortModel.setDefaultDimension(SortModel.SORT_DIMENSION_ID_TITLE)
            val folderInfo = listOf(
                FolderInfo(
                    TestProvidersAccess.PICKLES.rootId,
                    TestProvidersAccess.PICKLES.authority,
                    TestProvidersAccess.DOWNLOADS.supportsSearchResultLimit()
                ),
                FolderInfo(
                    TestProvidersAccess.HOME.rootId,
                    TestProvidersAccess.HOME.authority,
                    TestProvidersAccess.DOWNLOADS.supportsSearchResultLimit()
                ),
            )
            val loader = SearchLoader(
                mActivity,
                listOf(TestProvidersAccess.PICKLES.userId, TestProvidersAccess.HOME.userId),
                TestFileTypeLookup(),
                mContentObserver,
                folderInfo,
                "document-",
                QueryOptions(
                    maxCount,
                    ALL_RESULTS,
                    null,
                    null,
                    false,
                    arrayOf("image/png"),
                    Bundle()
                ),
                sortModel,
                mExecutor,
            )
            val result = loader.loadInBackground()
            expect.that(result?.cursor?.getCount()).isEqualTo(maxCount)
            // We expect a perfect mix of documents from PICKLES and HOME. Pickles has odd indices
            // Home has even indices. However, the filtering cursor should take out all non-images
            // leaving us with 0, 4, 8, ..., from PICKLES and 1, 5, 9, ... from HOME.
            val model = Model(TestFeatures())
            model.update(result)
            val names = model.modelIds.map {
                model.getDocument(it)!!.displayName
            }
            expect.that(names).isEqualTo((0..maxCount - 1).map {
                val index = String.format(Locale.US, "%05d", 4 * (it / 2) + (it % 2))
                "document-$index.png"
            })
        }

        @Test
        fun testExtraArgs() {
            mEnv.mockProviders.apply {
                get(TestProvidersAccess.PICKLES.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(2, 1, arrayOf("png", "avi"))
                )
            }
            val folderInfo = listOf(
                FolderInfo(
                    TestProvidersAccess.PICKLES.rootId,
                    TestProvidersAccess.PICKLES.authority,
                    TestProvidersAccess.PICKLES.supportsSearchResultLimit(),
                ),
            )
            val loader = SearchLoader(
                mActivity,
                listOf(TestProvidersAccess.PICKLES.userId, TestProvidersAccess.HOME.userId),
                TestFileTypeLookup(),
                mContentObserver,
                folderInfo,
                "document",
                QueryOptions(10, ALL_RESULTS, null, null, false, arrayOf("image/png"), Bundle()),
                mEnv.state.sortModel,
                mExecutor,
            )
            val result = loader.loadInBackground()
            expect.that(result!!.cursor).isNotNull()
            val extras = result.cursor.extras
            expect.that(extras).isNotNull()
            expect.that(extras.containsKey(DocumentsContract.EXTRA_LOADING)).isTrue()
            // TODO(417818526): Add ability to force mock providers to be extra slow, so that
            // we can test for the case when they do not finish on time.
            expect.that(extras.getBoolean(DocumentsContract.EXTRA_LOADING)).isFalse()
        }
    }
}
