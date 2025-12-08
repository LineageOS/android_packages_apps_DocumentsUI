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
import android.platform.test.annotations.EnableFlags
import android.provider.DocumentsContract
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.test.filters.SmallTest
import com.android.documentsui.ContentLock
import com.android.documentsui.DirectoryResult
import com.android.documentsui.LockingContentObserver
import com.android.documentsui.Model
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.sorting.SortModel
import com.android.documentsui.testing.TestFeatures
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import com.google.common.truth.Expect
import java.time.Duration
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime
import org.junit.After
import org.junit.Assert.assertEquals
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
    args.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES, arrayOf(*mimeTypes))
    return args
}

@RunWith(Enclosed::class)
class SearchLoaderTest {

    // Collection of tests that are parametrized by query, duration, and MIME type.
    @RunWith(Parameterized::class)
    @SmallTest
    class ParametrizedTests(private val testParams: LoaderTestParams) : BaseLoaderTest() {
        lateinit var executor: ExecutorService
        val contentLock = ContentLock()
        val contentObserver = LockingContentObserver(contentLock) {}

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
        val setFlags = OverrideFlagsRule()

        @get:Rule
        val expect: Expect = Expect.create()

        @Before
        fun setUpTest() {
            executor = Executors.newSingleThreadExecutor()
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testLoadInBackground() {
            val mockProvider = environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
            val docs = createDocuments(testParams.fakeFileCount)
            mockProvider!!.setNextChildDocumentsReturns(*docs)
            val queryOptions = QueryOptions(
                testParams.fakeFileCount + 1,
                testParams.maxResultsPerRoot,
                testParams.lastModifiedDelta,
                null,
                true,
                arrayOf("*/*"),
                testParams.otherArgs,
            )

            val rootInfoList = listOf(TestProvidersAccess.DOWNLOADS)

            val loader = SearchLoader(
                activity,
                rootInfoList,
                TestFileTypeLookup(),
                contentObserver,
                testParams.query,
                queryOptions,
                environment.state.sortModel,
                executor,
            )
            val directoryResult = loader.loadInBackground()
            expect.that(getFileCount(directoryResult)).isEqualTo(testParams.expectedCount)
        }
    }

    // Collection of plain tests that do not use parameters.
    @SmallTest
    class PlainTests : BaseLoaderTest() {
        @get:Rule
        val setFlags = OverrideFlagsRule()

        @get:Rule
        val expect: Expect = Expect.create()

        lateinit var executor: ExecutorService
        val contentLock = ContentLock()
        val contentObserver = LockingContentObserver(contentLock) {}

        @Before
        fun setUpTest() {
            executor = Executors.newSingleThreadExecutor()
        }

        @After
        fun tearDownTest() {
            for (provider in environment.mockProviders) {
                provider.value.setQueryDelay(0)
            }
        }

        fun generateDocuments(
            count: Int,
            suffixOffset: Int,
            extensions: Array<String>
        ): Array<DocumentInfo> {
            return Array(count) { i ->
                val suffix = String.format(Locale.US, "%05d", 2 * i + suffixOffset)
                val ext = extensions[i % extensions.size]
                environment.model.createFile("document-$suffix.$ext")
            }
        }

        /**
         * Checks that the merging, filtering and sorting of results works correctly. We set up
         * two providers: home and pickles storage. They get files with names that have a zipper
         * like pattern, when sorted. Here we are checking if merging two cursors, with filtering
         * produces the expected result.
         */
        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testValidateMergeFilterSort() {
            val fileCount = 200
            val maxCount = fileCount / 2
            environment.mockProviders.apply {
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
            val loader = SearchLoader(
                activity,
                listOf(TestProvidersAccess.PICKLES, TestProvidersAccess.HOME),
                TestFileTypeLookup(),
                contentObserver,
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
                executor,
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
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testExtraArgs() {
            environment.mockProviders.apply {
                get(TestProvidersAccess.PICKLES.authority)!!.setNextChildDocumentsReturns(
                    *generateDocuments(2, 1, arrayOf("png", "avi"))
                )
            }
            val loader = SearchLoader(
                activity,
                listOf(TestProvidersAccess.PICKLES, TestProvidersAccess.HOME),
                TestFileTypeLookup(),
                contentObserver,
                "document",
                QueryOptions(10, ALL_RESULTS, null, null, false, arrayOf("image/png"), Bundle()),
                environment.state.sortModel,
                executor,
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

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testShowOrHideHiddenFiles() {
            val commonSearchString = "verdant"
            val doc1 = environment.model.createFile(".test$commonSearchString")
            val doc2 = environment.model.createFile("test$commonSearchString")
            doc1.documentId = ".test"
            doc2.documentId = "parent_folder/.hidden_folder/test"
            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                setNextChildDocumentsReturns(
                    doc1,
                    doc2
                )
            }

            val hideHiddenLoader = SearchLoader(
                activity,
                listOf(TestProvidersAccess.DOWNLOADS),
                TestFileTypeLookup(),
                contentObserver,
                commonSearchString,
                QueryOptions(10, ALL_RESULTS, null, null, false, null, Bundle()),
                environment.state.sortModel,
                executor,
            )

            var result: DirectoryResult = hideHiddenLoader.loadInBackground()!!
            assertEquals(0, result.cursor.getCount())

            val showHiddenLoader = SearchLoader(
                activity,
                listOf(TestProvidersAccess.DOWNLOADS),
                TestFileTypeLookup(),
                contentObserver,
                commonSearchString,
                QueryOptions(10, ALL_RESULTS, null, null, true, null, Bundle()),
                environment.state.sortModel,
                executor,
            )
            result = showHiddenLoader.loadInBackground()!!
            assertEquals(2, result.cursor.getCount())
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testCompletesInPresenceOfExceptions() {
            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                setThrownRuntimeMessage("Testing exception throwing")
            }

            val loader =
                SearchLoader(
                    activity,
                    listOf(TestProvidersAccess.DOWNLOADS),
                    TestFileTypeLookup(),
                    contentObserver,
                    "query",
                    QueryOptions(10, ALL_RESULTS, null, null, true, null, Bundle()),
                    environment.state.sortModel,
                    executor,
                )
            val queryDuration = measureTime {
                val result = loader.loadInBackground()
                val cursor = result?.cursor
                expect.that(cursor).isNotNull()
                expect.that(cursor!!.count).isEqualTo(0)
                // Expect that no cursor is still loading.
                expect.that(cursor.extras?.getBoolean(DocumentsContract.EXTRA_LOADING)).isFalse()
            }
            // The no results should be due to the task terminating immediately, not because
            // it timed out. We give it 100 milliseconds.
            expect.that(queryDuration).isLessThan(100.milliseconds)
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testDeliversFastAndSlowResults() {
            val commonSearchString = UUID.randomUUID().toString()
            val doc1 = environment.model.createFile("downloads$commonSearchString")
            val doc2 = environment.model.createFile("pickles$commonSearchString")
            val doc3 = environment.model.createFile("home$commonSearchString")
            // The barrier awaits for 2 callers. One from the test thread and one from
            // the thread that runs the loader.
            val barrier = CyclicBarrier(2)
            var result: DirectoryResult? = null
            val firstPassWaitMs = 500L
            val passDeltaMs = 200L
            // bufferMs is to allow some processing time between the time the results are
            // released by a document provider vs the time they make it to onLoadFinished method.
            val bufferMs = 100L

            // Wait times for the above firstPassWaitMs and passDeltaMs are going to be:
            //  DOWNLOADS: 300ms
            //  PICKLES:   700ms
            //  HOME:      900ms
            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                setQueryDelay(firstPassWaitMs - passDeltaMs)
                setNextChildDocumentsReturns(doc1)
            }
            environment.mockProviders[TestProvidersAccess.PICKLES.authority]?.apply {
                setQueryDelay(firstPassWaitMs + passDeltaMs)
                setNextChildDocumentsReturns(doc2)
            }
            environment.mockProviders[TestProvidersAccess.HOME.authority]?.apply {
                setQueryDelay(firstPassWaitMs + 2 * passDeltaMs)
                setNextChildDocumentsReturns(doc3)
            }

            val loaderCallbacks: LoaderManager.LoaderCallbacks<DirectoryResult> =
                object : LoaderManager.LoaderCallbacks<DirectoryResult> {

                    override fun onCreateLoader(
                        id: Int,
                        args: Bundle?
                    ): Loader<DirectoryResult?> {
                        return SearchLoader(
                            activity,
                            listOf(
                                TestProvidersAccess.DOWNLOADS,
                                TestProvidersAccess.PICKLES,
                                TestProvidersAccess.HOME,
                            ),
                            TestFileTypeLookup(),
                            contentObserver,
                            commonSearchString,
                            QueryOptions(
                                10,
                                ALL_RESULTS,
                                null,
                                Duration.ofMillis(firstPassWaitMs),
                                false,
                                null,
                                Bundle()
                            ),
                            environment.state.sortModel,
                            Executors.newFixedThreadPool(3)
                        )
                    }

                    override fun onLoadFinished(
                        loader: Loader<DirectoryResult>,
                        data: DirectoryResult?
                    ) {
                        result = data
                        barrier.await()
                    }

                    override fun onLoaderReset(loader: Loader<DirectoryResult>) {
                        loader.reset()
                    }
                }

            activity.supportLoaderManager.restartLoader(1, null, loaderCallbacks).startLoading()
            // Wait for the Downloads result.
            barrier.await()
            expect.that(getFileCount(result)).isEqualTo(1)

            // Now wait for the PICKLES result.
            barrier.await()
            // Expect that both the old and the new results are returned.
            expect.that(getFileCount(result)).isEqualTo(2)

            barrier.await()
            expect.that(getFileCount(result)).isEqualTo(3)
        }
    }
}
