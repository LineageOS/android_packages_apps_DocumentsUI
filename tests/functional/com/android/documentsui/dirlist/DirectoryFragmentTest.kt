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
package com.android.documentsui.dirlist

import android.app.ActivityManager
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.MatrixCursor
import android.os.Bundle
import android.os.Looper
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.SelectionTracker
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.ActionHandler
import com.android.documentsui.ActionModeController
import com.android.documentsui.BaseActivity
import com.android.documentsui.DirectoryResult
import com.android.documentsui.Injector
import com.android.documentsui.MenuManager
import com.android.documentsui.ProfileTabsController
import com.android.documentsui.R
import com.android.documentsui.SelectionBarController
import com.android.documentsui.roots.RootCursorWrapper
import com.android.documentsui.testing.SortModels
import com.android.documentsui.testing.TestEnv
import com.android.documentsui.testing.TestProvidersAccess
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
@MediumTest
class DirectoryFragmentTest {
    private lateinit var env: TestEnv
    private lateinit var injector: Injector<ActionHandler>
    private lateinit var fragment: DirectoryFragmentWithActivity

    @Before
    fun setUp() {
        env = TestEnv.create()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.setTheme(getRes(R.style.DocumentsTheme))
        context.theme.applyStyle(getRes(R.style.DocumentsDefaultTheme), false)
        val inflater = LayoutInflater.from(context)
        val container = FrameLayout(context)
        env.state.sortModel = SortModels.createTestSortModel()

        // Mock injector.
        injector = spy(env.injector)
        injector.profileTabsController = mock(ProfileTabsController::class.java)
        injector.menuManager = mock(MenuManager::class.java)
        injector.actions = mock(ActionHandler::class.java)
        doNothing().`when`(injector.actions).loadDocumentsForCurrentStack()
        `when`(injector.getActionHandler(any())).thenReturn(injector.actions)
        injector.actionModeController = mock(ActionModeController::class.java)
        `when`(injector.actionModeController!!.reset(any(), any()))
            .thenReturn(injector.actionModeController)
        // SelectionBarController itself can't be mocked, initialize a real one.
        injector.selectionBarController =
            SelectionBarController(
                MaterialToolbar(context),
                MaterialToolbar(context),
                injector.menuManager,
                injector.selectionMgr,
            )
        // Mock updateSharedSelectionTracker so the test selection manager from the TestEnv won't
        // be replaced with something we can't mock.
        doNothing().`when`(injector).updateSharedSelectionTracker(any())
        // Mock the activity and its dependencies.
        val activity = mock(BaseActivity::class.java)
        `when`(activity.displayState).thenReturn(env.state)
        `when`(activity.getInjector()).thenReturn(injector)
        `when`(activity.getSystemService(Context.ACTIVITY_SERVICE))
            .thenReturn(mock(ActivityManager::class.java))
        `when`(activity.packageManager).thenReturn(mock(PackageManager::class.java))
        `when`(activity.contentResolver).thenReturn(mock(ContentResolver::class.java))
        `when`(activity.selectedUser).thenReturn(env.userId)
        `when`(activity.resources).thenReturn(context.resources)
        `when`(activity.applicationContext).thenReturn(context.applicationContext)

        fragment = DirectoryFragmentWithActivity(context, activity)
        fragment.arguments = Bundle()

        // Need this to create selection tracker inside onActivityCreated().
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }
        fragment.onCreateView(inflater, container, null)
        fragment.onActivityCreated(null)
    }

    @Test
    fun testOnModelUpdate_notifySelectionChange() {
        val authority = TestProvidersAccess.HOME.authority

        // Match model update ID format: "0|com.android.externalstorage.documents|file1".
        fun getModelId(name: String): String = "${env.userId}|$authority|$name"
        // Put 2 files into selection manager.
        assertThat(injector.selectionMgr.selection).hasSize(0)
        injector.selectionMgr.select(getModelId("file1"))
        injector.selectionMgr.select(getModelId("file2"))
        assertThat(injector.selectionMgr.selection).hasSize(2)

        // Add an observer to the selection manager.
        val observer =
            object : SelectionTracker.SelectionObserver<String>() {
                var selections = MutableSelection<String>()

                override fun onSelectionChanged() {
                    injector.selectionMgr.copySelection(selections)
                }
            }
        injector.selectionMgr.addObserver(observer)

        // model.update() will trigger a updateLayout() call in ModelUpdateListener, which then
        // triggers SwipeRefreshLayout to set the offset, which internally triggers
        // CircularProgressDrawable's animation. The animation requires a valid Looper thread.
        if (Looper.myLooper() == null) {
            Looper.prepare()
        }

        // Trigger a model update with only "file2" in it to simulate the deletion of "file1".
        val cursor =
            MatrixCursor(
                arrayOf(
                    RootCursorWrapper.COLUMN_AUTHORITY,
                    RootCursorWrapper.COLUMN_USER_ID,
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                ),
            )
        val row = cursor.newRow()
        row.add(RootCursorWrapper.COLUMN_AUTHORITY, authority)
        row.add(RootCursorWrapper.COLUMN_USER_ID, env.userId)
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, "file2")
        val result = DirectoryResult()
        result.cursor = cursor
        env.model.update(result)

        // Assert that only "file2" is left in the selection model and its observer.
        assertThat(injector.selectionMgr.selection).hasSize(1)
        assertThat(injector.selectionMgr.selection).contains(getModelId("file2"))
        assertThat(observer.selections).hasSize(1)
        assertThat(observer.selections).contains(getModelId("file2"))
    }
}

// DirectoryFragment requires a valid activity, use this class to provide a fake one for it so we
// don't need to actually launch an activity.
class DirectoryFragmentWithActivity(
    private val context: Context,
    private val fakeActivity: BaseActivity,
) : DirectoryFragment() {
    override fun getBaseActivity(): BaseActivity = fakeActivity

    override fun getContext(): Context = context
}
