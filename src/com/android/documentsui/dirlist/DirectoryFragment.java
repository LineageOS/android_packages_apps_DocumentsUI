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

import static android.content.Context.RECEIVER_NOT_EXPORTED;

import static androidx.core.content.IntentCompat.getParcelableArrayListExtra;

import static com.android.documentsui.ActionHandler.VIEW_TYPE_NONE;
import static com.android.documentsui.ActionHandler.VIEW_TYPE_PREVIEW;
import static com.android.documentsui.ActionHandler.VIEW_TYPE_REGULAR;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.VERBOSE;
import static com.android.documentsui.base.SharedMinimal.redact;
import static com.android.documentsui.base.State.ACTION_BROWSE;
import static com.android.documentsui.base.State.MODE_GRID;
import static com.android.documentsui.base.State.MODE_LIST;
import static com.android.documentsui.dirlist.SummaryProviderManagerKt.displaySummaryForRoot;
import static com.android.documentsui.services.FileOperationService.ACTION_PROGRESS;
import static com.android.documentsui.services.FileOperationService.EXTRA_PROGRESS;
import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;
import static com.android.documentsui.services.FileOperationService.OPERATION_TRASH;
import static com.android.documentsui.services.FileOperationService.OPERATION_UNPACK;
import static com.android.documentsui.services.Job.STATE_COMPLETED;
import static com.android.documentsui.util.FlagUtils.isDesktopFileHandlingFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isDesktopUxPhase2FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isHomeScreenFilesFlagEnabled;
import static com.android.documentsui.util.FlagUtils.isSearchV2Enabled;
import static com.android.documentsui.util.FlagUtils.isSyncStateEnabled;
import static com.android.documentsui.util.FlagUtils.isTrashFlowEnabled;
import static com.android.documentsui.util.FlagUtils.isUseFileSummaryEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.FlagUtils.isUseNewOpenWithEnabled;
import static com.android.documentsui.util.FlagUtils.isZipNgFlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserProperties;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.annotation.DimenRes;
import androidx.annotation.FractionRes;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.SelectionTracker.SelectionPredicate;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.RecyclerListener;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.ActionModeController;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.ContentLock;
import com.android.documentsui.DocsSelectionHelper.DocDetailsLookup;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.DragHoverListener;
import com.android.documentsui.FocusManager;
import com.android.documentsui.Injector;
import com.android.documentsui.Injector.ContentScoped;
import com.android.documentsui.Injector.Injected;
import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.Model;
import com.android.documentsui.ProfileTabsController;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.SelectionBarController;
import com.android.documentsui.ThumbnailCache;
import com.android.documentsui.TimeoutTask;
import com.android.documentsui.base.DocumentFilters;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.PathExtractor;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;
import com.android.documentsui.base.UserId;
import com.android.documentsui.breadcrumbs.BreadcrumbController;
import com.android.documentsui.clipping.ClipStore;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.dirlist.AnimationView.OnSizeChangedListener;
import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.services.JobProgress;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.ui.Snackbars;
import com.android.documentsui.util.FileUtils;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Objects;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Display the documents inside a single directory.
 */
public class DirectoryFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    static final int TYPE_NORMAL = 1;
    static final int TYPE_RECENT_OPEN = 2;
    private static final Random RANDOM = new Random();

    @IntDef(flag = true, value = {
            REQUEST_COPY_DESTINATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestCode {
    }

    public static final int REQUEST_COPY_DESTINATION = 1;

    static final String TAG = "DirectoryFragment";

    private static final int CACHE_EVICT_LIMIT = 100;
    private static final int REFRESH_SPINNER_TIMEOUT = 500;
    private static final int PROVIDER_MAX_RETRIES = 10;
    private static final long PROVIDER_TEST_DELAY = 4000;
    private static final String ACTION_MEDIA_REMOVED = "android.intent.action.MEDIA_REMOVED";
    private static final String ACTION_MEDIA_MOUNTED = "android.intent.action.MEDIA_MOUNTED";
    private static final String ACTION_MEDIA_EJECT = "android.intent.action.MEDIA_EJECT";

    @VisibleForTesting public static final int TICK_VISIBLE_DURATION_MS = 1200;

    private BaseActivity mActivity;

    private State mState;
    private Model mModel;
    private final EventListener<Model.Update> mModelUpdateListener = new ModelUpdateListener();
    private final DocumentsAdapter.Environment mAdapterEnv = new AdapterEnvironment();

    @Injected
    @ContentScoped
    private Injector<?> mInjector;

    @Injected
    @ContentScoped
    private SelectionTracker<String> mSelectionMgr;

    @Injected
    @ContentScoped
    private FocusManager mFocusManager;

    @Injected
    @ContentScoped
    private ActionHandler mActions;

    // Returns null when the `use_material3` flag is enabled.
    @Injected @ContentScoped private @Nullable ActionModeController mActionModeController;

    // Returns null when the `use_material3` flag is disabled.
    @Injected @ContentScoped private @Nullable SelectionBarController mSelectionBarController;

    @Injected
    @ContentScoped
    private ProfileTabsController mProfileTabsController;

    private DocDetailsLookup mDetailsLookup;
    private SelectionMetadata mSelectionMetadata;
    private KeyInputHandler mKeyListener;
    private @Nullable DragHoverListener mDragHoverListener;
    private AnimationView mRootView;
    private IconHelper mIconHelper;
    private SwipeRefreshLayout mRefreshLayout;
    private RecyclerView mRecView;
    private GridEvenSpacingDecoration mGridEvenSpacingDecoration;
    private DocumentsAdapter mAdapter;
    private DocumentClipper mClipper;
    private GridLayoutManager mLayout;
    private int mColumnCount = 1;  // This will get updated when layout changes.
    private int mColumnUnit = 1;

    // When the `DirectoryFragment` is first attached it kicks of a document load, unfortunately it
    // happens again in onStart (via onRefresh) which ends up kicking off double loaders. This
    // ensures the second one is not kicked off if the first has happened.
    private boolean mDocumentsInitialLoad = false;

    private float mLiveScale = 1.0f;
    private @ViewMode int mMode;
    private int mAppBarHeight;
    private int mBottomOverlayHeight;

    private View mProgressBar;

    private DirectoryState mLocalState;

    private Handler mHandler;
    private Runnable mProviderTestRunnable;

    private @Nullable PathExtractor mPathExtractor;
    private @Nullable String mSelectedItemKey = null;

    private AtomicInteger mVersion = new AtomicInteger(0);

    private final Observer<Boolean> mSummaryObserver =
            new Observer<>() {
                @Override
                public void onChanged(Boolean isEnabled) {
                    mActions.loadDocumentsForCurrentStack();
                }
            };

    // getActivity() from Fragment is final and can't be override/mock in the test, so we extract
    // all getActivity() to this method so we can't override it in the unit test.
    protected BaseActivity getBaseActivity() {
        return (BaseActivity) getActivity();
    }

    // Note, we use !null to indicate that selection was restored (from rotation).
    // So don't fiddle with this field unless you've got the bigger picture in mind.
    private @Nullable Bundle mRestoredState;

    // Blocks loading/reloading of content while user is actively making selection.
    private ContentLock mContentLock = new ContentLock();

    @VisibleForTesting @Nullable ItemDecorationInvalidator mItemDecorationInvalidator;
    private long mLastActivationTapTime;

    private SortModel.UpdateListener mSortListener = (model, updateType) -> {
        // Only when sort order has changed do we need to trigger another loading.
        if ((updateType & SortModel.UPDATE_TYPE_SORTING) != 0) {
            mActions.loadDocumentsForCurrentStack();
        }
    };

    private final Runnable mOnDisplayStateChanged = this::onDisplayStateChanged;

    private final ViewTreeObserver.OnPreDrawListener mToolbarPreDrawListener = () -> {
        final boolean appBarHeightChanged = mAppBarHeight != getAppBarLayoutHeight();
        if (appBarHeightChanged || mBottomOverlayHeight != getBottomOverlayHeight()) {
            updateLayout(mState.derivedMode);

            if (appBarHeightChanged) {
                scrollToTop();
            }
            return false;
        }
        return true;
    };

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (SdkLevel.isAtLeastV()
                    && mState.configStore.isPrivateSpaceInDocsUIEnabled()) {
                profileStatusReceiverPostV(intent, action);
            } else {
                profileStatusReceiverPreV(intent, action);
            }
        }

        private void profileStatusReceiverPostV(Intent intent, String action) {
            if (!SdkLevel.isAtLeastV()) return;
            if (!isProfileStatusAction(action)) return;
            UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
            UserId userId = UserId.of(userHandle);
            UserManager userManager = mActivity.getSystemService(UserManager.class);
            if (userManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            UserProperties userProperties = userManager.getUserProperties(userHandle);
            if (userProperties.getShowInQuietMode()
                    == UserProperties.SHOW_IN_QUIET_MODE_PAUSED) {
                if (Objects.equal(mActivity.getSelectedUser(), userId)) {
                    // We only need to refresh the layout when the selected user is equal to
                    // the received profile user.
                    onPausedProfileStatusChange(action, userId);
                }
                return;
            }
            if (userProperties.getShowInQuietMode()
                    == UserProperties.SHOW_IN_QUIET_MODE_HIDDEN) {
                onHiddenProfileStatusChange(action, userId);
            }
        }

        private void profileStatusReceiverPreV(Intent intent, String action) {
            if (!isManagedProfileAction(action)) return;
            UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER);
            UserId userId = UserId.of(userHandle);
            if (Objects.equal(mActivity.getSelectedUser(), userId)) {
                // We only need to refresh the layout when the selected user is equal to the
                // received profile user.
                onPausedProfileStatusChange(action, userId);
            }
        }
    };

    /**
     * This observer ensures that, when the enclosing DirectoryFragment is showing some search
     * results and when a destructive job (file deletion or trashing) finishes, the search results
     * are refreshed.
     */
    @VisibleForTesting
    protected final BroadcastReceiver mJobProgressObserver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (!isUseMaterial3FlagEnabled()
                            || mActivity == null
                            || !mActivity.isSearching()) {
                        return;
                    }

                    assert ACTION_PROGRESS.equals(intent.getAction());
                    final Collection<JobProgress> progresses =
                            getParcelableArrayListExtra(intent, EXTRA_PROGRESS, JobProgress.class);
                    assert progresses != null;
                    for (JobProgress p : progresses) {
                        if (p.state == STATE_COMPLETED
                                && (p.operationType == OPERATION_DELETE
                                        || p.operationType == OPERATION_TRASH)) {
                            onRefresh();
                            return;
                        }
                    }
                }
            };

    private void onPausedProfileStatusChange(String action, UserId userId) {
        if (Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action)
                || (SdkLevel.isAtLeastV() && Intent.ACTION_PROFILE_UNAVAILABLE.equals(action))) {
            // If the managed/paused profile is turned off, we need to refresh the directory
            // to update the UI to show an appropriate error message.
            if (mProviderTestRunnable != null) {
                mHandler.removeCallbacks(mProviderTestRunnable);
                mProviderTestRunnable = null;
            }
            onRefresh();
            return;
        }

        // When the managed/paused profile becomes available, the provider may not be available
        // immediately, we need to check if it is ready before we reload the content.
        if (Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)
                || (SdkLevel.isAtLeastV() && Intent.ACTION_PROFILE_AVAILABLE.equals(action))) {
            checkUriAndScheduleCheckIfNeeded(userId);
        }
    }

    private void onHiddenProfileStatusChange(String action, UserId userId) {
        mActivity.updateRecentsSetting();
        if (Intent.ACTION_PROFILE_UNAVAILABLE.equals(action)) {
            if (mProviderTestRunnable != null) {
                mHandler.removeCallbacks(mProviderTestRunnable);
                mProviderTestRunnable = null;
            }
            if (!mActivity.isSearchExpanded()) {
                if (mActivity.getLastSelectedUser() != null
                        && mActivity.getLastSelectedUser().equals(userId)) {
                    mState.stack.reset(mActivity.getInitialStack());
                }
                mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
            }
        }
    }

    private final BroadcastReceiver mSdCardBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            onRefresh();
        }
    };

    private IntentFilter getSdCardStateChangeFilter() {
        IntentFilter sdCardStateChangeFilter = new IntentFilter();
        sdCardStateChangeFilter.addAction(ACTION_MEDIA_REMOVED);
        sdCardStateChangeFilter.addAction(ACTION_MEDIA_MOUNTED);
        sdCardStateChangeFilter.addAction(ACTION_MEDIA_EJECT);
        sdCardStateChangeFilter.addDataScheme("file");
        return sdCardStateChangeFilter;
    }

    private void checkUriAndScheduleCheckIfNeeded(UserId userId) {
        RootInfo currentRoot = mActivity.getCurrentRoot();
        DocumentInfo currentDoc = mActivity.getDisplayState().stack.peek();
        Uri uri = getCurrentUri(currentRoot, currentDoc);
        if (isProviderAvailable(uri, userId) || mActivity.isInRecents()) {
            if (mProviderTestRunnable != null) {
                mHandler.removeCallbacks(mProviderTestRunnable);
                mProviderTestRunnable = null;
            }
            mHandler.post(this::onRefresh);
        } else {
            checkUriWithDelay(/* numOfRetries= */1, uri, userId);
        }
    }

    private void checkUriWithDelay(int numOfRetries, Uri uri, UserId userId) {
        mProviderTestRunnable = () -> {
            RootInfo currentRoot = mActivity.getCurrentRoot();
            DocumentInfo currentDoc = mActivity.getDisplayState().stack.peek();
            if (mActivity.getSelectedUser().equals(userId)
                    && uri.equals(getCurrentUri(currentRoot, currentDoc))) {
                if (isProviderAvailable(uri, userId)
                        || userId.isQuietModeEnabled(mActivity)
                        || numOfRetries >= PROVIDER_MAX_RETRIES) {
                    // We stop the recursive check when
                    // 1. the provider is available
                    // 2. the profile is in quiet mode, i.e. provider will not be available
                    // 3. after maximum retries
                    onRefresh();
                    mProviderTestRunnable = null;
                } else {
                    Log.d(TAG, "Provider is not available. Retry after " + PROVIDER_TEST_DELAY);
                    checkUriWithDelay(numOfRetries + 1, uri, userId);
                }
            }
        };
        mHandler.postDelayed(mProviderTestRunnable, PROVIDER_TEST_DELAY);
    }

    private Uri getCurrentUri(RootInfo root, @Nullable DocumentInfo doc) {
        String authority = doc == null ? root.authority : doc.authority;
        String documentId = doc == null ? root.documentId : doc.documentId;
        return DocumentsContract.buildDocumentUri(authority, documentId);
    }

    private boolean isProviderAvailable(Uri uri, UserId userId) {
        try (ContentProviderClient userClient =
                     DocumentsApplication.acquireUnstableProviderOrThrow(
                             userId.getContentResolver(mActivity), uri.getAuthority())) {
            Cursor testCursor = userClient.query(uri, /* projection= */ null,
                    /* queryArgs= */null, /* cancellationSignal= */ null);
            if (testCursor != null) {
                return true;
            }
        } catch (Exception e) {
            // Provider is not available. Ignore.
        }
        return false;
    }

    private boolean isProfileStatusAction(String action) {
        if (!SdkLevel.isAtLeastV()) return isManagedProfileAction(action);
        return Intent.ACTION_PROFILE_AVAILABLE.equals(action)
                || Intent.ACTION_PROFILE_UNAVAILABLE.equals(action)
                || Intent.ACTION_PROFILE_ADDED.equals(action)
                || Intent.ACTION_PROFILE_REMOVED.equals(action);
    }

    private static boolean isManagedProfileAction(String action) {
        return Intent.ACTION_MANAGED_PROFILE_UNLOCKED.equals(action)
                || Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action);
    }

    private OnSizeChangedListener mOnSizeChangedListener =
            new AnimationView.OnSizeChangedListener() {
                @Override
                public void onSizeChanged() {
                    if (isUseMaterial3FlagEnabled() && mState.derivedMode != MODE_LIST) {
                        // Update the grid layout when the window size changes.
                        updateLayout(mState.derivedMode);
                    }
                }
            };

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mHandler = new Handler(Looper.getMainLooper());
        mActivity = getBaseActivity();
        if (isSearchV2Enabled()) {
            mPathExtractor = new PathExtractor(mActivity, mActivity.getProvidersAccess());
        }
        mRootView =
                (AnimationView)
                        inflater.inflate(getRes(R.layout.fragment_directory), container, false);
        if (isUseMaterial3FlagEnabled()) {
            mRootView.addOnSizeChangedListener(mOnSizeChangedListener);
        }

        mProgressBar = mRootView.findViewById(getRes(R.id.progressbar));
        assert mProgressBar != null;

        mRecView = (RecyclerView) mRootView.findViewById(getRes(R.id.dir_list));
        mRecView.setRecyclerListener(
                new RecyclerListener() {
                    @Override
                    public void onViewRecycled(ViewHolder holder) {
                        cancelThumbnailTask(holder.itemView);
                    }
                });

        mRefreshLayout = (SwipeRefreshLayout) mRootView.findViewById(getRes(R.id.refresh_layout));
        mRefreshLayout.setOnRefreshListener(this);
        mRecView.setItemAnimator(new DirectoryItemAnimator());

        mInjector = mActivity.getInjector();
        // Initially, this selection tracker (delegator) uses a stub implementation, so it must be
        // updated (reset) when necessary things are ready.
        mSelectionMgr = mInjector.selectionMgr;
        mModel = mInjector.getModel();
        mModel.reset();

        mInjector.actions.registerDisplayStateChangedListener(mOnDisplayStateChanged);

        mClipper = DocumentsApplication.getDocumentClipper(getContext());
        if (mInjector.config.dragAndDropEnabled()) {
            DirectoryDragListener listener = new DirectoryDragListener(
                    new DragHost<>(
                            mActivity,
                            DocumentsApplication.getDragAndDropManager(mActivity),
                            mSelectionMgr,
                            mInjector.actions,
                            mActivity.getDisplayState(),
                            mInjector.dialogs,
                            mActivity.getDocumentsAccess(),
                            (View v) -> {
                                return getModelId(v) != null;
                            },
                            this::getDocumentHolder,
                            this::getDestination
                    ));
            mDragHoverListener = DragHoverListener.create(listener, mRecView);
        }
        // Make the recycler and the empty views responsive to drop events when allowed.
        mRecView.setOnDragListener(mDragHoverListener);

        setPreDrawListenerEnabled(true);

        // Register an observer on the state of SummaryProviderManager.
        // When the summary provider is enabled/disabled we refresh the file list to make sure the
        // description column is shown/hidden. OnLoadFinished in AbstractActionHandler kicks off an
        // update in SummariesViewModel and a full redraw of RecyclerView which has the description
        // column. notifyDirectoryLoaded here updates the column headers accordingly as well.
        if (isUseFileSummaryEnabled() && mInjector.getSummaryProviderManager() != null) {
            mInjector
                    .getSummaryProviderManager()
                    .isEnabledLiveData()
                    .observe(this, mSummaryObserver);
        }

        return mRootView;
    }

    @VisibleForTesting
    public void setDragSpringTimeoutForTest(int testDragSpringTimeout) {
        if (mDragHoverListener != null) {
            mDragHoverListener.setDragSpringTimeoutForTest(testDragSpringTimeout);
        }
    }

    @Override
    public void onDestroyView() {
        mInjector.actions.unregisterDisplayStateChangedListener(mOnDisplayStateChanged);

        if (isUseMaterial3FlagEnabled()) {
            getContext().unregisterReceiver(mJobProgressObserver);
        }

        if (mState.supportsCrossProfile()) {
            LocalBroadcastManager.getInstance(mActivity).unregisterReceiver(mReceiver);
            if (mProviderTestRunnable != null) {
                mHandler.removeCallbacks(mProviderTestRunnable);
            }
        }
        getContext().unregisterReceiver(mSdCardBroadcastReceiver);

        // Cancel any outstanding thumbnail requests
        final int count = mRecView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = mRecView.getChildAt(i);
            cancelThumbnailTask(view);
        }

        mModel.removeUpdateListener(mModelUpdateListener);
        mModel.removeUpdateListener(mAdapter.getModelUpdateListener());
        if (isUseFileSummaryEnabled()) {
            mModel.removeSummaryUpdateListener(mAdapter);
        }
        setPreDrawListenerEnabled(false);

        if (isUseMaterial3FlagEnabled()) {
            mRootView.removeOnSizeChangedListener(mOnSizeChangedListener);
        }

        if (mItemDecorationInvalidator != null) {
            mItemDecorationInvalidator.teardown();
            mItemDecorationInvalidator = null;
        }

        if (isSyncStateEnabled()) {
            mInjector.networkMonitor.removeNetworkListener(mAdapter.getNetworkListener());
        }

        if (isUseFileSummaryEnabled() && mInjector.getSummaryProviderManager() != null) {
            mInjector
                    .getSummaryProviderManager()
                    .isEnabledLiveData()
                    .removeObserver(mSummaryObserver);
        }

        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mState = mActivity.getDisplayState();

        if (isUseMaterial3FlagEnabled()) {
            mGridEvenSpacingDecoration = new GridEvenSpacingDecoration();
            if (mState.derivedMode == MODE_GRID) {
                // Ensure items are spaced evenly in the grid layout.
                mRecView.addItemDecoration(mGridEvenSpacingDecoration);
            }
        }

        // Read arguments when object created for the first time.
        // Restore state if fragment recreated.
        Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState;
        mRestoredState = args;

        mLocalState = new DirectoryState();
        mLocalState.restore(args);
        if (mLocalState.mSelectionId == null) {
            mLocalState.mSelectionId = Integer.toHexString(System.identityHashCode(mRecView));
        }

        mIconHelper = new IconHelper(mActivity, MODE_GRID, mState.supportsCrossProfile(),
                mState.configStore);

        mAdapter = getModelBackedDocumentsAdapter();

        if (isSyncStateEnabled()) {
            mInjector.networkMonitor.addNetworkListener(mAdapter.getNetworkListener());
        }

        mRecView.setAdapter(mAdapter);

        // When mFocusManager.onLayoutCompleted() is called inside the GridLayoutManager's
        // onLayoutCompleted(), the newly added document (e.g.  after new folder creation)
        // hasn't appeared in the list yet, which makes focusing on the document fail. Instead, we
        // need to call it after the model update (e.g. ModelUpdateListener below).
        mLayout =
                isUseMaterial3FlagEnabled()
                        ? new GridLayoutManager(getContext(), mColumnCount)
                        : new GridLayoutManager(getContext(), mColumnCount) {
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
        if (isUseFileSummaryEnabled()) {
            mModel.addSummaryUpdateListener(mAdapter);
        }

        SelectionPredicate<String> selectionPredicate =
                new DocsSelectionPredicate(mInjector.config, mState, mModel, mRecView, mAdapterEnv);

        mFocusManager = mInjector.getFocusManager(mRecView, mModel);
        mActions = mInjector.getActionHandler(mContentLock);

        if (isUseFileSummaryEnabled()) {
            mActions.bindSummariesViewModel(this, createSummariesViewModel());
        }

        mRecView.setAccessibilityDelegateCompat(
                new AccessibilityEventRouter(mRecView,
                        (View child) -> onAccessibilityClick(child),
                        (View child) -> onAccessibilityLongClick(child), mState.action));
        mSelectionMetadata =
                new SelectionMetadata(
                        mModel::getItem,
                        (String modelId) -> {
                            DocumentInfo doc = mModel.getDocument(modelId);
                            if (doc != null) {
                                return FileUtils.countOpeningApps(
                                        doc, mActivity.getPackageManager());
                            }
                            return 0;
                        },
                        mAdapterEnv::isContentAvailable);
        mDetailsLookup = new DocsItemDetailsLookup(mRecView);

        DragStartListener dragStartListener =
                mInjector.config.dragAndDropEnabled()
                        ? DragStartListener.create(
                                mIconHelper,
                                mModel,
                                mSelectionMgr,
                                mSelectionMetadata,
                                mState,
                                this::getModelId,
                                mRecView::findChildViewUnder,
                                mAdapterEnv::isContentAvailable,
                                DocumentsApplication.getDragAndDropManager(mActivity),
                                mActivity.getDocumentsAccess())
                        : DragStartListener.STUB;

        {
            // Limiting the scope of the localTracker so nobody uses it.
            // This block initializes/updates the global SelectionTracker held in mSelectionMgr.
            SelectionTracker<String> localTracker =
                    new SelectionTracker.Builder<>(
                                    mLocalState.mSelectionId,
                                    mRecView,
                                    new DocsStableIdProvider(mAdapter),
                                    mDetailsLookup,
                                    StorageStrategy.createStringStorage())
                            .withBandOverlay(getRes(R.drawable.band_select_overlay))
                            .withFocusDelegate(mFocusManager)
                            .withOnDragInitiatedListener(dragStartListener::onDragEvent)
                            .withOnContextClickListener(this::onContextMenuClick)
                            .withOnItemActivatedListener(this::onItemActivated)
                            .withOperationMonitor(mContentLock.getMonitor())
                            .withSelectionPredicate(selectionPredicate)
                            .withGestureTooltypes(
                                    MotionEvent.TOOL_TYPE_FINGER, MotionEvent.TOOL_TYPE_STYLUS)
                            .build();
            mInjector.updateSharedSelectionTracker(localTracker);
        }

        mSelectionMgr.addObserver(mSelectionMetadata);
        if (isSearchV2Enabled()) {
            mSelectionMgr.addObserver(
                    new SelectionTracker.SelectionObserver<>() {
                        @Override
                        public void onSelectionChanged() {
                            handleSearchResultSelection();
                        }

                        @Override
                        public void onSelectionRestored() {
                            // When selection is restored (e.g. after window size change), it
                            // doesn't trigger onSelectionChanged(), so we need to call the same
                            // handleSearchResultSelection() here.
                            handleSearchResultSelection();
                        }
                    });
        }

        // Construction of the input handlers is non trivial, so to keep logic clear,
        // and code flexible, and DirectoryFragment small, the construction has been
        // moved off into a separate class.
        InputHandlers handlers = new InputHandlers(
                mActions,
                mSelectionMgr,
                selectionPredicate,
                mFocusManager,
                mRecView);

        // This little guy gets added to each Holder, so that we can be notified of key events
        // on RecyclerView items.
        mKeyListener = handlers.createKeyHandler();

        if (DEBUG) {
            new ScaleHelper(this.getContext(), mInjector.features, this::scaleLayout)
                    .attach(mRecView);
        }

        new RefreshHelper(mRefreshLayout::setEnabled)
                .attach(mRecView);

        if (isUseMaterial3FlagEnabled()) {
            mSelectionBarController =
                    mInjector.getSelectionBarController(
                            mSelectionMetadata, this::handleMenuItemClick);
            mSelectionMgr.addObserver(mSelectionBarController);
        } else {
            mActionModeController =
                    mInjector.getActionModeController(
                            mSelectionMetadata, this::handleMenuItemClick);
            assert (mActionModeController != null);
            mSelectionMgr.addObserver(mActionModeController);
        }

        mProfileTabsController = mInjector.profileTabsController;
        mSelectionMgr.addObserver(mProfileTabsController);

        final ActivityManager am = (ActivityManager) mActivity.getSystemService(
                Context.ACTIVITY_SERVICE);
        boolean svelte = am.isLowRamDevice() && (mState.stack.isRecents());
        mIconHelper.setThumbnailsEnabled(!svelte);

        // If mDocument is null, we sort it by last modified by default because it's in Recents.
        final boolean prefersLastModified =
                (mLocalState.mDocument == null)
                        || mLocalState.mDocument.prefersSortByLastModified();
        // Call this before adding the listener to avoid restarting the loader one more time
        mState.sortModel.setDefaultDimension(
                prefersLastModified
                        ? SortModel.SORT_DIMENSION_ID_DATE
                        : SortModel.SORT_DIMENSION_ID_TITLE);

        // Kick off loader at least once
        mActions.loadDocumentsForCurrentStack();
        mDocumentsInitialLoad = isSearchV2Enabled();

        if (mState.supportsCrossProfile()) {
            final IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNLOCKED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
            if (SdkLevel.isAtLeastV()) {
                filter.addAction(Intent.ACTION_PROFILE_AVAILABLE);
                filter.addAction(Intent.ACTION_PROFILE_UNAVAILABLE);
                filter.addAction(Intent.ACTION_PROFILE_ADDED);
                filter.addAction(Intent.ACTION_PROFILE_REMOVED);
            }
            // DocumentsApplication will resend the broadcast locally after roots are updated.
            // Register to a local broadcast manager to avoid this fragment from updating before
            // roots are updated.
            LocalBroadcastManager.getInstance(mActivity).registerReceiver(mReceiver, filter);
        }

        if (isUseMaterial3FlagEnabled()) {
            getContext()
                    .registerReceiver(
                            mJobProgressObserver,
                            new IntentFilter(ACTION_PROGRESS),
                            RECEIVER_NOT_EXPORTED);
        }

        getContext().registerReceiver(mSdCardBroadcastReceiver, getSdCardStateChangeFilter());
    }

    private DocumentsAdapter getModelBackedDocumentsAdapter() {
        if (SdkLevel.isAtLeastS() && mState.configStore.isPrivateSpaceInDocsUIEnabled()) {
            return new DirectoryAddonsAdapter(
                    mAdapterEnv, new ModelBackedDocumentsAdapter(mAdapterEnv, mIconHelper,
                    mInjector.fileTypeLookup, mState.configStore),
                    UserId.CURRENT_USER,
                    mActivity.getSelectedUser(),
                    DocumentsApplication.getUserManagerState(getContext()).getUserIdToLabelMap(),
                    getContext().getSystemService(UserManager.class), mState.configStore);
        }
        return new DirectoryAddonsAdapter(
                        mAdapterEnv,
                        new ModelBackedDocumentsAdapter(mAdapterEnv, mIconHelper,
                                mInjector.fileTypeLookup, mState.configStore),
                        mState.configStore);
    }

    @Override
    public void onStart() {
        super.onStart();

        // Add listener to update contents on sort model change
        mState.sortModel.addListener(mSortListener);
        // After SD card is formatted, we go out of the view and come back. Similarly when users
        // go out of the app to delete some files, we want to refresh the directory.
        onRefresh();
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

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mLocalState.save(outState);
        mSelectionMgr.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final MenuInflater inflater = getBaseActivity().getMenuInflater();

        final String modelId = getModelId(v);
        if (modelId == null) {
            // TODO: inject DirectoryDetails into MenuManager constructor
            // Since both classes are supplied by Activity and created
            // at the same time.
            mInjector.menuManager.inflateContextMenuForContainer(
                    menu, inflater, mSelectionMetadata);
        } else {
            mInjector.menuManager.inflateContextMenuForDocs(
                    menu, inflater, mSelectionMetadata);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        try {
            return handleMenuItemClick(item);
        } catch (Exception e) {
            Log.e(TAG, "Cannot handle menu item " + item.getItemId(), e);
            return false;
        }
    }

    /**
     * Handles a change in selection of search results. Checks if there are necessary conditions to
     * set the path (one element is selected, the user is searching or is in recents, and the code
     * has access to the breadcrumb controller). If so, it invokes in the background, fetching of
     * the document stack for the currently selected result. If successfully completed, updates the
     * path and click handler on the breadcrumb controller.
     */
    private void handleSearchResultSelection() {
        if (!isSearchV2Enabled()) {
            return;
        }
        BreadcrumbController controller = mInjector.getBreadcrumbController();
        if (controller == null) {
            return;
        }
        // If the path extractor or the breadcrumb model were not set up or the
        // activity is either null or indicating that it is neither in the
        // recents view or is searching, do not extract paths from the currently
        // selected files. The extracted path is used only in recent and search
        // results to show the location of the selected file.
        if (mPathExtractor == null || mActivity == null) {
            return;
        }
        if (!(mActivity.isSearching() || mActivity.isInRecents())) {
            // Just in case, since breadcrumb V2 is only visible while searching or in recents, and
            // we are neither searching nor in recent, hide the breadcrumb v2.
            setSearchResultBreadcrumbHidden(controller);
            return;
        }
        String selectedId = null;
        if (mSelectionMgr.getSelection().size() == 1) {
            for (String id : mSelectionMgr.getSelection()) {
                selectedId = id;
            }
        }
        if (selectedId == null) {
            setSearchResultBreadcrumbHidden(controller);
            return;
        }
        DocumentInfo info = mModel.getDocument(selectedId);
        if (info == null) {
            setSearchResultBreadcrumbHidden(controller);
            return;
        }
        if (selectedId.equals(mSelectedItemKey)) {
            if (DEBUG) {
                Log.d(TAG, "Skipping as selected ID key equal to the shown item key");
            }
            // Already in progress; skip.
            return;
        }
        mSelectedItemKey = selectedId;
        final int taskVersion = mVersion.incrementAndGet();
        ProviderExecutor.forAuthority(info.authority)
                .execute(
                        () -> {
                            try {
                                DocumentStack stack = mPathExtractor.getDocumentStack(info);
                                mHandler.post(
                                        () -> {
                                            showSearchResultBreadcrumb(
                                                    controller, stack, taskVersion);
                                        });
                            } catch (Exception e) {
                                if (DEBUG) {
                                    Log.d(TAG, "Failed to get stack for " + info, e);
                                }
                                setSearchResultBreadcrumbHidden(controller);
                            }
                        });
    }

    /**
     * For the given document stack updates the controller to both display the path and react to
     * clicks on that path.
     *
     * @param controller A non-null breadcrumb controller.
     * @param stack The stack to be used to create a path.
     */
    private void showSearchResultBreadcrumb(
            BreadcrumbController controller, DocumentStack stack, int version) {
        if (version != mVersion.get()) {
            return;
        }
        controller.getModel().setFromStack(stack);
        controller.setSearchBreadcrumbVisible(true);
        if (stack.getRoot() != null && stack.getRoot().isRecents()) {
            // No click consumer for recents, as it would only take us back to recents.
            return;
        }
        controller.setSearchBreadcrumbClickConsumer(
                (i) -> {
                    // Remove items after the i-th element.
                    while (stack.size() > i + 1) {
                        stack.pop();
                    }
                    mInjector.searchManager.onClose();
                    mState.stack.reset(stack);
                    mActivity.getNavigator().forceDirectoryToCurrentStack();
                });
    }

    /**
     * Hides the search result breadcrumb, using the handler. This is done so that the sequence of
     * hide/show search breadcrumb calls results in the correct state (hidden or visible) of the
     * breadcrumb.
     *
     * @param controller The controller that manages breadcrumb visibility.
     */
    public void setSearchResultBreadcrumbHidden(@NonNull BreadcrumbController controller) {
        if (isSearchV2Enabled()) {
            mVersion.incrementAndGet();
            mSelectedItemKey = null;
            controller.setSearchBreadcrumbVisible(false);
        }
    }

    private void onCopyDestinationPicked(int resultCode, Intent data) {

        FileOperation operation = mLocalState.claimPendingOperation();

        if (resultCode == FragmentActivity.RESULT_CANCELED || data == null) {
            // User pressed the back button or otherwise cancelled the destination pick. Don't
            // proceed with the copy.
            operation.dispose();
            return;
        }

        operation.setDestination(data.getParcelableExtra(Shared.EXTRA_STACK));
        final String jobId = FileOperations.createJobId();
        mInjector.dialogs.showProgressDialog(jobId, operation);
        FileOperations.start(
                mActivity,
                operation,
                mInjector.dialogs::showFileOperationStatus,
                jobId);
    }

    // TODO: Move to UserInputHander.
    protected boolean onContextMenuClick(MotionEvent e) {

        if (mDetailsLookup.isOverItemWithSelectionKey(e)) {
            View childView = mRecView.findChildViewUnder(e.getX(), e.getY());
            ViewHolder holder = mRecView.getChildViewHolder(childView);

            View view = holder.itemView;
            float x = e.getX() - view.getLeft();
            float y = e.getY() - view.getTop();
            mInjector.menuManager.showContextMenu(this, view, x, y);
            return true;
        }

        mInjector.menuManager.showContextMenu(this, mRecView, e.getX(), e.getY());
        return true;
    }

    @VisibleForTesting
    public boolean onItemActivated(ItemDetails<String> item, MotionEvent e) {
        if (isUseMaterial3FlagEnabled()) {
            if (e.getSource() == InputDevice.SOURCE_TOUCHSCREEN) {
                // If this tap happens within the double tap timeout, we consider it as a double tap
                // and will not activate the item because the previous tap should have already
                // activated the item. This is to avoid double tap on touchscreen accidentally
                // opening the file twice.
                if (SystemClock.uptimeMillis() - mLastActivationTapTime
                        < ViewConfiguration.getDoubleTapTimeout()) {
                    return true;
                }
                mLastActivationTapTime = SystemClock.uptimeMillis();
            }
        }

        if (item instanceof DocumentItemDetails) {
            final DocumentItemDetails docDetails = (DocumentItemDetails) item;
            if (docDetails.inPreviewIconHotspot(e)) return mActions.previewItem(item);
            if (startUnpackingArchive(docDetails)) return true;
        }

        // This was reverted as desktop file handling was rolling out until
        // we have default file opening apps out-of-the box.
        // Since the default file opening app uses a build flag, we're using
        // another flag that's rolling out in the same cycle to flag protect
        // the revert^2.
        if (isDesktopFileHandlingFlagEnabled() && isUseNewOpenWithEnabled()) {
            return mActions.openItem(item, VIEW_TYPE_REGULAR, VIEW_TYPE_NONE);
        }
        return mActions.openItem(item, VIEW_TYPE_PREVIEW, VIEW_TYPE_REGULAR);
    }

    /**
     * If the zip_ng_ro flag is enabled, and if DocsUI is in file manager mode (i.e. not in file
     * picker mode), and if the activated item is a supported archive, and if this archive is
     * located in a writable folder, then this method starts unpacking the archive.
     *
     * @return whether the initiating user action is considered as fully handled.
     */
    private boolean startUnpackingArchive(DocumentItemDetails docDetails) {
        if (!isZipNgFlagEnabled() || mState.action != ACTION_BROWSE) return false;

        final String key = docDetails.getSelectionKey();
        if (TextUtils.isEmpty(key)) return false;

        final DocumentInfo doc = mModel.getDocument(key);
        if (doc == null || !doc.isArchive()) return false;

        final DocumentInfo dir = mState.stack.peek();
        if (!dir.isCreateSupported()) {
            Log.e(TAG, "Cannot extract archive in read-only folder");
            Snackbars.showError(mActivity, R.string.cannot_extract_in_read_only_folder);
            return true;
        }

        final MutableSelection<String> selected = new MutableSelection<>();
        selected.add(key);
        transferDocuments(selected, mState.stack, OPERATION_UNPACK);
        return true;
    }

    public void onViewModeChanged() {
        if (isUseMaterial3FlagEnabled()) {
            // Only enable the decoration for grid mode.
            if (mState.derivedMode != MODE_GRID) {
                mRecView.removeItemDecoration(mGridEvenSpacingDecoration);
            } else {
                mRecView.addItemDecoration(mGridEvenSpacingDecoration);

            }
        }
        // Mode change is just visual change; no need to kick loader.
        mRootView.announceForAccessibility(
                getString(
                        mState.derivedMode == State.MODE_GRID
                                ? getRes(R.string.grid_mode_showing)
                                : getRes(R.string.list_mode_showing)));
        onDisplayStateChanged();
    }

    private void onDisplayStateChanged() {
        updateLayout(mState.derivedMode);
        mRecView.setAdapter(mAdapter);
    }

    /**
     * Updates the layout after the view mode switches.
     *
     * @param mode The new view mode.
     */
    private void updateLayout(@ViewMode int mode) {
        mMode = mode;
        mAppBarHeight = getAppBarLayoutHeight();
        mBottomOverlayHeight = getBottomOverlayHeight();

        if (isUseMaterial3FlagEnabled()) {
            if (mode == MODE_GRID) {
                int itemMarg =
                        getResources().getDimensionPixelSize(getRes(R.dimen.grid_item_margin));
                // Subtract the item's margin since we don't want to double count the margin in the
                // distance between the outer grid items and the grid boundary.
                int leftPad =
                        getResources()
                                        .getDimensionPixelSize(
                                                getRes(R.dimen.grid_container_padding_left))
                                - itemMarg;
                int topPad =
                        getResources()
                                        .getDimensionPixelSize(
                                                getRes(R.dimen.grid_container_padding_top))
                                - itemMarg;
                int rightPad =
                        getResources()
                                        .getDimensionPixelSize(
                                                getRes(R.dimen.grid_container_padding_right))
                                - itemMarg;
                int botPad =
                        getResources()
                                        .getDimensionPixelSize(
                                                getRes(R.dimen.grid_container_padding_bottom))
                                - itemMarg;
                mRecView.setPadding(leftPad, topPad + mAppBarHeight, rightPad, botPad);
            } else {
                int pad = getDirectoryPadding(mode);
                // Add bottom padding to provide a blank space at the end of the list where the user
                // can right-click, drag, etc.
                int botPad =
                        getResources()
                                .getDimensionPixelSize(
                                        getRes(R.dimen.list_container_padding_bottom));
                mRecView.setPadding(pad, mAppBarHeight, pad, botPad);
            }
            mColumnCount = calculateColumnCount(mode);
            if (mLayout != null) {
                mLayout.setSpanCount(mColumnCount);
            }
        } else {
            mColumnCount = calculateColumnCount(mode);
            if (mLayout != null) {
                mLayout.setSpanCount(mColumnCount);
            }
            int pad = getDirectoryPadding(mode);
            mRecView.setPadding(pad, mAppBarHeight, pad, mBottomOverlayHeight);
        }

        if (isUseMaterial3FlagEnabled() && mRecView.getItemDecorationCount() > 0) {
            if (mItemDecorationInvalidator == null
                    || mItemDecorationInvalidator.hasFinishedInvalidation()) {
                // Create a new ItemDecorationInvalidator to invalidate the item decorations the
                // next time the recycler view is idle.
                mItemDecorationInvalidator = ItemDecorationInvalidator.create(mRecView);
            }
        } else {
            mRecView.requestLayout();
        }
        mIconHelper.setViewMode(mode);

        int range = getResources().getDimensionPixelOffset(getRes(R.dimen.refresh_icon_range));
        mRefreshLayout.setProgressViewOffset(true, mAppBarHeight, mAppBarHeight + range);
    }

    private int getAppBarLayoutHeight() {
        View appBarLayout = getBaseActivity().findViewById(getRes(R.id.app_bar));
        View collapsingBar = getBaseActivity().findViewById(getRes(R.id.collapsing_toolbar));
        return collapsingBar == null ? 0 : appBarLayout.getHeight();
    }

    /** Returns the height of any UI components that overlap the bottom of the directory list. */
    private int getBottomOverlayHeight() {
        if (isUseMaterial3FlagEnabled()) {
            // The bottom bar is laid out as a sibling rather than an overlay so there is no
            // overlap.
            return 0;
        }
        View containerSave = getBaseActivity().findViewById(getRes(R.id.container_save));
        return containerSave == null ? 0 : containerSave.getHeight();
    }

    /**
     * Updates the layout after the view mode switches.
     *
     * @param scale The new view mode.
     */
    private void scaleLayout(float scale) {
        assert DEBUG;

        if (VERBOSE) {
            Log.v(
                    TAG, "Handling scale event: " + scale + ", existing scale: " + mLiveScale);
        }

        if (mMode == MODE_GRID) {
            float minScale = getFraction(R.fraction.grid_scale_min);
            float maxScale = getFraction(R.fraction.grid_scale_max);
            float nextScale = mLiveScale * scale;

            if (VERBOSE) {
                Log.v(TAG,
                        "Next scale " + nextScale + ", Min/max scale " + minScale + "/" + maxScale);
            }

            if (nextScale > minScale && nextScale < maxScale) {
                if (DEBUG) {
                    Log.d(TAG, "Updating grid scale: " + scale);
                }
                mLiveScale = nextScale;
                updateLayout(mMode);
            }

        } else {
            if (DEBUG) {
                Log.d(TAG, "List mode, ignoring scale: " + scale);
            }
            mLiveScale = 1.0f;
        }
    }

    private int calculateColumnCount(@ViewMode int mode) {
        // For fixing a11y issue b/141223688, if there's only "no items" displayed, we should set
        // span column to 1 to avoid talkback speaking unnecessary information.
        if (mModel != null && mModel.getItemCount() == 0) {
            return 1;
        }

        if (mode == MODE_LIST) {
            // List mode is a "grid" with 1 column.
            return 1;
        }
        Resources resources = getResources();
        float scaling = isUseMaterial3FlagEnabled() ? 1.0f : mLiveScale;

        int cellWidth =
                (int) (resources.getDimensionPixelSize(getRes(R.dimen.grid_width)) * scaling);
        int cellMargin =
                2
                        * (int)
                                (resources.getDimensionPixelSize(getRes(R.dimen.grid_item_margin))
                                        * scaling);
        int viewPadding =
                (int) ((mRecView.getPaddingLeft() + mRecView.getPaddingRight()) * scaling);
        int viewWidth =
                isUseMaterial3FlagEnabled() ? (int) (mRecView.getMeasuredWidth() * scaling)
                        : mRecView.getWidth();

        // RecyclerView sometimes gets a width of 0 (see b/27150284).
        // Clamp so that we always lay out the grid with at least 2 columns by default.
        // If on photo picking state, the UI should show 3 images a row or 2 folders a row,
        // so use 6 columns by default and set folder size to 3 and document size is to 2.
        mColumnUnit = (!isUseMaterial3FlagEnabled() && mState.isPhotoPicking()) ? 3 : 1;
        int columnCount = mColumnUnit * Math.max(2,
                (viewWidth - viewPadding) / (cellWidth + cellMargin));

        // Finally with our grid count logic firmly in place, we apply any live scaling
        // captured by the scale gesture detector.
        if (isUseMaterial3FlagEnabled()) {
            return Math.max(1, (int) Math.floor(columnCount / scaling));
        }
        return Math.max(1, Math.round(columnCount / scaling));
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
                return getResources().getDimensionPixelSize(getRes(R.dimen.grid_container_padding));
            case MODE_LIST:
                return getResources().getDimensionPixelSize(getRes(R.dimen.list_container_padding));
            default:
                throw new IllegalArgumentException("Unsupported layout mode: " + mode);
        }
    }

    private void closeSelectionBar() {
        if (isUseMaterial3FlagEnabled()) {
            mSelectionBarController.closeSelectionBar();
        } else {
            mActionModeController.finishActionMode();
        }
    }

    /**
     * This handles both selection bar menu and context menu item click, but it doesn't handle the
     * option menu (normal app bar) menu item click.
     */
    private boolean handleMenuItemClick(MenuItem item) {
        if (mInjector.pickResult != null) {
            mInjector.pickResult.increaseActionCount();
        }
        MutableSelection<String> selection = new MutableSelection<>();
        mSelectionMgr.copySelection(selection);

        final int id = item.getItemId();
        if (isDesktopFileHandlingFlagEnabled() && id == getRes(R.id.dir_menu_open)) {
            // The "Open" menu item is displayed in desktop mode.
            // Open behaves the same as a double click on the matching document which is handled by
            // onItemActivated but since onItemActivated requires a RecyclerView ItemDetails, we're
            // using viewDocument that takes a Selection.
            viewDocument(selection);
            return true;
        } else if (isZipNgFlagEnabled()
                && (id == getRes(R.id.dir_menu_browse) || id == getRes(R.id.action_menu_browse))) {
            // Handles "in-place" zip file browsing.
            viewDocument(selection);
            if (isSearchV2Enabled()) {
                // The selected item could have had the path shown in the breadcrumb. Hide it, as
                // viewing document opens the directory path in another breadcrumb.
                BreadcrumbController controller = mInjector.getBreadcrumbController();
                if (controller != null) {
                    setSearchResultBreadcrumbHidden(controller);
                }
            }
            return true;
        } else if (id == getRes(R.id.action_menu_select) || id == getRes(R.id.dir_menu_open)) {
            // Note: this code path is never executed for `dir_menu_open`. The menu item is always
            // hidden unless the desktopFileHandling flag is enabled, in which case the menu item
            // will be handled by the condition above.
            openDocuments(selection);
            closeSelectionBar();
            return true;
        } else if (id == getRes(R.id.action_menu_open_with)
                || id == getRes(R.id.dir_menu_open_with)) {
            showChooserForDoc(selection);
            return true;
        } else if (id == getRes(R.id.dir_menu_open_in_new_window)) {
            mActions.openSelectedInNewWindow();
            return true;
        } else if (id == getRes(R.id.action_menu_share) || id == getRes(R.id.dir_menu_share)) {
            mActions.shareSelectedDocuments();
            return true;
        } else if (id == getRes(R.id.action_menu_delete) || id == getRes(R.id.dir_menu_delete)) {
            // deleteDocuments will end action mode if the documents are deleted.
            // It won't end action mode if user cancels the delete.
            mActions.showDeleteDialog();
            return true;
        } else if (isTrashFlowEnabled()
                && (id == getRes(R.id.action_menu_move_to_trash)
                        || id == getRes(R.id.dir_menu_move_to_trash))) {
            mActions.trashSelectedDocuments();
            return true;
        } else if (isTrashFlowEnabled()
                && (id == getRes(R.id.action_menu_restore_from_trash)
                        || id == getRes(R.id.dir_menu_restore_from_trash))) {
            restoreDocumentsFromTrash(selection);
            return true;
        } else if (id == getRes(R.id.action_menu_copy_to)) {
            transferDocuments(selection, null, FileOperationService.OPERATION_COPY);
            // TODO: Only finish selection mode if copy-to is not canceled.
            // Need to plum down into handling the way we do with deleteDocuments.
            closeSelectionBar();
            return true;
        } else if (id == getRes(R.id.action_menu_compress)
                || id == getRes(R.id.dir_menu_compress)) {
            transferDocuments(selection, mState.stack,
                    FileOperationService.OPERATION_COMPRESS);
            // TODO: Only finish selection mode if compress is not canceled.
            // Need to plum down into handling the way we do with deleteDocuments.
            closeSelectionBar();
            return true;
        } else if (isZipNgFlagEnabled() && (id == getRes(R.id.dir_menu_extract_here)
                || id == getRes(R.id.action_menu_extract_here))) {
            transferDocuments(selection, mState.stack, OPERATION_UNPACK);
            closeSelectionBar();
            return true;
        } else if (id == getRes(R.id.action_menu_extract_to)
                || id == getRes(R.id.option_menu_extract_all)) {
            transferDocuments(selection, null, FileOperationService.OPERATION_EXTRACT);
            // TODO: Only finish selection mode if compress-to is not canceled.
            // Need to plum down into handling the way we do with deleteDocuments.
            closeSelectionBar();
            return true;
        } else if (id == getRes(R.id.action_menu_move_to)) {
            if (mModel.hasDocuments(selection, DocumentFilters.NOT_MOVABLE)) {
                mInjector.dialogs.showOperationUnsupported();
                return true;
            }
            if (isHomeScreenFilesFlagEnabled()) {
                // Block the operation if one of the selected documents is a shortcut folder.
                List<Uri> uris = new ArrayList<>();
                UserId userId = null;
                for (DocumentInfo doc : mModel.getDocuments(selection)) {
                    uris.add(doc.getDocumentUri());
                    userId = doc.userId;
                }
                if (mActions.blockOperationForShortcuts(uris, userId)) {
                    Log.e(TAG, "Unable to move because a protected folder is selected.");
                    return true;
                }
            }
            // Exit selection mode first, so we avoid deselecting deleted documents.
            closeSelectionBar();
            transferDocuments(selection, null, FileOperationService.OPERATION_MOVE);
            return true;
        } else if (id == getRes(R.id.action_menu_inspect) || id == getRes(R.id.dir_menu_inspect)) {
            closeSelectionBar();
            assert selection.size() <= 1;
            DocumentInfo doc = selection.isEmpty()
                    ? mActivity.getCurrentDirectory()
                    : mModel.getDocuments(selection).get(0);

            mActions.showPreview(doc);
            return true;
        } else if (id == getRes(R.id.dir_menu_cut_to_clipboard)) {
            mActions.cutToClipboard();
            return true;
        } else if (id == getRes(R.id.dir_menu_copy_to_clipboard)) {
            mActions.copyToClipboard();
            return true;
        } else if (id == getRes(R.id.dir_menu_paste_from_clipboard)) {
            pasteFromClipboard();
            return true;
        } else if (id == getRes(R.id.dir_menu_paste_into_folder)) {
            pasteIntoFolder();
            return true;
        } else if (id == getRes(R.id.action_menu_select_all)
                || id == getRes(R.id.dir_menu_select_all)) {
            mActions.selectAllFiles();
            return true;
        } else if (id == getRes(R.id.action_menu_deselect_all)
                || id == getRes(R.id.dir_menu_deselect_all)) {
            mActions.deselectAllFiles();
            return true;
        } else if (id == getRes(R.id.action_menu_rename) || id == getRes(R.id.dir_menu_rename)) {
            renameDocuments(selection);
            return true;
        } else if (id == getRes(R.id.dir_menu_create_dir)) {
            mActions.showCreateDirectoryDialog();
            return true;
        } else if (id == getRes(R.id.dir_menu_view_in_owner)) {
            mActions.viewInOwner();
            return true;
        } else if (id == getRes(R.id.action_menu_sort)) {
            mActions.showSortDialog();
            return true;
        } else if (id == R.id.action_menu_add_shortcut || id == R.id.dir_menu_add_shortcut) {
            assert selection.size() <= 1;
            DocumentInfo documentInfo = selection.isEmpty()
                    ? mActivity.getCurrentDirectory()
                    : mModel.getDocuments(selection).get(0);

            mActions.showAddShortcutDialog(documentInfo);
            return true;
        }
        if (isUseMaterial3FlagEnabled()) {
            if (isDesktopFileHandlingFlagEnabled() && id == getRes(R.id.action_menu_open)) {
                viewDocument(selection);
                return true;
            }
            if (id == getRes(R.id.action_menu_open_in_new_window)) {
                mActions.openSelectedInNewWindow();
                return true;
            }
            if (id == getRes(R.id.action_menu_paste_into_folder)) {
                pasteIntoFolder();
                return true;
            }
        }

        final boolean showCopyToMoveTo =
                getResources().getBoolean(R.bool.show_copy_to_move_to_menus);
        if (isDesktopUxPhase2FlagEnabled() && !showCopyToMoveTo) {
            if (id == getRes(R.id.action_menu_cut_to_clipboard)) {
                mActions.cutToClipboard();
                return true;
            }
            if (id == getRes(R.id.action_menu_copy_to_clipboard)) {
                mActions.copyToClipboard();
                return true;
            }
        }

        if (DEBUG) {
            Log.d(TAG, "Cannot handle unexpected menu item " + id);
        }

        return false;
    }

    private boolean onAccessibilityClick(View child) {
        if (mSelectionMgr.hasSelection()) {
            selectItem(child);
        } else {
            DocumentHolder holder = getDocumentHolder(child);
            // This was reverted as desktop file handling was rolling out until
            // we have default file opening apps out-of-the box.
            // Since the default file opening app uses a build flag, we're using
            // another flag that's rolling out in the same cycle to flag protect
            // the revert^2.
            if (isDesktopFileHandlingFlagEnabled() && isUseNewOpenWithEnabled()) {
                mActions.openItem(holder.getItemDetails(), VIEW_TYPE_REGULAR, VIEW_TYPE_NONE);
            } else {
                mActions.openItem(holder.getItemDetails(), VIEW_TYPE_PREVIEW, VIEW_TYPE_REGULAR);
            }
        }
        return true;
    }

    private boolean onAccessibilityLongClick(View child) {
        selectItem(child);
        return true;
    }

    private void selectItem(View child) {
        final String id = getModelId(child);
        if (mSelectionMgr.isSelected(id)) {
            mSelectionMgr.deselect(id);
        } else {
            mSelectionMgr.select(id);
        }
    }

    private void cancelThumbnailTask(View view) {
        final ImageView iconThumb = (ImageView) view.findViewById(getRes(R.id.icon_thumb));
        if (iconThumb != null) {
            mIconHelper.stopLoading(iconThumb);
        }
    }

    // Support for opening multiple documents is currently exclusive to DocumentsActivity.
    private void openDocuments(final Selection selected) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_OPEN);

        if (selected.isEmpty()) {
            return;
        }

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);
        if (docs.size() > 1) {
            mActivity.onDocumentsPicked(docs);
        } else {
            mActivity.onDocumentPicked(docs.get(0));
        }
    }

    private void restoreDocumentsFromTrash(final Selection selected) {
        if (selected.isEmpty()) {
            return;
        }

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);
        mActions.restoreSelectedDocumentsFromTrash(docs);
    }

    private void showChooserForDoc(final Selection<String> selected) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_OPEN);

        if (selected.isEmpty()) {
            return;
        }

        assert selected.size() == 1;
        DocumentInfo doc =
                DocumentInfo.fromDirectoryCursor(mModel.getItem(selected.iterator().next()));
        mActions.showChooserForDoc(doc);
    }

    private void viewDocument(final Selection<String> selected) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_OPEN);
        Trace.beginSection("DirectoryFragment#viewDocument");

        if (selected.isEmpty()) {
            Trace.endSection();
            return;
        }

        assert selected.size() == 1;
        DocumentInfo doc =
                DocumentInfo.fromDirectoryCursor(mModel.getItem(selected.iterator().next()));

        mActions.openDocumentViewOnly(doc);
        Trace.endSection();
    }

    private void transferDocuments(
            final Selection<String> selected, @Nullable DocumentStack destination,
            final @OpType int mode) {
        if (selected.isEmpty()) {
            return;
        }

        switch (mode) {
            case FileOperationService.OPERATION_COPY:
                Metrics.logUserAction(MetricConsts.USER_ACTION_COPY_TO);
                break;
            case FileOperationService.OPERATION_COMPRESS:
                Metrics.logUserAction(MetricConsts.USER_ACTION_COMPRESS);
                break;
            case FileOperationService.OPERATION_EXTRACT:
                Metrics.logUserAction(MetricConsts.USER_ACTION_EXTRACT_TO);
                break;
            case FileOperationService.OPERATION_MOVE:
                Metrics.logUserAction(MetricConsts.USER_ACTION_MOVE_TO);
                break;
            case FileOperationService.OPERATION_UNPACK:
                Metrics.logUserAction(MetricConsts.USER_ACTION_UNPACK);
                break;
        }

        UrisSupplier srcs;
        try {
            ClipStore clipStorage = DocumentsApplication.getClipStore(getContext());
            srcs = UrisSupplier.create(selected, mModel::getItemUri, clipStorage);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create uri supplier.", e);
        }

        final DocumentInfo parent = mActivity.getCurrentDirectory();
        Uri parentUri = parent == null ? null : parent.derivedUri;

        // If the user is in the "Recent" view, there is no meaningful parent URI, but the
        // FileOperationService can successfully deal with this for move operations. This is only
        // enabled for Search v2 as using the old loaders masks out flags like FLAG_SUPPORTS_DELETE
        // for the "Recent" view.
        if (isSearchV2Enabled() && (mode == FileOperationService.OPERATION_MOVE
                && mState.stack.isRecents())) {
            parentUri = null;
        }

        final FileOperation operation = new FileOperation.Builder()
                .withOpType(mode)
                .withSrcParent(parentUri)
                .withSrcs(srcs)
                .build();

        if (destination != null) {
            operation.setDestination(destination);
            final String jobId = FileOperations.createJobId();
            mInjector.dialogs.showProgressDialog(jobId, operation);
            FileOperations.start(
                    mActivity,
                    operation,
                    mInjector.dialogs::showFileOperationStatus,
                    jobId);
            return;
        }

        // Pop up a dialog to pick a destination.  This is inadequate but works for now.
        // TODO: Implement a picker that is to spec.
        mLocalState.mPendingOperation = operation;
        final Intent intent = new Intent(
                Shared.ACTION_PICK_COPY_DESTINATION,
                Uri.EMPTY,
                getActivity(),
                PickActivity.class);

        // Set an appropriate title on the drawer when it is shown in the picker.
        // Coupled with the fact that we auto-open the drawer for copy/move operations
        // it should basically be the thing people see first.
        int drawerTitleId;
        switch (mode) {
            case FileOperationService.OPERATION_COPY:
                drawerTitleId = getRes(R.string.menu_copy);
                break;
            case FileOperationService.OPERATION_COMPRESS:
                drawerTitleId = getRes(R.string.menu_compress);
                break;
            case FileOperationService.OPERATION_EXTRACT:
                drawerTitleId = getRes(R.string.menu_extract);
                break;
            case FileOperationService.OPERATION_MOVE:
                drawerTitleId = getRes(R.string.menu_move);
                break;
            default:
                throw new UnsupportedOperationException("Unknown mode: " + mode);
        }

        intent.putExtra(DocumentsContract.EXTRA_PROMPT, drawerTitleId);

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);

        // Determine if there is a directory in the set of documents
        // to be copied? Why? Directory creation isn't supported by some roots
        // (like Downloads). This informs DocumentsActivity (the "picker")
        // to restrict available roots to just those with support.
        intent.putExtra(FileOperationService.EXTRA_OPERATION_TYPE, mode);

        // This just identifies the type of request...we'll check it
        // when we receive a response.
        startActivityForResult(intent, REQUEST_COPY_DESTINATION);
    }

    @Override
    public void onActivityResult(@RequestCode int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_COPY_DESTINATION:
                onCopyDestinationPicked(resultCode, data);
                break;
            default:
                throw new UnsupportedOperationException("Unknown request code: " + requestCode);
        }
    }

    /**
     * Displays the "Rename" dialog box for the first selected document. Does nothing if there are
     * no selected documents. Does nothing if the selected document is a shortcut folder. Does
     * nothing if the selected document does not support the rename operation.
     */
    public void renameDocuments(@NonNull Selection<String> selected) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_RENAME);

        if (selected.isEmpty()) {
            return;
        }

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);

        // Batch renaming is not supported. Only consider the first document.
        final DocumentInfo doc = docs.get(0);

        if (isUseMaterial3FlagEnabled() && !doc.isRenameSupported()) {
            if (DEBUG) Log.d(TAG, "Cannot rename " + redact(doc) + ": Operation not supported");
            return;
        }

        // Block the file operation if the selected document is a shortcut folder.
        if (isHomeScreenFilesFlagEnabled()
                && mActions.blockOperationForShortcuts(List.of(doc.derivedUri), doc.userId)) {
            Log.e(TAG, "Cannot rename protected folder " + redact(doc));
            return;
        }

        RenameDocumentFragment.show(getChildFragmentManager(), doc);
    }

    Model getModel() {
        return mModel;
    }

    /**
     * Paste selection files from the primary clip into the current window.
     */
    public void pasteFromClipboard() {
        Metrics.logUserAction(MetricConsts.USER_ACTION_PASTE_CLIPBOARD);
        int cookie = Trace.isEnabled() ? RANDOM.nextInt() : 0;
        if (Trace.isEnabled()) {
            Trace.beginAsyncSection("DirectoryFragment#pasteFromClipboard", cookie);
        }
        // Since we are pasting into the current window, we already have the destination in the
        // stack. No need for a destination DocumentInfo.
        mClipper.copyFromClipboard(
                mState.stack,
                (status, opType, docCount) -> {
                    mInjector.dialogs.showFileOperationStatus(status, opType, docCount);
                    Trace.endAsyncSection("DirectoryFragment#pasteFromClipboard", cookie);
                });
        getBaseActivity().invalidateOptionsMenu();
    }

    public void pasteIntoFolder() {
        if (mSelectionMgr.getSelection().isEmpty()) {
            return;
        }
        assert (mSelectionMgr.getSelection().size() == 1);

        String modelId = mSelectionMgr.getSelection().iterator().next();
        Cursor dstCursor = mModel.getItem(modelId);
        if (dstCursor == null) {
            Log.w(TAG, "Invalid destination. Can't obtain cursor for modelId: " + modelId);
            return;
        }
        DocumentInfo destination = DocumentInfo.fromDirectoryCursor(dstCursor);
        mClipper.copyFromClipboard(
                destination,
                mState.stack,
                mInjector.dialogs::showFileOperationStatus);
        getBaseActivity().invalidateOptionsMenu();
    }

    private void setupDragAndDropOnDocumentView(View view, Cursor cursor) {
        final String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
        if (Document.MIME_TYPE_DIR.equals(docMimeType)) {
            // Make a directory item a drop target. Drop on non-directories and empty space
            // is handled at the list/grid view level.
            view.setOnDragListener(mDragHoverListener);
        }
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

        if (v == mRecView) {
            return mActivity.getCurrentDirectory();
        }

        return null;
    }

    /**
     * Gets the model ID for a given RecyclerView item.
     *
     * @param view A View that is a document item view, or a child of a document item view.
     * @return The Model ID for the given document, or null if the given view is not associated with
     * a document item view.
     */
    private @Nullable String getModelId(View view) {
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

    /**
     * Add or remove mToolbarPreDrawListener implement on DirectoryFragment to ViewTreeObserver.
     */
    public void setPreDrawListenerEnabled(boolean enable) {
        if (mActivity == null) {
            return;
        }

        final View bar = mActivity.findViewById(getRes(R.id.collapsing_toolbar));
        if (bar != null) {
            bar.getViewTreeObserver().removeOnPreDrawListener(mToolbarPreDrawListener);
            if (enable) {
                bar.getViewTreeObserver().addOnPreDrawListener(mToolbarPreDrawListener);
            }
        }
    }

    public static void showDirectory(
            FragmentManager fm, RootInfo root, DocumentInfo doc, int anim) {
        if (DEBUG) Log.d(TAG, "Showing dir " + doc);
        create(fm, root, doc, anim);
    }

    public static void showRecentsOpen(FragmentManager fm, int anim) {
        create(fm, null, null, anim);
    }

    public static void create(
            FragmentManager fm,
            RootInfo root,
            @Nullable DocumentInfo doc,
            @AnimationType int anim) {
        if (DEBUG) Log.d(TAG, "Creating new fragment for dir " + doc);

        final Bundle args = new Bundle();
        args.putParcelable(Shared.EXTRA_ROOT, root);
        args.putParcelable(Shared.EXTRA_DOC, doc);

        final FragmentTransaction ft = fm.beginTransaction();
        AnimationView.setupAnimations(ft, anim, args);

        final DirectoryFragment fragment = new DirectoryFragment();
        fragment.setArguments(args);

        ft.replace(getFragmentId(), fragment);
        ft.commitAllowingStateLoss();
    }

    /** Gets the fragment from the fragment manager. */
    public static @Nullable DirectoryFragment get(FragmentManager fm) {
        // TODO: deal with multiple directories shown at once
        Fragment fragment = fm.findFragmentById(getFragmentId());
        return fragment instanceof DirectoryFragment
                ? (DirectoryFragment) fragment
                : null;
    }

    private static int getFragmentId() {
        return getRes(R.id.container_directory);
    }

    /**
     * Scroll to top of recyclerView in fragment
     */
    public void scrollToTop() {
        if (mRecView != null) {
            mRecView.scrollToPosition(0);
        }
    }

    /**
     * Stop the scroll of recyclerView in fragment
     */
    public void stopScroll() {
        if (mRecView != null) {
            mRecView.stopScroll();
        }
    }

    @Override
    public void onRefresh() {
        // Remove thumbnail cache. We do this not because we're worried about stale thumbnails as it
        // should be covered by last modified value we store in thumbnail cache, but rather to give
        // the user a greater sense that contents are being reloaded.
        Context context = getContext();
        if (context == null) {
            Log.w(TAG, "Fragment is not attached to an activity.");
        } else {
            ThumbnailCache cache = DocumentsApplication.getThumbnailCache(context);
            String[] ids = mModel.getModelIds();
            int numOfEvicts = Math.min(ids.length, CACHE_EVICT_LIMIT);
            for (int i = 0; i < numOfEvicts; ++i) {
                cache.removeUri(mModel.getItemUri(ids[i]), mModel.getItemUserId(ids[i]));
            }
        }

        final DocumentInfo doc = mActivity.getCurrentDirectory();
        if (doc == null && !mActivity.getSelectedUser().isQuietModeEnabled(mActivity)) {
            // If there is no root doc, try to reload the root doc from root info.
            Log.w(TAG, "No root document. Try to get root document.");
            getRootDocumentAndMaybeRefreshDocument();
            return;
        }
        boolean initialLoad = isSearchV2Enabled() && mDocumentsInitialLoad;
        if (isSearchV2Enabled() && mDocumentsInitialLoad) {
            mDocumentsInitialLoad = false;
        }
        mActions.refreshDocument(
                doc,
                (boolean refreshSupported) -> {
                    if (refreshSupported) {
                        mRefreshLayout.setRefreshing(false);
                    } else {
                        // If Refresh API isn't available, we will explicitly reload the loader,
                        // unless it's the initial load, in which case reloading is redundant, as
                        // refresh did not happen.
                        if (isSearchV2Enabled() && initialLoad) {
                            return;
                        }
                        mActions.loadDocumentsForCurrentStack();
                    }
                });
    }

    private void getRootDocumentAndMaybeRefreshDocument() {
        // If we can reload the root doc successfully, we will push it to the stack and load the
        // stack.
        final RootInfo emptyDocRoot = mActivity.getCurrentRoot();
        mInjector.actions.getDocument(
                emptyDocRoot.authority,
                emptyDocRoot.documentId,
                emptyDocRoot.userId,
                TimeoutTask.DEFAULT_TIMEOUT, rootDoc -> {
                    mRefreshLayout.setRefreshing(false);
                    if (rootDoc != null && mActivity.getCurrentDirectory() == null) {
                        // Make sure the stack does not change during task was running.
                        mState.stack.push(rootDoc);
                        mActivity.updateNavigator();
                        mActions.loadDocumentsForCurrentStack();
                    }
                });
    }

    protected SummariesViewModel createSummariesViewModel() {
        return new ViewModelProvider(
                        this, new SummariesViewModel.Factory(getBaseActivity().getApplication()))
                .get(SummariesViewModel.class);
    }

    private final class ModelUpdateListener implements EventListener<Model.Update> {

        @Override
        public void accept(Model.Update update) {
            if (DEBUG) {
                Log.d(TAG, "Received model update. Loading=" + mModel.isLoading());
            }

            mProgressBar.setVisibility(mModel.isLoading() ? View.VISIBLE : View.GONE);

            updateLayout(mState.derivedMode);

            // Update the selection to remove any disappeared IDs.
            List<String> disappearedIds = new ArrayList<>();
            Set<String> modelIds = new HashSet<>(mAdapter.getStableIds());
            for (String key : mSelectionMgr.getSelection()) {
                if (!modelIds.contains(key)) {
                    disappearedIds.add(key);
                }
            }
            // setItemsSelected will notify the observers so they can react to the selection change
            // (e.g. SelectionBarController can update its "X selected" title).
            if (!disappearedIds.isEmpty()) {
                // Deselect ids in batch to avoid multiple onSelectionChanged() calls.
                mSelectionMgr.setItemsSelected(disappearedIds, false);
            }

            mAdapter.notifyDataSetChanged();

            boolean shouldRestoreSelection = mRestoredState != null;
            // When search_v2 is ON, we also check if the Model is still in loading state, where
            // the Model will be empty, so nothing will be restored, we need to wait for the next
            // update with loading=false.
            if (isSearchV2Enabled()) {
                shouldRestoreSelection = shouldRestoreSelection && !mModel.isLoading();
            }
            if (shouldRestoreSelection) {
                mSelectionMgr.onRestoreInstanceState(mRestoredState);
                mRestoredState = null;
            }

            // Restore any previous instance state
            final SparseArray<Parcelable> container =
                    mState.dirConfigs.remove(mLocalState.getConfigKey());
            final int curSortedDimensionId = mState.sortModel.getSortedDimensionId();

            final SortDimension curSortedDimension =
                    mState.sortModel.getDimensionById(curSortedDimensionId);

            // Default not restore to avoid app bar layout expand to confuse users.
            if (container != null
                    && !getArguments().getBoolean(Shared.EXTRA_IGNORE_STATE, true)) {
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

            if (!mModel.isLoading()) {
                mActivity.notifyDirectoryLoaded(
                        mModel.doc != null ? mModel.doc.derivedUri : null);
                // For orientation changed case, sometimes the docs loading comes after the menu
                // update. We need to update the menu here to ensure the status is correct.
                mInjector.menuManager.updateModel(mModel);
                mInjector.menuManager.updateOptionMenu();
                if (VersionUtils.isAtLeastS()) {
                    mActivity.updateHeader(update.hasCrossProfileException());
                } else {
                    mActivity.updateHeaderTitle();
                }
            }
            if (isUseMaterial3FlagEnabled()) {
                mRecView.post(mFocusManager::onLayoutCompleted);
            }
        }
    }

    private final class AdapterEnvironment implements DocumentsAdapter.Environment {

        @Override
        public Features getFeatures() {
            return mInjector.features;
        }

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
            return mInjector.searchManager.isSearching();
        }

        @Override
        public Model getModel() {
            return mModel;
        }

        /**
         * Gets the inline sync tick icon visibility duration. In test code this can be overridden
         * and found with getTickDurationSupplierForTest(). Otherwise, it will be the constant
         * TICK_VISIBLE_DURATION_MS.
         */
        @Override
        public int getTickDuration() {
            Supplier<Integer> testSupplier = mActivity.getTickDurationSupplierForTest();
            // Return the overridden duration only if set by tests.
            return (testSupplier != null) ? testSupplier.get() : TICK_VISIBLE_DURATION_MS;
        }

        @Override
        public int getColumnCount() {
            return mColumnCount;
        }

        @Override
        public boolean isSelected(String id) {
            return mSelectionMgr.isSelected(id);
        }

        @Override
        public boolean isOnline() {
            if (!isSyncStateEnabled()) {
                return true;
            }
            return mInjector.networkMonitor.isOnline();
        }

        @Override
        public boolean isDocumentEnabled(DocumentInfo doc) {
            return mInjector.config.isDocumentEnabled(doc, mState, isOnline());
        }

        @Override
        public boolean isContentAvailable(DocumentInfo doc) {
            return mInjector.config.isContentAvailable(doc, mState, isOnline());
        }

        @Override
        public void initDocumentHolder(DocumentHolder holder) {
            holder.addKeyEventListener(mKeyListener);
            holder.itemView.setOnFocusChangeListener(mFocusManager);
        }

        @Override
        public void onBindDocumentHolder(DocumentHolder holder, Cursor cursor) {
            setupDragAndDropOnDocumentView(holder.itemView, cursor);
        }

        @Override
        public ActionHandler getActionHandler() {
            return mActions;
        }

        @Override
        public boolean isOnTrashPage() {
            return mState.stack.isTrashTopLevel();
        }

        @Override
        public boolean shouldDisplaySummary() {
            return displaySummaryForRoot(
                    mInjector.getSummaryProviderManager(),
                    mState.stack.getRoot(),
                    mState.stack.peek());
        }
    }
}
