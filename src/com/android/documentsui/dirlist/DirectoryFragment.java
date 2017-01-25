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

import static android.content.ContentResolver.EXTRA_REFRESH_SUPPORTED;
import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.VERBOSE;
import static com.android.documentsui.base.State.MODE_GRID;
import static com.android.documentsui.base.State.MODE_LIST;

import android.annotation.DimenRes;
import android.annotation.FractionRes;
import android.annotation.IntDef;
import android.annotation.StringRes;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.GridLayoutManager.SpanSizeLookup;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.RecyclerListener;
import android.support.v7.widget.RecyclerView.ViewHolder;
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
import com.android.documentsui.ActionModeController;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.BaseActivity.RetainedState;
import com.android.documentsui.DirectoryLoader;
import com.android.documentsui.DirectoryReloadLock;
import com.android.documentsui.DirectoryResult;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.DragAndDropHelper;
import com.android.documentsui.FocusManager;
import com.android.documentsui.Injector;
import com.android.documentsui.Injector.ContentScoped;
import com.android.documentsui.Injector.Injected;
import com.android.documentsui.ItemDragListener;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.RecentsLoader;
import com.android.documentsui.RefreshTask;
import com.android.documentsui.ThumbnailCache;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Events.InputEvent;
import com.android.documentsui.base.Events.MotionInputEvent;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;
import com.android.documentsui.clipping.ClipStore;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.roots.RootsAccess;
import com.android.documentsui.selection.BandController;
import com.android.documentsui.selection.GestureSelector;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionMetadata;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.ui.DialogController;

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
        implements ItemDragListener.DragHost, SwipeRefreshLayout.OnRefreshListener {

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
    private static final int REFRESH_SPINNER_TIMEOUT = 500;

    private BaseActivity mActivity;

    private State mState;
    private final Model mModel = new Model();
    private final EventListener<Model.Update> mModelUpdateListener = new ModelUpdateListener();
    private final DocumentsAdapter.Environment mAdapterEnv = new AdapterEnvironment();
    private final LoaderCallbacks<DirectoryResult> mLoaderCallbacks = new LoaderBindings();

    @Injected
    @ContentScoped
    private Injector<?> mInjector;

    @Injected
    @ContentScoped
    private SelectionManager mSelectionMgr;

    @Injected
    @ContentScoped
    private FocusManager mFocusManager;

    @Injected
    @ContentScoped
    private ActionHandler mActions;

    @Injected
    @ContentScoped
    private ActionModeController mActionModeController;

    private SelectionMetadata mSelectionMetadata;
    private UserInputHandler<InputEvent> mInputHandler;
    private @Nullable BandController mBandController;
    private @Nullable DragHoverListener mDragHoverListener;
    private IconHelper mIconHelper;
    private SwipeRefreshLayout mRefreshLayout;
    private RecyclerView mRecView;
    private View mFileList;

    private DocumentsAdapter mAdapter;
    private DocumentClipper mClipper;
    private GridLayoutManager mLayout;
    private int mColumnCount = 1;  // This will get updated when layout changes.

    private float mLiveScale = 1.0f;
    private @ViewMode int mMode;

    private View mProgressBar;

    private DirectoryState mLocalState;
    private DirectoryReloadLock mReloadLock = new DirectoryReloadLock();

    // Note, we use !null to indicate that selection was restored (from rotation).
    // So don't fiddle with this field unless you've got the bigger picture in mind.
    private @Nullable Selection mRestoredSelection = null;

    private SortModel.UpdateListener mSortListener = (model, updateType) -> {
        // Only when sort order has changed do we need to trigger another loading.
        if ((updateType & SortModel.UPDATE_TYPE_SORTING) != 0) {
            getLoaderManager().restartLoader(LOADER_ID, null, mLoaderCallbacks);
        }
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        BaseActivity activity = (BaseActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_directory, container, false);

        mProgressBar = view.findViewById(R.id.progressbar);
        assert(mProgressBar != null);

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.refresh_layout);
        mRefreshLayout.setOnRefreshListener(this);

        mRecView = (RecyclerView) view.findViewById(R.id.dir_list);
        mRecView.setRecyclerListener(
                new RecyclerListener() {
                    @Override
                    public void onViewRecycled(ViewHolder holder) {
                        cancelThumbnailTask(holder.itemView);
                    }
                });
        mRecView.setItemAnimator(new DirectoryItemAnimator(activity));
        mFileList = view.findViewById(R.id.file_list);

        mInjector = activity.getInjector();
        mDragHoverListener = mInjector.config.dragAndDropEnabled()
                ? DragHoverListener.create(new DirectoryDragListener(this), mRecView)
                : null;

        // Make the recycler and the empty views responsive to drop events when allowed.
        mRecView.setOnDragListener(mDragHoverListener);

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

        mActivity = (BaseActivity) getActivity();
        mState = mActivity.getDisplayState();

        // Read arguments when object created for the first time.
        // Restore state if fragment recreated.
        Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState;

        mLocalState = new DirectoryState();
        mLocalState.restore(args);

        // Restore any selection we may have squirreled away in retained state.
        @Nullable RetainedState retained = mActivity.getRetainedState();
        if (retained != null && retained.hasSelection()) {
            // We claim the selection for ourselves and null it out once used
            // so we don't have a rando selection hanging around in RetainedState.
            mRestoredSelection = retained.selection;
            retained.selection = null;
        }

        mIconHelper = new IconHelper(mActivity, MODE_GRID);
        mClipper = DocumentsApplication.getDocumentClipper(getContext());

        mAdapter = new DirectoryAddonsAdapter(
                mAdapterEnv, new ModelBackedDocumentsAdapter(mAdapterEnv, mIconHelper));

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

        mModel.addUpdateListener(mAdapter.getModelUpdateListener());
        mModel.addUpdateListener(mModelUpdateListener);

        mSelectionMgr = mInjector.getSelectionManager(mAdapter, this::canSetSelectionState);
        mFocusManager = mInjector.getFocusManager(mRecView, mModel);
        mActions = mInjector.getActionHandler(mModel);

        mSelectionMetadata = new SelectionMetadata(mModel::getItem);
        mSelectionMgr.addItemCallback(mSelectionMetadata);

        GestureSelector gestureSel = GestureSelector.create(mSelectionMgr, mRecView, mReloadLock);

        if (mState.allowMultiple) {
            mBandController = new BandController(
                    mRecView,
                    mAdapter,
                    mSelectionMgr,
                    mReloadLock,
                    (int pos) -> {
                        // The band selection model only operates on documents and directories.
                        // Exclude other types of adapter items like whitespace and dividers.
                        RecyclerView.ViewHolder vh = mRecView.findViewHolderForAdapterPosition(pos);
                        return ModelBackedDocumentsAdapter.isContentType(vh.getItemViewType());
                    });
        }

        DragStartListener mDragStartListener = mInjector.config.dragAndDropEnabled()
                ? DragStartListener.create(
                        mIconHelper,
                        mActivity,
                        mModel,
                        mSelectionMgr,
                        mClipper,
                        mState,
                        this::getModelId,
                        mRecView::findChildViewUnder,
                        getContext().getDrawable(com.android.internal.R.drawable.ic_doc_generic),
                        mActivity.getShadowBuilder())
                : DragStartListener.DUMMY;

        EventHandler<InputEvent> gestureHandler = mState.allowMultiple
                ? gestureSel::start
                : EventHandler.createStub(false);

        mInputHandler = new UserInputHandler<>(
                mActions,
                mFocusManager,
                mSelectionMgr,
                (MotionEvent t) -> MotionInputEvent.obtain(t, mRecView),
                this::canSelect,
                this::onContextMenuClick,
                mDragStartListener::onTouchDragEvent,
                gestureHandler);

        new ListeningGestureDetector(
                this.getContext(),
                mRecView,
                mDragStartListener::onMouseDragEvent,
                gestureSel,
                mInputHandler,
                mBandController,
                this::scaleLayout);

        mActionModeController = mInjector.getActionModeController(
                mSelectionMetadata,
                this::handleMenuItemClick,
                mRecView);

        mSelectionMgr.addCallback(mActionModeController);

        final ActivityManager am = (ActivityManager) mActivity.getSystemService(
                Context.ACTIVITY_SERVICE);
        boolean svelte = am.isLowRamDevice() && (mLocalState.mType == TYPE_RECENT_OPEN);
        mIconHelper.setThumbnailsEnabled(!svelte);

        // If mDocument is null, we sort it by last modified by default because it's in Recents.
        final boolean prefersLastModified =
                (mLocalState.mDocument != null)
                        ? (mLocalState.mDocument.flags & Document.FLAG_DIR_PREFERS_LAST_MODIFIED) != 0
                        : true;
        // Call this before adding the listener to avoid restarting the loader one more time
        mState.sortModel.setDefaultDimension(
                prefersLastModified
                        ? SortModel.SORT_DIMENSION_ID_DATE
                        : SortModel.SORT_DIMENSION_ID_TITLE);

        // Kick off loader at least once
        getLoaderManager().restartLoader(LOADER_ID, null, mLoaderCallbacks);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Add listener to update contents on sort model change
        mState.sortModel.addListener(mSortListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        mState.sortModel.removeListener(mSortListener);

        // Remember last scroll location
        final SparseArray<Parcelable> container = new SparseArray<>();
        getView().saveHierarchyState(container);
        mState.dirConfigs.put(mLocalState.getConfigKey(), container);
    }

    public void retainState(RetainedState state) {
        state.selection = mSelectionMgr.getSelection(new Selection());
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mLocalState.save(outState);
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
            mInjector.menuManager.inflateContextMenuForContainer(menu, inflater);
        } else {
            mInjector.menuManager.inflateContextMenuForDocs(menu, inflater, mSelectionMetadata);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return handleMenuItemClick(item);
    }

    private void handleCopyResult(int resultCode, Intent data) {

        FileOperation operation = mLocalState.claimPendingOperation();

        if (resultCode == Activity.RESULT_CANCELED || data == null) {
            // User pressed the back button or otherwise cancelled the destination pick. Don't
            // proceed with the copy.
            operation.dispose();
            return;
        }

        operation.setDestination(data.getParcelableExtra(Shared.EXTRA_STACK));
        FileOperations.start(
                mActivity,
                operation,
                mInjector.dialogs::showFileOperationStatus);
    }

    protected boolean onContextMenuClick(InputEvent e) {
        final View v;
        final float x, y;
        if (e.isOverModelItem()) {
            DocumentHolder doc = (DocumentHolder) e.getDocumentDetails();

            v = doc.itemView;
            x = e.getX() - v.getLeft();
            y = e.getY() - v.getTop();
        } else {
            v = mRecView;
            x = e.getX();
            y = e.getY();
        }

        mInjector.menuManager.showContextMenu(this, v, x, y);

        return true;
    }

    public void onViewModeChanged() {
        // Mode change is just visual change; no need to kick loader.
        updateDisplayState();
    }

    private void updateDisplayState() {
        updateLayout(mState.derivedMode);
        mRecView.setAdapter(mAdapter);
    }

    /**
     * Updates the layout after the view mode switches.
     * @param mode The new view mode.
     */
    private void updateLayout(@ViewMode int mode) {
        mMode = mode;
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

    /**
     * Updates the layout after the view mode switches.
     * @param mode The new view mode.
     */
    private void scaleLayout(float scale) {
        assert(Build.IS_DEBUGGABLE);
        if (VERBOSE) Log.v(
                TAG, "Handling scale event: " + scale + ", existing scale: " + mLiveScale);

        if (mMode == MODE_GRID) {
            float minScale = getFraction(R.fraction.grid_scale_min);
            float maxScale = getFraction(R.fraction.grid_scale_max);
            float nextScale = mLiveScale * scale;

            if (VERBOSE) Log.v(TAG,
                    "Next scale " + nextScale + ", Min/max scale " + minScale + "/" + maxScale);

            if (nextScale > minScale && nextScale < maxScale) {
                if (DEBUG) Log.d(TAG, "Updating grid scale: " + scale);
                mLiveScale = nextScale;
                updateLayout(mMode);
            }

        } else {
            if (DEBUG) Log.d(TAG, "List mode, ignoring scale: " + scale);
            mLiveScale = 1.0f;
        }
    }

    private int calculateColumnCount(@ViewMode int mode) {
        if (mode == MODE_LIST) {
            // List mode is a "grid" with 1 column.
            return 1;
        }

        int cellWidth = getScaledSize(R.dimen.grid_width);
        int cellMargin = 2 * getScaledSize(R.dimen.grid_item_margin);
        int viewPadding =
                (int) ((mRecView.getPaddingLeft() + mRecView.getPaddingRight()) * mLiveScale);

        // RecyclerView sometimes gets a width of 0 (see b/27150284).
        // Clamp so that we always lay out the grid with at least 2 columns by default.
        int columnCount = Math.max(2,
                (mRecView.getWidth() - viewPadding) / (cellWidth + cellMargin));

        // Finally with our grid count logic firmly in place, we apply any live scaling
        // captured by the scale gesture detector.
        return Math.max(1, Math.round(columnCount / mLiveScale));
    }


    /**
     * Moderately abuse the "fraction" resource type for our purposes.
     */
    private float getFraction(@FractionRes int id) {
        return getResources().getFraction(id, 1, 0);
    }

    private int getScaledSize(@DimenRes int id) {
        return (int) (getResources().getDimensionPixelSize(id) * mLiveScale);
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
                mActions.openSelectedInNewWindow();
                return true;

            case R.id.menu_share:
                mActions.shareSelectedDocuments();
                return true;

            case R.id.menu_delete:
                // deleteDocuments will end action mode if the documents are deleted.
                // It won't end action mode if user cancels the delete.
                mActions.deleteSelectedDocuments();
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
                mActions.cutToClipboard();
                return true;

            case R.id.menu_copy_to_clipboard:
                mActions.copyToClipboard();
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
                if (!mActivity.onOptionsItemSelected(item)) {
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
        if (docs.size() > 1) {
            mActivity.onDocumentsPicked(docs);
        } else {
            mActivity.onDocumentPicked(docs.get(0));
        }
    }

    private void showChooserForDoc(final Selection selected) {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_OPEN);

        assert(selected.size() == 1);
        DocumentInfo doc =
                DocumentInfo.fromDirectoryCursor(mModel.getItem(selected.iterator().next()));
        mActions.showChooserForDoc(doc);
    }

    private void transferDocuments(final Selection selected, final @OpType int mode) {
        if (mode == FileOperationService.OPERATION_COPY) {
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
            ClipStore clipStorage = DocumentsApplication.getClipStore(getContext());
            srcs = UrisSupplier.create(selected, mModel::getItemUri, clipStorage);
        } catch(IOException e) {
            throw new RuntimeException("Failed to create uri supplier.", e);
        }

        final DocumentInfo parent = mState.stack.peek();
        mLocalState.mPendingOperation = new FileOperation.Builder()
                .withOpType(mode)
                .withSrcParent(parent == null ? null : parent.derivedUri)
                .withSrcs(srcs)
                .build();

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
        RenameDocumentFragment.show(getFragmentManager(), docs.get(0), mModel::hasFileWithName);
    }

    private boolean isDocumentEnabled(String mimeType, int flags) {
        return mInjector.config.isDocumentEnabled(mimeType, flags, mState);
    }

    public void pasteFromClipboard() {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_PASTE_CLIPBOARD);

        BaseActivity activity = (BaseActivity) getActivity();
        DocumentInfo destination = activity.getCurrentDirectory();
        mClipper.copyFromClipboard(
                destination,
                mState.stack,
                mInjector.dialogs::showFileOperationStatus);
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
        BaseActivity activity = mActivity;
        DocumentInfo destination = DocumentInfo.fromDirectoryCursor(dstCursor);
        mClipper.copyFromClipboard(
                destination,
                mState.stack,
                mInjector.dialogs::showFileOperationStatus);
        getActivity().invalidateOptionsMenu();
    }

    public void selectAllFiles() {
        Metrics.logUserAction(getContext(), Metrics.USER_ACTION_SELECT_ALL);

        // Exclude disabled files
        List<String> enabled = new ArrayList<>();
        for (String id : mAdapter.getModelIds()) {
            Cursor cursor = mModel.getItem(id);
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

    private void setupDragAndDropOnDocumentView(View view, Cursor cursor) {
        final String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
        if (Document.MIME_TYPE_DIR.equals(docMimeType)) {
            // Make a directory item a drop target. Drop on non-directories and empty space
            // is handled at the list/grid view level.
            view.setOnDragListener(mDragHoverListener);
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

    // In DirectoryFragment, we close the roots drawer right away.
    // We also want to update the Drag Shadow to indicate whether the
    // item is droppable or not.
    @Override
    public void onDragEntered(View v, Object localState) {
        mActivity.setRootsDrawerOpen(false);
        mActivity.getShadowBuilder()
                .setAppearDroppable(DragAndDropHelper.canCopyTo(localState, getDestination(v)));
        v.updateDragShadow(mActivity.getShadowBuilder());
    }

    // In DirectoryFragment, we always reset the background of the Drag Shadow once it
    // exits.
    @Override
    public void onDragExited(View v, Object localState) {
        mActivity.getShadowBuilder().resetBackground();
        v.updateDragShadow(mActivity.getShadowBuilder());
        if (v.getParent() == mRecView) {
            DocumentHolder holder = getDocumentHolder(v);
            if (holder != null) {
                holder.resetDropHighlight();
            }
        }
    }

    // In DirectoryFragment, we spring loads the hovered folder.
    @Override
    public void onViewHovered(View view) {
        BaseActivity activity = mActivity;
        if (getModelId(view) != null) {
            mActions.springOpenDirectory(getDestination(view));
        }
        activity.setRootsDrawerOpen(false);
    }

    boolean handleDropEvent(View v, DragEvent event) {
        BaseActivity activity = (BaseActivity) getActivity();
        activity.setRootsDrawerOpen(false);

        ClipData clipData = event.getClipData();
        assert (clipData != null);

        assert(DocumentClipper.getOpType(clipData) == FileOperationService.OPERATION_COPY);

        if (!DragAndDropHelper.canCopyTo(event.getLocalState(), getDestination(v))) {
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
        mClipper.copyFromClipData(
                dst,
                mState.stack,
                clipData,
                mInjector.dialogs::showFileOperationStatus);
        return true;
    }

    DocumentInfo getDestination(View v) {
        String id = getModelId(v);
        if (id != null) {
            Cursor dstCursor = mModel.getItem(id);
            if (dstCursor == null) {
                Log.w(TAG, "Invalid destination. Can't obtain cursor for modelId: " + id);
                return null;
            }
            return DocumentInfo.fromDirectoryCursor(dstCursor);
        }

        if (v == mRecView) {
            return mState.stack.peek();
        }

        return null;
    }

    @Override
    public void setDropTargetHighlight(View v, Object localState, boolean highlight) {
        // Note: use exact comparison - this code is searching for views which are children of
        // the RecyclerView instance in the UI.
        if (v.getParent() == mRecView) {
            DocumentHolder holder = getDocumentHolder(v);
            if (holder != null) {
                if (!highlight) {
                    holder.resetDropHighlight();
                } else {
                    holder.setDroppableHighlight(
                            DragAndDropHelper.canCopyTo(localState, getDestination(v)));
                }
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

    private @Nullable DocumentHolder getDocumentHolder(View v) {
        RecyclerView.ViewHolder vh = mRecView.getChildViewHolder(v);
        if (vh instanceof DocumentHolder) {
            return (DocumentHolder) vh;
        }
        return null;
    }

    // TODO: Move to activities when Model becomes activity level object.
    private boolean canSelect(DocumentDetails doc) {
        return canSetSelectionState(doc.getModelId(), true);
    }

    // TODO: Move to activities when Model becomes activity level object.
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
            return mInjector.config.canSelectType(docMimeType, docFlags, mState);
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

        df.mLocalState.update(root, doc, query);
        df.getLoaderManager().restartLoader(LOADER_ID, null, df.mLoaderCallbacks);
    }

    public static void reload(FragmentManager fm, int type, RootInfo root, DocumentInfo doc,
            String query) {
        if (DEBUG) Log.d(TAG, "Reloading directory: " + DocumentInfo.debugString(doc));
        DirectoryFragment df = get(fm);

        df.mLocalState.update(type, root, doc, query);
        df.getLoaderManager().restartLoader(LOADER_ID, null, df.mLoaderCallbacks);
    }

    public static void create(
            FragmentManager fm,
            int type,
            RootInfo root,
            @Nullable DocumentInfo doc,
            String query,
            @AnimationType int anim) {

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

        final DocumentInfo doc = mState.stack.peek();
        mActions.refreshDocument(doc, (boolean refreshSupported) -> {
            if (refreshSupported) {
                mRefreshLayout.setRefreshing(false);
            } else {
                // If Refresh API isn't available, we will explicitly reload the loader
                getLoaderManager().restartLoader(LOADER_ID, null, mLoaderCallbacks);
            }
        });
    }

    private final class ModelUpdateListener implements EventListener<Model.Update> {

        @Override
        public void accept(Model.Update update) {
            if (DEBUG) Log.d(TAG, "Received model update. Loading=" + mModel.isLoading());

            mProgressBar.setVisibility(mModel.isLoading() ? View.VISIBLE : View.GONE);

            mAdapter.notifyDataSetChanged();

            if (!mModel.isLoading()) {
                mActivity.notifyDirectoryLoaded(
                        mModel.doc != null ? mModel.doc.derivedUri : null);
            }
        }
    }

    private final class AdapterEnvironment implements DocumentsAdapter.Environment {

        @Override
        public Context getContext() {
            return mActivity;
        }

        @Override
        public State getDisplayState() {
            return mState;
        }

        @Override
        public boolean isInSearchMode() {
            return mLocalState.mSearchMode;
        }

        @Override
        public Model getModel() {
            return mModel;
        }

        @Override
        public int getColumnCount() {
            return mColumnCount;
        }

        @Override
        public boolean isSelected(String id) {
            return mSelectionMgr.getSelection().contains(id);
        }

        @Override
        public boolean isDocumentEnabled(String mimeType, int flags) {
            return mInjector.config.isDocumentEnabled(mimeType, flags, mState);
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
    }

    private final class LoaderBindings implements LoaderCallbacks<DirectoryResult> {

        @Override
        public Loader<DirectoryResult> onCreateLoader(int id, Bundle args) {
            Context context = getActivity();

            Uri contentsUri;
            switch (mLocalState.mType) {
                case TYPE_NORMAL:
                    contentsUri = mLocalState.mSearchMode
                            ? DocumentsContract.buildSearchDocumentsUri(
                                    mLocalState.mRoot.authority,
                                    mLocalState.mRoot.rootId,
                                    mLocalState.mQuery)
                            : DocumentsContract.buildChildDocumentsUri(
                                    mLocalState.mDocument.authority,
                                    mLocalState.mDocument.documentId);

                    if (mInjector.config.managedModeEnabled(mState.stack)) {
                        contentsUri = DocumentsContract.setManageMode(contentsUri);
                    }

                    if (DEBUG) Log.d(TAG,
                            "Creating new directory loader for: "
                            + DocumentInfo.debugString(mLocalState.mDocument));

                    return new DirectoryLoader(
                            context,
                            mLocalState.mRoot,
                            mLocalState.mDocument,
                            contentsUri,
                            mState.sortModel,
                            mReloadLock,
                            mLocalState.mSearchMode);

                case TYPE_RECENT_OPEN:
                    if (DEBUG) Log.d(TAG, "Creating new loader recents.");
                    final RootsAccess roots = DocumentsApplication.getRootsCache(context);
                    return new RecentsLoader(context, roots, mState);

                default:
                    throw new IllegalStateException("Unknown type " + mLocalState.mType);
            }
        }

        @Override
        public void onLoadFinished(Loader<DirectoryResult> loader, DirectoryResult result) {
            if (DEBUG) Log.d(TAG, "Loader has finished for: "
                    + DocumentInfo.debugString(mLocalState.mDocument));
            assert(result != null);

            if (!isAdded()) return;

            if (mLocalState.mSearchMode) {
                Metrics.logUserAction(getContext(), Metrics.USER_ACTION_SEARCH);
            }

            mAdapter.notifyDataSetChanged();
            mModel.update(result);

            updateLayout(mState.derivedMode);

            if (mRestoredSelection != null) {
                mSelectionMgr.restoreSelection(mRestoredSelection);
                // Note, we'll take care of cleaning up retained selection
                // in the selection handler where we already have some
                // specialized code to handle when selection was restored.
            }

            // Restore any previous instance state
            final SparseArray<Parcelable> container =
                    mState.dirConfigs.remove(mLocalState.getConfigKey());
            final int curSortedDimensionId = mState.sortModel.getSortedDimensionId();

            final SortDimension curSortedDimension =
                    mState.sortModel.getDimensionById(curSortedDimensionId);
            if (container != null
                    && !getArguments().getBoolean(Shared.EXTRA_IGNORE_STATE, false)) {
                getView().restoreHierarchyState(container);
            } else if (mLocalState.mLastSortDimensionId != curSortedDimension.getId()
                    || mLocalState.mLastSortDimensionId == SortModel.SORT_DIMENSION_ID_UNKNOWN
                    || mLocalState.mLastSortDirection != curSortedDimension.getSortDirection()) {
                // Scroll to the top if the sort order actually changed.
                mRecView.smoothScrollToPosition(0);
            }

            mLocalState.mLastSortDimensionId = curSortedDimension.getId();
            mLocalState.mLastSortDirection = curSortedDimension.getSortDirection();

            if (mRefreshLayout.isRefreshing()) {
                new Handler().postDelayed(
                        () -> mRefreshLayout.setRefreshing(false),
                        REFRESH_SPINNER_TIMEOUT);
            }
        }

        @Override
        public void onLoaderReset(Loader<DirectoryResult> loader) {
            if (DEBUG) Log.d(TAG, "Resetting loader for: "
                        + DocumentInfo.debugString(mLocalState.mDocument));
            mModel.onLoaderReset();

            mRefreshLayout.setRefreshing(false);
        }
    }
}
