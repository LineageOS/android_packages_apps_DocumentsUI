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

import android.view.MenuItem
import android.view.View
import androidx.core.view.isNotEmpty
import androidx.recyclerview.selection.MutableSelection
import androidx.recyclerview.selection.SelectionTracker
import com.android.documentsui.base.EventHandler
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.appbar.MaterialToolbar

/**
 * Controls the secondary `MaterialToolbar` that shows the current selection and the associated
 * action menu for the selected items. This is only instantiated when the `use_material3` flag is
 * enabled.
 */
class SelectionBarController(
    private val appBar: MaterialToolbar,
    private val selectionBar: MaterialToolbar,
    private val menuManager: MenuManager,
    private val selectionManager: DocsSelectionHelper
) : SelectionTracker.SelectionObserver<String>() {
    // The clicker and selectionDetails are initialised in the DirectoryFragment and thus gets setup
    // each time the directory changes.
    private var selectionDetails: MenuManager.SelectionDetails? = null
    private var menuItemClicker: EventHandler<MenuItem>? = null

    // Copy the selection when it changes to ensure the update is done without any interference.
    private var selectedItems = MutableSelection<String>()

    override fun onSelectionChanged() {
        selectionManager.copySelection(selectedItems)
        updateSelectionBar()
    }

    override fun onSelectionRestored() {
        onSelectionChanged()
    }

    /**
     * Method that is used by the DirectoryFragment to effectively "reset" the current selection and
     * menu item click function when the directory fragment is changed. This enforces the
     * @ContentScoped invariant.
     */
    fun updateSelection(
        selectionDetails: MenuManager.SelectionDetails,
        menuItemClicker: EventHandler<MenuItem>
    ): SelectionBarController {
        this.selectionDetails = selectionDetails
        this.menuItemClicker = menuItemClicker
        return this
    }

    fun closeSelectionBar() {
        selectionManager.clearSelection()
    }

    private fun updateSelectionBar() {
        if (selectedItems.isEmpty) {
            appBar.visibility = View.VISIBLE
            selectionBar.visibility = View.GONE
            return
        }
        selectionBar.visibility = View.VISIBLE
        appBar.visibility = View.GONE

        val quantity: Int = selectedItems.size()
        val title: String =
            selectionBar.context
                .getResources()
                .getQuantityString(
                    getRes(R.plurals.elements_selected),
                    quantity,
                    quantity
                )
        selectionBar.title = title
        selectionBar.setNavigationIcon(getRes(R.drawable.ic_cancel))
        selectionBar.setNavigationContentDescription(R.string.clear_selection)
        selectionBar.setOnMenuItemClickListener { menuItemClicker?.accept(it) == true }
        selectionBar.setNavigationOnClickListener { closeSelectionBar() }
        updateSelectionMenu()
    }

    /** Updates the selection menu (including inflating it if required). */
    private fun updateSelectionMenu() {
        val isMenuInflated = selectionBar.menu != null && selectionBar.menu.isNotEmpty()
        if (!isMenuInflated) {
            selectionBar.inflateMenu(getRes(R.menu.action_mode_menu))
        }
        menuManager.updateActionMenu(selectionBar.menu, selectionDetails)
    }
}
