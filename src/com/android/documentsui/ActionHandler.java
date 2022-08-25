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

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.view.DragEvent;

import androidx.annotation.IntDef;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

import com.android.documentsui.OperationDialogFragment.DialogType;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.ShortcutInfo;
import com.android.documentsui.base.SidebarEntryItemInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.dirlist.SummariesViewModel;
import com.android.documentsui.services.JobProgress;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

/**
 * Interface to handle action for document.
 */
public interface ActionHandler {

    @IntDef({
        VIEW_TYPE_NONE,
        VIEW_TYPE_REGULAR,
        VIEW_TYPE_PREVIEW
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ViewType {}
    public static final int VIEW_TYPE_NONE = 0;
    public static final int VIEW_TYPE_REGULAR = 1;
    public static final int VIEW_TYPE_PREVIEW = 2;

    void onActivityResult(int requestCode, int resultCode, Intent data);

    /** Binds the ActionHandler to the SummariesViewModel and observes it for summary updates. */
    void bindSummariesViewModel(LifecycleOwner owner, SummariesViewModel summariesViewModel);

    void openSettings(RootInfo root);

    /**
     * Drops documents on a root.
     */
    boolean dropOn(DragEvent event, RootInfo root);

    /**
     * Drops documents on a shortcut sidebar entry.
     */
    boolean dropOn(DragEvent event, ShortcutInfo shortcut);

    /**
     * Gets the current selected user.
     */
    UserId getSelectedUser();

    /**
     * Attempts to eject the identified root. Returns a boolean answer to listener.
     */
    void ejectRoot(RootInfo root, BooleanConsumer listener);

    /**
     * Attempts to fetch the DocumentInfo for the supplied authority, documentId and userId.
     * Returns the DocumentInfo to the callback. If the task times out, callback will be called
     * with null DocumentInfo. Supply {@link TimeoutTask#DEFAULT_TIMEOUT} if you don't want to the
     * task to ever time out.
     */
    void getDocument(String authority, String documentId, UserId userId, int timeout,
            Consumer<DocumentInfo> callback);

    /**
     * Attempts to refresh the given DocumentInfo, which should be at the top of the state stack.
     * Returns a boolean answer to the callback, given by {@link ContentProvider#refresh}.
     */
    void refreshDocument(DocumentInfo doc, BooleanConsumer callback);


    /**
     * Attempts to start the authentication process caused by
     * {@link android.app.AuthenticationRequiredException}.
     */
    void startAuthentication(PendingIntent intent);

    void requestQuietModeDisabled(RootInfo info, UserId userId);

    void showAppDetails(ResolveInfo info, UserId userId);

    void openRoot(RootInfo root);

    /**
     * Opens the contents of a shortcut (through a sidebar entry click).
     */
    void openShortcut(ShortcutInfo shortcut);

    void openRoot(ResolveInfo app, UserId userId);

    void loadRoot(Uri uri, UserId userId);

    void loadCrossProfileRoot(RootInfo info, UserId selectedUser);

    void loadFirstRoot(Uri uri);

    void openSelectedInNewWindow();

    void openInNewWindow(DocumentStack path, ShortcutInfo shortcut);

    /**
     * Pastes the selected items into a sidebar item entry.
     * @param itemInfo - the destination sidebar item entry
     */
    void pasteIntoFolder(SidebarEntryItemInfo itemInfo);

    void selectAllFiles();

    /**
     * Attempts to deselect all selected files.
     */
    void deselectAllFiles();

    /** Toggles the focused item's selection. Does nothing if no item is focused. */
    void toggleFocusedItemSelection();

    void showCreateDirectoryDialog();

    void showPreview(DocumentInfo doc);

    @Nullable DocumentInfo renameDocument(String name, DocumentInfo document);

    /**
     * If container, then opens the container, otherwise views using the specified type of view.
     * If the primary view type is unavailable, then fallback to the alternative type of view.
     */
    boolean openItem(ItemDetails<String> doc, @ViewType int type, @ViewType int fallback);

    /**
     * Similar to openItem but takes DocumentInfo instead of DocumentItemDetails and uses
     * VIEW_TYPE_VIEW with no fallback.
     */
    void openDocumentViewOnly(DocumentInfo doc);

    /**
     * This is called when user hovers over a doc for enough time during a drag n' drop, to open a
     * folder that accepts drop. We should only open a container that's not an archive, since archives
     * do not accept dropping.
     */
    void springOpenDirectory(DocumentInfo doc);

    /**
     * Replaces the existing stack with the given stack.
     */
    void jumpToDirectory(DocumentStack stack);

    void showChooserForDoc(DocumentInfo doc);

    void openRootDocument(@Nullable DocumentInfo rootDoc);

    void openContainerDocument(DocumentInfo doc);

    boolean previewItem(ItemDetails<String> doc);

    void cutToClipboard();

    void copyToClipboard();

    /**
     * Show delete dialog
     */
    void showDeleteDialog();

    /**
     * Delete the selected document(s)
     */
    void deleteSelectedDocuments(List<DocumentInfo> docs, DocumentInfo srcParent);

    /**
     * Trash the selected document(s)
     */
    void trashSelectedDocuments();

    /**
     * Restore the selected document(s)
     */
    void restoreSelectedDocumentsFromTrash(List<DocumentInfo> docs);

    void shareSelectedDocuments();

    /**
     * Called when initial activity setup is complete. Implementations
     * should override this method to set the initial location of the
     * app.
     */
    void initLocation(Intent intent);

    void registerDisplayStateChangedListener(Runnable l);
    void unregisterDisplayStateChangedListener(Runnable l);

    void loadDocumentsForCurrentStack();

    void viewInOwner();

    void setDebugMode(boolean enabled);
    void showDebugMessage();

    /**
     * Shows an alert dialog informing the user about the files that failed during a file operation.
     */
    void showFileOperationDetailsDialog(@DialogType int dialogType, JobProgress jobProgress);

    void showSortDialog();

    /**
     * Switch launch icon show/hide status.
     */
    void switchLauncherIcon();

    /**
     * Creates an intent to be sent to the approved app.
     * @param app - The component name of an approved app to send the document(s) to..
     * @return intent to be sent to the approved app.
     */
    @Nullable Intent createApprovedHandlerIntent(ComponentName app);

    /**
     * Shows a dialog to add file shortcut to launcher.
     */
    void showAddShortcutDialog(DocumentInfo document);

    /**
     * Allow action handler to be initialized in a new scope.
     * @return this
     */
    <T extends ActionHandler> T reset(ContentLock contentLock);

    /**
     * Runs `LoadDocStackTask` to fetch the DocumentStack of the provided document.
     * @param uri - The URI of the document
     * @param userId - User ID of the current user
     * @param callback - Callback sequence after the document stack task has finished executing
     */
    void loadDocument(Uri uri, UserId userId, LoadDocStackTask.LoadDocStackCallback callback);

    /**
     * Shows a confirmation dialog to the user before emptying the trash. This dialog should clarify
     * that this action is irreversible and will permanently delete all items.
     */
    void showEmptyTrashConfirmationDialog();

    /**
     * Permanently deletes all documents that are currently in the trash. This action is typically
     * triggered after the user confirms the action, for instance, via the dialog shown by {@link
     * #showEmptyTrashConfirmationDialog()}.
     */
    void permanentlyDeleteTrashDocuments();

    /**
     * Retrieves the list of shortcuts for the given user and compares the shortcuts'
     * URIs against the provided collection of URIs. If there is a match, the file operation
     * should be blocked and a dialog should be shown to inform the user of this block.
     * @return boolean value on whether or not the file operation should be blocked.
     */
    boolean blockOperationForShortcuts(Collection<Uri> uris, UserId userId);
}
