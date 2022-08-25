/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.util.FlagUtils.isDesktopUxPhase2FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isSyncStateEnabled;
import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;
import static com.android.documentsui.util.FlagUtils.isUseApprovedDocumentHandlerEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.util.Log;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.core.view.MenuCompat;
import androidx.fragment.app.Fragment;

import com.android.documentsui.approveddochandlers.ApprovedDocMenuController;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Menus;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.sidebar.RootsFragment;

import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

import javax.annotation.Nullable;

public abstract class MenuManager {
    private final static String TAG = "MenuManager";

    protected final SearchViewManager mSearchManager;
    protected final State mState;
    protected final DirectoryDetails mDirDetails;
    protected final IntSupplier mFilesCountSupplier;
    protected final Context mContext;
    protected final Features mFeatures;
    protected final Injector<?> mInjector;
    @Nullable protected final ApprovedDocMenuController mApprovedDocMenuController;

    protected Menu mOptionMenu;

    /** The current context menu. */
    protected Menu mContextMenu;

    /** The selection details for the current context menu. */
    protected SelectionDetails mContextMenuDetails;

    public MenuManager(
            SearchViewManager searchManager,
            State displayState,
            DirectoryDetails dirDetails,
            IntSupplier filesCountSupplier,
            Context context,
            Features features,
            Injector<?> injector,
            @Nullable ApprovedDocMenuController approvedDocMenuController) {
        mSearchManager = searchManager;
        mState = displayState;
        mDirDetails = dirDetails;
        mFilesCountSupplier = filesCountSupplier;
        mContext = context;
        mFeatures = features;
        mInjector = injector;
        mApprovedDocMenuController = approvedDocMenuController;
    }

    /** @see ActionModeController */
    public void updateActionMenu(Menu menu, SelectionDetails selection) {
        Menus.disableHiddenItems(menu);

        updateOpenWith(menu.findItem(getRes(R.id.action_menu_open_with)), selection);
        updateDelete(menu.findItem(getRes(R.id.action_menu_delete)), selection);
        updateShare(menu.findItem(getRes(R.id.action_menu_share)), selection);
        updateRename(menu.findItem(getRes(R.id.action_menu_rename)), selection);
        updateCompress(menu.findItem(getRes(R.id.action_menu_compress)), selection);
        updateExtractTo(menu.findItem(getRes(R.id.action_menu_extract_to)), selection);
        updateInspect(menu.findItem(getRes(R.id.action_menu_inspect)), selection);
        updateViewInOwner(menu.findItem(getRes(R.id.action_menu_view_in_owner)), selection);
        updateAddLauncherShortcut(menu.findItem(R.id.action_menu_add_shortcut), selection);

        if (isUseMaterial3FlagEnabled()) {
            updateOpen(menu.findItem(getRes(R.id.action_menu_open)), selection);
            MenuItem pasteInto = menu.findItem(getRes(R.id.action_menu_paste_into_folder));
            MenuItem openInNewWindow = menu.findItem(getRes(R.id.action_menu_open_in_new_window));
            updatePasteInto(pasteInto, selection);
            updateOpenInNewWindow(openInNewWindow, selection);
        } else {
            // These menu items are deleted when user_material3 is ON.
            updateSelect(menu.findItem(getRes(R.id.action_menu_select)), selection);
            updateSelectAll(menu.findItem(getRes(R.id.action_menu_select_all)), selection);
            updateDeselectAll(menu.findItem(getRes(R.id.action_menu_deselect_all)), selection);
            updateSort(menu.findItem(getRes(R.id.action_menu_sort)));
        }

        final boolean showCopyToMoveTo =
                mContext.getResources().getBoolean(R.bool.show_copy_to_move_to_menus);
        if (isDesktopUxPhase2FlagEnabled() && !showCopyToMoveTo) {
            updateCopyAndCut(
                    menu.findItem(getRes(R.id.action_menu_copy_to_clipboard)),
                    menu.findItem(getRes(R.id.action_menu_cut_to_clipboard)),
                    selection);
        } else {
            updateMoveTo(menu.findItem(getRes(R.id.action_menu_move_to)), selection);
            updateCopyTo(menu.findItem(getRes(R.id.action_menu_copy_to)), selection);
        }

        if (isZipNgFlagEnabled()) {
            updateExtractHere(menu.findItem(getRes(R.id.action_menu_extract_here)), selection);
            updateBrowse(menu.findItem(getRes(R.id.action_menu_browse)), selection);
        }

        if (isTrashFlowEnabled()) {
            updateMoveToTrash(menu.findItem(getRes(R.id.action_menu_move_to_trash)), selection);
            updateRestoreFromTrash(
                    menu.findItem(getRes(R.id.action_menu_restore_from_trash)), selection);
        }

        if (isUseApprovedDocumentHandlerEnabled()) {
            updateApprovedDocHandlers(menu, selection);
        }
    }

    /**
     * Updates the menu with actions from approved document handlers. This method dynamically adds
     * or removes menu items based on the available approved document handlers and the current
     * selection.
     *
     * @param menu The menu to be updated.
     * @param selection Details about the current selection of documents.
     */
    public void updateApprovedDocHandlers(Menu menu, SelectionDetails selection) {
        if (mApprovedDocMenuController != null) {
            mApprovedDocMenuController.updateApprovedDocHandlerMenus(menu, selection);
        }
    }

    /** @see BaseActivity#onPrepareOptionsMenu */
    public void updateOptionMenu(Menu menu) {
        mOptionMenu = menu;
        updateOptionMenu();
    }

    public void updateOptionMenu() {
        if (mOptionMenu == null) {
            return;
        }
        Menus.disableHiddenItems(mOptionMenu);

        final boolean showCopyToMoveTo =
                mContext.getResources().getBoolean(R.bool.show_copy_to_move_to_menus);
        if (isDesktopUxPhase2FlagEnabled() && !showCopyToMoveTo) {
            updatePaste(mOptionMenu.findItem(getRes(R.id.option_menu_paste_from_clipboard)));
        }
        updateCreateDir(mOptionMenu.findItem(getRes(R.id.option_menu_create_dir)));
        if (isZipNgFlagEnabled()) {
            updateExtractAll(mOptionMenu.findItem(getRes(R.id.option_menu_extract_all)));
        }
        updateSelectAll(mOptionMenu.findItem(getRes(R.id.option_menu_select_all)));
        updateNewWindow(mOptionMenu.findItem(getRes(R.id.option_menu_new_window)));
        updateDebug(mOptionMenu.findItem(getRes(R.id.option_menu_debug)));
        updateInspect(mOptionMenu.findItem(getRes(R.id.option_menu_inspect)));
        updateSort(mOptionMenu.findItem(getRes(R.id.option_menu_sort)));
        updateLauncher(mOptionMenu.findItem(getRes(R.id.option_menu_launcher)));
        updateShowHiddenFiles(mOptionMenu.findItem(getRes(R.id.option_menu_show_hidden_files)));
        updateShowSummaryColumn(mOptionMenu.findItem(R.id.option_show_summary));
        updateAddLauncherShortcut(mOptionMenu.findItem(R.id.option_menu_add_shortcut));

        if (isUseMaterial3FlagEnabled()) {
            updateSettings(mOptionMenu.findItem(getRes(R.id.option_menu_manage_device)));
            updateModePicker(
                    mOptionMenu.findItem(getRes(R.id.sub_menu_grid)),
                    mOptionMenu.findItem(getRes(R.id.sub_menu_list)));
        } else {
            updateSettings(mOptionMenu.findItem(getRes(R.id.option_menu_settings)));
        }

        mSearchManager.updateMenu();
    }

    public void updateSubMenu(Menu menu) {
        // Remove the subMenu when material3 is launched b/379776735.
        if (isUseMaterial3FlagEnabled()) {
            menu = mOptionMenu;
            if (menu == null) {
                return;
            }
        }
        updateModePicker(
                menu.findItem(getRes(R.id.sub_menu_grid)),
                menu.findItem(getRes(R.id.sub_menu_list)));
    }

    public void updateModel(Model model) {}

    /**
     * Called when we needs {@link MenuManager} to ask Android to show context menu for us.
     * {@link MenuManager} can choose to defeat this request.
     *
     * {@link #inflateContextMenuForDocs} and {@link #inflateContextMenuForContainer} are called
     * afterwards when Android asks us to provide the content of context menus, so they're not
     * correct locations to suppress context menus.
     */
    public void showContextMenu(Fragment f, View v, float x, float y) {
        // Register context menu here so long-press doesn't trigger this context floating menu.
        f.registerForContextMenu(v);
        v.showContextMenu(x, y);
        f.unregisterForContextMenu(v);
    }

    /**
     * Called when container context menu needs to be inflated.
     *
     * @param menu context menu from activity or fragment
     * @param inflater the MenuInflater
     * @param selectionDetails selection of files
     */
    public void inflateContextMenuForContainer(
            Menu menu, MenuInflater inflater, SelectionDetails selectionDetails) {
        mContextMenu = menu;
        mContextMenuDetails = selectionDetails;

        inflater.inflate(getRes(R.menu.container_context_menu), menu);
        if (isUseMaterial3FlagEnabled()) {
            MenuCompat.setGroupDividerEnabled(menu, true);
        }
        updateContextMenuForContainer(menu, selectionDetails);
    }

    public void inflateContextMenuForDocs(
            Menu menu, MenuInflater inflater, SelectionDetails selectionDetails) {
        mContextMenu = menu;
        mContextMenuDetails = selectionDetails;

        final boolean hasDir = selectionDetails.containsDirectories();
        final boolean hasFile = selectionDetails.containsFiles();

        if (isUseMaterial3FlagEnabled()) {
            // If no dir or file are selected, do not show any context menu. Note: this is different
            // from "right click at the empty area" which is handled by
            // "inflateContextMenuForContainer" above. This happens especially in Picker where the
            // folders are not selectable, when right clicking on folders, no dir/file is selected.
            if (!hasDir && !hasFile) {
                return;
            }
        }
        // Note: this doesn't throw error if fails, it does nothing.
        assert hasDir || hasFile;
        if (!hasDir) {
            inflater.inflate(getRes(R.menu.file_context_menu), menu);
            if (isUseMaterial3FlagEnabled()) {
                MenuCompat.setGroupDividerEnabled(menu, true);
            }
            updateContextMenuForFiles(menu, selectionDetails);
            return;
        }

        if (!hasFile) {
            inflater.inflate(getRes(R.menu.dir_context_menu), menu);
            if (isUseMaterial3FlagEnabled()) {
                MenuCompat.setGroupDividerEnabled(menu, true);
            }
            updateContextMenuForDirs(menu, selectionDetails);
            return;
        }

        inflater.inflate(getRes(R.menu.mixed_context_menu), menu);
        if (isUseMaterial3FlagEnabled()) {
            MenuCompat.setGroupDividerEnabled(menu, true);
        }
        updateContextMenu(menu, selectionDetails);
    }

    /**
     * Updates the current context menu.
     *
     * <p>This allows the caller to update the current context menu without knowing what is the
     * current menu or what is the current selection.
     */
    public void updateContextMenu() {
        if (mContextMenu == null || mContextMenuDetails == null) {
            return;
        }
        updateContextMenu(mContextMenu, mContextMenuDetails);
    }

    /**
     * Called when user tries to generate a context menu anchored to a file when the selection
     * doesn't contain any folder.
     *
     * @see DirectoryFragment#onCreateContextMenu
     *
     * @param selectionDetails
     *      containsFiles may return false because this may be called when user right clicks on an
     *      unselectable item in pickers
     */
    @VisibleForTesting
    public void updateContextMenuForFiles(Menu menu, SelectionDetails selectionDetails) {
        assert selectionDetails != null;

        MenuItem share = menu.findItem(getRes(R.id.dir_menu_share));
        MenuItem open = menu.findItem(getRes(R.id.dir_menu_open));
        MenuItem openWith = menu.findItem(getRes(R.id.dir_menu_open_with));
        MenuItem rename = menu.findItem(getRes(R.id.dir_menu_rename));
        MenuItem viewInOwner = menu.findItem(getRes(R.id.dir_menu_view_in_owner));

        updateShare(share, selectionDetails);
        updateOpen(open, selectionDetails);
        updateOpenWith(openWith, selectionDetails);
        updateRename(rename, selectionDetails);
        updateViewInOwner(viewInOwner, selectionDetails);

        if (isZipNgFlagEnabled()) {
            updateExtractHere(menu.findItem(getRes(R.id.dir_menu_extract_here)), selectionDetails);
            updateBrowse(menu.findItem(getRes(R.id.dir_menu_browse)), selectionDetails);
        }

        updateContextMenu(menu, selectionDetails);
    }

    /**
     * Called when user tries to generate a context menu anchored to a folder when the selection
     * doesn't contain any file.
     *
     * @see DirectoryFragment#onCreateContextMenu
     *
     * @param selectionDetails
     *      containDirectories may return false because this may be called when user right clicks on
     *      an unselectable item in pickers
     */
    @VisibleForTesting
    public void updateContextMenuForDirs(Menu menu, SelectionDetails selectionDetails) {
        assert selectionDetails != null;

        MenuItem openInNewWindow = menu.findItem(getRes(R.id.dir_menu_open_in_new_window));
        MenuItem rename = menu.findItem(getRes(R.id.dir_menu_rename));
        MenuItem pasteInto = menu.findItem(getRes(R.id.dir_menu_paste_into_folder));

        updateOpenInNewWindow(openInNewWindow, selectionDetails);
        updateRename(rename, selectionDetails);
        updatePasteInto(pasteInto, selectionDetails);

        updateContextMenu(menu, selectionDetails);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Update shared context menu items of both files and folders context menus.
     */
    @VisibleForTesting
    public void updateContextMenu(Menu menu, SelectionDetails selectionDetails) {
        assert selectionDetails != null;

        MenuItem cut = menu.findItem(getRes(R.id.dir_menu_cut_to_clipboard));
        MenuItem copy = menu.findItem(getRes(R.id.dir_menu_copy_to_clipboard));
        MenuItem delete = menu.findItem(getRes(R.id.dir_menu_delete));
        MenuItem inspect = menu.findItem(getRes(R.id.dir_menu_inspect));
        MenuItem addLauncherShortcut = menu.findItem(R.id.dir_menu_add_shortcut);

        if (isTrashFlowEnabled()) {
            MenuItem moveToTrash = menu.findItem(getRes(R.id.dir_menu_move_to_trash));
            MenuItem restoreFromTrash = menu.findItem(getRes(R.id.dir_menu_restore_from_trash));
            updateMoveToTrash(moveToTrash, selectionDetails);
            updateRestoreFromTrash(restoreFromTrash, selectionDetails);
        }

        updateCopyAndCut(copy, cut, selectionDetails);

        if (isUseMaterial3FlagEnabled()) {
            updateDelete(delete, selectionDetails);
            updateInspect(inspect, selectionDetails);
        } else {
            Menus.setEnabledAndVisible(delete, selectionDetails.canDelete());
            Menus.setEnabledAndVisible(inspect, selectionDetails.size() == 1);
        }

        Menus.setEnabledAndVisible(addLauncherShortcut, selectionDetails.size() == 1);

        updateCompress(menu.findItem(getRes(R.id.dir_menu_compress)), selectionDetails);
    }

    /**
     * @see DirectoryFragment#onCreateContextMenu
     *
     * Called when user tries to generate a context menu anchored to an empty pane.
     */
    @VisibleForTesting
    public void updateContextMenuForContainer(Menu menu, SelectionDetails selectionDetails) {
        MenuItem paste = menu.findItem(getRes(R.id.dir_menu_paste_from_clipboard));
        MenuItem selectAll = menu.findItem(getRes(R.id.dir_menu_select_all));
        MenuItem deselectAll = menu.findItem(getRes(R.id.dir_menu_deselect_all));
        MenuItem createDir = menu.findItem(getRes(R.id.dir_menu_create_dir));
        MenuItem inspect = menu.findItem(getRes(R.id.dir_menu_inspect));
        MenuItem addLauncherShortcut = menu.findItem(R.id.dir_menu_add_shortcut);

        updatePaste(paste);
        updateSelectAll(selectAll, selectionDetails);
        updateDeselectAll(deselectAll, selectionDetails);
        updateCreateDir(createDir);
        updateInspect(inspect);
        updateAddLauncherShortcut(addLauncherShortcut);
    }

    /**
     * @see RootsFragment#onCreateContextMenu
     */
    public void updateSidebarItemContextMenu(Menu menu, SidebarEntryItemInfo itemInfo,
            DocumentInfo docInfo) {
        MenuItem eject = menu.findItem(getRes(R.id.root_menu_eject_root));
        MenuItem pasteInto = menu.findItem(getRes(R.id.root_menu_paste_into_folder));
        MenuItem openInNewWindow = menu.findItem(getRes(R.id.root_menu_open_in_new_window));
        MenuItem settings = menu.findItem(getRes(R.id.root_menu_settings));
        MenuItem getInfo = menu.findItem(getRes(R.id.root_menu_inspect));
        MenuItem manageDevice = menu.findItem(getRes(R.id.root_menu_manage_device));

        updateEject(eject, itemInfo);
        updatePasteInto(pasteInto, itemInfo, docInfo);
        updateOpenInNewWindow(openInNewWindow, itemInfo);
        if (isUseMaterial3FlagEnabled()) {
            updateSettings(manageDevice, itemInfo);
            if (settings != null) {
                settings.setVisible(false);
            }
        } else {
            updateSettings(settings, itemInfo);
            if (manageDevice != null) {
                manageDevice.setVisible(false);
            }
        }
        updateInspect(getInfo, itemInfo);
    }

    public abstract void updateKeyboardShortcutsMenu(
            List<KeyboardShortcutGroup> data, IntFunction<String> stringSupplier);

    /**
     * Called on option menu creation to instantiate the job progress item if applicable.
     *
     * @param menu The option menu created.
     */
    public void instantiateJobProgress(Menu menu) {
        // This icon is not shown in the picker.
    }

    protected void updateModePicker(MenuItem grid, MenuItem list) {
        // The order of enabling disabling menu item in wrong order removed accessibility focus.
        if (mState.derivedMode != State.MODE_LIST) {
            Menus.setEnabledAndVisible(list, mState.derivedMode != State.MODE_LIST);
            Menus.setEnabledAndVisible(grid, mState.derivedMode != State.MODE_GRID);
        } else {
            Menus.setEnabledAndVisible(grid, mState.derivedMode != State.MODE_GRID);
            Menus.setEnabledAndVisible(list, mState.derivedMode != State.MODE_LIST);
        }
    }

    protected void updateShowHiddenFiles(MenuItem showHidden) {
        // Don't show "Show/hide hidden files" menu item if trash flow is enabled.
        if (isTrashFlowEnabled() && mState.stack.isTrashRoot()) {
            Menus.setEnabledAndVisible(showHidden, false);
            return;
        }

        Menus.setEnabledAndVisible(showHidden, true);
        showHidden.setTitle(
                mState.shouldShowHiddenFiles()
                        ? getRes(R.string.menu_hide_hidden_files)
                        : getRes(R.string.menu_show_hidden_files));
    }

    protected void updateShowSummaryColumn(@Nullable MenuItem showSummary) {
        if (showSummary == null) {
            if (DEBUG) Log.d(TAG, "show summary menu is null");
            return;
        }
        if (mInjector.getSummaryProviderManager() == null) {
            if (DEBUG) Log.d(TAG, "mSummaryProviderManager is null");
            showSummary.setVisible(false);
            return;
        }
        mInjector.getSummaryProviderManager().updateMenuState(showSummary);
    }

    protected void updateSort(MenuItem sort) {
        Menus.setEnabledAndVisible(sort, true);
    }

    protected void updateDebug(MenuItem debug) {
        Menus.setEnabledAndVisible(debug, mState.debugMode);
    }

    protected void updateSettings(MenuItem settings) {
        Menus.setEnabledAndVisible(settings, false);
    }

    protected void updateSettings(MenuItem settings, SidebarEntryItemInfo itemInfo) {
        Menus.setEnabledAndVisible(settings, false);
    }

    protected void updateEject(MenuItem eject, SidebarEntryItemInfo itemInfo) {
        Menus.setEnabledAndVisible(eject, false);
    }

    protected void updateNewWindow(MenuItem newWindow) {
        Menus.setEnabledAndVisible(newWindow, false);
    }

    protected void updatePaste(MenuItem paste) {
        Menus.setEnabledAndVisible(
                paste, mDirDetails.hasItemsToPaste() && mDirDetails.canCreateDoc());
    }

    protected void updateSelect(MenuItem select, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(select, false);
    }

    protected void updateOpenWith(MenuItem openWith, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(openWith, false);
    }

    protected void updateOpenInNewWindow(
            MenuItem openInNewWindow, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(openInNewWindow, false);
    }

    protected void updateOpenInNewWindow(MenuItem openInNewWindow, SidebarEntryItemInfo itemInfo) {
        Menus.setEnabledAndVisible(openInNewWindow, false);
    }

    protected void updateShare(MenuItem share, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(share, false);
    }

    protected void updateDelete(MenuItem delete, SelectionDetails selectionDetails) {
        boolean enabled = selectionDetails.canDelete();
        Menus.setEnabledAndVisible(delete, enabled);
        // The delete menu item's visibility is tied to the trash flow's status.
        // Since the XML defaults to never showing this action, we must manually make it visible
        // when trash is disabled to give users a direct way to delete items.
        if (!isTrashFlowEnabled()) {
            delete.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        }
    }

    protected void updateRename(MenuItem rename, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(
                rename, !selectionDetails.containsPartialFiles() && selectionDetails.canRename());
    }

    /**
     * This method is called for standard activity option menu as opposed to when there is a
     * selection.
     */
    protected void updateInspect(MenuItem inspect) {
        boolean visible = mFeatures.isInspectorEnabled();
        Menus.setEnabledAndVisible(inspect, visible && mDirDetails.canInspectDirectory());
    }

    protected void updateAddLauncherShortcut(MenuItem addLauncherShortcut) {
        Menus.setEnabledAndVisible(addLauncherShortcut, false);
    }

    /** This method is called for action mode, when a selection exists. */
    protected void updateInspect(MenuItem inspect, SelectionDetails selectionDetails) {
        boolean visible = mFeatures.isInspectorEnabled() && selectionDetails.size() <= 1;
        Menus.setEnabledAndVisible(inspect, visible);
    }

    /**
     * This method is called during a sidebar context menu click with a reference to the
     * item's information.
     */
    protected void updateInspect(MenuItem inspect, SidebarEntryItemInfo itemInfo) {
        Menus.setEnabledAndVisible(inspect, false);
    }

    protected void updateViewInOwner(MenuItem view, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(view, false);
    }

    protected void updateMoveTo(MenuItem moveTo, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(moveTo, false);
    }

    protected void updateCopyTo(MenuItem copyTo, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(copyTo, false);
    }

    protected void updateCopyAndCut(
            MenuItem copy, MenuItem cut, SelectionDetails selectionDetails) {
        final boolean canRestore = isTrashFlowEnabled() && selectionDetails.canRestore();
        final boolean canCopy =
                selectionDetails.size() > 0
                        && !selectionDetails.containsPartialFiles()
                        && !canRestore;
        final boolean canDelete = selectionDetails.canDelete();
        final boolean canCut = canCopy && canDelete;
        if (isSyncStateEnabled() && selectionDetails.containsDocumentsWithUnavailableContent()) {
            // Disable actions as they are not valid right now because the required content is not
            // available.
            Menus.disableAndSetVisibility(copy, /* visible= */ canCopy);
            Menus.disableAndSetVisibility(cut, /* visible= */ canCut);
            return;
        }
        Menus.setEnabledAndVisible(copy, canCopy);
        Menus.setEnabledAndVisible(cut, canCut);
    }

    protected void updateCompress(@NonNull MenuItem it, @NonNull SelectionDetails selection) {
        final boolean enabled =
                mFeatures.isArchiveCreationEnabled()
                        && mDirDetails.canCreateDoc()
                        && !selection.containsPartialFiles()
                        && !selection.canExtract();
        if (enabled && isZipNgFlagEnabled()) it.setTitle(getRes(R.string.menu_zip));
        if (!disableIfContentUnavailable(it, selection, enabled)) {
            Menus.setEnabledAndVisible(it, enabled);
        }
    }

    protected void updateExtractTo(MenuItem extractTo, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(extractTo, false);
    }

    protected void updateExtractHere(@NonNull MenuItem it, @NonNull SelectionDetails selection) {
        Menus.setEnabledAndVisible(it, false);
    }

    protected void updateBrowse(@NonNull MenuItem it, @NonNull SelectionDetails selection) {
        Menus.setEnabledAndVisible(it, false);
    }

    protected void updatePasteInto(MenuItem pasteInto, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(pasteInto, false);
    }

    protected void updatePasteInto(MenuItem pasteInto, SidebarEntryItemInfo itemInfo,
            DocumentInfo docInfo) {
        Menus.setEnabledAndVisible(pasteInto, false);
    }

    protected void updateOpen(MenuItem open, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(open, false);
    }

    protected void updateAddLauncherShortcut(MenuItem addLauncherShortcut,
            SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(addLauncherShortcut, false);
    }

    protected void updateLauncher(MenuItem launcher) {
        Menus.setEnabledAndVisible(launcher, false);
    }

    protected void updateExtractAll(MenuItem it) {
        Menus.setEnabledAndVisible(it, false);
    }

    protected void updateMoveToTrash(MenuItem moveToTrash, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(moveToTrash, false);
    }

    protected void updateRestoreFromTrash(MenuItem restoreFromTrash,
            SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(restoreFromTrash, false);
    }

    protected abstract void updateSelectAll(MenuItem selectAll);

    protected abstract void updateSelectAll(MenuItem selectAll, SelectionDetails selectionDetails);

    protected abstract void updateDeselectAll(
            MenuItem deselectAll, SelectionDetails selectionDetails);

    protected void updateCreateDir(MenuItem createDir) {
        Menus.setEnabledAndVisible(createDir, mDirDetails.canCreateDirectory());
    }

    /**
     * Disable the menu item and return true if the selection contains documents with unavailable
     * content. Otherwise return false.
     *
     * <p>When disabling the item, keep it visible if it is normally enabled, otherwise hide it.
     */
    protected boolean disableIfContentUnavailable(
            MenuItem item, SelectionDetails selectionDetails, boolean normallyEnabled) {
        if (isSyncStateEnabled() && selectionDetails.containsDocumentsWithUnavailableContent()) {
            // Disable action as it is not valid right now because the required content is not
            // available.
            Menus.disableAndSetVisibility(item, /* visible= */ normallyEnabled);
            return true;
        }
        return false;
    }

    /**
     * Access to meta data about the selection.
     */
    public interface SelectionDetails {
        /** Gets the total number of items (files and directories) in the selection. */
        int size();

        /** Returns whether the selection contains at least a directory. */
        boolean containsDirectories();

        /** Returns whether the selection contains at least a file. */
        boolean containsFiles();

        /**
         * Returns whether the selection contains at least a file that has not been fully downloaded
         * yet.
         */
        boolean containsPartialFiles();

        /** Returns whether the selection contains at least a file located in a mounted archive. */
        boolean containsFilesInArchive();

        /**
         * Returns whether the selection contains at least a document that has unavailable content.
         */
        boolean containsDocumentsWithUnavailableContent();

        /**
         * Returns whether the selection contains exactly one file which is also a supported archive
         * type.
         */
        boolean isArchive();

        /**
         * Returns whether the selection is a single file that can be opened by multiple opening
         * apps.
         *
         * This is a necessary signal to enable "open with" on desktop devices since performing
         * "open with" with a file that has a single opening app will automatically open that app
         * (i.e. does not do the expected "open with" behavior).
         */
        boolean hasMultipleOpeningApps();

        // TODO: Update these to express characteristics instead of answering concrete questions,
        // since the answer to those questions is (or can be) activity specific.
        boolean canDelete();

        boolean canRename();

        boolean canPasteInto();

        boolean canExtract();

        boolean canOpen();

        boolean canViewInOwner();

        /**
         * Check whether to show the trash option on the selection
         */
        boolean canTrash();

        /**
         * Check whether to show the restore option on the selection.
         */
        boolean canRestore();

        /**
         * Returns a set of unique MIME types of the selected documents.
         */
        Set<String> mimeTypes();
    }

    public static class DirectoryDetails {
        private final BaseActivity mActivity;

        public DirectoryDetails(BaseActivity activity) {
            mActivity = activity;
        }

        public boolean hasRootSettings() {
            return mActivity.getCurrentRoot().hasSettings();
        }

        public boolean hasItemsToPaste() {
            return false;
        }

        public boolean canCreateDoc() {
            return isInRecents()
                    ? false
                    // This can be called to evaluate the option menu "Paste" visibility, where
                    // the navigation stack is empty, thus the non-null check below.
                    : (mActivity.getCurrentDirectory() != null
                            && mActivity.getCurrentDirectory().isCreateSupported());
        }

        public boolean isInRecents() {
            return mActivity.isInRecents();
        }

        /** Is the current directory the trash root. */
        public boolean isTrashTopLevel() {
            return mActivity.mState.stack.isTrashTopLevel();
        }

        /** Is the current directory showing the contents of an archive? */
        public boolean isInArchive() {
            final DocumentInfo dir = mActivity.getCurrentDirectory();
            return dir != null && ArchivesProvider.AUTHORITY.equals(dir.authority);
        }

        public boolean canCreateDirectory() {
            return mActivity.canCreateDirectory();
        }

        public boolean canInspectDirectory() {
            return mActivity.canInspectDirectory() && !isInRecents() && !isTrashTopLevel();
        }
    }
}
