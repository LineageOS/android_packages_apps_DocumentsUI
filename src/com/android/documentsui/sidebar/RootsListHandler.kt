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
import com.android.documentsui.BaseActivity
import com.android.documentsui.ItemDragListener

/**
 * The root list can be a ListView (use_material3 flag OFF) or a RecyclerView (use_material3 flag
 * ON), this interface abstracts away the common operations for these 2 types of the lists and
 * their corresponding adapters, so the caller doesn't need to care about the underlying list
 * implementation.
 *
 * Note: mark it as internal because of the reference of DragHost below.
 */
internal interface RootsListHandler {
  /**
   * Setup the list.
   * @param activity the base activity of the app.
   */
  fun setup(activity: BaseActivity)

  /**
   * Add generic motion listener.
   * @param listener the generic motion listener.
   */
  fun setOnGenericMotionListener(listener: OnGenericMotionListener)

  /**
   * Get the root list item (model) from the mousy event coordinates.
   * @param x the x coordinates of the mousy event.
   * @param y the y coordinates of the mousy event.
   * @return the root list item (model).
   */
  fun getItemFromViewUnder(x: Int, y: Int): Item?

  /**
   * Create the [View.OnDragListener] from the [ItemDragListener].
   * @param listener the customized the drag listener which supports drag hover to open.
   * @return the wrapped drag listener.
   */
  fun createDragListener(listener: ItemDragListener<DragHost>): OnDragListener

  /**
   * Find the first visible position in the root list and scroll to it.
   * The behavior is a bit different depending on if the adapter is initialized or not, it will
   * create adapter internally if it doesn't exist.
   * @param items the model list used to create the adapter.
   * @param dragListener the drag listener used to create the adapter.
   */
  fun scrollToFirstVisiblePosition(items: List<Item>, dragListener: OnDragListener?)

  /**
   * Reset the adapter and unlink it from the list.
   */
  fun resetAdapter()

  /**
   * The callback function which triggers when the [com.android.documentsui.base.State] changes.
   */
  fun onDisplayStateChange()

  /**
   * Check if the adapter has been initialized or not.
   */
  fun isAdapterInitialized(): Boolean

  /**
   * Get the item (model) in the root list with a given position.
   * @param position the position ins the root list.
   * @return the item (model) behind the view at the position.
   */
  fun getItem(position: Int): Item?

  /**
   * Get the total item count in the root list.
   */
  fun getItemCount(): Int

  /**
   * Make the item at the specified position selected (corresponding to the activated state in
   * the style).
   * @param position the position in the root list.
   */
  fun selectItem(position: Int)

  /**
   * Request the focus for the root list.
   */
  fun requestListFocus(): Boolean

  /**
   * Get the item (model) at the coordinates when the context menu opens.
   * @param menuInfo the context menu info.
   * @return the item (model).
   */
  fun getItemForContextMenu(menuInfo: ContextMenu.ContextMenuInfo?): Item?

  /**
   * Get the item view at the coordinates when the context menu opens.
   * @param menuInfo the context menu info.
   * @return the item view.
   */
  fun getItemViewForContextMenu(menuInfo: ContextMenu.ContextMenuInfo?): View?
}
