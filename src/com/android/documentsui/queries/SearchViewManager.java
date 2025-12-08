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

package com.android.documentsui.queries;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ActionType;
import static com.android.documentsui.util.FlagUtils.isSearchV2Enabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.annotation.GuardedBy;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SearchView.OnQueryTextListener;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.modules.utils.build.SdkLevel;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Manages searching UI behavior.
 */
public class SearchViewManager implements
        SearchView.OnCloseListener, OnQueryTextListener, OnClickListener, OnFocusChangeListener,
        OnActionExpandListener {

    private static final String TAG = "SearchManager";

    // How long we wait after the user finishes typing before kicking off a search.
    public static final int SEARCH_DELAY_MS = 750;

    private final SearchManagerListener mListener;
    private final EventHandler<String> mCommandProcessor;
    private final SearchChipViewManager mChipViewManager;
    private final SearchOptionsController mSearchOptionsController;
    private final Timer mTimer;
    private final Handler mUiHandler;

    private final Object mSearchLock;
    @GuardedBy("mSearchLock")
    private @Nullable Runnable mQueuedSearchRunnable;
    @GuardedBy("mSearchLock")
    private @Nullable TimerTask mQueuedSearchTask;
    private @Nullable String mCurrentSearch;
    private String mQueryContentFromIntent;
    private boolean mSearchExpanded;
    private boolean mIgnoreNextClose;
    private boolean mFullBar;
    private boolean mIsHistorySearch;
    private boolean mShowSearchBar;
    private @Nullable SearchLocationOption mLocationOption;
    private boolean mShowDockedSearch;

    private @Nullable Menu mMenu;
    private @Nullable MenuItem mMenuItem;
    private @Nullable SearchView mSearchView;
    private @Nullable MenuItem mDockedSearch;
    private @Nullable EditText mDockedSearchEditText;
    private @Nullable FragmentManager mFragmentManager;
    private @Nullable RootInfo mCurrentRoot;

    public SearchViewManager(
            SearchManagerListener listener,
            EventHandler<String> commandProcessor,
            ViewGroup chipGroup,
            @Nullable View optionsContainer,
            @Nullable Bundle savedState) {
        this(listener, commandProcessor, new SearchChipViewManager(chipGroup),
                new SearchOptionsController(optionsContainer), savedState,
                new Timer(), new Handler(Looper.getMainLooper()));
    }

    @VisibleForTesting
    protected SearchViewManager(
            SearchManagerListener listener,
            EventHandler<String> commandProcessor,
            SearchChipViewManager chipViewManager,
            @Nullable SearchOptionsController searchOptionsController,
            @Nullable Bundle savedState,
            Timer timer,
            Handler handler) {
        assert (listener != null);
        assert (commandProcessor != null);

        mSearchLock = new Object();
        mListener = listener;
        mCommandProcessor = commandProcessor;
        mTimer = timer;
        mUiHandler = handler;
        mChipViewManager = chipViewManager;
        mChipViewManager.setSearchChipViewManagerListener(this::onChipCheckedStateChanged);
        mCurrentRoot = null;
        if (!isSearchV2Enabled()) {
            mSearchOptionsController = null;
        } else {
            mSearchOptionsController = searchOptionsController;
            mLocationOption = SearchLocationOption.ROOT_FOLDER;
            if (mSearchOptionsController != null) {
                mSearchOptionsController.setOptionChangeListener(this::onSearchOptionsChanged);
            }
        }

        if (savedState != null) {
            mCurrentSearch = savedState.getString(Shared.EXTRA_QUERY);
            mChipViewManager.restoreCheckedChipItems(savedState);
        } else {
            mCurrentSearch = null;
        }
    }

    private void onChipCheckedStateChanged(View v) {
        mListener.onSearchChipStateChanged(v);
        performSearch(mCurrentSearch);
    }

    private void onSearchOptionsChanged(SearchOptionsState optionsState) {
        mLocationOption = optionsState.getLocation();
        performSearch(mCurrentSearch);
    }

    /**
     * Parse the query content from Intent. If the action is not {@link State#ACTION_GET_CONTENT}
     * or {@link State#ACTION_OPEN}, don't perform search.
     * @param intent the intent to parse.
     * @param action the action to check.
     * @return True, if get the query content from the intent. Otherwise, false.
     */
    public boolean parseQueryContentFromIntent(Intent intent, @ActionType int action) {
        if (action == ACTION_OPEN || action == ACTION_GET_CONTENT) {
            final String queryString = intent.getStringExtra(Intent.EXTRA_CONTENT_QUERY);
            if (!TextUtils.isEmpty(queryString)) {
                mQueryContentFromIntent = queryString;
                return true;
            }
        }
        return false;
    }

    /**
     * Build the bundle of query arguments.
     * Example: search string and mime types
     *
     * @return the bundle of query arguments
     */
    public Bundle buildQueryArgs() {
        final Bundle queryArgs = isSearchV2Enabled() && mSearchOptionsController.isVisible()
                ? mSearchOptionsController.getOptionsQueryArgs()
                : mChipViewManager.getCheckedChipQueryArgs();
        if (!TextUtils.isEmpty(mCurrentSearch)) {
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, mCurrentSearch);
        } else if (isExpanded() && isSearching()) {
            // The existence of the DocumentsContract.QUERY_ARG_DISPLAY_NAME constant is used to
            // determine if this is a text search (as opposed to simply filtering from within a
            // non-searching view), so ensure the argument exists when searching.
            queryArgs.putString(DocumentsContract.QUERY_ARG_DISPLAY_NAME, "");
        }

        return queryArgs;
    }

    /**
     * Initialize the search chips base on the acceptMimeTypes.
     *
     * @param acceptMimeTypes use to filter chips
     */
    public void initChipSets(String[] acceptMimeTypes) {
        mChipViewManager.initChipSets(acceptMimeTypes);
    }

    /**
     * Update the search chips base on the acceptMimeTypes.
     * If the count of matched chips is less than two, we will
     * hide the chip row.
     *
     * @param acceptMimeTypes use to filter chips
     */
    public void updateChips(String[] acceptMimeTypes) {
        mChipViewManager.updateChips(acceptMimeTypes);
    }

    /**
     * Bind chip data in ChipViewManager on other view groups
     *
     * @param chipGroup target view group for bind ChipViewManager data
     */
    public void bindChips(ViewGroup chipGroup) {
        mChipViewManager.bindMirrorGroup(chipGroup);
    }

    /**
     * Click behavior when chip in synced chip group click.
     *
     * @param data SearchChipData synced in mirror group
     */
    public void onMirrorChipClick(SearchChipData data) {
        mChipViewManager.onMirrorChipClick(data);
        if (!mShowDockedSearch && mSearchView != null) {
            mSearchView.clearFocus();
        } else if (mShowDockedSearch && mDockedSearchEditText != null) {
            mDockedSearchEditText.clearFocus();
        }
    }

    /**
     * Initailize search view by option menu.
     *
     * @param menu            the menu include search view
     * @param isFullBarSearch whether hide other menu when search view expand
     * @param isShowSearchBar whether replace collapsed search view by search hint text
     * @param showDockedSearch whether show a docked (inline) search bar in the toolbar. When true,
     *                         the search icon and search view will be hidden.
     */
    public void install(Menu menu, boolean isFullBarSearch, boolean isShowSearchBar,
            boolean showDockedSearch) {
        mMenu = menu;
        mMenuItem = mMenu.findItem(getRes(R.id.option_menu_search));
        mSearchView = (SearchView) mMenuItem.getActionView();

        mSearchView.setOnQueryTextListener(this);
        mSearchView.setOnCloseListener(this);
        mSearchView.setOnSearchClickListener(this);
        mSearchView.setOnQueryTextFocusChangeListener(this);
        final View clearButton = mSearchView.findViewById(androidx.appcompat.R.id.search_close_btn);
        if (clearButton != null) {
            clearButton.setPadding(clearButton.getPaddingStart() + getPixelForDp(4),
                    clearButton.getPaddingTop(), clearButton.getPaddingEnd() + getPixelForDp(4),
                    clearButton.getPaddingBottom());
            clearButton.setOnClickListener(v -> {
                mSearchView.setQuery("", false);
                mSearchView.requestFocus();
                mListener.onSearchViewClearClicked();
            });
        }
        if (SdkLevel.isAtLeastU()) {
            final View textView = mSearchView.findViewById(androidx.appcompat.R.id.search_src_text);
            if (textView != null) {
                try {
                    textView.setIsHandwritingDelegate(true);
                } catch (LinkageError e) {
                    // Running on a device with an older build of Android U
                    // TODO(b/274154553): Remove try/catch block after Android U Beta 1 is released
                }
            }
        }

        // showDockedSearch comes from a config but enforce that use_material3 flag is enabled.
        mShowDockedSearch = isUseMaterial3FlagEnabled() && showDockedSearch;
        if (mShowDockedSearch) {
            mDockedSearch = mMenu.findItem(R.id.option_menu_docked_search);
            mDockedSearchEditText = mDockedSearch.getActionView().findViewById(
                    R.id.docked_search_text);
            View dockedSearchClear = mDockedSearch.getActionView().findViewById(
                    R.id.docked_search_clear);

            mDockedSearchEditText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus) {
                    onSearchExpanded();
                } else if (mCurrentSearch == null || mCurrentSearch.isEmpty()) {
                    onClose();
                }
                mListener.onSearchViewFocusChanged(hasFocus);
            });
            mDockedSearchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    dockedSearchClear.setVisibility(
                            TextUtils.isEmpty(s) ? View.INVISIBLE : View.VISIBLE);
                    onQueryTextChange(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) { }
            });
            mDockedSearchEditText.setOnEditorActionListener(
                    (v, actionId, event) -> onQueryTextSubmit(v.getText().toString()));
            dockedSearchClear.setOnClickListener(v -> {
                mDockedSearchEditText.setText("");
                mDockedSearchEditText.requestFocus();
                mListener.onSearchViewClearClicked();
            });
        }

        mFullBar = isFullBarSearch;
        mShowSearchBar = isShowSearchBar;
        mSearchView.setMaxWidth(Integer.MAX_VALUE);
        mMenuItem.setOnActionExpandListener(this);

        restoreSearch(true);
    }

    public void setFragmentManager(FragmentManager fragmentManager) {
        mFragmentManager = fragmentManager;
    }

    /**
     * Used to hide menu icons, when the search is being restored. Needed because search restoration
     * is done before onPrepareOptionsMenu(Menu menu) that is overriding the icons visibility.
     */
    public void updateMenu() {
        if (mMenu != null && isExpanded() && mFullBar) {
            mMenu.setGroupVisible(getRes(R.id.group_hide_when_searching), false);
        }
    }

    /**
     * @param stack New stack.
     */
    public void update(DocumentStack stack) {
        // If docked search is enabled, restore the search query. Otherwise expand search view
        // and restore the search query there.
        if (mShowDockedSearch) {
            if (mDockedSearchEditText == null) {
                if (DEBUG) {
                    Log.d(TAG, "update called before Search MenuItem installed.");
                }
                return;
            }
            if (mCurrentSearch != null) {
                mDockedSearchEditText.setText(mCurrentSearch);
            } else {
                mDockedSearchEditText.setText("");
                mDockedSearchEditText.clearFocus();
            }
        } else {
            if (mMenuItem == null || mSearchView == null) {
                if (DEBUG) {
                    Log.d(TAG, "update called before Search MenuItem installed.");
                }
                return;
            }
            if (mCurrentSearch != null) {
                mMenuItem.expandActionView();

                mSearchView.setIconified(false);
                mSearchView.clearFocus();
                mSearchView.setQuery(mCurrentSearch, false);
            } else {
                mSearchView.clearFocus();
                if (!mSearchView.isIconified()) {
                    mIgnoreNextClose = true;
                    mSearchView.setIconified(true);
                }

                if (mMenuItem.isActionViewExpanded()) {
                    mMenuItem.collapseActionView();
                }
            }
        }

        showMenu(stack);
    }

    public void showMenu(@Nullable DocumentStack stack) {
        final DocumentInfo cwd = stack != null ? stack.peek() : null;

        boolean supportsSearch = true;

        // Searching in archives is not enabled, as archives are backed by
        // a different provider than the root provider.
        if (cwd != null && cwd.isInArchive()) {
            supportsSearch = false;
        }

        final RootInfo root = stack != null ? stack.getRoot() : null;
        if (root == null || !root.supportsSearch()) {
            supportsSearch = false;
        }

        if (mMenuItem == null) {
            if (DEBUG) {
                Log.d(TAG, "showMenu called before Search MenuItem installed.");
            }
            return;
        }

        if (!supportsSearch) {
            mCurrentSearch = null;
        }

        if (mShowDockedSearch && !mShowSearchBar) {
            // When show_docked_search is enabled, we replace the search icon with a docked
            // searchbar.
            mMenuItem.setVisible(false);
            mDockedSearch.setVisible(supportsSearch);
        } else {
            // Recent root show open search bar, do not show duplicate search icon.
            mMenuItem.setVisible(supportsSearch && (!stack.isRecents() || !mShowSearchBar));
        }

        if (!isSearchV2Enabled()) {
            mChipViewManager.setChipsRowVisible(supportsSearch && root.supportsMimeTypesSearch());
        }
    }

    /**
     * Cancels current search operation. Triggers clearing and collapsing the SearchView.
     *
     * @return True if it cancels search. False if it does not operate search currently.
     */
    public boolean cancelSearch() {
        if ((isExpanded() || isSearching())) {
            cancelQueuedSearch();

            if (mFullBar || mShowDockedSearch) {
                this.onStopSearch();
            } else if (mSearchView != null) {
                // Causes calling onClose(). onClose() is triggering directory content update.
                mSearchView.setIconified(true);
            }

            return true;
        }
        return false;
    }

    private int getPixelForDp(int dp) {
        final float scale = getCurrentContext().getResources().getDisplayMetrics().density;
        return (int) (dp * scale + 0.5f);
    }

    private void cancelQueuedSearch() {
        synchronized (mSearchLock) {
            if (mQueuedSearchTask != null) {
                mQueuedSearchTask.cancel();
            }
            mQueuedSearchTask = null;
            mUiHandler.removeCallbacks(mQueuedSearchRunnable);
            mQueuedSearchRunnable = null;
            mIsHistorySearch = false;
        }
    }

    /**
     * Sets search view into the searching state. Used to restore state after device orientation
     * change.
     */
    public void restoreSearch(boolean keepFocus) {
        if (isTextSearching()) {
            if (!mShowDockedSearch) {
                if (mSearchView == null) {
                    return;
                }

                onSearchBarClicked();
                mSearchView.setQuery(mCurrentSearch, false);

                if (keepFocus) {
                    mSearchView.requestFocus();
                } else {
                    mSearchView.clearFocus();
                }
            } else {
                if (mDockedSearchEditText == null) {
                    return;
                }

                onSearchExpanded();
                mDockedSearchEditText.setText(mCurrentSearch);

                if (keepFocus) {
                    mDockedSearchEditText.requestFocus();
                } else {
                    mDockedSearchEditText.clearFocus();
                }
            }
        }
    }

    public void onSearchBarClicked() {
        if (mMenuItem == null) {
            return;
        }

        mMenuItem.expandActionView();
        onSearchExpanded();
    }

    private void onSearchExpanded() {
        mSearchExpanded = true;
        if (mFullBar && mMenu != null) {
            mMenu.setGroupVisible(getRes(R.id.group_hide_when_searching), false);
        }

        mListener.onSearchViewChanged(true);
    }

    /**
     * Sets the current root for which searches may be executed. This is part of SearchV2 and
     * has no effect otherwise. For SearchV2 the current root is used to extract the root
     * title in the dropdown options.
     * @param root The current root that may be searched.
     */
    public void setCurrentRoot(RootInfo root) {
        if (isSearchV2Enabled()) {
            mCurrentRoot = root;
        }
    }

    /**
     * Toggles between chips and dropdowns search option controls. This only has any effect if
     * SearchV2 is enabled.
     * @param controls Which controls are to be made visible.
     */
    private void useSearchOptions(SearchOptionsControls controls) {
        if (isSearchV2Enabled()) {
            if (mSearchOptionsController != null) {
                if (SearchOptionsControls.DROPDOWNS == controls) {
                    Integer mimeChipType = mChipViewManager.getLeadingMimeChipType();
                    if (mimeChipType != null) {
                        mSearchOptionsController.setSelectedFileType(mimeChipType);
                    }
                    mSearchOptionsController.show(mCurrentRoot);
                } else {
                    mSearchOptionsController.hide();
                }
            }
            mChipViewManager.setChipsRowVisible(SearchOptionsControls.CHIPS == controls);
        }
    }

    /**
     * Clears the search. Triggers refreshing of the directory content.
     *
     * @return True if the default behavior of clearing/dismissing SearchView should be overridden.
     *         False otherwise.
     */
    @Override
    public boolean onClose() {
        useSearchOptions(SearchOptionsControls.CHIPS);
        return onStopSearch();
    }

    private boolean onStopSearch() {
        mSearchExpanded = false;
        if (mIgnoreNextClose) {
            mIgnoreNextClose = false;
            return false;
        }

        // Refresh the directory if a search was done
        if (mCurrentSearch != null || mChipViewManager.hasCheckedItems()) {
            // Make sure SearchFragment was dismissed.
            if (mFragmentManager != null) {
                SearchFragment.dismissFragment(mFragmentManager);
            }

            // Clear checked chips
            mChipViewManager.clearCheckedChips();
            mCurrentSearch = null;
            mListener.onSearchChanged(mCurrentSearch);
        }

        if (mFullBar && mMenuItem != null) {
            mMenuItem.collapseActionView();
        }
        mListener.onSearchFinished();

        mListener.onSearchViewChanged(false);

        return false;
    }

    /**
     * Called when owning activity is saving state to be used to restore state during creation.
     *
     * @param state Bundle to save state too
     */
    public void onSaveInstanceState(Bundle state) {
        boolean hasFocus = mShowDockedSearch ? mDockedSearchEditText != null
                && mDockedSearchEditText.hasFocus() : mSearchView != null && mSearchView.hasFocus();
        if (hasFocus && mCurrentSearch == null) {
            // Restore focus even if no text was input before screen rotation.
            mCurrentSearch = "";
        }
        state.putString(Shared.EXTRA_QUERY, mCurrentSearch);
        mChipViewManager.onSaveInstanceState(state);
    }

    /**
     * Sets mSearchExpanded. Called when search icon is clicked to start search for both search view
     * modes.
     */
    @Override
    public void onClick(View v) {
        onSearchExpanded();
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        if (mCommandProcessor.accept(query)) {
            if (!mShowDockedSearch && mSearchView != null) {
                mSearchView.setQuery("", false);
            } else if (mShowDockedSearch && mDockedSearchEditText != null) {
                mDockedSearchEditText.setText("");
            }
        } else {
            cancelQueuedSearch();
            // Don't kick off a search if we've already finished it.
            if (!TextUtils.equals(mCurrentSearch, query)) {
                mCurrentSearch = query;
                mListener.onSearchChanged(mCurrentSearch);
            }
            recordHistory();
            if (!mShowDockedSearch && mSearchView != null) {
                mSearchView.clearFocus();
            } else if (mShowDockedSearch && mDockedSearchEditText != null) {
                mDockedSearchEditText.clearFocus();
            }
        }

        return true;
    }

    /**
     * Used to detect and handle back button pressed event when search is expanded.
     */
    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        // This is only called for SearchView. The docked search has a separate focus listener.
        if (!hasFocus && !mChipViewManager.hasCheckedItems()) {
            if (mCurrentSearch == null) {
                if (!mShowDockedSearch && mSearchView != null) {
                    mSearchView.setIconified(true);
                }
            } else if (TextUtils.isEmpty(getSearchViewText())) {
                cancelSearch();
            }
        }
        mListener.onSearchViewFocusChanged(hasFocus);
    }

    @VisibleForTesting
    protected TimerTask createSearchTask(String newText) {
        return new TimerTask() {
            @Override
            public void run() {
                // Do the actual work on the main looper.
                synchronized (mSearchLock) {
                    mQueuedSearchRunnable = () -> {
                        mCurrentSearch = newText;
                        if (mCurrentSearch != null && mCurrentSearch.isEmpty()) {
                            mCurrentSearch = null;
                        }
                        logTextSearchMetric();
                        mListener.onSearchChanged(mCurrentSearch);
                    };
                    mUiHandler.post(mQueuedSearchRunnable);
                }
            }
        };
    }

    /**
     * A method to isolate the task of getting context out of search view. This method is here
     * so that we have only one place where compiler may warn about NullPointerException.
     * @return The current context in which this search view manager operates.
     */
    private Context getCurrentContext() {
        return Objects.requireNonNull(mSearchView, "SearchView is null").getContext();
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (isSearchV2Enabled()) {
            if (newText.isEmpty()) {
                useSearchOptions(SearchOptionsControls.CHIPS);
            } else {
                useSearchOptions(SearchOptionsControls.DROPDOWNS);
            }
        }
        //Skip first search when search expanded
        if (mCurrentSearch == null && newText.isEmpty()) {
            return true;
        }

        performSearch(newText);
        if (mFragmentManager != null) {
            if (!newText.isEmpty()) {
                SearchFragment.dismissFragment(mFragmentManager);
            } else {
                SearchFragment.showFragment(mFragmentManager, "");
            }
        }
        return true;
    }

    private void performSearch(String newText) {
        if (isSearchV2Enabled()) {
            mListener.onSearchStarting();
        }
        cancelQueuedSearch();
        synchronized (mSearchLock) {
            mQueuedSearchTask = createSearchTask(newText);

            mTimer.schedule(mQueuedSearchTask, SEARCH_DELAY_MS);
        }
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        mMenu.setGroupVisible(getRes(R.id.group_hide_when_searching), true);

        // Handles case when search view is collapsed by using the arrow on the left of the bar
        if (isExpanded() || isSearching()) {
            cancelSearch();
            return false;
        }
        return true;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    public String getCurrentSearch() {
        return mCurrentSearch;
    }

    /**
     * Get current text on search view.
     *
     * @return  Current string on search view
     */
    public String getSearchViewText() {
        if (!mShowDockedSearch && mSearchView != null) {
            return mSearchView.getQuery().toString();
        } else if (mShowDockedSearch && mDockedSearchEditText != null) {
            return mDockedSearchEditText.getText().toString();
        }
        return null;
    }

    /**
     * Record current search for history.
     */
    public void recordHistory() {
        if (TextUtils.isEmpty(mCurrentSearch)) {
            return;
        }

        recordHistoryInternal();
    }

    protected void recordHistoryInternal() {
        if (mSearchView == null) {
            Log.w(TAG, "Search view is null, skip record history this time");
            return;
        }

        SearchHistoryManager.getInstance(
                getCurrentContext().getApplicationContext()).addHistory(mCurrentSearch);
    }

    /**
     * Remove specific text item in history list.
     *
     * @param history target string for removed.
     */
    public void removeHistory(String history) {
        if (mSearchView == null) {
            Log.w(TAG, "Search view is null, skip remove history this time");
            return;
        }

        SearchHistoryManager.getInstance(
                getCurrentContext().getApplicationContext()).deleteHistory(history);
    }

    private void logTextSearchMetric() {
        if (isTextSearching()) {
            Metrics.logUserAction(mIsHistorySearch
                    ? MetricConsts.USER_ACTION_SEARCH_HISTORY : MetricConsts.USER_ACTION_SEARCH);
            Metrics.logSearchType(mIsHistorySearch
                    ? MetricConsts.TYPE_SEARCH_HISTORY : MetricConsts.TYPE_SEARCH_STRING);
            mIsHistorySearch = false;
        }
    }

    /**
     * Get the query content from intent.
     * @return If has query content, return the query content. Otherwise, return null
     * @see #parseQueryContentFromIntent(Intent, int)
     */
    public String getQueryContentFromIntent() {
        return mQueryContentFromIntent;
    }

    public void setCurrentSearch(String queryString) {
        mCurrentSearch = queryString;
    }

    /**
     * Set next search type is history search.
     */
    public void setHistorySearch() {
        mIsHistorySearch = true;
    }

    public boolean isSearching() {
        return mCurrentSearch != null || mChipViewManager.hasCheckedItems();
    }

    public boolean isTextSearching() {
        return mCurrentSearch != null;
    }

    public boolean hasCheckedChip() {
        return mChipViewManager.hasCheckedItems();
    }

    public boolean isExpanded() {
        return mSearchExpanded;
    }

    /**
     * Returns roots that are queried for recent files.
     * @param roots A stream of roots that is guaranteed to have rootId, and authority.
     * @param userId The user ID of the recents root.
     * @return The subset of roots to be queried about recent files.
     */
    private Collection<RootInfo> getRecentRoots(Stream<RootInfo> roots, UserId userId) {
        return roots.filter(r -> r.isLocalOnly()
                && r.supportsRecents() && r.userId.equals(userId)
                && !r.isExternalStorage()).collect(
                Collectors.toList());
    }

    /**
     * Returns all roots that can deliver search results. In order to avoid duplicate results,
     * we remove all MEDIA sources, and downloads, since files in those providers are also known
     * to the external storage provider.
     * @param roots A stream of roots that is guaranteed to have rootId, and authority.
     * @return The subset of roots that can be searched.
     */
    private Collection<RootInfo> getAllSearchableRoots(Stream<RootInfo> roots) {
        return roots.filter(r -> !Providers.AUTHORITY_MEDIA.equals(r.authority)
                && !r.isDownloads()).collect(Collectors.toList());
    }

    /**
     * For the given set of roots, and the current state of the document stack, it returns a list
     * of searchable folders. This method uses the state of the search options to narrow down
     * the list of folders to the one that the user asks to be searched.
     *
     * @param roots The starting list of potentially searchable roots.
     * @param stack The current state of the document stack.
     * @return A list of searchable roots that should be searched based on the search options.
     */
    public Collection<RootInfo> getSearchRoots(Collection<RootInfo> roots,
            DocumentStack stack) {
        if (mLocationOption == null || stack.getRoot() == null) {
            // If we don't know where to search, search nowhere.
            return Collections.emptyList();
        }
        Stream<RootInfo> core = roots.stream().filter(
                r -> r.rootId != null && r.authority != null && r.supportsSearch());
        if (mLocationOption == SearchLocationOption.EVERYWHERE) {
            // If the current location is everywhere get all searchable roots.
            return getAllSearchableRoots(core);
        }
        if (stack.isRecents()) {
            // For recents use recent roots that match current recent root user ID.
            return getRecentRoots(core, stack.getRoot().userId);
        }
        if (mLocationOption != SearchLocationOption.ROOT_FOLDER) {
            throw new IllegalStateException(
                    "Unhandled location option " + mLocationOption.name());
        }
        // Just search current root.
        return Collections.singletonList(stack.getRoot());
    }

    public interface SearchManagerListener {
        void onSearchChanged(@Nullable String query);

        /**
         * Called when the search is about to start. There may be other tasks performed
         * before actual searching commences, such as debouncing, etc. However, this is
         * the signal that the SearchViewManager is getting ready to start searching.
         */
        void onSearchStarting();

        void onSearchFinished();

        void onSearchViewChanged(boolean opened);

        void onSearchChipStateChanged(View v);

        void onSearchViewFocusChanged(boolean hasFocus);

        /**
         * Call back when search view clear button clicked
         */
        void onSearchViewClearClicked();
    }
}
