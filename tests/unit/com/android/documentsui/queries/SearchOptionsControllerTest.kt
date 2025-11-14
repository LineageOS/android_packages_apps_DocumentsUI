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

package com.android.documentsui.queries

import android.content.Context
import android.platform.test.annotations.RequiresFlagsEnabled
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.flags.Flags.FLAG_USE_SEARCH_V2_READ_ONLY
import com.android.documentsui.rules.CheckAndForceMaterial3Flag
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.spy

class TestSearchOptionsListener() : SearchOptionsListener {
    var optionsState: SearchOptionsState? = null

    override fun onOptionsChanged(options: SearchOptionsState) {
        optionsState = options
    }
}

@RequiresFlagsEnabled(FLAG_USE_SEARCH_V2_READ_ONLY)
@RunWith(AndroidJUnit4::class)
@SmallTest
class SearchOptionsControllerTest {
    @get:Rule
    val checkFlags = CheckAndForceMaterial3Flag()

    var context: Context? = null
    var controller: SearchOptionsController? = null
    var container: LinearLayout? = null
    val optionsListener = TestSearchOptionsListener()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        container = spy(LinearLayout(context))
        controller = SearchOptionsController(container)
        controller!!.setOptionChangeListener(optionsListener)
    }

    @Test
    fun testOptionsUpdateWorks() {
        for (e in SearchLocationOption.entries) {
            controller!!.onLocationSelected(e.value)
            controller!!.notifyOptionsChangeListener()
            assertEquals(optionsListener.optionsState!!.location, e)
        }
        for (e in LastModifiedOption.entries) {
            controller!!.onLastModifiedSelected(e.value)
            controller!!.notifyOptionsChangeListener()
            assertEquals(optionsListener.optionsState!!.lastModified, e)
        }
        for (e in FileTypeOption.entries) {
            controller!!.onFileTypeSelected(e.value)
            controller!!.notifyOptionsChangeListener()
            assertEquals(optionsListener.optionsState!!.fileType, e)
        }
    }

    @Test
    fun testGetOptionsQueryArgs() {
        // Reset the options to minimum filtering state.
        controller!!.onLocationSelected(SearchLocationOption.EVERYWHERE.ordinal)
        controller!!.onLastModifiedSelected(LastModifiedOption.ANY_TIME.ordinal)
        controller!!.onFileTypeSelected(FileTypeOption.ANY_TYPE.ordinal)

        val queryArgs = controller!!.getOptionsQueryArgs()
        // Expect no query args with the default (no limits) settings.
        assertEquals(queryArgs.size, 0)
    }
}
