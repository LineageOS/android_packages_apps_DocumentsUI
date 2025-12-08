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

import android.view.ContextMenu
import android.view.View
import android.view.View.OnDragListener
import android.view.View.OnGenericMotionListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.documentsui.BaseActivity
import com.android.documentsui.DragHoverListener
import com.android.documentsui.ItemDragListener

/**
 * ListView implementation of RootsListHandler.
 * This class is used when use_material3 flag is ON to replace [RootsListViewHandler].
 */
internal class RootsRecyclerViewHandler(private val recyclerView: RecyclerView) : RootsListHandler {

  private lateinit var baseActivity: BaseActivity
  private var adapter: RecyclerRootsAdapter? = null

  /**
   * For RecyclerView, the `menuInfo` retrieved from the menu item in the callback
   * [RootsFragment.onContextItemSelected] is null, so there's no way to get the position from it,
   * hence declaring a variable here to store the position when right click happens.
   */
  private var contextMenuPosition = RecyclerView.NO_POSITION

  override fun setup(activity: BaseActivity) {
    baseActivity = activity
    recyclerView.layoutManager = LinearLayoutManager(activity)
    recyclerView.addItemDecoration(RootsListGapItemDecoration())
  }

  override fun setOnGenericMotionListener(listener: OnGenericMotionListener) {
    recyclerView.setOnGenericMotionListener(listener)
  }

  override fun getItemFromViewUnder(x: Int, y: Int): Item? {
    val child = recyclerView.findChildViewUnder(x.toFloat(), y.toFloat())
    if (child == null) {
      return null
    }
    val pos = recyclerView.getChildAdapterPosition(child)
    contextMenuPosition = pos
    return adapter?.getItem(pos)
  }

  override fun createDragListener(listener: ItemDragListener<DragHost>): OnDragListener {
    return DragHoverListener.create(listener, recyclerView)
  }

  override fun scrollToFirstVisiblePosition(items: List<Item>, dragListener: OnDragListener?) {
    // Get the first visible position and offset.
    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
    val firstPosition = layoutManager.findFirstVisibleItemPosition()
    val firstChild = recyclerView.getChildAt(0)
    val offset = if (firstChild != null) firstChild.top - recyclerView.paddingTop else 0
    val originalItemCount = getItemCount()
    adapter = RecyclerRootsAdapter(baseActivity, items as MutableList<Item>, dragListener)
    recyclerView.adapter = adapter

    // Recover the position.
    if (originalItemCount == getItemCount()) {
      layoutManager.scrollToPositionWithOffset(firstPosition, offset)
    }
  }

  override fun resetAdapter() {
    adapter = null
    recyclerView.adapter = null
  }

  override fun onDisplayStateChange() {
    // Intentionally doing nothing.
  }

  override fun isAdapterInitialized(): Boolean {
    return adapter != null
  }

  override fun getItem(position: Int): Item? {
    return adapter?.getItem(position)
  }

  override fun getItemCount(): Int {
    return adapter?.itemCount ?: 0
  }

  override fun selectItem(position: Int) {
    adapter?.setItemSelected(position, true)
  }

  override fun requestListFocus(): Boolean {
    return recyclerView.requestFocus()
  }

  override fun getItemForContextMenu(menuInfo: ContextMenu.ContextMenuInfo?): Item? {
    if (contextMenuPosition == RecyclerView.NO_POSITION) {
      return null
    }
    return adapter?.getItem(contextMenuPosition)
  }

  override fun getItemViewForContextMenu(menuInfo: ContextMenu.ContextMenuInfo?): View? {
    if (contextMenuPosition == RecyclerView.NO_POSITION) {
      return null
    }
    val holder = recyclerView.findViewHolderForAdapterPosition(contextMenuPosition)
    return holder?.itemView
  }
}
