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

import android.graphics.Color
import android.view.ContextMenu
import android.view.View
import android.view.View.OnDragListener
import android.view.View.OnGenericMotionListener
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.AdapterView.OnItemLongClickListener
import android.widget.ListView
import androidx.core.graphics.drawable.toDrawable
import com.android.documentsui.BaseActivity
import com.android.documentsui.DragHoverListener
import com.android.documentsui.ItemDragListener
import com.android.documentsui.base.State

/**
 * ListView implementation of RootsListHandler.
 * This class is only used when use_material3 flag is OFF, it's intended to be removed after
 * the use_material3 flag is fully launched.
 */
internal class RootsListViewHandler(private val listView: ListView) : RootsListHandler {

  private lateinit var baseActivity: BaseActivity
  private var adapter: RootsAdapter? = null

  private val itemClickListener: OnItemClickListener =
    OnItemClickListener { parent, view, position, id ->
      val item: Item? = adapter?.getItem(position)
      item?.open()

      baseActivity.setRootsDrawerOpen(false)
    }

  private val itemLongClickListener: OnItemLongClickListener =
    OnItemLongClickListener { parent, view, position, id ->
      val item: Item? = adapter?.getItem(position)
      item?.showAppDetails() ?: false
    }

  override fun setup(activity: BaseActivity) {
    baseActivity = activity
    listView.onItemClickListener = itemClickListener
    listView.choiceMode = ListView.CHOICE_MODE_SINGLE
    listView.selector = Color.TRANSPARENT.toDrawable()
  }

  override fun setOnGenericMotionListener(listener: OnGenericMotionListener) {
    listView.setOnGenericMotionListener(listener)
  }

  override fun getItemFromViewUnder(x: Int, y: Int): Item? {
    val pos = listView.pointToPosition(x, y)
    return adapter?.getItem(pos)
  }

  override fun createDragListener(listener: ItemDragListener<DragHost>): OnDragListener {
    val dragListener = DragHoverListener.create(listener, listView)
    listView.setOnDragListener(dragListener)
    return dragListener
  }

  override fun scrollToFirstVisiblePosition(items: List<Item>, dragListener: OnDragListener?) {
    // Get the first visible position and offset.
    val firstPosition: Int = listView.firstVisiblePosition
    val firstChild: View? = listView.getChildAt(0)
    val offset = if (firstChild != null) firstChild.top - listView.paddingTop else 0
    val originalItemCount = getItemCount()
    adapter = RootsAdapter(baseActivity, items, dragListener)
    listView.adapter = adapter

    // Recover the position.
    if (originalItemCount == getItemCount()) {
      listView.setSelectionFromTop(firstPosition, offset)
    }
  }

  override fun resetAdapter() {
    adapter = null
    listView.adapter = null
  }

  override fun onDisplayStateChange() {
    val state = baseActivity.displayState

    if (state.action == State.ACTION_GET_CONTENT) {
      listView.onItemLongClickListener = itemLongClickListener
    } else {
      listView.onItemLongClickListener = null
      listView.isLongClickable = false
    }
  }

  override fun isAdapterInitialized(): Boolean {
    return adapter != null
  }

  override fun getItem(position: Int): Item? {
    return adapter?.getItem(position)
  }

  override fun getItemCount(): Int {
    return adapter?.count ?: 0
  }

  override fun selectItem(position: Int) {
    listView.setItemChecked(position, true)
  }

  override fun requestListFocus(): Boolean {
    return listView.requestFocus()
  }

  override fun getItemForContextMenu(menuInfo: ContextMenu.ContextMenuInfo?): Item? {
    // There is a possibility that this is called from DirectoryFragment since
    // all fragments' onContextItemSelected gets called when any menu item is selected
    // This is to guard against it since DirectoryFragment's RecyclerView does not have a
    // menuInfo.
    if (menuInfo == null) {
      return null
    }
    val adapterMenuInfo = menuInfo as AdapterView.AdapterContextMenuInfo
    return adapter?.getItem(adapterMenuInfo.position)
  }

  override fun getItemViewForContextMenu(menuInfo: ContextMenu.ContextMenuInfo?): View? {
    if (menuInfo == null) {
      return null
    }
    val adapterMenuInfo = menuInfo as AdapterView.AdapterContextMenuInfo
    return adapterMenuInfo.targetView
  }
}
