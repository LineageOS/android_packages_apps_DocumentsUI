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
package com.android.documentsui.sidebar

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.android.documentsui.R
import com.android.documentsui.sidebar.RecyclerRootsAdapter.Companion.TYPE_SPACER
import com.android.documentsui.util.Material3Config.Companion.getRes

/**
 * Use this custom ItemDecoration to achieve the top margin between the roots list items inside
 * RecyclerView. Define it here instead of the layout file because we don't want to top margin for
 * the 1st item in the list, and there's no way to tell if an item is the 1st item or not in the
 * layout file.
 */
class RootsListGapItemDecoration : RecyclerView.ItemDecoration() {
  override fun getItemOffsets(
    outRect: Rect,
    view: View,
    parent: RecyclerView,
    state: RecyclerView.State,
  ) {
    val position = parent.getChildAdapterPosition(view)
    val itemMarginTop =
      parent.resources.getDimensionPixelSize(getRes(R.dimen.drawer_item_vertical_margin))
    val dividerMargin =
      parent.resources.getDimensionPixelSize(getRes(R.dimen.drawer_divider_padding_vertical))

    val isFirstItem = position == 0
    val isSpacerItem = parent.adapter?.getItemViewType(position) == TYPE_SPACER
    val isBelowSpacerItem =
      position > 0 && parent.adapter?.getItemViewType(position - 1) == TYPE_SPACER
    if (isFirstItem || isBelowSpacerItem) {
      outRect.top = 0
    } else if (isSpacerItem) {
      outRect.top = dividerMargin
      outRect.bottom = dividerMargin
    } else {
      outRect.top = itemMarginTop
    }
  }
}
