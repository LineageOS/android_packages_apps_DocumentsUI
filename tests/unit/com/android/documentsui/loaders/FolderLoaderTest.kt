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

import android.content.Context
import android.os.Bundle
import android.platform.test.annotations.EnableFlags
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.test.filters.SmallTest
import com.android.documentsui.ContentLock
import com.android.documentsui.DirectoryResult
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.testing.TestDocumentsProvider
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import java.time.Duration
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import org.mockito.Mockito

private const val TOTAL_FILE_COUNT = 10

@RunWith(Enclosed::class)
class FolderLoaderTest() {

    @RunWith(Parameterized::class)
    @SmallTest
    class ParametrizedTests(private val testParams: LoaderTestParams) : BaseLoaderTest() {
        companion object {
            @JvmStatic
            @Parameters(name = "with parameters {0}")
            fun data() = listOf(
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "",
                    null,
                    ALL_RESULTS,
                    Bundle(),
                    TOTAL_FILE_COUNT
                ),
                // Result limiting only works for search, not folder navigation, expect limit to be ignored.
                LoaderTestParams(TOTAL_FILE_COUNT, "", null, 2, Bundle(), TOTAL_FILE_COUNT),
                // The first file is at NOW, the second at NOW - 1h, etc.
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "",
                    Duration.ofMinutes(1L),
                    ALL_RESULTS,
                    Bundle(),
                    1
                ),
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "",
                    Duration.ofMinutes(60L + 1),
                    ALL_RESULTS,
                    Bundle(),
                    2
                ),
                LoaderTestParams(
                    TOTAL_FILE_COUNT,
                    "",
                    Duration.ofMinutes(TOTAL_FILE_COUNT * 60L + 1),
                    ALL_RESULTS,
                    Bundle(),
                    TOTAL_FILE_COUNT
                ),
            )
        }

        @get:Rule
        val setFlags = OverrideFlagsRule()

        val contentLock = ContentLock()
        lateinit var mockProvider: TestDocumentsProvider
        lateinit var queryOptions: QueryOptions

        @Before
        fun setUpTest() {
            queryOptions =
                QueryOptions(
                    testParams.fakeFileCount + 1,
                    testParams.maxResultsPerRoot,
                    testParams.lastModifiedDelta,
                    null,
                    true,
                    arrayOf("*/*"),
                    testParams.otherArgs,
                )
            // Set up sample files using Downloads provider.
            mockProvider = environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]!!
            val docs = createDocuments(TOTAL_FILE_COUNT)
            mockProvider.setNextChildDocumentsReturns(*docs)
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testLoadInBackground() {
            // TODO(majewski): Is there a better way to create Downloads root folder DocumentInfo?
            val rootFolderInfo = DocumentInfo()
            rootFolderInfo.authority = TestProvidersAccess.DOWNLOADS.authority
            rootFolderInfo.userId = TestProvidersAccess.DOWNLOADS.userId

            val loader =
                FolderLoader(
                    activity,
                    TestFileTypeLookup(),
                    contentLock,
                    TestProvidersAccess.DOWNLOADS,
                    rootFolderInfo,
                    queryOptions,
                    environment.state.sortModel
                )
            val directoryResult = loader.loadInBackground()
            assertEquals(testParams.expectedCount, getFileCount(directoryResult))
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testListRootIfNullFolder() {
            val loader =
                FolderLoader(
                    activity,
                    TestFileTypeLookup(),
                    contentLock,
                    TestProvidersAccess.DOWNLOADS,
                    null,
                    queryOptions,
                    environment.state.sortModel
                )
            val directoryResult = loader.loadInBackground()
            assertEquals(testParams.expectedCount, getFileCount(directoryResult))
        }
    }

    @SmallTest
    class PlainTests : BaseLoaderTest() {
        @get:Rule
        val setFlags = OverrideFlagsRule()

        val contentLock = ContentLock()
        lateinit var mockProvider: TestDocumentsProvider
        lateinit var queryOptions: QueryOptions
        lateinit var context: Context

        @Before
        fun setUpTest() {
            queryOptions =
                QueryOptions(
                    TOTAL_FILE_COUNT,
                    TOTAL_FILE_COUNT,
                    null,
                    null,
                    true,
                    arrayOf("*/*"),
                    Bundle()
                )
            // Set up sample files using Downloads provider.
            mockProvider = environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]!!
            val docs = createDocuments(TOTAL_FILE_COUNT)
            mockProvider.setNextChildDocumentsReturns(*docs)
            context = Mockito.mock(Context::class.java)
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testThrownExceptions() {
            val message = "Testing exception throwing"
            mockProvider.setThrownRuntimeMessage(message)

            val loader =
                FolderLoader(
                    activity,
                    TestFileTypeLookup(),
                    contentLock,
                    TestProvidersAccess.DOWNLOADS,
                    null,
                    queryOptions,
                    environment.state.sortModel
                )
            val result = loader.loadInBackground()
            assertEquals(0, result?.cursor?.count)
            assertEquals(message, result?.exception?.message)
        }

        @Test
        @EnableFlags(FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3)
        fun testCancelWhileLoading() {
            val doc1 = environment.model.createFile("downloads-file.txt")
            val doc2 = environment.model.createFile("pickles-file-1.txt")
            val doc3 = environment.model.createFile("pickles-file-2.txt")
            var result: DirectoryResult? = null

            // The latch that is released once the downloads provider starts its query.
            val downloadsStartedLoading = CountDownLatch(1)

            environment.mockProviders[TestProvidersAccess.DOWNLOADS.authority]?.apply {
                // Setting the latch that will be unlocked once the Downloads provider start
                // loading.
                setQueryDelayLatch(downloadsStartedLoading)
                // Setting the delay that ensures that the downloads provider still is loading
                // while we trigger a new loader creation and thus cancellation of the old one.
                setQueryDelay(500L)
                setNextChildDocumentsReturns(doc1)
            }
            environment.mockProviders[TestProvidersAccess.PICKLES.authority]?.apply {
                setNextChildDocumentsReturns(doc2, doc3)
            }
            // This latch is released once the results are delivered to the loader.
            val resultsDelivered = CountDownLatch(1)

            val loaderCallbacks: LoaderManager.LoaderCallbacks<DirectoryResult> =
                object : LoaderManager.LoaderCallbacks<DirectoryResult> {
                    var runCount = 0

                    override fun onCreateLoader(id: Int, args: Bundle?): Loader<DirectoryResult?> {
                        val rootInfo =
                            if (++runCount == 1) {
                                TestProvidersAccess.DOWNLOADS
                            } else {
                                TestProvidersAccess.PICKLES
                            }
                        val rootFolderInfo = DocumentInfo()
                        rootFolderInfo.authority = rootInfo.authority
                        rootFolderInfo.userId = rootInfo.userId

                        return FolderLoader(
                            activity,
                            TestFileTypeLookup(),
                            contentLock,
                            rootInfo,
                            rootFolderInfo,
                            queryOptions,
                            environment.state.sortModel,
                        )
                    }

                    override fun onLoadFinished(
                        loader: Loader<DirectoryResult>,
                        data: DirectoryResult?,
                    ) {
                        result = data
                        resultsDelivered.countDown()
                    }

                    override fun onLoaderReset(loader: Loader<DirectoryResult>) {
                        loader.reset()
                    }
                }

            // Effectively calls `onCreateLoader` and then runs it by calling `startLoading()`.
            activity.supportLoaderManager.restartLoader(1, null, loaderCallbacks).startLoading()
            downloadsStartedLoading.await()
            // Effectively calls `onCreateLoader` the second time, causing the first loader to
            // be cancelled. Then runs the new loader by calling `startLoading()`.
            activity.supportLoaderManager.restartLoader(1, null, loaderCallbacks).startLoading()
            resultsDelivered.await()
            // We use the fact that pickles returns 2 returns to verify we only got pickles results.
            assertNotNull(result)
            assertEquals(2, getFileCount(result))
            val resultSet = getDocuments(result).map { it.displayName }.toSet()
            assertEquals(setOf(doc2.displayName, doc3.displayName), resultSet)
        }
    }
}
