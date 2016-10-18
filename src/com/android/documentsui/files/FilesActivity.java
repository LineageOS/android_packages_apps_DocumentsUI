/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_UNKNOWN;
import static com.android.documentsui.base.Shared.DEBUG;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.documentsui.ActionModeController;
import com.android.documentsui.ActivityConfig;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.DragShadowBuilder;
import com.android.documentsui.MenuManager.DirectoryDetails;
import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.OperationDialogFragment;
import com.android.documentsui.OperationDialogFragment.DialogType;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.ScopedPreferences;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.dirlist.DocumentsAdapter;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionManager.SelectionPredicate;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.sidebar.RootsFragment;
import com.android.documentsui.ui.DialogController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Standalone file management activity.
 */
public class FilesActivity
        extends BaseActivity<ActionHandler<FilesActivity>> implements ActionHandler.Addons {

    private static final String TAG = "FilesActivity";
    private static final String PREFERENCES_SCOPE = "files";

    private final Config mConfig = new Config();

    private ScopedPreferences mPrefs;
    private SelectionManager mSelectionMgr;
    private MenuManager mMenuManager;
    private DialogController mDialogs;
    private DocumentClipper mClipper;
    private ActionModeController mActionModeController;
    private ActivityInputHandler mActivityInputHandler;
    private DragShadowBuilder mShadowBuilder;

    public FilesActivity() {
        super(R.layout.files_activity, TAG);
    }

    @Override
    public void onCreate(Bundle icicle) {

        // must be initialized before calling super.onCreate because prefs
        // are used in State initialization.
        mPrefs = ScopedPreferences.create(this, PREFERENCES_SCOPE);

        super.onCreate(icicle);

        mClipper = DocumentsApplication.getDocumentClipper(this);
        mSelectionMgr = new SelectionManager(SelectionManager.MODE_MULTIPLE);
        mMenuManager = new MenuManager(
                mSearchManager,
                mState,
                new DirectoryDetails(this) {
                    @Override
                    public boolean hasItemsToPaste() {
                        return mClipper.hasItemsToPaste();
                    }
                });
        mDialogs = DialogController.create(this, getMessages());

        mShadowBuilder = new DragShadowBuilder(this);
        mActionModeController = new ActionModeController(
                this,
                mSelectionMgr,
                mMenuManager,
                getMessages());

        mActions = new ActionHandler<>(
                this,
                mState,
                mRoots,
                mDocs,
                mFocusManager,
                mSelectionMgr,
                mSearchManager,
                ProviderExecutor::forAuthority,
                mActionModeController,
                mDialogs,
                mConfig,
                mClipper,
                DocumentsApplication.getClipStore(this));

        mActivityInputHandler = new ActivityInputHandler(mActions::deleteSelectedDocuments);

        RootsFragment.show(getFragmentManager(), null);

        final Intent intent = getIntent();

        mActions.initLocation(intent);
        presentFileErrors(icicle, intent);
    }

    private void presentFileErrors(Bundle icicle, final Intent intent) {
        final @DialogType int dialogType = intent.getIntExtra(
                FileOperationService.EXTRA_DIALOG_TYPE, DIALOG_TYPE_UNKNOWN);
        // DialogFragment takes care of restoring the dialog on configuration change.
        // Only show it manually for the first time (icicle is null).
        if (icicle == null && dialogType != DIALOG_TYPE_UNKNOWN) {
            final int opType = intent.getIntExtra(
                    FileOperationService.EXTRA_OPERATION_TYPE,
                    FileOperationService.OPERATION_COPY);
            final ArrayList<DocumentInfo> srcList =
                    intent.getParcelableArrayListExtra(FileOperationService.EXTRA_SRC_LIST);
            OperationDialogFragment.show(
                    getFragmentManager(),
                    dialogType,
                    srcList,
                    mState.stack,
                    opType);
        }
    }

    @Override
    public void includeState(State state) {
        final Intent intent = getIntent();

        state.action = State.ACTION_BROWSE;
        state.allowMultiple = true;

        // Options specific to the DocumentsActivity.
        assert(!intent.hasExtra(Intent.EXTRA_LOCAL_ONLY));

        final DocumentStack stack = intent.getParcelableExtra(Shared.EXTRA_STACK);
        if (stack != null) {
            state.stack.reset(stack);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        // This check avoids a flicker from "Recents" to "Home".
        // Only update action bar at this point if there is an active
        // serach. Why? Because this avoid an early (undesired) load of
        // the recents root...which is the default root in other activities.
        // In Files app "Home" is the default, but it is loaded async.
        // update will be called once Home root is loaded.
        // Except while searching we need this call to ensure the
        // search bits get layed out correctly.
        if (mSearchManager.isSearching()) {
            mNavigator.update();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        final RootInfo root = getCurrentRoot();

        // If we're browsing a specific root, and that root went away, then we
        // have no reason to hang around.
        // TODO: Rather than just disappearing, maybe we should inform
        // the user what has happened, let them close us. Less surprising.
        if (mRoots.getRootBlocking(root.authority, root.rootId) == null) {
            finish();
        }
    }

    @Override
    public String getDrawerTitle() {
        Intent intent = getIntent();
        return (intent != null && intent.hasExtra(Intent.EXTRA_TITLE))
                ? intent.getStringExtra(Intent.EXTRA_TITLE)
                : getTitle().toString();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mMenuManager.updateOptionMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_create_dir:
                assert(canCreateDirectory());
                showCreateDirectoryDialog();
                break;
            case R.id.menu_new_window:
                mActions.openInNewWindow(mState.stack);
                break;
            case R.id.menu_paste_from_clipboard:
                DirectoryFragment dir = getDirectoryFragment();
                if (dir != null) {
                    dir.pasteFromClipboard();
                }
                break;
            case R.id.menu_settings:
                mActions.openSettings(getCurrentRoot());
                break;
            default:
                return super.onOptionsItemSelected(item);
        }
        return true;
    }

    @Override
    public void onProvideKeyboardShortcuts(
            List<KeyboardShortcutGroup> data, Menu menu, int deviceId) {
        getMenuManager().updateKeyboardShortcutsMenu(data, this::getString);
    }

    @Override
    public void refreshDirectory(@AnimationType int anim) {
        final FragmentManager fm = getFragmentManager();
        final RootInfo root = getCurrentRoot();
        final DocumentInfo cwd = getCurrentDirectory();

        assert(!mSearchManager.isSearching());

        if (cwd == null) {
            DirectoryFragment.showRecentsOpen(fm, anim);
        } else {
            // Normal boring directory
            DirectoryFragment.showDirectory(fm, root, cwd, anim);
        }
    }

    @Override
    public void onDocumentsPicked(List<DocumentInfo> docs) {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated use {@link ActionHandler#onDocumentPicked(DocumentInfo)}
     * @param doc
     */
    @Override
    public void onDocumentPicked(DocumentInfo doc) {
        mActions.onDocumentPicked(doc);
    }

    @Override
    public void onDirectoryCreated(DocumentInfo doc) {
        assert(doc.isDirectory());
        mFocusManager.focusDocument(doc.documentId);
    }

    @Override
    public void springOpenDirectory(DocumentInfo doc) {
        assert(doc.isContainer());
        assert(!doc.isArchive());
        mActions.openContainerDocument(doc);
    }

    @CallSuper
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mActivityInputHandler.onKeyDown(keyCode, event) ? true
                : super.onKeyDown(keyCode, event);
    }

    @Override
    public DragShadowBuilder getShadowBuilder() {
        return mShadowBuilder;
    }

    @Override
    public boolean onKeyShortcut(int keyCode, KeyEvent event) {
        DirectoryFragment dir;
        // TODO: All key events should be statically bound using alphabeticShortcut.
        // But not working.
        switch (keyCode) {
            case KeyEvent.KEYCODE_A:
                dir = getDirectoryFragment();
                if (dir != null) {
                    dir.selectAllFiles();
                }
                return true;
            case KeyEvent.KEYCODE_X:
                mActions.cutToClipboard();
                return true;
            case KeyEvent.KEYCODE_C:
                mActions.copyToClipboard();
                return true;
            case KeyEvent.KEYCODE_V:
                dir = getDirectoryFragment();
                if (dir != null) {
                    dir.pasteFromClipboard();
                }
                return true;
            default:
                return super.onKeyShortcut(keyCode, event);
        }
    }

    @Override
    public void onTaskFinished(Uri... uris) {
        if (DEBUG) Log.d(TAG, "onFinished() " + Arrays.toString(uris));

        final Intent intent = new Intent();
        if (uris.length == 1) {
            intent.setData(uris[0]);
        } else if (uris.length > 1) {
            final ClipData clipData = new ClipData(
                    null, mState.acceptMimes, new ClipData.Item(uris[0]));
            for (int i = 1; i < uris.length; i++) {
                clipData.addItem(new ClipData.Item(uris[i]));
            }
            intent.setClipData(clipData);
        }

        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    @Override
    public ActivityConfig getActivityConfig() {
        return mConfig;
    }

    @Override
    public ScopedPreferences getScopedPreferences() {
        return mPrefs;
    }

    @Override
    public SelectionManager getSelectionManager(
            DocumentsAdapter adapter, SelectionPredicate canSetState) {
        return mSelectionMgr.reset(adapter, canSetState);
    }

    @Override
    public MenuManager getMenuManager() {
        return mMenuManager;
    }

    @Override
    public final ActionModeController getActionModeController(
            SelectionDetails selectionDetails, EventHandler<MenuItem> menuItemClicker, View view) {
        return mActionModeController.reset(selectionDetails, menuItemClicker, view);
    }

    @Override
    public ActionHandler<FilesActivity> getActionHandler(Model model, boolean searchMode) {

        // provide our friend, RootsFragment, early access to this special feature!
        if (model == null) {
            return mActions;
        }

        return mActions.reset(model, searchMode);
    }

    @Override
    public DialogController getDialogController() {
        return mDialogs;
    }
}
