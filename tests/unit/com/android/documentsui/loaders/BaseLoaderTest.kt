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
import android.os.Parcel
import android.provider.DocumentsContract
import com.android.documentsui.DirectoryResult
import com.android.documentsui.TestActivity
import com.android.documentsui.TestConfigStore
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.base.UserId
import com.android.documentsui.sorting.SortModel
import com.android.documentsui.testing.ActivityManagers
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.TestModel
import com.android.documentsui.testing.UserManagers
import java.time.Duration
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import org.junit.Before

/**
 * Returns the number of matched files, or -1.
 */
fun getFileCount(result: DirectoryResult?) = result?.cursor?.count ?: -1

/**
 * A data class that holds parameters that can be varied for the loader test. The last
 * value, expectedCount, can be used for simple tests that check that the number of
 * returned files matches the expectations.
 */
data class LoaderTestParams(
    // Number of fake files to populate the mock (queried) DocumentsProvider with.
    val fakeFileCount: Int,
    // A query, matched against the fake file names. May be empty.
    val query: String,
    // The delta from now that indicates maximum age of matched files.
    val lastModifiedDelta: Duration?,
    // The maximum number of files to ask each Root for (QueryOptions.ALL_RESULTS means no limit).
    val maxResultsPerRoot: Int,
    // The extra arguments typically supplied by SearchViewManager.
    val otherArgs: Bundle,
    // The number of files that are expected, for the above parameters, to be found by a loader.
    val expectedCount: Int,
) {
    override fun toString(): String {
        var base = "query '$query', maxResultsPerRoot: '$maxResultsPerRoot'"
        if (lastModifiedDelta != null) {
            base = "$base, modified in the last $lastModifiedDelta"
        }
        if (!otherArgs.isEmpty) {
            base = "$base, and $otherArgs"
        }
        return "$base, expecting $expectedCount matches"
    }
}

/**
 * Common base class for search and folder loaders.
 */
open class BaseLoaderTest {
    lateinit var mEnv: TestEnv
    lateinit var mActivity: TestActivity
    lateinit var mTestConfigStore: TestConfigStore

    @Before
    fun setUp() {
        mEnv = TestEnv.create()
        mTestConfigStore = TestConfigStore()
        mEnv.state.configStore = mTestConfigStore
        mEnv.state.showHiddenFiles = false
        val parcel = Parcel.obtain()
        mEnv.state.sortModel = SortModel.CREATOR.createFromParcel(parcel)

        mActivity = TestActivity.create(mEnv)
        mActivity.activityManager = ActivityManagers.create(false)
        mActivity.userManager = UserManagers.create()
    }

    /**
     * Creates a text, PNG, MP4 and MPG files named sample-000x, for x in 0 .. count - 1.
     * Each file gets a matching extension. The 0th file is modified 1h, 1st 2 hours, .. etc., ago.
     */
    fun createDocuments(count: Int): Array<DocumentInfo> {
        val extensionList = arrayOf("txt", "png", "mp4", "mpg")
        val now = System.currentTimeMillis()
        val flags = (DocumentsContract.Document.FLAG_SUPPORTS_WRITE
                or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                or DocumentsContract.Document.FLAG_SUPPORTS_RENAME)
        return Array<DocumentInfo>(count) { i ->
            val id = String.format(Locale.US, "%05d", i)
            val name = "sample-$id.${extensionList[i % extensionList.size]}"
            mEnv.model.createDocumentForUser(
                name,
                TestModel.guessMimeType(name),
                flags,
                now - 1.hours.inWholeMilliseconds * i,
                UserId.DEFAULT_USER
            )
        }
    }
}
