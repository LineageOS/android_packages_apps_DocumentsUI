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
package com.android.documentsui.queries

import android.content.Context
import android.os.Bundle
import android.provider.DocumentsContract
import android.view.MenuItem
import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.iterator
import com.android.documentsui.MetricConsts
import com.android.documentsui.R
import com.android.documentsui.base.RootInfo
import com.android.documentsui.util.FlagUtils
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.chip.Chip
import java.time.LocalDate
import java.time.ZoneId

/**
 * The controller for the search option dropdowns. This controller manages the UI interactions
 * and converts them to a state of the dropdowns. It must be created with the view that contains
 * the buttons that trigger showing or hiding of the dropdowns.
 */
class SearchOptionsController(private val container: View?) {
    // The value of currently selected options. Initialized to sensible defaults.
    private var lastModifiedOption: LastModifiedOption = LastModifiedOption.ANY_TIME
    private var fileTypeOption: FileTypeOption = FileTypeOption.ANY_TYPE
    private var locationOption: SearchLocationOption = SearchLocationOption.ROOT_FOLDER

    // We dynamically set the name of the root folder, based on where in the directory tree
    // the user is located at the time search is opened. This does not change while the search
    // options are visible.
    private var currentRoot: RootInfo? = null

    // A single listener to query option change events.
    private var optionsListener: SearchOptionsListener? = null

    init {
        if (FlagUtils.isUseMaterial3FlagEnabled() && container != null) {
            makeTrigger(
                getRes(R.id.search_location_trigger),
                getRes(R.menu.search_location_menu),
                this::onLocationSelected
            )
            makeTrigger(
                getRes(R.id.search_last_modified_trigger),
                getRes(R.menu.search_last_modified_menu),
                this::onLastModifiedSelected
            )
            makeTrigger(
                getRes(R.id.search_file_type_trigger),
                getRes(R.menu.search_file_type_menu),
                this::onFileTypeSelected
            )
        }
    }

    private fun getSelectedMenuOption(@MenuRes menuId: Int): Int {
        return when (menuId) {
            R.menu.search_location_menu -> locationOption.value
            R.menu.search_last_modified_menu -> lastModifiedOption.value
            R.menu.search_file_type_menu -> fileTypeOption.value
            else -> throw IllegalArgumentException("Unexpected menu ID $menuId")
        }
    }

    /**
     * Explicitly sets the file type based on a MetricConsts.
     */
    public fun setSelectedFileType(@MetricConsts.SearchType typeId: Int) {
        fileTypeOption = when (typeId) {
            MetricConsts.TYPE_CHIP_AUDIOS -> FileTypeOption.AUDIO
            MetricConsts.TYPE_CHIP_DOCS -> FileTypeOption.DOCUMENTS
            MetricConsts.TYPE_CHIP_IMAGES -> FileTypeOption.IMAGES
            MetricConsts.TYPE_CHIP_VIDEOS -> FileTypeOption.VIDEO
            else -> throw IllegalArgumentException("Cannot convert $typeId to file type")
        }
    }

    /**
     * Helper function that removes repetitive setup for each of the option triggers.
     */
    private fun makeTrigger(
        @IdRes triggerId: Int,
        @MenuRes menuId: Int,
        callback: (option: Int) -> Boolean
    ) {
        val trigger = container?.findViewById<Chip>(triggerId)
        trigger?.setOnClickListener {
            showMenu(trigger, menuId) {
                if (callback(it)) {
                    notifyOptionsChangeListener()
                }
            }
        }
    }

    /**
     * Updates the location option based on the given resource ID. Returns true, if the option
     * has been changed. Otherwise, returns false.
     */
    fun onLocationSelected(locationId: Int): Boolean {
        val selectedOption = searchLocationOptionFor(locationId) ?: return false
        if (selectedOption == locationOption) {
            return false
        }
        locationOption = selectedOption
        updateUiForRoot()
        return true
    }

    /**
     * Updates the file type option based on the given resource ID. Returns true, if the option
     * has been changed. Otherwise, returns false.
     */
    fun onFileTypeSelected(fileTypeId: Int): Boolean {
        val selectedOption = fileTypeOptionFor(fileTypeId) ?: return false
        if (selectedOption == fileTypeOption) {
            return false
        }
        fileTypeOption = selectedOption
        return true
    }

    /**
     * Updates the last modified option based on the given resource ID. Returns true, if the option
     * has been changed. Otherwise, returns false.
     */
    fun onLastModifiedSelected(lastModifiedId: Int): Boolean {
        val selectedOption = lastModifiedOptionFor(lastModifiedId) ?: return false
        if (selectedOption == lastModifiedOption) {
            return false
        }
        lastModifiedOption = selectedOption
        return true
    }

    /**
     * Notifies an option listener about options change, if one is registered.
     */
    fun notifyOptionsChangeListener() {
        optionsListener?.onOptionsChanged(
            SearchOptionsState(
                fileTypeOption,
                lastModifiedOption,
                locationOption
            )
        )
    }

    /**
     * Sets the option change listener. Currently only one listener is supported. If the listener
     * is set to null, that is equivalent to removing the listener.
     */
    fun setOptionChangeListener(listener: SearchOptionsListener) {
        optionsListener = listener
    }

    /**
     * Creates a bundle, potentially empty, that contains DocumentsContract last modified argument
     * set based on mLastModifiedOptions.
     */
    private fun getLastModifiedQueryArgs(): Bundle {
        val bundle = Bundle()
        if (lastModifiedOption != LastModifiedOption.ANY_TIME) {
            bundle.putLong(
                DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
                LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() - lastModifiedOption.millis
            )
        }
        return bundle
    }

    private fun getFileTypeQueryArgs(): Bundle {
        val bundle = Bundle()
        val mimeTypes = when (fileTypeOption) {
            FileTypeOption.AUDIO -> SearchChipViewManager.AUDIO_MIMETYPES
            FileTypeOption.DOCUMENTS -> SearchChipViewManager.DOCUMENTS_MIMETYPES
            FileTypeOption.IMAGES -> SearchChipViewManager.IMAGES_MIMETYPES
            FileTypeOption.VIDEO -> SearchChipViewManager.VIDEOS_MIMETYPES
            else -> arrayOf<String>()
        }
        if (mimeTypes.isNotEmpty()) {
            bundle.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES, mimeTypes)
        }
        return bundle
    }

    /**
     * Creates a bundle with query arguments compliant with DocumentsContract.
     */
    fun getOptionsQueryArgs(): Bundle {
        val bundle = Bundle()
        bundle.putAll(getLastModifiedQueryArgs())
        bundle.putAll(getFileTypeQueryArgs())
        return bundle
    }

    /**
     * Shows the search dropdown options bar. The root must be the root to which search is limited,
     * if the user selects current root search, rather than everywhere search.
     * @param root The current root in the directory tree.
     */
    fun show(root: RootInfo?) {
        if (container == null) {
            return
        }
        currentRoot = root
        if (isInRecentRoot()) {
            // If the user goes into in the Recents view from another root we force the location
            // to the ROOT_FOLDER, and the last modified option to 30 days to match the Recent
            // view defaults.
            lastModifiedOption = LastModifiedOption.LAST_30_DAYS
            locationOption = SearchLocationOption.ROOT_FOLDER
        }
        updateUiForRoot()
        container.visibility = View.VISIBLE
    }

    /**
     * Hides the dropdown bar by making it GONE.
     */
    fun hide() {
        container?.visibility = View.GONE
    }

    /**
     * Returns the text to be shown in the root folder. This method uses the current root's title,
     * if available, otherwise falls back to the hardcoded default.
     */
    private fun getRootFolderFallbackText(context: Context): String {
        return currentRoot?.title ?: context.getString(R.string.search_location_root_folder)
    }

    /**
     * @return A safe version for checking if the current location is the Recents view.
     */
    private fun isInRecentRoot(): Boolean {
        return currentRoot?.isRecents ?: false
    }

    /**
     * Alters the default UI based on the current root. If this method was called from the show()
     * method, it adjusts the defaults
     */
    private fun updateUiForRoot() {
        if (container == null) {
            return
        }
        val searchingRoot = locationOption == SearchLocationOption.ROOT_FOLDER
        if (searchingRoot) {
            // If the locationOption is the root folder, updated the location trigger text.
            val chip = container.findViewById<Chip>(R.id.search_location_trigger)
            if (chip != null) {
                chip.text = getRootFolderFallbackText(container.context)
            }
        }
        val chip = container.findViewById<Chip>(getRes(R.id.search_last_modified_trigger))
        if (chip != null) {
           if (isInRecentRoot()) {
               // In the Recents view last modified should be visible only when the user is
               // searching everywhere.
               chip.visibility = if (searchingRoot) View.GONE else View.VISIBLE
               chip.text = container.resources.getString(lastModifiedOption.textId)
           } else {
               chip.visibility = View.VISIBLE
           }
        }
        val typeChip = container.findViewById<Chip>(getRes(R.id.search_file_type_trigger))
        if (typeChip != null) {
            typeChip.text = container.resources.getString(fileTypeOption.textId)
        }
    }

    /**
     * Returns whether or not this controller is visible.
     */
    fun isVisible(): Boolean {
        return container?.visibility == View.VISIBLE
    }

    fun showMenu(chip: Chip, @MenuRes menuRes: Int, callback: (option: Int) -> Unit) {
        // We prevent the chip from working as a chip. Instead it acts as a button.
        chip.isChecked = false

        // Activate the specified popup menu for the chip.
        PopupMenu(chip.context, chip).apply {
            menuInflater.inflate(menuRes, menu)
            setForceShowIcon(true)
            setOnMenuItemClickListener { menuItem: MenuItem ->
                chip.text = menuItem.title
                callback(menuItem.itemId)
                false
            }
            val selectedValue = getSelectedMenuOption(menuRes)
            for (menuItem in menu) {
                if (menuItem.itemId == R.id.root_folder_option) {
                    menuItem.title = getRootFolderFallbackText(chip.context)
                }
                if (menuItem.itemId != selectedValue) {
                    menuItem.icon = null
                }
            }
            show()
        }
    }
}
