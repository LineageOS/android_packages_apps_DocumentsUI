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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.Shared.DEBUG;

import android.annotation.IdRes;
import android.annotation.Nullable;
import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.view.ActionMode;
import android.view.HapticFeedbackConstants;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.documentsui.MenuManager;
import com.android.documentsui.R;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.Menus;
import com.android.documentsui.base.Shared;
import com.android.documentsui.dirlist.MultiSelectManager.Selection;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;

/**
 * A controller that listens to selection changes and manages life cycles of action modes.
 */
class ActionModeController implements MultiSelectManager.Callback, ActionMode.Callback {

    private static final String TAG = "ActionModeController";

    private final Context mContext;
    private final MultiSelectManager mSelectionMgr;
    private final MenuManager mMenuManager;
    private final MenuManager.SelectionDetails mSelectionDetails;

    private final Function<ActionMode.Callback, ActionMode> mActionModeFactory;
    private final EventHandler<MenuItem> mMenuItemClicker;
    private final IntConsumer mHapticPerformer;
    private final Consumer<CharSequence> mAccessibilityAnnouncer;
    private final AccessibilityImportanceSetter mAccessibilityImportanceSetter;

    private final Selection mSelected = new Selection();

    private @Nullable ActionMode mActionMode;
    private @Nullable Menu mMenu;

    private ActionModeController(
            Context context,
            MultiSelectManager selectionMgr,
            MenuManager menuManager,
            MenuManager.SelectionDetails selectionDetails,
            Function<ActionMode.Callback, ActionMode> actionModeFactory,
            EventHandler<MenuItem> menuItemClicker,
            IntConsumer hapticPerformer,
            Consumer<CharSequence> accessibilityAnnouncer,
            AccessibilityImportanceSetter accessibilityImportanceSetter) {
        mContext = context;
        mSelectionMgr = selectionMgr;
        mMenuManager = menuManager;
        mSelectionDetails = selectionDetails;

        mActionModeFactory = actionModeFactory;
        mMenuItemClicker = menuItemClicker;
        mHapticPerformer = hapticPerformer;
        mAccessibilityAnnouncer = accessibilityAnnouncer;
        mAccessibilityImportanceSetter = accessibilityImportanceSetter;
    }

    @Override
    public void onSelectionChanged() {
        mSelectionMgr.getSelection(mSelected);
        if (mSelected.size() > 0) {
            if (mActionMode == null) {
                if (DEBUG) Log.d(TAG, "Starting action mode.");
                mActionMode = mActionModeFactory.apply(this);
                mHapticPerformer.accept(HapticFeedbackConstants.LONG_PRESS);
            }
            updateActionMenu();
        } else {
            if (mActionMode != null) {
                if (DEBUG) Log.d(TAG, "Finishing action mode.");
                mActionMode.finish();
            }
        }

        if (mActionMode != null) {
            assert(!mSelected.isEmpty());
            final String title = Shared.getQuantityString(mContext,
                    R.plurals.elements_selected, mSelected.size());
            mActionMode.setTitle(title);
            mAccessibilityAnnouncer.accept(title);
        }
    }

    @Override
    public void onSelectionRestored() {
        mSelectionMgr.getSelection(mSelected);
        if (mSelected.size() > 0) {
            if (mActionMode == null) {
                if (DEBUG) Log.d(TAG, "Starting action mode.");
                mActionMode = mActionModeFactory.apply(this);
            }
            updateActionMenu();
        } else {
            if (mActionMode != null) {
                if (DEBUG) Log.d(TAG, "Finishing action mode.");
                mActionMode.finish();
            }
        }

        if (mActionMode != null) {
            assert(!mSelected.isEmpty());
            final String title = Shared.getQuantityString(mContext,
                    R.plurals.elements_selected, mSelected.size());
            mActionMode.setTitle(title);
            mAccessibilityAnnouncer.accept(title);
        }
    }

    void finishActionMode() {
        if (mActionMode != null) {
            mActionMode.finish();
            mActionMode = null;
        } else {
            Log.w(TAG, "Tried to finish a null action mode.");
        }
    }

    // Called when the user exits the action mode
    @Override
    public void onDestroyActionMode(ActionMode mode) {
        if (DEBUG) Log.d(TAG, "Handling action mode destroyed.");
        mActionMode = null;
        // clear selection
        mSelectionMgr.clearSelection();
        mSelected.clear();

        // Re-enable TalkBack for the toolbars, as they are no longer covered by action mode.
        mAccessibilityImportanceSetter.setAccessibilityImportance(
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, R.id.toolbar, R.id.roots_toolbar);
    }

    @Override
    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
        int size = mSelectionMgr.getSelection().size();
        mode.getMenuInflater().inflate(R.menu.mode_directory, menu);
        mode.setTitle(TextUtils.formatSelectedCount(size));

        if (size > 0) {

            // Hide the toolbars if action mode is enabled, so TalkBack doesn't navigate to
            // these controls when using linear navigation.
            mAccessibilityImportanceSetter.setAccessibilityImportance(
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
                    R.id.toolbar,
                    R.id.roots_toolbar);
            return true;
        }

        return false;
    }

    @Override
    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
        mMenu = menu;
        updateActionMenu();
        return true;
    }

    private void updateActionMenu() {
        assert(mMenu != null);
        mMenuManager.updateActionMenu(mMenu, mSelectionDetails);
        Menus.disableHiddenItems(mMenu);
    }

    @Override
    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
        return mMenuItemClicker.accept(item);
    }

    static ActionModeController create(
            Context context,
            MultiSelectManager selectionMgr,
            MenuManager menuManager,
            MenuManager.SelectionDetails selectionDetails,
            Activity activity,
            View view,
            EventHandler<MenuItem> menuItemClicker) {
        return new ActionModeController(
                context,
                selectionMgr,
                menuManager,
                selectionDetails,
                activity::startActionMode,
                menuItemClicker,
                view::performHapticFeedback,
                view::announceForAccessibility,
                (int accessibilityImportance, @IdRes int[] viewIds) -> {
                    setImportantForAccessibility(activity, accessibilityImportance, viewIds);
                });
    }

    private static void setImportantForAccessibility(
            Activity activity, int accessibilityImportance, @IdRes int[] viewIds) {
        for (final int id : viewIds) {
            final View v = activity.findViewById(id);
            if (v != null) {
                v.setImportantForAccessibility(accessibilityImportance);
            }
        }
    }

    @FunctionalInterface
    private interface AccessibilityImportanceSetter {
        void setAccessibilityImportance(int accessibilityImportance, @IdRes int... viewIds);
    }
}
