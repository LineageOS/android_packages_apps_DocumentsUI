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

package com.android.documentsui.manager;

import static com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_UNKNOWN;
import static com.android.documentsui.base.Shared.DEBUG;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import com.android.documentsui.BaseActivity;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.FocusManager;
import com.android.documentsui.MenuManager.DirectoryDetails;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.OperationDialogFragment;
import com.android.documentsui.OperationDialogFragment.DialogType;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.MultiSelectManager;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.sidebar.RootsFragment;
import com.android.documentsui.ui.DialogController;
import com.android.documentsui.ui.Snackbars;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Standalone file management activity.
 */
public class ManageActivity extends BaseActivity implements ActionHandler.Addons {

    public static final String TAG = "FilesActivity";

    private Tuner mTuner;
    private MenuManager mMenuManager;
    private FocusManager mFocusManager;
    private ActionHandler<ManageActivity> mActions;
    private DialogController mDialogs;
    private DocumentClipper mClipper;

    public ManageActivity() {
        super(R.layout.files_activity, TAG);
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mClipper = DocumentsApplication.getDocumentClipper(this);
        mMenuManager = new MenuManager(
                mSearchManager,
                mState,
                new DirectoryDetails(this) {
                    @Override
                    public boolean hasItemsToPaste() {
                        return mClipper.hasItemsToPaste();
                    }
                });

        mTuner = new Tuner(this, mState);
        // Make sure this is done after the RecyclerView and the Model are set up.
        mFocusManager = new FocusManager(getColor(R.color.accent_dark));
        mDialogs = DialogController.create(this);
        mActions = new ActionHandler<>(
                this,
                mState,
                mRoots,
                ProviderExecutor::forAuthority,
                mDialogs,
                mTuner,
                mClipper,
                DocumentsApplication.getClipStore(this));

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
            state.stack = stack;
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

    @Override
    public void onDocumentPicked(DocumentInfo doc, Model model) {
        if (doc.isContainer()) {
            openContainerDocument(doc);
            return;
        }

        if (manageDocument(doc)) {
            return;
        }

        if (previewDocument(doc, model)) {
            return;
        }

        viewDocument(doc);
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
        openContainerDocument(doc);
    }

    public void showChooserForDoc(DocumentInfo doc) {
        assert(!doc.isContainer());

        if (manageDocument(doc)) {
            Log.w(TAG, "Open with is not yet supported for managed doc.");
            return;
        }

        Intent intent = Intent.createChooser(buildViewIntent(doc), null);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Snackbars.makeSnackbar(
                    this, R.string.toast_no_application, Snackbar.LENGTH_SHORT).show();
        }
    }

    // TODO: Move to ActionHandler.
    @Override
    public boolean viewDocument(DocumentInfo doc) {
        if (doc.isPartial()) {
            Log.w(TAG, "Can't view partial file.");
            return false;
        }

        if (doc.isContainer()) {
            openContainerDocument(doc);
            return true;
        }

        // this is a redundant check.
        if (manageDocument(doc)) {
            return true;
        }

        // Fall back to traditional VIEW action...
        Intent intent = buildViewIntent(doc);
        if (DEBUG && intent.getClipData() != null) {
            Log.d(TAG, "Starting intent w/ clip data: " + intent.getClipData());
        }

        try {
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {
            Snackbars.makeSnackbar(
                    this, R.string.toast_no_application, Snackbar.LENGTH_SHORT).show();
        }
        return false;
    }

    // TODO: Move to ActionHandler.
    @Override
    public boolean previewDocument(DocumentInfo doc, Model model) {
        if (doc.isPartial()) {
            Log.w(TAG, "Can't view partial file.");
            return false;
        }

        Intent intent = new QuickViewIntentBuilder(
                getPackageManager(), getResources(), doc, model).build();

        if (intent != null) {
            // TODO: un-work around issue b/24963914. Should be fixed soon.
            try {
                startActivity(intent);
                return true;
            } catch (SecurityException e) {
                // Carry on to regular view mode.
                Log.e(TAG, "Caught security error: " + e.getLocalizedMessage());
            }
        }

        return false;
    }

    private boolean manageDocument(DocumentInfo doc) {
        if (isManagedDocument(doc)) {
            // First try managing the document; we expect manager to filter
            // based on authority, so we don't grant.
            Intent manage = new Intent(DocumentsContract.ACTION_MANAGE_DOCUMENT);
            manage.setData(doc.derivedUri);
            try {
                startActivity(manage);
                return true;
            } catch (ActivityNotFoundException ex) {
                // Fall back to regular handling.
            }
        }

        return false;
    }

    private boolean isManagedDocument(DocumentInfo doc) {
        // Anything on downloads goes through the back through downloads manager
        // (that's the MANAGE_DOCUMENT bit).
        // This is done for two reasons:
        // 1) The file in question might be a failed/queued or otherwise have some
        //    specialized download handling.
        // 2) For APKs, the download manager will add on some important security stuff
        //    like origin URL.
        // All other files not on downloads, event APKs, would get no benefit from this
        // treatment, thusly the "isDownloads" check.

        // Launch MANAGE_DOCUMENTS only for the root level files, so it's not called for
        // files in archives. Also, if the activity is already browsing a ZIP from downloads,
        // then skip MANAGE_DOCUMENTS.
        // Oh, and only launch for APKs.
        final boolean isViewing = Intent.ACTION_VIEW.equals(getIntent().getAction());
        final boolean isInArchive = mState.stack.size() > 1;
        return getCurrentRoot().isDownloads()
                && "application/vnd.android.package-archive".equals(doc.mimeType)
                && !isInArchive
                && !isViewing;
    }

    private Intent buildViewIntent(DocumentInfo doc) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(doc.derivedUri, doc.mimeType);

        // Downloads has traditionally added the WRITE permission
        // in the TrampolineActivity. Since this behavior is long
        // established, we set the same permission for non-managed files
        // This ensures consistent behavior between the Downloads root
        // and other roots.
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (doc.isWriteSupported()) {
            flags |= Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        }
        intent.setFlags(flags);

        return intent;
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
                dir = getDirectoryFragment();
                if (dir != null) {
                    dir.cutSelectedToClipboard();
                }
                return true;
            case KeyEvent.KEYCODE_C:
                dir = getDirectoryFragment();
                if (dir != null) {
                    dir.copySelectedToClipboard();
                }
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
    public FragmentTuner getFragmentTuner(Model model, boolean mSearchMode) {
        return mTuner.reset(model, mSearchMode);
    }

    @Override
    public FocusManager getFocusManager(RecyclerView view, Model model) {
        return mFocusManager.reset(view, model);
    }

    @Override
    public MenuManager getMenuManager() {
        return mMenuManager;
    }

    @Override
    public ActionHandler<ManageActivity> getActionHandler(
            Model model, MultiSelectManager selectionMgr) {

        // provide our friend, RootsFragment, early access to this special feature!
        if (model == null || selectionMgr == null) {
            assert(model == null);
            assert(selectionMgr == null);
            return mActions;
        }
        return mActions.reset(model, selectionMgr);
    }

    @Override
    public DialogController getDialogController() {
        return mDialogs;
    }
}
