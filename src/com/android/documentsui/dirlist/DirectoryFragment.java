/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.State.MODE_GRID;
import static com.android.documentsui.base.State.MODE_LIST;

import android.annotation.IntDef;
import android.annotation.StringRes;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.RecyclerListener;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.text.BidiFormatter;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.BaseActivity.RetainedState;
import com.android.documentsui.DirectoryLoader;
import com.android.documentsui.DirectoryResult;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.FocusManager;
import com.android.documentsui.ItemDragListener;
import com.android.documentsui.MenuManager;
import com.android.documentsui.MessageBar;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.RecentsLoader;
import com.android.documentsui.Snackbars;
import com.android.documentsui.ThumbnailCache;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.base.Events.MotionInputEvent;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.roots.RootsCache;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortDimension.SortDirection;
import com.android.documentsui.sorting.SortModel;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Display the documents inside a single directory.
 */
public class DirectoryFragment extends Fragment
        implements DocumentsAdapter.Environment, LoaderCallbacks<DirectoryResult>,
        ItemDragListener.DragHost, SwipeRefreshLayout.OnRefreshListener {

    @IntDef(flag = true, value = {
            TYPE_NORMAL,
            TYPE_RECENT_OPEN
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ResultType {}
    public static final int TYPE_NORMAL = 1;
    public static final int TYPE_RECENT_OPEN = 2;

    @IntDef(flag = true, value = {
            REQUEST_COPY_DESTINATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestCode {}
    public static final int REQUEST_COPY_DESTINATION = 1;

    private static final String TAG = "DirectoryFragment";
    private static final int LOADER_ID = 42;

    private static final int CACHE_EVICT_LIMIT = 100;
    private static final int REFRESH_SPINNER_DISMISS_DELAY = 500;

    private final Model mModel = new Model();
    private final EventListener<Model.Update> mModelUpdateListener = new ModelUpdateListener();

    // This dependency is informally "injected" from the owning Activity in our onCreate method.
    private FragmentTuner mTuner;

    // This dependency is informally "injected" from the owning Activity in our onCreate method.
    private FocusManager mFocusManager;

    // This dependency is informally "injected" from the owning Activity in our onCreate method.
    private ActionHandler mActionHandler;

    // This dependency is informally "injected" from the owning Activity in our onCreate method.
    private MenuManager mMenuManager;

    private MultiSelectManager mSelectionMgr;
    private ActionModeController mActionModeController;
    private SelectionMetadata mSelectionMetadata;
    private UserInputHandler<InputEvent> mInputHandler;
    private @Nullable BandController mBandController;
    private DragHoverListener mDragHoverListener;
    private IconHelper mIconHelper;
    private SwipeRefreshLayout mRefreshLayout;
    private View mEmptyView;
    private RecyclerView mRecView;
    private View mFileList;

    private DocumentsAdapter mAdapter;
    private DocumentClipper mClipper;
    private GridLayoutManager mLayout;
    private int mColumnCount = 1;  // This will get updated when layout changes.

    private LayoutInflater mInflater;
    private MessageBar mMessageBar;
    private View mProgressBar;

    private Config mConfig;

    // Note, we use !null to indicate that selection was restored (from rotation).
    // So don't fiddle with this field unless you've got the bigger picture in mind.
    private @Nullable Selection mRestoredSelection = null;

    private SortModel.UpdateListener mSortListener = (model, updateType) -> {
        // Only when sort order has changed do we need to trigger another loading.
        if ((updateType & SortModel.UPDATE_TYPE_SORTING) != 0) {
            getLoaderManager().restartLoader(LOADER_ID, null, this);
        }
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mInflater = inflater;
        final View view = inflater.inflate(R.layout.fragment_directory, container, false);

        mMessageBar = MessageBar.create(getChildFragmentManager());
        mProgressBar = getActivity().findViewById(R.id.progressbar);
        assert(mProgressBar != null);

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.refresh_layout);
        mRefreshLayout.setOnRefreshListener(this);

        mEmptyView = view.findViewById(android.R.id.empty);
        mRecView = (RecyclerView) view.findViewById(R.id.dir_list);
        mRecView.setRecyclerListener(
                new RecyclerListener() {
                    @Override
                    public void onViewRecycled(ViewHolder holder) {
                        cancelThumbnailTask(holder.itemView);
                    }
                });
        mRecView.setItemAnimator(new DirectoryItemAnimator(getActivity()));
        mFileList = view.findViewById(R.id.file_list);

        final int edgeHeight = (int) getResources().getDimension(R.dimen.autoscroll_edge_height);
        mDragHoverListener = DragHoverListener.create(
                edgeHeight, new DirectoryDragListener(this), mRecView);

        // Make the recycler and the empty views responsive to drop events.
        mRecView.setOnDragListener(mDragHoverListener);
        mEmptyView.setOnDragListener(mDragHoverListener);

        return view;
    }

    @Override
    public void onDestroyView() {
        mSelectionMgr.clearSelection();

        // Cancel any outstanding thumbnail requests
        final int count = mRecView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = mRecView.getChildAt(i);
            cancelThumbnailTask(view);
        }

        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Context context = getActivity();
        final State state = getDisplayState();

        // Read arguments when object created for the first time.
        // Restore state if fragment recreated.
        Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState;

        mConfig = new Config();
        mConfig.restore(args);

        // Restore any selection we may have squirreled away in retained state.
        @Nullable RetainedState retained = getBaseActivity().getRetainedState();
        if (retained != null && retained.hasSelection()) {
            // We claim the selection for ourselves and null it out once used
            // so we don't have a rando selection hanging around in RetainedState.
            mRestoredSelection = retained.selection;
            retained.selection = null;
        }

        mIconHelper = new IconHelper(context, MODE_GRID);
        mClipper = DocumentsApplication.getDocumentClipper(getContext());

        mAdapter = new SectionBreakDocumentsAdapterWrapper(
                this, new ModelBackedDocumentsAdapter(this, mIconHelper));

        mRecView.setAdapter(mAdapter);

        mLayout = new GridLayoutManager(getContext(), mColumnCount) {
            @Override
            public void onLayoutCompleted(RecyclerView.State state) {
                super.onLayoutCompleted(state);
                mFocusManager.onLayoutCompleted();
            }
        };

        SpanSizeLookup lookup = mAdapter.createSpanSizeLookup();
        if (lookup != null) {
            mLayout.setSpanSizeLookup(lookup);
        }
        mRecView.setLayoutManager(mLayout);

        // TODO: instead of inserting the view into the constructor, extract listener-creation code
        // and set the listener on the view after the fact.  Then the view doesn't need to be passed
        // into the selection manager.
        mSelectionMgr = new MultiSelectManager(
                mAdapter,
                state.allowMultiple
                    ? MultiSelectManager.MODE_MULTIPLE
                    : MultiSelectManager.MODE_SINGLE,
                this::canSetSelectionState);
        mSelectionMetadata = new SelectionMetadata(mModel::getItem);
        mSelectionMgr.addItemCallback(mSelectionMetadata);

        mModel.addUpdateListener(mAdapter.getModelUpdateListener());
        mModel.addUpdateListener(mModelUpdateListener);

        final int edgeHeight = (int) getResources().getDimension(R.dimen.autoscroll_edge_height);
        GestureSelector gestureSel = GestureSelector.create(
                edgeHeight, mSelectionMgr, mRecView);

        final BaseActivity activity = getBaseActivity();
        mTuner = activity.getFragmentTuner(mModel, mConfig.mSearchMode);
        mFocusManager = activity.getFocusManager(mRecView, mModel);
        mActionHandler = activity.getActionHandler(mModel, mSelectionMgr);
        mMenuManager = activity.getMenuManager();

        if (state.allowMultiple) {
            mBandController = new BandController(mRecView, mAdapter, mSelectionMgr);
        }

        DragStartListener mDragStartListener = mTuner.dragAndDropEnabled()
                ? DragStartListener.create(
                        mIconHelper,
                        getContext(),
                        mModel,
                        mSelectionMgr,
                        mClipper,
                        getDisplayState(),
                        this::getModelId,
                        mRecView::findChildViewUnder,
                        getContext().getDrawable(com.android.internal.R.drawable.ic_doc_generic))
                : DragStartListener.DUMMY;

        EventHandler<InputEvent> gestureHandler = state.allowMultiple
                ? gestureSel::start
                : EventHandler.createStub(false);
        mInputHandler = new UserInputHandler<>(
                mActionHandler,
                mFocusManager,
                mSelectionMgr,
                (MotionEvent t) -> MotionInputEvent.obtain(t, mRecView),
                this::canSelect,
                this::onRightClick,
                (DocumentDetails ignored) -> onDeleteSelectedDocuments(), // delete handler
                mDragStartListener::onTouchDragEvent,
                gestureHandler);

        new ListeningGestureDetector(
                this.getContext(),
                mRecView,
                mEmptyView,
                mDragStartListener::onMouseDragEvent,
                gestureSel,
                mInputHandler,
                mBandController);

        mMenuManager = activity.getMenuManager();

        mActionModeController = ActionModeController.create(
                getContext(),
                mSelectionMgr,
                mMenuManager,
                mSelectionMetadata,
                getActivity(),
                mRecView,
                this::handleMenuItemClick);
        mSelectionMgr.addCallback(mActionModeController);

        final ActivityManager am = (ActivityManager) context.getSystemService(
                Context.ACTIVITY_SERVICE);
        boolean svelte = am.isLowRamDevice() && (mConfig.mType == TYPE_RECENT_OPEN);
        mIconHelper.setThumbnailsEnabled(!svelte);

        // If mDocument is null, we sort it by last modified by default because it's in Recents.
        final boolean prefersLastModified =
                (mConfig.mDocument != null)
                        ? (mConfig.mDocument.flags & Document.FLAG_DIR_PREFERS_LAST_MODIFIED) != 0
                        : true;
        // Call this before adding the listener to avoid restarting the loader one more time
        state.sortModel.setDefaultDimension(
                prefersLastModified
                        ? SortModel.SORT_DIMENSION_ID_DATE
                        : SortModel.SORT_DIMENSION_ID_TITLE);

        // Kick off loader at least once
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Add listener to update contents on sort model change
        getDisplayState().sortModel.addListener(mSortListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        getDisplayState().sortModel.removeListener(mSortListener);

        // Remember last scroll location
        final SparseArray<Parcelable> container = new SparseArray<>();
        getView().saveHierarchyState(container);
        final State state = getDisplayState();
        state.dirConfigs.put(mConfig.getConfigKey(), container);
    }

    public void retainState(RetainedState state) {
        state.selection = mSelectionMgr.getSelection(new Selection());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mConfig.save(outState);
    }

    @Override
    public void onActivityResult(@RequestCode int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_COPY_DESTINATION:
                handleCopyResult(resultCode, data);
                break;
            default:
                throw new UnsupportedOperationException("Unknown request code: " + requestCode);
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final MenuInflater inflater = getActivity().getMenuInflater();

        final String modelId = getModelId(v);
        if (modelId == null) {
            // TODO: inject DirectoryDetails into MenuManager constructor
            // Since both classes are supplied by Activity and created
            // at the same time.
            mMenuManager.inflateContextMenuForContainer(menu, inflater);
        } else {
            mMenuManager.inflateContextMenuForDocs(menu, inflater, mSelectionMetadata);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return handleMenuItemClick(item);
    }

    private void handleCopyResult(int resultCode, Intent data) {

        FileOperation operation = mConfig.claimPendingOperation();

        if (resultCode == Activity.RESULT_CANCELED || data == null) {
            // User pressed the back button or otherwise cancelled the destination pick. Don't
            // proceed with the copy.
            operation.dispose();
            return;
        }

        operation.setDestination(data.getParcelableExtra(Shared.EXTRA_STACK));

        BaseActivity activity = getBaseActivity();
        FileOperations.start(activity, operation, activity.fileOpCallback);
    }

    protected boolean onRightClick(InputEvent e) {
        final View v;
        final float x, y;
        if (e.isOverModelItem()) {
            DocumentHolder doc = (DocumentHolder) e.getDocumentDetails();

            v = doc.itemView;
            x = e.getX() - v.getLeft();
            y = e.getY() - v.getTop();
        } else {
            v = (mEmptyView.getVisibility() == View.VISIBLE)
                    ? mEmptyView
                    : mRecView;
            x = e.getX();
            y = e.getY();
        }

        mMenuManager.showContextMenu(this, v, x, y);

        return true;
    }

    public void onViewModeChanged() {
        // Mode change is just visual change; no need to kick loader.
        updateDisplayState();
    }

    private void updateDisplayState() {
        State state = getDisplayState();
        updateLayout(state.derivedMode);
        mRecView.setAdapter(mAdapter);
    }

    /**
     * Updates the layout after the view mode switches.
     * @param mode The new view mode.
     */
    private void updateLayout(@ViewMode int mode) {
        mColumnCount = calculateColumnCount(mode);
        if (mLayout != null) {
            mLayout.setSpanCount(mColumnCount);
        }

        int pad = getDirectoryPadding(mode);
        mRecView.setPadding(pad, pad, pad, pad);
        mRecView.requestLayout();
        if (mBandController != null) {
            mBandController.handleLayoutChanged();
        }
        mIconHelper.setViewMode(mode);
    }

    private int calculateColumnCount(@ViewMode int mode) {
        if (mode == MODE_LIST) {
            // List mode is a "grid" with 1 column.
            return 1;
        }

        int cellWidth = getResources().getDimensionPixelSize(R.dimen.grid_width);
        int cellMargin = 2 * getResources().getDimensionPixelSize(R.dimen.grid_item_margin);
        int viewPadding = mRecView.getPaddingLeft() + mRecView.getPaddingRight();

        // RecyclerView sometimes gets a width of 0 (see b/27150284).  Clamp so that we always lay
        // out the grid with at least 2 columns.
        int columnCount = Math.max(2,
                (mRecView.getWidth() - viewPadding) / (cellWidth + cellMargin));

        return columnCount;
    }

    private int getDirectoryPadding(@ViewMode int mode) {
        switch (mode) {
            case MODE_GRID:
                return getResources().getDimensionPixelSize(R.dimen.grid_container_padding);
            case MODE_LIST:
                return getResources().getDimensionPixelSize(R.dimen.list_container_padding);
            default:
                throw new IllegalArgumentException("Unsupported layout mode: " + mode);
        }
    }

    @Override
    public int getColumnCount() {
        return mColumnCount;
    }

    // Support method to replace getOwner().foo() with something
    // slightly less clumsy like: getOwner().foo().
    private BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }

    private boolean handleMenuItemClick(MenuItem item) {
        Selection selection = mSelectionMgr.getSelection(new Selection());

        switch (item.getItemId()) {
            case R.id.menu_open:
                openDocuments(selection);
                mActionModeController.finishActionMode();
                return true;

            case R.id.menu_open_with:
                showChooserForDoc(selection);
                return true;

            case R.id.menu_open_in_new_window:
                openInNewWindow(selection);
                return true;

            case R.id.menu_share:
                shareDocuments(selection);
                // TODO: Only finish selection if share action is completed.
                mActionModeController.finishActionMode();
                return true;

            case R.id.menu_delete:
                // deleteDocuments will end action mode if the documents are deleted.
                // It won't end action mode if user cancels the delete.
                deleteDocuments(selection);
                return true;

            case R.id.menu_copy_to:
                transferDocuments(selection, FileOperationService.OPERATION_COPY);
                // TODO: Only finish selection mode if copy-to is not canceled.
                // Need to plum down into handling the way we do with deleteDocuments.
                mActionModeController.finishActionMode();
                return true;

            case R.id.menu_move_to:
                // Exit selection mode first, so we avoid deselecting deleted documents.
                mActionModeController.finishActionMode();
                transferDocuments(selection, FileOperationService.OPERATION_MOVE);
                return true;

            case R.id.menu_cut_to_clipboard:
                cutSelectedToClipboard();
                return true;

            case R.id.menu_copy_to_clipboard:
                copySelectedToClipboard();
                return true;

            case R.id.menu_paste_from_clipboard:
                pasteFromClipboard();
                return true;

            case R.id.menu_paste_into_folder:
                pasteIntoFolder();
                return true;

            case R.id.menu_select_all:
                selectAllFiles();
                return true;

            case R.id.menu_rename:
                // Exit selection mode first, so we avoid deselecting deleted
                // (renamed) documents.
                mActionModeController.finishActionMode();
                renameDocuments(selection);
                return true;

            default:
                // See if BaseActivity can handle this particular MenuItem
                if (!getBaseActivity().onOptionsItemSelected(item)) {
                    if (DEBUG) Log.d(TAG, "Unhandled menu item selected: " + item);
                    return false;
                }
                return true;
        }
    }

    public final boolean onBackPressed() {
        if (mSelectionMgr.hasSelection()) {
            if (DEBUG) Log.d(TAG, "Clearing selection on selection manager.");
            mSelectionMgr.clearSelection();
            return true;
        }
        return false;
    }

    private void cancelThumbnailTask(View view) {
        final ImageView iconThumb = (ImageView) view.findViewById(R.id.icon_thumb);
        if (iconThumb != null) {
            mIconHelper.stopLoading(iconThumb);
        }
    }

    // Support for opening multiple documents is currently exclusive to DocumentsActivity.
    private void openDocuments(final Selection selected) {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_OPEN);

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);
        BaseActivity activity = getBaseActivity();
        if (docs.size() > 1) {
            activity.onDocumentsPicked(docs);
        } else {
            activity.onDocumentPicked(docs.get(0), mModel);
        }
    }

    private void showChooserForDoc(final Selection selected) {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_OPEN);

        assert(selected.size() == 1);
        DocumentInfo doc =
                DocumentInfo.fromDirectoryCursor(mModel.getItem(selected.iterator().next()));
        mTuner.showChooserForDoc(doc);
    }

    // TODO: Once selection manager is activity owned, move this logic into
    // a new method: ActionHandler#openWindowOnSelectedDocument
    private void openInNewWindow(final Selection selected) {
        assert(selected.size() == 1);
        DocumentInfo doc =
                DocumentInfo.fromDirectoryCursor(mModel.getItem(selected.iterator().next()));
        assert(doc != null);
        mActionHandler.openInNewWindow(new DocumentStack(getDisplayState().stack, doc));
    }

    private void shareDocuments(final Selection selected) {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_SHARE);

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);

        Intent intent;

        // Filter out directories and virtual files - those can't be shared.
        List<DocumentInfo> docsForSend = new ArrayList<>();
        for (DocumentInfo doc: docs) {
            if (!doc.isDirectory() && !doc.isVirtualDocument()) {
                docsForSend.add(doc);
            }
        }

        if (docsForSend.size() == 1) {
            final DocumentInfo doc = docsForSend.get(0);

            intent = new Intent(Intent.ACTION_SEND);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setType(doc.mimeType);
            intent.putExtra(Intent.EXTRA_STREAM, doc.derivedUri);

        } else if (docsForSend.size() > 1) {
            intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addCategory(Intent.CATEGORY_DEFAULT);

            final ArrayList<String> mimeTypes = new ArrayList<>();
            final ArrayList<Uri> uris = new ArrayList<>();
            for (DocumentInfo doc : docsForSend) {
                mimeTypes.add(doc.mimeType);
                uris.add(doc.derivedUri);
            }

            intent.setType(findCommonMimeType(mimeTypes));
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);

        } else {
            return;
        }

        intent = Intent.createChooser(intent, getActivity().getText(R.string.share_via));
        startActivity(intent);
    }

    private String generateDeleteMessage(final List<DocumentInfo> docs) {
        String message;
        int dirsCount = 0;

        for (DocumentInfo doc : docs) {
            if (doc.isDirectory()) {
                ++dirsCount;
            }
        }

        if (docs.size() == 1) {
            // Deleteing 1 file xor 1 folder in cwd

            // Address b/28772371, where including user strings in message can result in
            // broken bidirectional support.
            String displayName = BidiFormatter.getInstance().unicodeWrap(docs.get(0).displayName);
            message = dirsCount == 0
                    ? getActivity().getString(R.string.delete_filename_confirmation_message,
                            displayName)
                    : getActivity().getString(R.string.delete_foldername_confirmation_message,
                            displayName);
        } else if (dirsCount == 0) {
            // Deleting only files in cwd
            message = Shared.getQuantityString(getActivity(),
                    R.plurals.delete_files_confirmation_message, docs.size());
        } else if (dirsCount == docs.size()) {
            // Deleting only folders in cwd
            message = Shared.getQuantityString(getActivity(),
                    R.plurals.delete_folders_confirmation_message, docs.size());
        } else {
            // Deleting mixed items (files and folders) in cwd
            message = Shared.getQuantityString(getActivity(),
                    R.plurals.delete_items_confirmation_message, docs.size());
        }
        return message;
    }

    private boolean onDeleteSelectedDocuments() {
        if (mSelectionMgr.hasSelection()) {
            deleteDocuments(mSelectionMgr.getSelection(new Selection()));
        }
        return false;
    }

    private void deleteDocuments(final Selection selected) {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_DELETE);

        assert(!selected.isEmpty());

        final DocumentInfo srcParent = getDisplayState().stack.peek();

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);

        TextView message =
                (TextView) mInflater.inflate(R.layout.dialog_delete_confirmation, null);
        message.setText(generateDeleteMessage(docs));

        // For now, we implement this dialog NOT
        // as a fragment (which can survive rotation and have its own state),
        // but as a simple runtime dialog. So rotating a device with an
        // active delete dialog...results in that dialog disappearing.
        // We can do better, but don't have cycles for it now.
        new AlertDialog.Builder(getActivity())
            .setView(message)
            .setPositiveButton(
                 android.R.string.ok,
                 new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        // Finish selection mode first which clears selection so we
                        // don't end up trying to deselect deleted documents.
                        // This is done here, rather in the onActionItemClicked
                        // so we can avoid de-selecting items in the case where
                        // the user cancels the delete.
                        mActionModeController.finishActionMode();

                        UrisSupplier srcs;
                        try {
                            srcs = UrisSupplier.create(
                                    selected,
                                    mModel::getItemUri,
                                    getContext());
                        } catch(IOException e) {
                            throw new RuntimeException("Failed to create uri supplier.", e);
                        }

                        FileOperation operation = new FileOperation.Builder()
                                .withOpType(FileOperationService.OPERATION_DELETE)
                                .withDestination(getDisplayState().stack)
                                .withSrcs(srcs)
                                .withSrcParent(srcParent.derivedUri)
                                .build();

                        BaseActivity activity = getBaseActivity();
                        FileOperations.start(activity, operation, activity.fileOpCallback);
                    }
                })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void transferDocuments(final Selection selected, final @OpType int mode) {
        if(mode == FileOperationService.OPERATION_COPY) {
            Metrics.logUserAction(getContext(), Metrics.USER_ACTION_COPY_TO);
        } else if (mode == FileOperationService.OPERATION_MOVE) {
            Metrics.logUserAction(getContext(), Metrics.USER_ACTION_MOVE_TO);
        }

        // Pop up a dialog to pick a destination.  This is inadequate but works for now.
        // TODO: Implement a picker that is to spec.
        final Intent intent = new Intent(
                Shared.ACTION_PICK_COPY_DESTINATION,
                Uri.EMPTY,
                getActivity(),
                PickActivity.class);

        UrisSupplier srcs;
        try {
            srcs = UrisSupplier.create(selected, mModel::getItemUri, getContext());
        } catch(IOException e) {
            throw new RuntimeException("Failed to create uri supplier.", e);
        }

        Uri srcParent = getDisplayState().stack.peek().derivedUri;
        mConfig.mPendingOperation = new FileOperation.Builder()
                .withOpType(mode)
                .withSrcParent(srcParent)
                .withSrcs(srcs)
                .build();

        // Relay any config overrides bits present in the original intent.
        Intent original = getActivity().getIntent();
        if (original != null && original.hasExtra(Shared.EXTRA_PRODUCTIVITY_MODE)) {
            intent.putExtra(
                    Shared.EXTRA_PRODUCTIVITY_MODE,
                    original.getBooleanExtra(Shared.EXTRA_PRODUCTIVITY_MODE, false));
        }

        // Set an appropriate title on the drawer when it is shown in the picker.
        // Coupled with the fact that we auto-open the drawer for copy/move operations
        // it should basically be the thing people see first.
        int drawerTitleId = mode == FileOperationService.OPERATION_MOVE
                ? R.string.menu_move : R.string.menu_copy;
        intent.putExtra(DocumentsContract.EXTRA_PROMPT, getResources().getString(drawerTitleId));

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);

        // Determine if there is a directory in the set of documents
        // to be copied? Why? Directory creation isn't supported by some roots
        // (like Downloads). This informs DocumentsActivity (the "picker")
        // to restrict available roots to just those with support.
        intent.putExtra(Shared.EXTRA_DIRECTORY_COPY, hasDirectory(docs));
        intent.putExtra(FileOperationService.EXTRA_OPERATION_TYPE, mode);

        // This just identifies the type of request...we'll check it
        // when we reveive a response.
        startActivityForResult(intent, REQUEST_COPY_DESTINATION);
    }

    private static boolean hasDirectory(List<DocumentInfo> docs) {
        for (DocumentInfo info : docs) {
            if (Document.MIME_TYPE_DIR.equals(info.mimeType)) {
                return true;
            }
        }
        return false;
    }

    private void renameDocuments(Selection selected) {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_RENAME);

        // Batch renaming not supported
        // Rename option is only available in menu when 1 document selected
        assert(selected.size() == 1);

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);
        RenameDocumentFragment.show(getFragmentManager(), docs.get(0));
    }

    @Override
    public void initDocumentHolder(DocumentHolder holder) {
        holder.addKeyEventListener(mInputHandler);
        holder.itemView.setOnFocusChangeListener(mFocusManager);
    }

    @Override
    public void onBindDocumentHolder(DocumentHolder holder, Cursor cursor) {
        setupDragAndDropOnDocumentView(holder.itemView, cursor);
    }

    @Override
    public State getDisplayState() {
        return getBaseActivity().getDisplayState();
    }

    @Override
    public Model getModel() {
        return mModel;
    }

    @Override
    public boolean isDocumentEnabled(String docMimeType, int docFlags) {
        return mTuner.isDocumentEnabled(docMimeType, docFlags);
    }

    private void showEmptyDirectory() {
        showEmptyView(R.string.empty, R.drawable.cabinet);
    }

    private void showNoResults(RootInfo root) {
        CharSequence msg = getContext().getResources().getText(R.string.no_results);
        showEmptyView(String.format(String.valueOf(msg), root.title), R.drawable.cabinet);
    }

    private void showQueryError() {
        showEmptyView(R.string.query_error, R.drawable.hourglass);
    }

    private void showEmptyView(@StringRes int id, int drawable) {
        showEmptyView(getContext().getResources().getText(id), drawable);
    }

    private void showEmptyView(CharSequence msg, int drawable) {
        View content = mEmptyView.findViewById(R.id.content);
        TextView msgView = (TextView) mEmptyView.findViewById(R.id.message);
        ImageView imageView = (ImageView) mEmptyView.findViewById(R.id.artwork);
        msgView.setText(msg);
        imageView.setImageResource(drawable);

        mEmptyView.setVisibility(View.VISIBLE);
        mEmptyView.requestFocus();
        mFileList.setVisibility(View.GONE);
    }

    private void showDirectory() {
        mEmptyView.setVisibility(View.GONE);
        mFileList.setVisibility(View.VISIBLE);
        mRecView.requestFocus();
    }

    private String findCommonMimeType(List<String> mimeTypes) {
        String[] commonType = mimeTypes.get(0).split("/");
        if (commonType.length != 2) {
            return "*/*";
        }

        for (int i = 1; i < mimeTypes.size(); i++) {
            String[] type = mimeTypes.get(i).split("/");
            if (type.length != 2) continue;

            if (!commonType[1].equals(type[1])) {
                commonType[1] = "*";
            }

            if (!commonType[0].equals(type[0])) {
                commonType[0] = "*";
                commonType[1] = "*";
                break;
            }
        }

        return commonType[0] + "/" + commonType[1];
    }

    public void copySelectedToClipboard() {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_COPY_CLIPBOARD);

        Selection selection = mSelectionMgr.getSelection(new Selection());
        if (selection.isEmpty()) {
            return;
        }
        mSelectionMgr.clearSelection();

        mClipper.clipDocumentsForCopy(mModel::getItemUri, selection);

        Snackbars.showDocumentsClipped(getActivity(), selection.size());
    }

    public void cutSelectedToClipboard() {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_CUT_CLIPBOARD);

        Selection selection = mSelectionMgr.getSelection(new Selection());
        if (selection.isEmpty()) {
            return;
        }
        mSelectionMgr.clearSelection();

        mClipper.clipDocumentsForCut(mModel::getItemUri, selection, getDisplayState().stack.peek());

        Snackbars.showDocumentsClipped(getActivity(), selection.size());
    }

    public void pasteFromClipboard() {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_PASTE_CLIPBOARD);

        BaseActivity activity = (BaseActivity) getActivity();
        DocumentInfo destination = activity.getCurrentDirectory();
        mClipper.copyFromClipboard(
                destination, activity.getDisplayState().stack, activity.fileOpCallback);
        getActivity().invalidateOptionsMenu();
    }

    public void pasteIntoFolder() {
        assert (mSelectionMgr.getSelection().size() == 1);

        String modelId = mSelectionMgr.getSelection().iterator().next();
        Cursor dstCursor = mModel.getItem(modelId);
        if (dstCursor == null) {
            Log.w(TAG, "Invalid destination. Can't obtain cursor for modelId: " + modelId);
            return;
        }
        BaseActivity activity = getBaseActivity();
        DocumentInfo destination = DocumentInfo.fromDirectoryCursor(dstCursor);
        mClipper.copyFromClipboard(
                destination, activity.getDisplayState().stack, activity.fileOpCallback);
        getActivity().invalidateOptionsMenu();
    }

    public void selectAllFiles() {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_SELECT_ALL);

        // Exclude disabled files
        List<String> enabled = new ArrayList<>();
        for (String id : mAdapter.getModelIds()) {
            Cursor cursor = getModel().getItem(id);
            if (cursor == null) {
                Log.w(TAG, "Skipping selection. Can't obtain cursor for modeId: " + id);
                continue;
            }
            String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            int docFlags = getCursorInt(cursor, Document.COLUMN_FLAGS);
            if (isDocumentEnabled(docMimeType, docFlags)) {
                enabled.add(id);
            }
        }

        // Only select things currently visible in the adapter.
        boolean changed = mSelectionMgr.setItemsSelected(enabled, true);
        if (changed) {
            updateDisplayState();
        }
    }

    /**
     * Attempts to restore focus on the directory listing.
     */
    public void requestFocus() {
        mFocusManager.restoreLastFocus();
    }

    private void setupDragAndDropOnDocumentView(View view, Cursor cursor) {
        final String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
        if (Document.MIME_TYPE_DIR.equals(docMimeType)) {
            // Make a directory item a drop target. Drop on non-directories and empty space
            // is handled at the list/grid view level.
            view.setOnDragListener(mDragHoverListener);
        }
    }

    void dragStarted() {
        // When files are selected for dragging, ActionMode is started. This obscures the breadcrumb
        // with an ActionBar. In order to make drag and drop to the breadcrumb possible, we first
        // end ActionMode so the breadcrumb is visible to the user.
        //
        // mActionModeController is null when dragStarted() is called on spring loaded
        // folders/roots.
        if (mActionModeController != null) {
            mActionModeController.finishActionMode();
        }
    }

    void dragStopped(boolean result) {
        if (result) {
            mSelectionMgr.clearSelection();
        }
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        getActivity().runOnUiThread(runnable);
    }

    /**
     * {@inheritDoc}
     *
     * In DirectoryFragment, we spring loads the hovered folder.
     */
    @Override
    public void onViewHovered(View view) {
        BaseActivity activity = (BaseActivity) getActivity();
        if (getModelId(view) != null) {
           activity.springOpenDirectory(getDestination(view));
        }
        activity.setRootsDrawerOpen(false);
    }

    boolean handleDropEvent(View v, DragEvent event) {
        BaseActivity activity = (BaseActivity) getActivity();
        activity.setRootsDrawerOpen(false);

        ClipData clipData = event.getClipData();
        assert (clipData != null);

        assert(DocumentClipper.getOpType(clipData) == FileOperationService.OPERATION_COPY);

        if (!shouldCopyTo(event.getLocalState(), v)) {
            return false;
        }

        // Recognize multi-window drag and drop based on the fact that localState is not
        // carried between processes. It will stop working when the localsState behavior
        // is changed. The info about window should be passed in the localState then.
        // The localState could also be null for copying from Recents in single window
        // mode, but Recents doesn't offer this functionality (no directories).
        Metrics.logUserAction(getContext(),
                event.getLocalState() == null ? Metrics.USER_ACTION_DRAG_N_DROP_MULTI_WINDOW
                        : Metrics.USER_ACTION_DRAG_N_DROP);

        DocumentInfo dst = getDestination(v);
        mClipper.copyFromClipData(dst, getDisplayState().stack, clipData, activity.fileOpCallback);
        return true;
    }

    // Don't copy from the cwd into a provided list of prohibited directories. (ie. into cwd, into
    // a selected directory). Note: this currently doesn't work for multi-window drag, because
    // localState isn't carried over from one process to another.
    boolean shouldCopyTo(Object dragLocalState, View destinationView) {
        if (dragLocalState == null || !(dragLocalState instanceof List<?>)) {
            if (DEBUG) Log.d(TAG, "Invalid local state object. Will allow copy.");
            return true;
        }
        DocumentInfo dst = getDestination(destinationView);
        List<?> src = (List<?>) dragLocalState;
        if (src.contains(dst)) {
            if (DEBUG) Log.d(TAG, "Drop target same as source. Ignoring.");
            return false;
        }
        return true;
    }

    private DocumentInfo getDestination(View v) {
        String id = getModelId(v);
        if (id != null) {
            Cursor dstCursor = mModel.getItem(id);
            if (dstCursor == null) {
                Log.w(TAG, "Invalid destination. Can't obtain cursor for modelId: " + id);
                return null;
            }
            return DocumentInfo.fromDirectoryCursor(dstCursor);
        }

        if (v == mRecView || v == mEmptyView) {
            return getDisplayState().stack.peek();
        }

        return null;
    }

    @Override
    public void setDropTargetHighlight(View v, boolean highlight) {
        // Note: use exact comparison - this code is searching for views which are children of
        // the RecyclerView instance in the UI.
        if (v.getParent() == mRecView) {
            RecyclerView.ViewHolder vh = mRecView.getChildViewHolder(v);
            if (vh instanceof DocumentHolder) {
                ((DocumentHolder) vh).setHighlighted(highlight);
            }
        }
    }

    /**
     * Gets the model ID for a given RecyclerView item.
     * @param view A View that is a document item view, or a child of a document item view.
     * @return The Model ID for the given document, or null if the given view is not associated with
     *     a document item view.
     */
    protected @Nullable String getModelId(View view) {
        View itemView = mRecView.findContainingItemView(view);
        if (itemView != null) {
            RecyclerView.ViewHolder vh = mRecView.getChildViewHolder(itemView);
            if (vh instanceof DocumentHolder) {
                return ((DocumentHolder) vh).getModelId();
            }
        }
        return null;
    }

    @Override
    public boolean isSelected(String modelId) {
        return mSelectionMgr.getSelection().contains(modelId);
    }

    private final class ModelUpdateListener implements EventListener<Model.Update> {

        @Override
        public void accept(Model.Update update) {
            if (update.hasError()) {
                showQueryError();
                return;
            }

            if (DEBUG) Log.d(TAG, "Received model update. Loading=" + mModel.isLoading());

            if (mModel.info != null || mModel.error != null) {
                mMessageBar.setInfo(mModel.info);
                mMessageBar.setError(mModel.error);
                mMessageBar.show();
            }

            mProgressBar.setVisibility(mModel.isLoading() ? View.VISIBLE : View.GONE);

            if (mModel.isEmpty()) {
                if (mConfig.mSearchMode) {
                    showNoResults(getDisplayState().stack.root);
                } else {
                    showEmptyDirectory();
                }
            } else {
                showDirectory();
                mAdapter.notifyDataSetChanged();
            }

            if (!mModel.isLoading()) {
                getBaseActivity().notifyDirectoryLoaded(
                        mModel.doc != null ? mModel.doc.derivedUri : null);
            }
        }
    }

    private boolean canSelect(DocumentDetails doc) {
        return canSetSelectionState(doc.getModelId(), true);
    }

    private boolean canSetSelectionState(String modelId, boolean nextState) {
        if (nextState) {
            // Check if an item can be selected
            final Cursor cursor = mModel.getItem(modelId);
            if (cursor == null) {
                Log.w(TAG, "Couldn't obtain cursor for modelId: " + modelId);
                return false;
            }

            final String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
            final int docFlags = getCursorInt(cursor, Document.COLUMN_FLAGS);
            return mTuner.canSelectType(docMimeType, docFlags);
        } else {
            // Right now all selected items can be deselected.
            return true;
        }
    }

    public static void showDirectory(
            FragmentManager fm, RootInfo root, DocumentInfo doc, int anim) {
        if (DEBUG) Log.d(TAG, "Showing directory: " + DocumentInfo.debugString(doc));
        create(fm, TYPE_NORMAL, root, doc, null, anim);
    }

    public static void showRecentsOpen(FragmentManager fm, int anim) {
        create(fm, TYPE_RECENT_OPEN, null, null, null, anim);
    }

    public static void reloadSearch(FragmentManager fm, RootInfo root, DocumentInfo doc,
            String query) {
        DirectoryFragment df = get(fm);

        df.mConfig.update(root, doc, query);
        df.getLoaderManager().restartLoader(LOADER_ID, null, df);
    }

    public static void reload(FragmentManager fm, int type, RootInfo root, DocumentInfo doc,
            String query) {
        if (DEBUG) Log.d(TAG, "Reloading directory: " + DocumentInfo.debugString(doc));
        DirectoryFragment df = get(fm);

        df.mConfig.update(type, root, doc, query);
        df.getLoaderManager().restartLoader(LOADER_ID, null, df);
    }

    public static void create(
            FragmentManager fm,
            int type,
            RootInfo root,
            @Nullable DocumentInfo doc,
            String query,
            int anim) {

        if (DEBUG) {
            if (doc == null) {
                Log.d(TAG, "Creating new fragment null directory");
            } else {
                Log.d(TAG, "Creating new fragment for directory: " + DocumentInfo.debugString(doc));
            }
        }

        final Bundle args = new Bundle();
        args.putInt(Shared.EXTRA_TYPE, type);
        args.putParcelable(Shared.EXTRA_ROOT, root);
        args.putParcelable(Shared.EXTRA_DOC, doc);
        args.putString(Shared.EXTRA_QUERY, query);
        args.putParcelable(Shared.EXTRA_SELECTION, new Selection());

        final FragmentTransaction ft = fm.beginTransaction();
        AnimationView.setupAnimations(ft, anim, args);

        final DirectoryFragment fragment = new DirectoryFragment();
        fragment.setArguments(args);

        ft.replace(getFragmentId(), fragment);
        ft.commitAllowingStateLoss();
    }

    public static @Nullable DirectoryFragment get(FragmentManager fm) {
        // TODO: deal with multiple directories shown at once
        Fragment fragment = fm.findFragmentById(getFragmentId());
        return fragment instanceof DirectoryFragment
                ? (DirectoryFragment) fragment
                : null;
    }

    private static int getFragmentId() {
        return R.id.container_directory;
    }

    @Override
    public void onRefresh() {
        // Remove thumbnail cache. We do this not because we're worried about stale thumbnails as it
        // should be covered by last modified value we store in thumbnail cache, but rather to give
        // the user a greater sense that contents are being reloaded.
        ThumbnailCache cache = DocumentsApplication.getThumbnailCache(getContext());
        String[] ids = mModel.getModelIds();
        int numOfEvicts = Math.min(ids.length, CACHE_EVICT_LIMIT);
        for (int i = 0; i < numOfEvicts; ++i) {
            cache.removeUri(mModel.getItemUri(ids[i]));
        }

        // Trigger loading
        getLoaderManager().restartLoader(LOADER_ID, null, this);
    }

    @Override
    public Loader<DirectoryResult> onCreateLoader(int id, Bundle args) {
        Context context = getActivity();
        State state = getDisplayState();

        Uri contentsUri;
        switch (mConfig.mType) {
            case TYPE_NORMAL:
                contentsUri = mConfig.mSearchMode ? DocumentsContract.buildSearchDocumentsUri(
                        mConfig.mRoot.authority, mConfig.mRoot.rootId, mConfig.mQuery)
                        : DocumentsContract.buildChildDocumentsUri(
                                mConfig.mDocument.authority, mConfig.mDocument.documentId);
                if (mTuner.managedModeEnabled()) {
                    contentsUri = DocumentsContract.setManageMode(contentsUri);
                }
                if (DEBUG) Log.d(TAG, "Creating new directory loader for: "
                        + DocumentInfo.debugString(mConfig.mDocument));
                return new DirectoryLoader(
                        context, mConfig.mRoot, mConfig.mDocument, contentsUri, state.sortModel,
                        mConfig.mSearchMode);
            case TYPE_RECENT_OPEN:
                if (DEBUG) Log.d(TAG, "Creating new loader recents.");
                final RootsCache roots = DocumentsApplication.getRootsCache(context);
                return new RecentsLoader(context, roots, state);

            default:
                throw new IllegalStateException("Unknown type " + mConfig.mType);
        }
    }

    @Override
    public void onLoadFinished(Loader<DirectoryResult> loader, DirectoryResult result) {
        if (DEBUG) Log.d(TAG, "Loader has finished for: "
                + DocumentInfo.debugString(mConfig.mDocument));
        assert(result != null);

        if (!isAdded()) return;

        if (mConfig.mSearchMode) {
            Metrics.logUserAction(getContext(), Metrics.USER_ACTION_SEARCH);
        }

        State state = getDisplayState();

        mAdapter.notifyDataSetChanged();
        mModel.update(result);

        updateLayout(state.derivedMode);

        if (mRestoredSelection != null) {
            mSelectionMgr.restoreSelection(mRestoredSelection);
            // Note, we'll take care of cleaning up retained selection
            // in the selection handler where we already have some
            // specialized code to handle when selection was restored.
        }

        // Restore any previous instance state
        final SparseArray<Parcelable> container = state.dirConfigs.remove(mConfig.getConfigKey());
        final int curSortedDimensionId = state.sortModel.getSortedDimensionId();

        final SortDimension curSortedDimension =
                state.sortModel.getDimensionById(curSortedDimensionId);
        if (container != null && !getArguments().getBoolean(Shared.EXTRA_IGNORE_STATE, false)) {
            getView().restoreHierarchyState(container);
        } else if (mConfig.mLastSortDimensionId != curSortedDimension.getId()
                || mConfig.mLastSortDimensionId == SortModel.SORT_DIMENSION_ID_UNKNOWN
                || mConfig.mLastSortDirection != curSortedDimension.getSortDirection()) {
            // Scroll to the top if the sort order actually changed.
            mRecView.smoothScrollToPosition(0);
        }

        mConfig.mLastSortDimensionId = curSortedDimension.getId();
        mConfig.mLastSortDirection = curSortedDimension.getSortDirection();

        if (mRefreshLayout.isRefreshing()) {
            new Handler().postDelayed(
                    () -> mRefreshLayout.setRefreshing(false),
                    REFRESH_SPINNER_DISMISS_DELAY);
        }
    }

    @Override
    public void onLoaderReset(Loader<DirectoryResult> loader) {
        if (DEBUG) Log.d(TAG, "Resetting loader for: "
                + DocumentInfo.debugString(mConfig.mDocument));
        mModel.onLoaderReset();

        mRefreshLayout.setRefreshing(false);
    }

    private static final class Config {

        private static final String EXTRA_SORT_DIMENSION_ID = "sortDimensionId";
        private static final String EXTRA_SORT_DIRECTION = "sortDirection";

        // Directory fragment state is defined by: root, document, query, type, selection
        private @ResultType int mType = TYPE_NORMAL;
        private RootInfo mRoot;
        // Null when viewing Recents directory.
        private @Nullable DocumentInfo mDocument;
        private String mQuery = null;
        // Here we save the clip details of moveTo/copyTo actions when picker shows up.
        // This will be written to saved instance.
        private @Nullable FileOperation mPendingOperation;
        private boolean mSearchMode;
        private int mLastSortDimensionId = SortModel.SORT_DIMENSION_ID_UNKNOWN;
        private @SortDirection int mLastSortDirection;

        private String mConfigKey;

        public void restore(Bundle bundle) {
            mRoot = bundle.getParcelable(Shared.EXTRA_ROOT);
            mDocument = bundle.getParcelable(Shared.EXTRA_DOC);
            mQuery = bundle.getString(Shared.EXTRA_QUERY);
            mType = bundle.getInt(Shared.EXTRA_TYPE);
            mSearchMode = bundle.getBoolean(Shared.EXTRA_SEARCH_MODE);
            mPendingOperation = bundle.getParcelable(FileOperationService.EXTRA_OPERATION);
            mLastSortDimensionId = bundle.getInt(EXTRA_SORT_DIMENSION_ID);
            mLastSortDirection = bundle.getInt(EXTRA_SORT_DIRECTION);
        }

        public void save(Bundle bundle) {
            bundle.putInt(Shared.EXTRA_TYPE, mType);
            bundle.putParcelable(Shared.EXTRA_ROOT, mRoot);
            bundle.putParcelable(Shared.EXTRA_DOC, mDocument);
            bundle.putString(Shared.EXTRA_QUERY, mQuery);
            bundle.putBoolean(Shared.EXTRA_SEARCH_MODE, mSearchMode);
            bundle.putParcelable(FileOperationService.EXTRA_OPERATION, mPendingOperation);
            bundle.putInt(EXTRA_SORT_DIMENSION_ID, mLastSortDimensionId);
            bundle.putInt(EXTRA_SORT_DIRECTION, mLastSortDirection);
        }

        public FileOperation claimPendingOperation() {
            FileOperation op = mPendingOperation;
            mPendingOperation = null;
            return op;
        }

        public void update(int type, RootInfo root, DocumentInfo doc, String query) {
            mType = type;
            update(root, doc, query);
        }

        public void update(RootInfo root, DocumentInfo doc, String query) {
            mQuery = query;
            mRoot = root;
            mDocument = doc;
            mSearchMode =  query != null;
        }

        private String getConfigKey() {
            if (mConfigKey == null) {
                final StringBuilder builder = new StringBuilder();
                builder.append(mRoot != null ? mRoot.authority : "null").append(';');
                builder.append(mRoot != null ? mRoot.rootId : "null").append(';');
                builder.append(mDocument != null ? mDocument.documentId : "null");
                mConfigKey = builder.toString();
            }
            return mConfigKey;
        }
    }
}
