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
import androidx.test.filters.SmallTest
import com.android.documentsui.ContentLock
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.CheckAndForceMaterial3Flag
import com.android.documentsui.testing.TestFileTypeLookup
import com.android.documentsui.testing.TestProvidersAccess
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

private const val TOTAL_FILE_COUNT = 10

@RunWith(Parameterized::class)
@SmallTest
class FolderLoaderTest(private val testParams: LoaderTestParams) : BaseLoaderTest() {
    companion object {
        @JvmStatic
        @Parameters(name = "with parameters {0}")
        fun data() = listOf(
            LoaderTestParams(TOTAL_FILE_COUNT, "", null, ALL_RESULTS, Bundle(), TOTAL_FILE_COUNT),
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
    val checkFlags = CheckAndForceMaterial3Flag()

    val contentLock = ContentLock()
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
        val mockProvider = mEnv.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
        val docs = createDocuments(TOTAL_FILE_COUNT)
        mockProvider!!.setNextChildDocumentsReturns(*docs)
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    fun testLoadInBackground() {
        val userIds = listOf(TestProvidersAccess.DOWNLOADS.userId)
        // TODO(majewski): Is there a better way to create Downloads root folder DocumentInfo?
        val rootFolderInfo = DocumentInfo()
        rootFolderInfo.authority = TestProvidersAccess.DOWNLOADS.authority
        rootFolderInfo.userId = userIds[0]

        val loader =
            FolderLoader(
                mActivity,
                userIds,
                TestFileTypeLookup(),
                contentLock,
                TestProvidersAccess.DOWNLOADS,
                rootFolderInfo,
                queryOptions,
                mEnv.state.sortModel
            )
        val directoryResult = loader.loadInBackground()
        assertEquals(testParams.expectedCount, getFileCount(directoryResult))
    }

    @Test
    @RequiresFlagsEnabled(FLAG_USE_SEARCH_V2_READ_ONLY)
    fun testListRootIfNullFolder() {
        val mockProvider = mEnv.mockProviders[TestProvidersAccess.DOWNLOADS.authority]
        val docs = createDocuments(TOTAL_FILE_COUNT)
        mockProvider!!.setNextChildDocumentsReturns(*docs)

        val loader =
            FolderLoader(
                mActivity,
                listOf(TestProvidersAccess.DOWNLOADS.userId),
                TestFileTypeLookup(),
                contentLock,
                TestProvidersAccess.DOWNLOADS,
                null,
                queryOptions,
                mEnv.state.sortModel
            )
        val directoryResult = loader.loadInBackground()
        assertEquals(testParams.expectedCount, getFileCount(directoryResult))
    }
}
