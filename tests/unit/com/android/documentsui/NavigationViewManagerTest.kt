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

import android.content.Context
import android.platform.test.annotations.EnableFlags
import android.view.View
import android.view.Window
import android.widget.LinearLayout
import androidx.appcompat.widget.Toolbar
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.base.State
import com.android.documentsui.base.UserId
import com.android.documentsui.flags.Flags
import com.android.documentsui.rules.OverrideFlagsRule
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.tabs.TabLayout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@RunWith(AndroidJUnit4::class)
@SmallTest
class NavigationViewManagerTest {

  @get:Rule val overrideFlagsRule = OverrideFlagsRule()

  private lateinit var context: Context
  private lateinit var activity: BaseActivity
  private lateinit var drawer: DrawerController
  private lateinit var toolbar: Toolbar
  private lateinit var collapsingToolbarLayout: CollapsingToolbarLayout
  private lateinit var userIdManager: UserIdManager
  private lateinit var state: State
  private lateinit var env: NavigationViewManager.Environment
  private lateinit var breadcrumb: NavigationViewManager.Breadcrumb
  private lateinit var configStore: ConfigStore
  private lateinit var navigationViewManager: NavigationViewManager
  private lateinit var appBarLayout: AppBarLayout
  private lateinit var collapsingContent: LinearLayout
  private lateinit var tabLayoutContainer: View

  @Before
  fun setUp() {
    context = InstrumentationRegistry.getInstrumentation().targetContext
    drawer = mock(DrawerController::class.java)
    state = State()
    env = mock(NavigationViewManager.Environment::class.java)
    breadcrumb = mock(NavigationViewManager.Breadcrumb::class.java)
    configStore = mock(ConfigStore::class.java)
    tabLayoutContainer = mock(View::class.java)
    collapsingContent = LinearLayout(context)

    toolbar = mock(Toolbar::class.java)
    `when`(toolbar.resources).thenReturn(context.getResources())

    activity = mock(BaseActivity::class.java)
    `when`(activity.resources).thenReturn(context.getResources())
    `when`(activity.getTheme()).thenReturn(context.getTheme())
    collapsingToolbarLayout = mock(CollapsingToolbarLayout::class.java)
    `when`(collapsingToolbarLayout.findViewById<View>(getRes(R.id.collapsing_content)))
      .thenReturn(collapsingContent)
    appBarLayout = mock(AppBarLayout::class.java)
    `when`(activity.findViewById<Toolbar>(getRes(R.id.toolbar))).thenReturn(toolbar)
    `when`(activity.findViewById<CollapsingToolbarLayout>(getRes(R.id.collapsing_toolbar)))
      .thenReturn(collapsingToolbarLayout)
    `when`(activity.findViewById<AppBarLayout>(getRes(R.id.app_bar))).thenReturn(appBarLayout)
    `when`(activity.findViewById<View>(getRes(R.id.directory_header)))
      .thenReturn(mock(View::class.java))
    `when`(activity.findViewById<View>(getRes(R.id.searchbar_title)))
      .thenReturn(mock(View::class.java))

    val window = mock(Window::class.java)
    `when`(window.decorView).thenReturn(mock(View::class.java))
    `when`(activity.window).thenReturn(window)

    userIdManager = mock(UserIdManager::class.java)
    `when`(userIdManager.getUserIds()).thenReturn(listOf(UserId.DEFAULT_USER))

    `when`(tabLayoutContainer.findViewById<TabLayout>(getRes(R.id.tabs)))
      .thenReturn(mock(TabLayout::class.java))
    `when`(tabLayoutContainer.findViewById<View>(getRes(R.id.tab_separator)))
      .thenReturn(mock(View::class.java))
  }

  @Test
  @EnableFlags(Flags.FLAG_USE_MATERIAL3)
  fun testCollapsedAppBar_expandsOnFocus() {
    navigationViewManager =
      NavigationViewManager(
        activity,
        drawer,
        state,
        env,
        breadcrumb,
        tabLayoutContainer,
        userIdManager,
        configStore,
      )

    // Manually set the offset to a negative value to meet the condition to trigger expand.
    navigationViewManager.onOffsetChanged(appBarLayout, -100)

    val childView = View(context)
    collapsingContent.addView(childView)
    navigationViewManager.onChildViewFocused(collapsingContent, childView)
    // Assert appBarLayout should be expanded.
    verify(appBarLayout).setExpanded(true, true)

    val nonChildView = View(context)
    navigationViewManager.onChildViewFocused(collapsingContent, nonChildView)
    // Assert no new call to appBarLayout.setExpanded.
    verify(appBarLayout, times(1)).setExpanded(true, true)
  }
}
