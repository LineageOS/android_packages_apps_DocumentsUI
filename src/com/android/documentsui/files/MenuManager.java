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

package com.android.documentsui.files;

import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;
import static com.android.documentsui.util.FlagUtils.isUseApprovedDocumentHandlerEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUseNewOpenWithEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.content.res.Resources;
import android.net.Uri;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.selection.SelectionTracker;

import com.android.documentsui.Injector;
import com.android.documentsui.JobPanelController;
import com.android.documentsui.R;
import com.android.documentsui.approveddochandlers.ApprovedDocHandlers;
import com.android.documentsui.approveddochandlers.ApprovedDocMenuController;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.LookupApplicationName;
import com.android.documentsui.base.Menus;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.queries.SearchViewManager;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

public final class MenuManager extends com.android.documentsui.MenuManager {

    private final SelectionTracker<String> mSelectionManager;
    private final Lookup<String, Uri> mUriLookup;
    private final LookupApplicationName mAppNameLookup;
    @Nullable private JobPanelController mJobPanelController;
    public MenuManager(
            Features features,
            SearchViewManager searchManager,
            State displayState,
            DirectoryDetails dirDetails,
            Context context,
            SelectionTracker<String> selectionManager,
            LookupApplicationName appNameLookup,
            Lookup<String, Uri> uriLookup,
            IntSupplier filesCountSupplier,
            Injector<?> injector,
            @Nullable ApprovedDocMenuController approvedDocMenuController) {

        super(
                searchManager,
                displayState,
                dirDetails,
                filesCountSupplier,
                context,
                features,
                injector,
                approvedDocMenuController);

        mSelectionManager = selectionManager;
        mAppNameLookup = appNameLookup;
        mUriLookup = uriLookup;
    }

    // TODO(b/378011512): Remove and merge with constructor once visual signals flag is removed.
    public void setJobPanelController(JobPanelController controller) {
        mJobPanelController = controller;
    }

    @Override
    public void updateContextMenu(Menu menu, SelectionDetails selectionDetails) {
        super.updateContextMenu(menu, selectionDetails);
        if (isUseApprovedDocumentHandlerEnabled()) {
            updateApprovedDocHandlers(menu, selectionDetails);
        }
    }

    @Override
    public void updateKeyboardShortcutsMenu(
            List<KeyboardShortcutGroup> data, IntFunction<String> stringSupplier) {
        KeyboardShortcutGroup group =
                new KeyboardShortcutGroup(stringSupplier.apply(getRes(R.string.app_label)));
        group.addItem(
                new KeyboardShortcutInfo(
                        stringSupplier.apply(getRes(R.string.menu_cut_to_clipboard)),
                        KeyEvent.KEYCODE_X,
                        KeyEvent.META_CTRL_ON));
        group.addItem(
                new KeyboardShortcutInfo(
                        stringSupplier.apply(getRes(R.string.menu_copy_to_clipboard)),
                        KeyEvent.KEYCODE_C,
                        KeyEvent.META_CTRL_ON));
        group.addItem(
                new KeyboardShortcutInfo(
                        stringSupplier.apply(getRes(R.string.menu_paste_from_clipboard)),
                        KeyEvent.KEYCODE_V,
                        KeyEvent.META_CTRL_ON));
        group.addItem(
                new KeyboardShortcutInfo(
                        stringSupplier.apply(getRes(R.string.menu_create_dir)),
                        KeyEvent.KEYCODE_E,
                        KeyEvent.META_CTRL_ON));
        group.addItem(
                new KeyboardShortcutInfo(
                        stringSupplier.apply(getRes(R.string.menu_select_all)),
                        KeyEvent.KEYCODE_A,
                        KeyEvent.META_CTRL_ON));
        group.addItem(
                new KeyboardShortcutInfo(
                        stringSupplier.apply(getRes(R.string.menu_new_window)),
                        KeyEvent.KEYCODE_N,
                        KeyEvent.META_CTRL_ON));
        if (isUseMaterial3FlagEnabled()) {
            group.addItem(
                    new KeyboardShortcutInfo(
                            stringSupplier.apply(getRes(R.string.menu_refresh)),
                            KeyEvent.KEYCODE_R,
                            KeyEvent.META_CTRL_ON));
            group.addItem(
                    new KeyboardShortcutInfo(
                            stringSupplier.apply(getRes(R.string.menu_rename)),
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.META_CTRL_ON));
        }
        data.add(group);
    }

    @Override
    public void instantiateJobProgress(Menu menu) {
        if (mJobPanelController == null) {
            return;
        }
        mJobPanelController.setMenuItem(menu.findItem(getRes(R.id.option_menu_job_progress)));
    }

    @Override
    protected void updateSettings(MenuItem settings, SidebarEntryItemInfo itemInfo) {
        Menus.setEnabledAndVisible(settings, itemInfo.hasSettings());
    }

    @Override
    protected void updateEject(MenuItem eject, SidebarEntryItemInfo itemInfo) {
        Menus.setEnabledAndVisible(eject, itemInfo.supportsEject() && !itemInfo.isEjecting());
    }

    @Override
    protected void updateSettings(MenuItem settings) {
        boolean enabled = mDirDetails.hasRootSettings();
        Menus.setEnabledAndVisible(settings, enabled);
    }

    @Override
    protected void updateNewWindow(MenuItem newWindow) {
        Menus.setEnabledAndVisible(
                newWindow, !isUseMaterial3FlagEnabled() || !mDirDetails.isInArchive());
    }

    @Override
    protected void updateOpenWith(MenuItem openWith, SelectionDetails selectionDetails) {
        boolean enabled = selectionDetails.canOpen();
        // When desktop file handling is enabled, "open with" opens ResolverActivity.
        // Currently ResolverActivity automatically opens the app when it is the only option for the
        // user. This breaks the expected behaviour for "open with" so we hide "open with".
        // With the new open-with this issue is resolved, so we only disable open-with when desktop
        // file handling is enabled but new open-with is disabled.
        if (isDesktopFileHandlingFlagEnabled() && !isUseNewOpenWithEnabled()) {
            enabled = enabled && selectionDetails.hasMultipleOpeningApps();
        }

        if (!disableIfContentUnavailable(openWith, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(openWith, enabled);
        }
    }

    @Override
    protected void updateOpen(MenuItem open, SelectionDetails selectionDetails) {
        boolean enabled = isDesktopFileHandlingFlagEnabled() && selectionDetails.canOpen();
        if (!disableIfContentUnavailable(open, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(open, enabled);
        }
    }

    @Override
    protected void updateOpenInNewWindow(
            MenuItem openInNewWindow, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(
                openInNewWindow,
                selectionDetails.size() == 1
                        && !selectionDetails.containsPartialFiles()
                        && selectionDetails.containsDirectories());
    }

    @Override
    protected void updateOpenInNewWindow(MenuItem openInNewWindow, SidebarEntryItemInfo itemInfo) {
        assert openInNewWindow.isVisible() && openInNewWindow.isEnabled();
    }

    @Override
    protected void updateMoveTo(MenuItem moveTo, SelectionDetails selectionDetails) {
        boolean enabled = !selectionDetails.containsPartialFiles() && selectionDetails.canDelete();
        if (!disableIfContentUnavailable(moveTo, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(moveTo, enabled);
        }
    }

    @Override
    protected void updateCopyTo(MenuItem copyTo, SelectionDetails selectionDetails) {
        boolean enabled =
                !selectionDetails.containsPartialFiles()
                        && !selectionDetails.canExtract()
                        && !selectionDetails.canRestore();
        if (!disableIfContentUnavailable(copyTo, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(copyTo, enabled);
        }
    }

    @Override
    protected void updateExtractTo(MenuItem extractTo, SelectionDetails selectionDetails) {
        boolean enabled = selectionDetails.canExtract();
        if (isZipNgFlagEnabled()) extractTo.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
        if (!disableIfContentUnavailable(extractTo, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(extractTo, enabled);
        }
    }

    @Override
    protected void updateExtractHere(@NonNull MenuItem it, @NonNull SelectionDetails selection) {
        boolean enabled = selection.isArchive() && mDirDetails.canCreateDirectory();
        if (!disableIfContentUnavailable(it, selection, enabled)) {
            Menus.setEnabledAndVisible(it, enabled);
        }
    }

    @Override
    protected void updateBrowse(@NonNull MenuItem it, @NonNull SelectionDetails selection) {
        boolean enabled = selection.isArchive() && !mDirDetails.isInArchive();
        if (!disableIfContentUnavailable(it, selection, enabled)) {
            Menus.setEnabledAndVisible(it, enabled);
        }
    }

    @Override
    protected void updatePasteInto(MenuItem pasteInto, SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(
                pasteInto,
                selectionDetails.containsDirectories()
                        && selectionDetails.canPasteInto()
                        && mDirDetails.hasItemsToPaste());
    }

    @Override
    protected void updatePasteInto(MenuItem pasteInto, SidebarEntryItemInfo itemInfo,
            DocumentInfo docInfo) {
        Menus.setEnabledAndVisible(pasteInto, itemInfo.supportsCreate()
                && docInfo != null
                && docInfo.isCreateSupported()
                && mDirDetails.hasItemsToPaste());
    }

    @Override
    protected void updateExtractAll(MenuItem it) {
        Menus.setEnabledAndVisible(it, mDirDetails.isInArchive());
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll) {
        // Only show the "Select all" option if there are files to be selected in the directory.
        Menus.setEnabledAndVisible(selectAll, mFilesCountSupplier.getAsInt() > 0);
    }

    @Override
    protected void updateSelectAll(MenuItem selectAll, SelectionDetails selectionDetails) {
        final boolean visible =
                mFilesCountSupplier.getAsInt() > 0
                        && selectionDetails.size() < mFilesCountSupplier.getAsInt();
        Menus.setEnabledAndVisible(selectAll, visible);
    }

    @Override
    protected void updateDeselectAll(MenuItem deselectAll, SelectionDetails selectionDetails) {
        final boolean visible =
                mFilesCountSupplier.getAsInt() > 0
                        && selectionDetails.size() == mFilesCountSupplier.getAsInt();
        Menus.setEnabledAndVisible(deselectAll, visible);
    }

    @Override
    protected void updateShare(MenuItem share, SelectionDetails selectionDetails) {
        boolean enabled = !selectionDetails.containsDirectories()
                && !selectionDetails.containsPartialFiles()
                && !selectionDetails.canExtract()
                && !selectionDetails.canRestore();
        if (!disableIfContentUnavailable(share, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(share, enabled);
        }
    }

    /**
     * This method is called during a sidebar context menu click with a reference to the
     * item's information.
     */
    @Override
    protected void updateInspect(MenuItem inspect, SidebarEntryItemInfo itemInfo) {
        Menus.setEnabledAndVisible(inspect, itemInfo.supportsInspect());
    }

    @Override
    protected void updateAddLauncherShortcut(MenuItem addLauncherShortcut) {
        Menus.setEnabledAndVisible(addLauncherShortcut, mDirDetails.canCreateDirectory());
    }

    @Override
    protected void updateAddLauncherShortcut(MenuItem addLauncherShortcut,
            SelectionDetails selectionDetails) {
        Menus.setEnabledAndVisible(addLauncherShortcut, selectionDetails.size() <= 1);
    }

    @Override
    protected void updateViewInOwner(MenuItem view, SelectionDetails selectionDetails) {
        if (selectionDetails.canViewInOwner() &&
                mSelectionManager.getSelection().iterator().hasNext()) {
            Menus.setEnabledAndVisible(view, true);
            Resources res = mContext.getResources();
            String selectedModelId = mSelectionManager.getSelection().iterator().next();
            Uri selectedUri = mUriLookup.lookup(selectedModelId);
            String appName = mAppNameLookup.getApplicationName(UserId.DEFAULT_USER,
                    selectedUri.getAuthority());
            String title = res.getString(getRes(R.string.menu_view_in_owner), appName);
            view.setTitle(title);
        } else {
            Menus.setEnabledAndVisible(view, false);
        }
    }

    @Override
    protected void updateLauncher(MenuItem launcher) {
        Menus.setEnabledAndVisible(launcher, mState.debugMode);
        launcher.setTitle(Shared.isLauncherEnabled(mContext)
                ? "Hide launcher icon" : "Show launcher icon");
    }

    @Override
    protected void updateMoveToTrash(MenuItem moveToTrash, SelectionDetails selectionDetails) {
        final boolean enabled = selectionDetails.canTrash() && isTrashFlowEnabled();
        if (!disableIfContentUnavailable(moveToTrash, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(moveToTrash, enabled);
        }
    }

    @Override
    protected void updateRestoreFromTrash(MenuItem restoreFromTrash,
            SelectionDetails selectionDetails) {
        final boolean enabled = selectionDetails.canRestore() && isTrashFlowEnabled();
        if (!disableIfContentUnavailable(restoreFromTrash, selectionDetails, enabled)) {
            Menus.setEnabledAndVisible(restoreFromTrash, enabled);
        }
    }
}
