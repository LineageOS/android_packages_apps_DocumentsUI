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

package com.android.documentsui;

import static com.android.documentsui.base.Shared.DEBUG;
import static com.android.documentsui.base.Shared.EXTRA_BENCHMARK;
import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;
import static com.android.documentsui.base.State.MODE_GRID;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.MessageQueue.IdleHandler;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Root;
import android.support.annotation.CallSuper;
import android.support.annotation.LayoutRes;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.documentsui.AbstractActionHandler.CommonAddons;
import com.android.documentsui.NavigationViewManager.Breadcrumb;
import com.android.documentsui.SearchViewManager.SearchManagerListener;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.LocalPreferences;
import com.android.documentsui.base.PairedTask;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.dirlist.FragmentTuner;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.dirlist.MultiSelectManager;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;
import com.android.documentsui.roots.RootsCache;
import com.android.documentsui.sidebar.RootsFragment;
import com.android.documentsui.sorting.SortController;
import com.android.documentsui.sorting.SortModel;
import com.android.documentsui.ui.DialogController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executor;

public abstract class BaseActivity
        extends Activity implements CommonAddons, NavigationViewManager.Environment {

    private static final String BENCHMARK_TESTING_PACKAGE = "com.android.documentsui.appperftests";

    protected SearchViewManager mSearchManager;
    protected State mState;

    protected @Nullable RetainedState mRetainedState;
    protected RootsCache mRoots;
    protected DrawerController mDrawer;
    protected NavigationViewManager mNavigator;
    List<EventListener> mEventListeners = new ArrayList<>();
    protected SortController mSortController;

    private final String mTag;
    private final ContentObserver mRootsCacheObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            new HandleRootsChangedTask(BaseActivity.this).execute(getCurrentRoot());
        }
    };

    @LayoutRes
    private int mLayoutId;

    private boolean mNavDrawerHasFocus;
    private long mStartTime;

    protected abstract void onTaskFinished(Uri... uris);
    protected abstract void refreshDirectory(int anim);
    /** Allows sub-classes to include information in a newly created State instance. */
    protected abstract void includeState(State initialState);
    protected abstract void onDirectoryCreated(DocumentInfo doc);

    /**
     * Provides Activity a means of injection into and specialization of
     * DirectoryFragment.
     */
    public abstract FragmentTuner getFragmentTuner(Model model, boolean mSearchMode);

    /**
     * Provides Activity a means of injection into and specialization of
     * DirectoryFragment.
     */
    public abstract FocusManager getFocusManager(RecyclerView view, Model model);

    /**
     * Provides Activity a means of injection into and specialization of
     * DirectoryFragment hosted menus.
     */
    public abstract MenuManager getMenuManager();

    /**
     * Provides Activity a means of injection into and specialization of
     * DirectoryFragment.
     */
    public abstract DialogController getDialogController();

    /**
     * Provides Activity a means of injection into and specialization of
     * fragment actions.
     *
     * Args can be nullable when called from a context lacking them, such as RootsFragment.
     */
    public abstract ActionHandler getActionHandler(
            @Nullable Model model, @Nullable MultiSelectManager selectionMgr);

    public BaseActivity(@LayoutRes int layoutId, String tag) {
        mLayoutId = layoutId;
        mTag = tag;
    }

    @CallSuper
    @Override
    public void onCreate(Bundle icicle) {
        // Record the time when onCreate is invoked for metric.
        mStartTime = new Date().getTime();

        super.onCreate(icicle);

        final Intent intent = getIntent();

        addListenerForLaunchCompletion();

        setContentView(mLayoutId);

        mDrawer = DrawerController.create(this);
        mState = getState(icicle);
        Metrics.logActivityLaunch(this, mState, intent);

        // we're really interested in retainining state in our very complex
        // DirectoryFragment. So we do a little code yoga to extend
        // support to that fragment.
        mRetainedState = (RetainedState) getLastNonConfigurationInstance();
        mRoots = DocumentsApplication.getRootsCache(this);

        getContentResolver().registerContentObserver(
                RootsCache.sNotificationUri, false, mRootsCacheObserver);

        DocumentsToolbar toolbar = (DocumentsToolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);

        Breadcrumb breadcrumb =
                Shared.findView(this, R.id.dropdown_breadcrumb, R.id.horizontal_breadcrumb);
        assert(breadcrumb != null);

        mNavigator = new NavigationViewManager(mDrawer, toolbar, mState, this, breadcrumb);
        SearchManagerListener searchListener = new SearchManagerListener() {
            /**
             * Called when search results changed. Refreshes the content of the directory. It
             * doesn't refresh elements on the action bar. e.g. The current directory name displayed
             * on the action bar won't get updated.
             */
            @Override
            public void onSearchChanged(@Nullable String query) {
                // We should not get here if root is not searchable
                assert (canSearchRoot());
                reloadSearch(query);
            }

            @Override
            public void onSearchFinished() {
                // Restores menu icons state
                invalidateOptionsMenu();
            }

            @Override
            public void onSearchViewChanged(boolean opened) {
                mState.sortModel.setSortEnabled(!opened);
                mNavigator.update();
            }
        };
        mSearchManager = new SearchViewManager(searchListener, icicle);
        mSortController = SortController.create(this, mState.derivedMode, mState.sortModel);


        // Base classes must update result in their onCreate.
        setResult(Activity.RESULT_CANCELED);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean showMenu = super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.activity, menu);
        mNavigator.update();
        boolean fullBarSearch = getResources().getBoolean(R.bool.full_bar_search_view);
        mSearchManager.install((DocumentsToolbar) findViewById(R.id.toolbar), fullBarSearch);

        return showMenu;
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mSearchManager.showMenu(canSearchRoot());
        return true;
    }

    @Override
    protected void onDestroy() {
        getContentResolver().unregisterContentObserver(mRootsCacheObserver);
        super.onDestroy();
    }

    private State getState(@Nullable Bundle icicle) {
        if (icicle != null) {
            State state = icicle.<State>getParcelable(Shared.EXTRA_STATE);
            if (DEBUG) Log.d(mTag, "Recovered existing state object: " + state);
            return state;
        }

        State state = new State();

        final Intent intent = getIntent();

        state.sortModel = SortModel.createModel();
        state.localOnly = intent.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY, false);
        state.initAcceptMimes(intent);
        state.excludedAuthorities = getExcludedAuthorities();

        includeState(state);

        // Advanced roots are shown by default without menu option if forced by config or intent.
        boolean forceAdvanced = Shared.shouldShowDeviceRoot(this, intent);
        boolean chosenAdvanced = LocalPreferences.getShowDeviceRoot(this, state.action);
        state.showAdvanced = forceAdvanced || chosenAdvanced;

        // Menu option is shown for whitelisted intents if advanced roots are not shown by default.
        state.showAdvancedOption = !forceAdvanced && (
                Shared.shouldShowFancyFeatures(this)
                || state.action == ACTION_OPEN
                || state.action == ACTION_CREATE
                || state.action == ACTION_OPEN_TREE
                || state.action == ACTION_PICK_COPY_DESTINATION
                || state.action == ACTION_GET_CONTENT);

        if (DEBUG) Log.d(mTag, "Created new state object: " + state);

        return state;
    }

    public void setRootsDrawerOpen(boolean open) {
        mNavigator.revealRootsDrawer(open);
    }

    @Override
    public void onRootPicked(RootInfo root) {
        // Clicking on the current root removes search
        mSearchManager.cancelSearch();

        // Skip refreshing if root nor directory didn't change
        if (root.equals(getCurrentRoot()) && mState.stack.size() == 1) {
            return;
        }

        mState.derivedMode = LocalPreferences.getViewMode(this, root, MODE_GRID);
        mSortController.onViewModeChanged(mState.derivedMode);

        // Set summary header's visibility. Only recents and downloads root may have summary in
        // their docs.
        mState.sortModel.setDimensionVisibility(
                SortModel.SORT_DIMENSION_ID_SUMMARY,
                root.isRecents() || root.isDownloads() ? View.VISIBLE : View.INVISIBLE);

        // Clear entire backstack and start in new root
        mState.onRootChanged(root);

        // Recents is always in memory, so we just load it directly.
        // Otherwise we delegate loading data from disk to a task
        // to ensure a responsive ui.
        if (mRoots.isRecentsRoot(root)) {
            refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        } else {
            new GetRootDocumentTask(
                    root,
                    this,
                    this::openContainerDocument)
                    .executeOnExecutor(getExecutorForCurrentDirectory());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;

            case R.id.menu_create_dir:
                showCreateDirectoryDialog();
                return true;

            case R.id.menu_search:
                // SearchViewManager listens for this directly.
                return false;

            case R.id.menu_grid:
                setViewMode(State.MODE_GRID);
                return true;

            case R.id.menu_list:
                setViewMode(State.MODE_LIST);
                return true;

            case R.id.menu_advanced:
                setDisplayAdvancedDevices(!mState.showAdvanced);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    protected final @Nullable DirectoryFragment getDirectoryFragment() {
        return DirectoryFragment.get(getFragmentManager());
    }

    protected void showCreateDirectoryDialog() {
        Metrics.logUserAction(this, Metrics.USER_ACTION_CREATE_DIR);

        CreateDirectoryFragment.show(getFragmentManager());
    }

    /**
     * Returns true if a directory can be created in the current location.
     * @return
     */
    protected boolean canCreateDirectory() {
        final RootInfo root = getCurrentRoot();
        final DocumentInfo cwd = getCurrentDirectory();
        return cwd != null
                && cwd.isCreateSupported()
                && !mSearchManager.isSearching()
                && !root.isRecents()
                && !root.isDownloads();
    }

    protected void openContainerDocument(DocumentInfo doc) {
        assert(doc.isContainer());

        notifyDirectoryNavigated(doc.derivedUri);

        mState.pushDocument(doc);
        // Show an opening animation only if pressing "back" would get us back to the
        // previous directory. Especially after opening a root document, pressing
        // back, wouldn't go to the previous root, but close the activity.
        final int anim = (mState.hasLocationChanged() && mState.stack.size() > 1)
                ? AnimationView.ANIM_ENTER : AnimationView.ANIM_NONE;
        refreshCurrentRootAndDirectory(anim);
    }

    /**
     * Refreshes the content of the director and the menu/action bar.
     * The current directory name and selection will get updated.
     * @param anim
     */
    @Override
    public final void refreshCurrentRootAndDirectory(int anim) {
        mSearchManager.cancelSearch();

        refreshDirectory(anim);

        final RootsFragment roots = RootsFragment.get(getFragmentManager());
        if (roots != null) {
            roots.onCurrentRootChanged();
        }

        mNavigator.update();
        invalidateOptionsMenu();
    }

    /**
     * This is called when user hovers over a doc for enough time during a drag n' drop, to open a
     * folder that accepts drop. We should only open a container that's not an archive.
     */
    public void springOpenDirectory(DocumentInfo doc) {
    }

    private void reloadSearch(String query) {
        FragmentManager fm = getFragmentManager();
        RootInfo root = getCurrentRoot();
        DocumentInfo cwd = getCurrentDirectory();

        DirectoryFragment.reloadSearch(fm, root, cwd, query);
    }

    private final List<String> getExcludedAuthorities() {
        List<String> authorities = new ArrayList<>();
        if (getIntent().getBooleanExtra(DocumentsContract.EXTRA_EXCLUDE_SELF, false)) {
            // Exclude roots provided by the calling package.
            String packageName = Shared.getCallingPackageName(this);
            try {
                PackageInfo pkgInfo = getPackageManager().getPackageInfo(packageName,
                        PackageManager.GET_PROVIDERS);
                for (ProviderInfo provider: pkgInfo.providers) {
                    authorities.add(provider.authority);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(mTag, "Calling package name does not resolve: " + packageName);
            }
        }
        return authorities;
    }

    boolean canSearchRoot() {
        final RootInfo root = getCurrentRoot();
        return (root.flags & Root.FLAG_SUPPORTS_SEARCH) != 0;
    }

    public static BaseActivity get(Fragment fragment) {
        return (BaseActivity) fragment.getActivity();
    }

    public State getDisplayState() {
        return mState;
    }

    /**
     * Set internal storage visible based on explicit user action.
     */
    void setDisplayAdvancedDevices(boolean display) {
        Metrics.logUserAction(this,
                display ? Metrics.USER_ACTION_SHOW_ADVANCED : Metrics.USER_ACTION_HIDE_ADVANCED);

        LocalPreferences.setShowDeviceRoot(this, mState.action, display);
        mState.showAdvanced = display;
        RootsFragment.get(getFragmentManager()).onDisplayStateChanged();
        invalidateOptionsMenu();
    }

    /**
     * Set mode based on explicit user action.
     */
    void setViewMode(@ViewMode int mode) {
        if (mode == State.MODE_GRID) {
            Metrics.logUserAction(this, Metrics.USER_ACTION_GRID);
        } else if (mode == State.MODE_LIST) {
            Metrics.logUserAction(this, Metrics.USER_ACTION_LIST);
        }

        LocalPreferences.setViewMode(this, getCurrentRoot(), mode);
        mState.derivedMode = mode;

        // view icon needs to be updated, but we *could* do it
        // in onOptionsItemSelected, and not do the full invalidation
        // But! That's a larger refactoring we'll save for another day.
        invalidateOptionsMenu();
        DirectoryFragment dir = getDirectoryFragment();
        if (dir != null) {
            dir.onViewModeChanged();
        }

        mSortController.onViewModeChanged(mode);
    }

    public void setPending(boolean pending) {
        // TODO: Isolate this behavior to PickActivity.
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(Shared.EXTRA_STATE, mState);
        mSearchManager.onSaveInstanceState(state);
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
    }

    /**
     * Delegate ths call to the current fragment so it can save selection.
     * Feel free to expand on this with other useful state.
     */
    @Override
    public RetainedState onRetainNonConfigurationInstance() {
        RetainedState retained = new RetainedState();
        DirectoryFragment fragment = DirectoryFragment.get(getFragmentManager());
        if (fragment != null) {
            fragment.retainState(retained);
        }
        return retained;
    }

    public @Nullable RetainedState getRetainedState() {
        return mRetainedState;
    }

    @Override
    public boolean isSearchExpanded() {
        return mSearchManager.isExpanded();
    }

    @Override
    public RootInfo getCurrentRoot() {
        if (mState.stack.root != null) {
            return mState.stack.root;
        } else {
            return mRoots.getRecentsRoot();
        }
    }

    public DocumentInfo getCurrentDirectory() {
        return mState.stack.peek();
    }

    public Executor getExecutorForCurrentDirectory() {
        final DocumentInfo cwd = getCurrentDirectory();
        if (cwd != null && cwd.authority != null) {
            return ProviderExecutor.forAuthority(cwd.authority);
        } else {
            return AsyncTask.THREAD_POOL_EXECUTOR;
        }
    }

    @Override
    public void onBackPressed() {
        // While action bar is expanded, the state stack UI is hidden.
        if (mSearchManager.cancelSearch()) {
            return;
        }

        DirectoryFragment dir = getDirectoryFragment();
        if (dir != null && dir.onBackPressed()) {
            return;
        }

        if (popDir()) {
            return;
        }

        super.onBackPressed();
    }

    /**
     * Declare a global key handler to route key events when there isn't a specific focus view. This
     * covers the scenario where a user opens DocumentsUI and just starts typing.
     *
     * @param keyCode
     * @param event
     * @return
     */
    @CallSuper
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Events.isNavigationKeyCode(keyCode)) {
            // Forward all unclaimed navigation keystrokes to the DirectoryFragment. This causes any
            // stray navigation keystrokes focus the content pane, which is probably what the user
            // is trying to do.
            DirectoryFragment df = DirectoryFragment.get(getFragmentManager());
            if (df != null) {
                df.requestFocus();
                return true;
            }
        } else if (keyCode == KeyEvent.KEYCODE_TAB) {
            // Tab toggles focus on the navigation drawer.
            toggleNavDrawerFocus();
            return true;
        } else if (keyCode == KeyEvent.KEYCODE_DEL) {
            popDir();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void addEventListener(EventListener listener) {
        mEventListeners.add(listener);
    }

    public void removeEventListener(EventListener listener) {
        mEventListeners.remove(listener);
    }

    public void notifyDirectoryLoaded(Uri uri) {
        for (EventListener listener : mEventListeners) {
            listener.onDirectoryLoaded(uri);
        }
    }

    void notifyDirectoryNavigated(Uri uri) {
        for (EventListener listener : mEventListeners) {
            listener.onDirectoryNavigated(uri);
        }
    }

    /**
     * Toggles focus between the navigation drawer and the directory listing. If the drawer isn't
     * locked, open/close it as appropriate.
     */
    void toggleNavDrawerFocus() {
        if (mNavDrawerHasFocus) {
            mDrawer.setOpen(false);
            DirectoryFragment df = DirectoryFragment.get(getFragmentManager());
            if (df != null) {
                df.requestFocus();
            }
        } else {
            mDrawer.setOpen(true);
            RootsFragment rf = RootsFragment.get(getFragmentManager());
            if (rf != null) {
                rf.requestFocus();
            }
        }
        mNavDrawerHasFocus = !mNavDrawerHasFocus;
    }

    /**
     * Pops the top entry off the directory stack, and returns the user to the previous directory.
     * If the directory stack only contains one item, this method does nothing.
     *
     * @return Whether the stack was popped.
     */
    private boolean popDir() {
        if (mState.stack.size() > 1) {
            mState.stack.pop();
            refreshCurrentRootAndDirectory(AnimationView.ANIM_LEAVE);
            return true;
        }
        return false;
    }

    /**
     * Closes the activity when it's idle.
     */
    private void addListenerForLaunchCompletion() {
        addEventListener(new EventListener() {
            @Override
            public void onDirectoryNavigated(Uri uri) {
            }

            @Override
            public void onDirectoryLoaded(Uri uri) {
                removeEventListener(this);
                getMainLooper().getQueue().addIdleHandler(new IdleHandler() {
                    @Override
                    public boolean queueIdle() {
                        // If startup benchmark is requested by a whitelisted testing package, then
                        // close the activity once idle, and notify the testing activity.
                        if (getIntent().getBooleanExtra(EXTRA_BENCHMARK, false) &&
                                BENCHMARK_TESTING_PACKAGE.equals(getCallingPackage())) {
                            setResult(RESULT_OK);
                            finish();
                        }

                        Metrics.logStartupMs(
                                BaseActivity.this, (int) (new Date().getTime() - mStartTime));

                        // Remove the idle handler.
                        return false;
                    }
                });
            }
        });
    }

    private static final class HandleRootsChangedTask
            extends PairedTask<BaseActivity, RootInfo, RootInfo> {
        RootInfo mCurrentRoot;
        DocumentInfo mDefaultRootDocument;

        public HandleRootsChangedTask(BaseActivity activity) {
            super(activity);
        }

        @Override
        protected RootInfo run(RootInfo... roots) {
            assert(roots.length == 1);
            mCurrentRoot = roots[0];
            final Collection<RootInfo> cachedRoots = mOwner.mRoots.getRootsBlocking();
            for (final RootInfo root : cachedRoots) {
                if (root.getUri().equals(mCurrentRoot.getUri())) {
                    // We don't need to change the current root as the current root was not removed.
                    return null;
                }
            }

            // Choose the default root.
            final RootInfo defaultRoot = mOwner.mRoots.getDefaultRootBlocking(mOwner.mState);
            assert(defaultRoot != null);
            if (!defaultRoot.isRecents()) {
                mDefaultRootDocument = defaultRoot.getRootDocumentBlocking(mOwner);
            }
            return defaultRoot;
        }

        @Override
        protected void finish(RootInfo defaultRoot) {
            if (defaultRoot == null) {
                return;
            }

            // If the activity has been launched for the specific root and it is removed, finish the
            // activity.
            final Uri uri = mOwner.getIntent().getData();
            if (uri != null && uri.equals(mCurrentRoot.getUri())) {
                mOwner.finish();
                return;
            }

            // Clear entire backstack and start in new root.
            mOwner.mState.onRootChanged(defaultRoot);
            mOwner.mSearchManager.update(defaultRoot);

            if (defaultRoot.isRecents()) {
                mOwner.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
            } else {
                mOwner.openContainerDocument(mDefaultRootDocument);
            }
        }
    }

    public final class RetainedState {
        public @Nullable Selection selection;

        public boolean hasSelection() {
            return selection != null;
        }
    }

    @VisibleForTesting
    protected interface EventListener {
        /**
         * @param uri Uri navigated to. If recents, then null.
         */
        void onDirectoryNavigated(@Nullable Uri uri);

        /**
         * @param uri Uri of the loaded directory. If recents, then null.
         */
        void onDirectoryLoaded(@Nullable Uri uri);
    }
}
