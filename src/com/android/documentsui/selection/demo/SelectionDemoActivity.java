/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.documentsui.selection.demo;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.CallSuper;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.widget.Toast;

import com.android.documentsui.R;
import com.android.documentsui.selection.DefaultSelectionHelper;
import com.android.documentsui.selection.MutableSelection;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionHelper;
import com.android.documentsui.selection.SelectionHelper.SelectionPredicate;
import com.android.documentsui.selection.SelectionHelper.StableIdProvider;
import com.android.documentsui.selection.addons.BandSelectionHelper;
import com.android.documentsui.selection.addons.ContentLock;
import com.android.documentsui.selection.addons.DefaultBandHost;
import com.android.documentsui.selection.addons.DefaultBandPredicate;
import com.android.documentsui.selection.addons.GestureRouter;
import com.android.documentsui.selection.addons.GestureSelectionHelper;
import com.android.documentsui.selection.addons.ItemDetailsLookup;
import com.android.documentsui.selection.addons.ItemDetailsLookup.ItemDetails;
import com.android.documentsui.selection.addons.MotionInputHandler;
import com.android.documentsui.selection.addons.MouseInputHandler;
import com.android.documentsui.selection.addons.TouchEventRouter;
import com.android.documentsui.selection.addons.TouchInputHandler;
import com.android.documentsui.selection.demo.SelectionDemoAdapter.OnBindCallback;

/**
 * ContentPager demo activity.
 */
public class SelectionDemoActivity extends AppCompatActivity {

    private static final String EXTRA_SAVED_SELECTION = "demo-saved-selection";
    private static final String EXTRA_COLUMN_COUNT = "demo-column-count";

    private Toolbar mToolbar;
    private SelectionDemoAdapter mAdapter;
    private SelectionHelper mSelectionHelper;

    private RecyclerView mRecView;
    private GridLayoutManager mLayout;
    private int mColumnCount = 1;  // This will get updated when layout changes.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.selection_demo_layout);
        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        mRecView = (RecyclerView) findViewById(R.id.list);

        mLayout = new GridLayoutManager(this, mColumnCount);
        mRecView.setLayoutManager(mLayout);

        mAdapter = new SelectionDemoAdapter(this);
        mRecView.setAdapter(mAdapter);

        StableIdProvider stableIds = new DemoStableIdProvider(mAdapter);

        // SelectionPredicate permits client control of which items can be selected.
        SelectionPredicate canSelectAnything = new SelectionPredicate() {
            @Override
            public boolean canSetStateForId(String id, boolean nextState) {
                return true;
            }

            @Override
            public boolean canSetStateAtPosition(int position, boolean nextState) {
                return true;
            }
        };

        // TODO: Reload content when it changes. Could use CursorLoader.
        // TODO: Retain selection. Restore when content changes.

        mSelectionHelper = new DefaultSelectionHelper(
                DefaultSelectionHelper.MODE_MULTIPLE,
                mAdapter,
                stableIds,
                canSelectAnything);

        // onBind event callback that allows items to be updated to reflect
        // selection status when bound by recycler view.
        // This allows us to defer initialization of the SelectionHelper dependency
        // which itself depends on the Adapter.
        mAdapter.addOnBindCallback(
                new OnBindCallback() {
                    @Override
                    void onBound(DemoHolder holder, int position) {
                        String id = mAdapter.getStableId(position);
                        holder.setSelected(mSelectionHelper.isSelected(id));
                    }
                });

        // Content lock provides a mechanism to block content reload while selection
        // activities are active. If using a loader to load content, route
        // the call through the content lock using ContentLock#runWhenUnlocked.
        // This is especially useful when listening on content change notification.
        ContentLock contentLock = new ContentLock();

        ItemDetailsLookup detailsLookup = new DemoDetailsLookup(mRecView);

        // Add touch input handling...
        GestureSelectionHelper gestureSel =
                GestureSelectionHelper.create(mSelectionHelper, mRecView, contentLock);

        TouchCallbacks touchCallbacks = new TouchCallbacks(this, mRecView, gestureSel, true);
        TouchInputHandler touchHandler = new TouchInputHandler(
                mSelectionHelper, detailsLookup, canSelectAnything, touchCallbacks);

        // Setup basic input handling, with the touch handler as the default consumer
        // of events. If mouse handling is configured as well, the mouse input
        // related handlers will intercept mouse input events.
        GestureRouter<MotionInputHandler> gestureRouter = new GestureRouter<>(touchHandler);

        GestureDetector gestureDetector = new GestureDetector(this, gestureRouter);
        TouchEventRouter eventRouter =
                new TouchEventRouter(gestureDetector, gestureSel.getTouchListener());

        // Begin mouse/band selection setup...
        // Add mouse driven band selection support. A mouse can be attached to the system
        // at any time, so avoid excluding mouse support based on a static check.
        // MouseInputHandler interprets mouse events as selection events,
        // and/or delegates event handilng the an instance of MouseInputHandler.Callbacks.
        MouseInputHandler mouseHandler = new MouseInputHandler(
                mSelectionHelper, detailsLookup, new MouseCallbacks(this, mRecView));
        gestureRouter.register(MotionEvent.TOOL_TYPE_MOUSE, mouseHandler);

        DefaultBandHost host = new DefaultBandHost(
                mRecView,
                R.drawable.selection_demo_band_overlay,
                new DefaultBandPredicate(detailsLookup));

        BandSelectionHelper bandSel = new BandSelectionHelper(
                host, mAdapter, stableIds, mSelectionHelper, canSelectAnything, contentLock);
        eventRouter.register(MotionEvent.TOOL_TYPE_MOUSE, bandSel.getTouchListener());

        mRecView.addOnItemTouchListener(eventRouter);
        // Done with mouse/band selection setup.

        // In order to preserve selection across various lifecycle events
        // selection is saved in instance state. Give it a chance to restore
        // restore selection now.
        updateFromSavedState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        MutableSelection selection = new MutableSelection();
        mSelectionHelper.copySelection(selection);
        state.putParcelable(EXTRA_SAVED_SELECTION, selection);
        state.putInt(EXTRA_COLUMN_COUNT, mColumnCount);
    }

    private void updateFromSavedState(Bundle state) {
        // In order to preserve selection across various lifecycle events be sure to save
        // the selection in onSaveInstanceState, and to restore it when present in the Bundle
        // pass in via onCreate(Bundle).
        if (state != null) {
            if (state.containsKey(EXTRA_SAVED_SELECTION)) {
                Selection savedSelection = state.getParcelable(EXTRA_SAVED_SELECTION);
                if (!savedSelection.isEmpty()) {
                    mSelectionHelper.restoreSelection(savedSelection);
                    CharSequence text = "Selection restored.";
                    Toast.makeText(this, "Selection restored.", Toast.LENGTH_SHORT).show();
                }
            }
            if (state.containsKey(EXTRA_COLUMN_COUNT)) {
                mColumnCount = state.getInt(EXTRA_COLUMN_COUNT);
                mLayout.setSpanCount(mColumnCount);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean showMenu = super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.selection_demo_actions, menu);
        return showMenu;
    }

    @Override
    @CallSuper
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        menu.findItem(R.id.option_menu_add_column).setEnabled(mColumnCount <= 3);
        menu.findItem(R.id.option_menu_remove_column).setEnabled(mColumnCount > 1);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.option_menu_add_column:
                // TODO: Add columns
                mLayout.setSpanCount(++mColumnCount);
                return true;

            case R.id.option_menu_remove_column:
                mLayout.setSpanCount(--mColumnCount);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    @Override
    public void onBackPressed () {
        if (mSelectionHelper.hasSelection()) {
            mSelectionHelper.clearSelection();
            mSelectionHelper.clearProvisionalSelection();
        } else {
            super.onBackPressed();
        }
    }

    private static void toast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        mSelectionHelper.clearSelection();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mAdapter.loadData();
    }

    // Implementation of MouseInputHandler.Callbacks allows handling
    // of higher level events, like onActivated.
    private static final class MouseCallbacks extends MouseInputHandler.Callbacks {

        private final Context mContext;
        private final RecyclerView mRecView;

        MouseCallbacks(Context context, RecyclerView recView) {
            mContext = context;
            mRecView = recView;
        }

        @Override
        public boolean onItemActivated(ItemDetails item, MotionEvent e) {
            toast(mContext, "Activate item: " + item.getStableId());
            return true;
        }

        @Override
        public boolean onContextClick(MotionEvent e) {
            toast(mContext, "Context click received.");
            return true;
        }

        @Override
        public void onPerformHapticFeedback() {
            mRecView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    private static final class TouchCallbacks extends TouchInputHandler.Callbacks {

        private final Context mContext;
        private final RecyclerView mRecView;
        private final GestureSelectionHelper mGestureSel;
        private final boolean mAllowMultiple;

        private TouchCallbacks(
                Context context,
                RecyclerView recView,
                GestureSelectionHelper gestureSel,
                boolean allowMultiple) {

            mContext = context;
            mRecView = recView;
            mGestureSel = gestureSel;
            mAllowMultiple = allowMultiple;
        }

        @Override
        public boolean onItemActivated(ItemDetails item, MotionEvent e) {
            toast(mContext, "Activate item: " + item.getStableId());
            return true;
        }

        @Override
        public boolean onGestureInitiated(MotionEvent e) {
            toast(mContext, "Gesture initiated.");
            // TODO: This can't be here. Needs to move into TouchInputHandler.
            return mAllowMultiple ? mGestureSel.start() : false;
        }

        @Override
        public void onPerformHapticFeedback() {
            mRecView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    }
}