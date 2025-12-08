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

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.documentsui.BaseActivity
import com.android.documentsui.R
import com.android.documentsui.base.State
import com.android.documentsui.util.Material3Config.Companion.getRes

// Since the main binding logic is in [Item.bindView], the ViewHolder itself
// doesn't do anything here.
class RootItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

/**
 * This is the RecyclerView version of the [RootsAdapter], it will replace [RootsAdapter] when the
 * use_material3 flag is ON. It not only covers the existing functionality of [RootsAdapter], but
 * also covers the [RootsList], because in the layout file we will use the naked RecyclerView
 * instead of [RootsList] when the flag is ON.
 */
class RecyclerRootsAdapter(
  private val mActivity: BaseActivity,
  private val mItems: MutableList<Item>,
  private val mDragListener: View.OnDragListener?,
) : RecyclerView.Adapter<RootItemViewHolder>() {
  private var mSelectedPosition = RecyclerView.NO_POSITION

  companion object {
    const val TYPE_ROOT = 0
    const val TYPE_NAV_RAIL_ROOT = 1
    const val TYPE_SPACER = 2
    const val TYPE_HEADER = 3
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RootItemViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val layoutId =
      when (viewType) {
        TYPE_ROOT -> getRes(R.layout.item_root)
        TYPE_NAV_RAIL_ROOT -> getRes(R.layout.nav_rail_item_root)
        TYPE_SPACER -> getRes(R.layout.item_root_spacer)
        TYPE_HEADER -> getRes(R.layout.item_root_header)
        else -> throw IllegalArgumentException("Invalid view type: $viewType")
      }

    val view = inflater.inflate(layoutId, parent, false)
    return RootItemViewHolder(view)
  }

  override fun onBindViewHolder(holder: RootItemViewHolder, position: Int) {
    val item = mItems[position]
    item.bindView(holder.itemView)
    holder.itemView.setTag(getRes(R.id.item_position_tag), if (item.isRoot) position else null)
    holder.itemView.setOnDragListener(if (item.isRoot) mDragListener else null)

    val isEnabled = item !is SpacerItem
    holder.itemView.isEnabled = isEnabled
    holder.itemView.isActivated = item.isSelected
    holder.itemView.isSelected = item.isSelected

    holder.itemView.setOnClickListener { v: View -> onClick(item) }
    holder.itemView.setOnLongClickListener { v: View -> onLongClick(item) }
    holder.itemView.setOnKeyListener { v: View, keyCode: Int, event: KeyEvent ->
      onKey(keyCode, event)
    }
  }

  fun onClick(item: Item) {
    item.open()
    mActivity.setRootsDrawerOpen(false)
  }

  fun onLongClick(item: Item): Boolean {
    val state = mActivity.displayState
    if (state.action == State.ACTION_GET_CONTENT) {
      return item.showAppDetails()
    }
    return false
  }

  fun onKey(keyCode: Int, event: KeyEvent): Boolean {
    if (event.action != KeyEvent.ACTION_DOWN) {
      return false
    }
    return when (keyCode) {
      /**
       * Ignore tab key events - this causes them to bubble up to the global key handler where they
       * are appropriately handled. See [com.android.documentsui.files.FilesActivity.onKeyDown] and
       * [com.android.documentsui.picker.PickActivity.onKeyDown].
       *
       * The tab press will be bubbled up only when the keyboard navigation feature is enabled,
       * otherwise the event will be swallowed here.
       */
      KeyEvent.KEYCODE_TAB -> !mActivity.getInjector().features.isSystemKeyboardNavigationEnabled()
      // Prevent left/right arrow keystrokes from shifting focus away from the roots list.
      KeyEvent.KEYCODE_DPAD_LEFT,
      KeyEvent.KEYCODE_DPAD_RIGHT -> true

      else -> false
    }
  }

  override fun getItemViewType(position: Int): Int {
    val item = mItems[position]
    return when (item) {
      is NavRailRootItem,
      is NavRailAppItem,
      is NavRailRootAndAppItem,
      is NavRailProfileItem -> TYPE_NAV_RAIL_ROOT
      is RootAndAppItem,
      is ProfileItem,
      is RootItem,
      is AppItem -> TYPE_ROOT
      is SpacerItem -> TYPE_SPACER
      is HeaderItem -> TYPE_HEADER

      else -> TYPE_ROOT
    }
  }

  override fun getItemCount(): Int {
    return mItems.size
  }

  fun getItem(position: Int): Item? {
    return mItems[position]
  }

  fun setItemSelected(position: Int, selected: Boolean) {
    if (position < 0 || position >= mItems.size) {
      return
    }
    if (mSelectedPosition != RecyclerView.NO_POSITION) {
      val previouslySelectedItem = mItems[mSelectedPosition]
      previouslySelectedItem.isSelected = false
      notifyItemChanged(mSelectedPosition)
    }
    mSelectedPosition = position
    val item = mItems[position]
    item.isSelected = selected
    notifyItemChanged(position)
  }
}
