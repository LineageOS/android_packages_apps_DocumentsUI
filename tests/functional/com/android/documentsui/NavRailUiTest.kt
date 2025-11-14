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

import android.app.ActivityOptions
import android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM
import android.content.Intent
import android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT
import android.content.res.Resources
import android.graphics.Rect
import android.platform.test.annotations.RequiresFlagsEnabled
import android.provider.DocumentsContract
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Display
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.documentsui.files.FilesActivity
import com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3
import com.android.documentsui.rules.CheckAndForceMaterial3Flag
import kotlin.math.roundToInt
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RequiresFlagsEnabled(FLAG_USE_MATERIAL3)
@RunWith(AndroidJUnit4::class)
class NavRailUiTest : ActivityTestJunit4<FilesActivity>() {
  @get:Rule
  val checkFlags = CheckAndForceMaterial3Flag()

  companion object {
    private const val MEDIUM_WINDOW_WIDTH = 700
    private const val MEDIUM_WINDOW_HEIGHT = 900
  }

  private fun dpToPx(dp: Float, metrics: DisplayMetrics?): Float {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, metrics)
  }

  private fun pxToDp(px: Float, metrics: DisplayMetrics): Float {
    return TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_DIP, px, metrics)
  }

  /** Override the base method to launch activity in a specified window size. */
  override fun launchActivity() {
    // check assumption before launching the activity.
    assumeMinWindowSizeAndFreeFormWindowFeature()

    val intent = Intent(context, FilesActivity::class.java)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
    if (this.initialRoot != null) {
      intent.setAction(Intent.ACTION_VIEW)
      intent.setDataAndType(this.initialRoot!!.uri, DocumentsContract.Root.MIME_TYPE_ITEM)
    }
    val displayMetrics = Resources.getSystem().displayMetrics
    val options = ActivityOptions.makeBasic()
    options.launchWindowingMode = WINDOWING_MODE_FREEFORM
    options.setLaunchBounds(
      Rect(
        0,
        0,
        dpToPx(MEDIUM_WINDOW_WIDTH.toFloat(), displayMetrics).roundToInt(),
        dpToPx(MEDIUM_WINDOW_HEIGHT.toFloat(), displayMetrics).roundToInt(),
      )
    )
    options.setLaunchDisplayId(Display.DEFAULT_DISPLAY)
    mActivityScenario = ActivityScenario.launch(intent, options.toBundle())
  }

  /** Making sure the test device meets the minimum window size and have freeform window feature. */
  fun assumeMinWindowSizeAndFreeFormWindowFeature() {
    assumeTrue(
      "Skipping test: test device doesn't support FreeForm window.",
      context!!.getPackageManager().hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT),
    )
    val displayMetrics = Resources.getSystem().displayMetrics
    assumeTrue(
      "Skipping test: test device window size is too small to support medium layout.",
      pxToDp(displayMetrics.widthPixels.toFloat(), displayMetrics) >= MEDIUM_WINDOW_WIDTH &&
        pxToDp(displayMetrics.heightPixels.toFloat(), displayMetrics) >= MEDIUM_WINDOW_HEIGHT,
    )
  }

  @Test
  fun testNavRailRootsNavigation() {
    bots.main.assertWindowTitle(StubProvider.ROOT_0_ID)
    bots.roots.openNavRailRoot(StubProvider.ROOT_1_ID)
    bots.main.assertWindowTitle(StubProvider.ROOT_1_ID)
  }

  @Test
  fun testNavRailDrawerRootsNavigation() {
    bots.main.assertWindowTitle(StubProvider.ROOT_0_ID)
    bots.roots.openDrawerFromNavRail()
    // The following openRoot will open root from the drawer instead of the navigation rail because
    // the drawer is open explicitly above.
    bots.roots.openRoot(StubProvider.ROOT_1_ID)
    bots.main.assertWindowTitle(StubProvider.ROOT_1_ID)
  }
}
