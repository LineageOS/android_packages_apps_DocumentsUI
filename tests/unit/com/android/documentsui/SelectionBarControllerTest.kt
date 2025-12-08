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
package com.android.documentsui

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.truth.Truth.assertThat
import junit.framework.TestCase.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock

@RunWith(AndroidJUnit4::class)
@SmallTest
class SelectionBarControllerTest {
    private lateinit var appBar: MaterialToolbar
    private lateinit var selectionBar: MaterialToolbar
    private lateinit var selectionManager: DocsSelectionHelper
    private lateinit var selectionBarController: SelectionBarController

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.setTheme(getRes(R.style.DocumentsTheme))
        context.theme.applyStyle(getRes(R.style.DocumentsDefaultTheme), false)

        appBar = MaterialToolbar(context)
        selectionBar = MaterialToolbar(context)
        // By default toolbar is visible but selection bar is not.
        appBar.visibility = View.VISIBLE
        selectionBar.visibility = View.GONE
        val menuManager = mock(MenuManager::class.java)
        selectionManager = SelectionHelpers.createTestInstance()
        selectionBarController =
            SelectionBarController(appBar, selectionBar, menuManager, selectionManager)
    }

    @Test
    fun testOnSelectionChanged_showHideSelectionBar() {
        assertThat(selectionManager.selection).hasSize(0)

        // Let selection bar controller observe selection manager.
        selectionManager.addObserver(selectionBarController)

        // Select one file and it should notify selection bar controller.
        selectionManager.select("file1")
        assertThat(selectionManager.selection).hasSize(1)

        // Assert selection bar should show and app bar should hide.
        assertEquals(selectionBar.visibility, View.VISIBLE)
        assertEquals(appBar.visibility, View.GONE)

        // Remove all selections.
        selectionManager.clearSelection()
        assertThat(selectionManager.selection).hasSize(0)

        // Assert selection bar should hide and app bar should show.
        assertEquals(selectionBar.visibility, View.GONE)
        assertEquals(appBar.visibility, View.VISIBLE)
    }
}
