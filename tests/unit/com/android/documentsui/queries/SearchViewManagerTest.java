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

package com.android.documentsui.queries;

import static android.provider.DocumentsContract.QUERY_ARG_DISPLAY_NAME;
import static android.provider.DocumentsContract.QUERY_ARG_FILE_SIZE_OVER;
import static android.provider.DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER;
import static android.provider.DocumentsContract.QUERY_ARG_MIME_TYPES;
import static android.provider.DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;

import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.Nullable;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.MetricConsts;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.queries.SearchViewManager.SearchManagerListener;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestEventHandler;
import com.android.documentsui.testing.TestHandler;
import com.android.documentsui.testing.TestMenu;
import com.android.documentsui.testing.TestMenuItem;
import com.android.documentsui.testing.TestTimer;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class SearchViewManagerTest {

    @Rule
    public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();

    private TestEventHandler<String> mTestEventHandler;
    private TestTimer mTestTimer;
    private TestHandler mTestHandler;
    private TestMenu mTestMenu;
    private TestMenuItem mSearchMenuItem;
    private TestableSearchViewManager mSearchViewManager;
    private SearchChipViewManager mSearchChipViewManager;
    private SearchOptionsController mSearchOptionsController;

    private boolean mListenerOnSearchChangedCalled;
    private int mOnSearchStartingCallCount;

    @Before
    public void setUp() {
        mTestEventHandler = new TestEventHandler<>();
        mTestTimer = new TestTimer();
        mTestHandler = new TestHandler();

        mOnSearchStartingCallCount = 0;

        final SearchManagerListener searchListener = new SearchManagerListener() {
            @Override
            public void onSearchChanged(@Nullable String query) {
                mListenerOnSearchChangedCalled = true;
            }

            @Override
            public void onSearchStarting() {
                ++mOnSearchStartingCallCount;
            }

            @Override
            public void onSearchFinished() {
            }

            @Override
            public void onSearchViewChanged(boolean opened) {
            }

            @Override
            public void onSearchChipStateChanged(View v) {
            }

            @Override
            public void onSearchViewFocusChanged(boolean hasFocus) {
            }

            @Override
            public void onSearchViewClearClicked() {
            }
        };

        ViewGroup chipGroup = mock(ViewGroup.class);
        mSearchChipViewManager = spy(new SearchChipViewManager(chipGroup));
        View searchOptionsView = mock(View.class);
        mSearchOptionsController = new SearchOptionsController(searchOptionsView);
        mSearchViewManager = new TestableSearchViewManager(
                searchListener,
                mTestEventHandler,
                mSearchChipViewManager,
                mSearchOptionsController,
                /*savedState=*/null,
                mTestTimer, mTestHandler);

        mTestMenu = TestMenu.create();
        mSearchMenuItem = mTestMenu.findItem(R.id.option_menu_search);
        mSearchViewManager.install(mTestMenu, true, false, false);
    }

    private static class TestableSearchViewManager extends SearchViewManager {

        private String mHistoryRecorded;
        private boolean mIsHistoryRecorded;

        public TestableSearchViewManager(
                SearchManagerListener listener,
                EventHandler<String> commandProcessor,
                SearchChipViewManager chipViewManager,
                SearchOptionsController optionsController,
                @Nullable Bundle savedState,
                Timer timer,
                Handler handler) {
            super(listener, commandProcessor, chipViewManager, optionsController, savedState, timer,
                    handler);
        }

        @Override
        public TimerTask createSearchTask(String newText) {
            TimerTask task = super.createSearchTask(newText);
            TestTimer.Task testTask = new TestTimer.Task(task);
            return testTask;
        }

        @Override
        protected void recordHistoryInternal() {
            mHistoryRecorded = getCurrentSearch();
            mIsHistoryRecorded = true;
        }

        public String getRecordedHistory() {
            return mHistoryRecorded;
        }

        public boolean isHistoryRecorded() {
            return mIsHistoryRecorded;
        }
    }

    private void fastForwardTo(long timeMs) {
        mTestTimer.fastForwardTo(timeMs);
        mTestHandler.dispatchAllMessages();
    }


    @Test
    public void testParseQueryContent_ActionIsNotMatched_NotParseQueryContent() {
        final String queryString = "query";
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_CONTENT_QUERY, queryString);

        mSearchViewManager.parseQueryContentFromIntent(intent, -1);
        assertTrue(mSearchViewManager.getQueryContentFromIntent() == null);
    }

    @Test
    public void testParseQueryContent_queryContentIsMatched() {
        final String queryString = "query";
        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_CONTENT_QUERY, queryString);

        mSearchViewManager.parseQueryContentFromIntent(intent, ACTION_GET_CONTENT);
        assertEquals(queryString, mSearchViewManager.getQueryContentFromIntent());
    }

    @Test
    public void testIsExpanded_ExpandsOnClick() {
        mSearchViewManager.onClick(null);
        assertTrue(mSearchViewManager.isExpanded());
    }

    @Test
    public void testIsExpanded_CollapsesOnMenuItemActionCollapse() {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onMenuItemActionCollapse(null);
        assertFalse(mSearchViewManager.isExpanded());
    }

    @Test
    public void testIsSearching_TrueHasCheckedChip() throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        assertTrue(mSearchViewManager.isSearching());
    }

    @Test
    public void testIsSearching_FalseOnClick() throws Exception {
        mSearchViewManager.onClick(null);
        assertFalse(mSearchViewManager.isSearching());
    }

    @Test
    public void testIsSearching_TrueOnQueryTextSubmit() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextSubmit("query");
        assertTrue(mSearchViewManager.isSearching());
    }

    @Test
    public void testIsSearching_FalseImmediatelyAfterOnQueryTextChange() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        assertFalse(mSearchViewManager.isSearching());
    }

    @Test
    public void testIsSearching_TrueAfterOnQueryTextChangeAndWait() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);
        assertTrue(mSearchViewManager.isSearching());
    }

    @Test
    public void testIsSearching_FalseWhenSecondOnQueryTextChangeResetsTimer() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS - 1);
        mSearchViewManager.onQueryTextChange("qu");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);
        assertFalse(mSearchViewManager.isSearching());
    }

    @Test
    public void testIsSearching_TrueAfterSecondOnQueryTextChangeResetsTimer() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS - 1);
        mSearchViewManager.onQueryTextChange("qu");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS * 2);
        assertTrue(mSearchViewManager.isSearching());
    }

    @Test
    public void testIsSearching_FalseIfSearchCanceled() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        mSearchViewManager.cancelSearch();
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);
        assertFalse(mSearchViewManager.isSearching());
    }

    @Test
    public void testOnSearchChanged_CalledAfterOnQueryTextSubmit() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextSubmit("q");
        assertTrue(mListenerOnSearchChangedCalled);
    }

    @Test
    public void testOnSearchChanged_NotCalledImmediatelyAfterOnQueryTextChanged() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        assertFalse(mListenerOnSearchChangedCalled);
    }

    @Test
    public void testOnSearchChanged_CalledAfterOnQueryTextChangedAndWait() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);
        assertTrue(mListenerOnSearchChangedCalled);
    }

    @Test
    public void testOnSearchChanged_CalledOnlyOnceAfterOnQueryTextSubmit() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        mSearchViewManager.onQueryTextSubmit("q");

        // Clear the flag to check if it gets set again.
        mListenerOnSearchChangedCalled = false;
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);
        assertFalse(mListenerOnSearchChangedCalled);
    }

    @Test
    public void testOnSearchChanged_NotCalledForOnQueryTextSubmitIfSearchAlreadyFinished()
            throws Exception {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);
        // Clear the flag to check if it gets set again.
        mListenerOnSearchChangedCalled = false;
        mSearchViewManager.onQueryTextSubmit("q");
        assertFalse(mListenerOnSearchChangedCalled);
    }

    @Test
    public void testHistoryRecorded_recordOnQueryTextSubmit() {
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextSubmit("q");

        assertEquals(mSearchViewManager.getCurrentSearch(),
                mSearchViewManager.getRecordedHistory());
    }

    @Test
    public void testHistoryRecorded_skipWhenNoSearchString() {
        mSearchViewManager.recordHistory();

        assertFalse(mSearchViewManager.isHistoryRecorded());
    }

    @Test
    public void testCheckedChipItems_IsEmptyIfSearchCanceled() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        mSearchViewManager.cancelSearch();
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);
        assertTrue(!mSearchChipViewManager.hasCheckedItems());
    }

    @Test
    public void testBuildQueryArgs_hasSearchString() throws Exception {
        final String query = "q";
        mSearchViewManager.onClick(null);
        mSearchViewManager.onQueryTextChange("q");
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);

        final Bundle queryArgs = mSearchViewManager.buildQueryArgs();
        assertFalse(queryArgs.isEmpty());

        final String queryString = queryArgs.getString(DocumentsContract.QUERY_ARG_DISPLAY_NAME);
        assertEquals(query, queryString);
    }

    @Test
    public void testBuildQueryArgs_emptySearchString_expandedSearchWithChips_hasEmptyButNotMissingSearchString()
            throws Exception {
        mSearchViewManager.onClick(null);
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);

        final String queryString =
                mSearchViewManager.buildQueryArgs()
                        .getString(DocumentsContract.QUERY_ARG_DISPLAY_NAME);
        assertEquals("", queryString);
    }

    @Test
    public void testBuildQueryArgs_emptySearchString_withChipsWithoutExpandedSearch_hasNoSearchString()
            throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);

        assertFalse(mSearchViewManager.buildQueryArgs().containsKey(QUERY_ARG_DISPLAY_NAME));
    }

    @Test
    public void testBuildQueryArgs_emptySearchString_expandedSearchWithNoChips_hasNoSearchString()
            throws Exception {
        mSearchViewManager.onClick(null);
        fastForwardTo(SearchViewManager.SEARCH_DELAY_MS);

        assertFalse(mSearchViewManager.buildQueryArgs().containsKey(QUERY_ARG_DISPLAY_NAME));
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testBuildQueryArgs_hasMimeType() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();

        final Bundle queryArgs = mSearchViewManager.buildQueryArgs();
        assertFalse(queryArgs.isEmpty());

        final String[] mimeTypes = queryArgs.getStringArray(QUERY_ARG_MIME_TYPES);
        assertTrue(mimeTypes.length > 0);
        assertEquals("image/*", mimeTypes[0]);
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testBuildQueryArgs_hasLargeFilesSize() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();

        final Bundle queryArgs = mSearchViewManager.buildQueryArgs();
        assertFalse(queryArgs.isEmpty());

        final long largeFilesSize = queryArgs.getLong(QUERY_ARG_FILE_SIZE_OVER);
        assertEquals(10000000L, largeFilesSize);
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testBuildQueryArgs_hasWeekAgoTime() throws Exception {
        mSearchViewManager.onClick(null);
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();

        final long weekAgoInstant = LocalDate.now().minusDays(7).atStartOfDay(
                ZoneId.systemDefault()).toInstant().toEpochMilli();

        final Bundle queryArgs = mSearchViewManager.buildQueryArgs();
        assertFalse(queryArgs.isEmpty());

        // The difference between our calculated instance, in milliseconds, and the one stored in
        // the queryArgs should not be more than one minute. It is typically much less, but when
        // looking for files a week old, one minute this or that way does not matter much.
        final long lastModifiedArg = queryArgs.getLong(QUERY_ARG_LAST_MODIFIED_AFTER);
        assertThat(weekAgoInstant - lastModifiedArg).isWithin(1000 * 60L).of(0);
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testSupportsMimeTypesSearch_showChips() throws Exception {
        RootInfo root = spy(new RootInfo());
        when(root.isRecents()).thenReturn(false);
        root.flags = FLAG_SUPPORTS_SEARCH;
        root.queryArgs = QUERY_ARG_MIME_TYPES;
        DocumentStack stack = new DocumentStack(root, new DocumentInfo());

        mSearchViewManager.showMenu(stack);

        verify(mSearchChipViewManager, times(1)).setChipsRowVisible(true);
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testNotSupportsMimeTypesSearch_notShowChips() throws Exception {
        RootInfo root = spy(new RootInfo());
        when(root.isRecents()).thenReturn(false);
        root.flags = FLAG_SUPPORTS_SEARCH;
        root.queryArgs = TextUtils.join("\n",
                new String[]{QUERY_ARG_DISPLAY_NAME, QUERY_ARG_FILE_SIZE_OVER,
                        QUERY_ARG_LAST_MODIFIED_AFTER});
        DocumentStack stack = new DocumentStack(root, new DocumentInfo());

        mSearchViewManager.showMenu(stack);

        verify(mSearchChipViewManager, times(1)).setChipsRowVisible(false);
    }

    @Test
    public void testSupportsSearch_showMenu() throws Exception {
        RootInfo root = spy(new RootInfo());
        when(root.isRecents()).thenReturn(false);
        root.flags = FLAG_SUPPORTS_SEARCH;
        DocumentStack stack = new DocumentStack(root, new DocumentInfo());

        mSearchViewManager.showMenu(stack);

        assertTrue(mSearchMenuItem.isVisible());
    }

    @Test
    @DisableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY})
    public void testNotSupportsSearch_notShowMenuAndChips() throws Exception {
        RootInfo root = spy(new RootInfo());
        when(root.isRecents()).thenReturn(false);
        root.queryArgs = QUERY_ARG_MIME_TYPES;
        DocumentStack stack = new DocumentStack(root, new DocumentInfo());

        mSearchViewManager.install(mTestMenu, true, false, false);
        mSearchViewManager.showMenu(stack);

        assertFalse(mSearchMenuItem.isVisible());
        verify(mSearchChipViewManager, times(1)).setChipsRowVisible(false);
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testOnSearchStartingCalled() {
        mSearchViewManager.onClick(null);
        mTestEventHandler.nextReturn(true);
        mSearchViewManager.onQueryTextChange("q");
        assertEquals(1, mOnSearchStartingCallCount);
        mSearchViewManager.onQueryTextChange("c");
        assertEquals(2, mOnSearchStartingCallCount);
    }

    private static Set<SearchChipData> getFakeSearchChipDataList() {
        final Set<SearchChipData> chipDataList = new HashSet<>();
        chipDataList.add(new SearchChipData(MetricConsts.TYPE_CHIP_IMAGES,
                0 /* titleRes */, new String[]{"image/*"}));
        chipDataList.add(new SearchChipData(MetricConsts.TYPE_CHIP_LARGE_FILES,
                0 /* titleRes */, new String[]{""}));
        chipDataList.add(new SearchChipData(MetricConsts.TYPE_CHIP_FROM_THIS_WEEK,
                0 /* titleRes */, new String[]{""}));
        return chipDataList;
    }

    @Test
    @EnableFlags({Flags.FLAG_USE_SEARCH_V2_READ_ONLY, FLAG_USE_MATERIAL3})
    public void testMediaAndDownloadsHiddenOnSearchEverywhere() {
        RootInfo mediaRoot = spy(new RootInfo());
        mediaRoot.authority = Providers.AUTHORITY_MEDIA;
        mediaRoot.rootId = "images";
        mediaRoot.flags =  DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        RootInfo downloadsRoot = spy(new RootInfo());
        downloadsRoot.authority = Providers.AUTHORITY_DOWNLOADS;
        downloadsRoot.rootId = "downloads";
        downloadsRoot.flags =  DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;
        RootInfo externalRoot = spy(new RootInfo());
        externalRoot.authority = Providers.AUTHORITY_STORAGE;
        externalRoot.rootId = "primary";
        externalRoot.flags =  DocumentsContract.Root.FLAG_SUPPORTS_SEARCH;

        Collection<RootInfo> roots = List.of(mediaRoot, downloadsRoot, externalRoot);
        DocumentInfo nestedFolder = new DocumentInfo();
        nestedFolder.authority = Providers.AUTHORITY_DOWNLOADS;
        nestedFolder.documentId = "xyz:Nested";
        DocumentStack stack = new DocumentStack(downloadsRoot, nestedFolder);
        // Force search everywhere in mSearchViewManager. This is a private variable, so we
        // use this round-about method of setting it.
        mSearchOptionsController.onLocationSelected(SearchLocationOption.EVERYWHERE.getValue());
        mSearchOptionsController.notifyOptionsChangeListener();

        assertThat(mSearchViewManager.getSearchRoots(roots, stack)).containsExactly(externalRoot);
    }
}
