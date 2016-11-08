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
package com.android.documentsui;

import android.support.annotation.Nullable;
import android.support.v7.widget.RecyclerView;
import android.view.MenuItem;
import android.view.View;

import com.android.documentsui.MenuManager.SelectionDetails;
import com.android.documentsui.base.EventHandler;
import com.android.documentsui.base.ScopedPreferences;
import com.android.documentsui.dirlist.DocumentsAdapter;
import com.android.documentsui.dirlist.Model;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.selection.SelectionManager.SelectionPredicate;
import com.android.documentsui.ui.DialogController;

/**
 * Provides access to runtime dependencies.
 */
public interface Injector {

    ActivityConfig getActivityConfig();
    ScopedPreferences getScopedPreferences();
    SelectionManager getSelectionManager(DocumentsAdapter adapter, SelectionPredicate canSetState);
    MenuManager getMenuManager();
    DialogController getDialogController();
    ActionHandler getActionHandler(@Nullable Model model, boolean searchMode);
    ActionModeController getActionModeController(
            SelectionDetails selectionDetails, EventHandler<MenuItem> menuItemClicker, View view);
    FocusManager getFocusManager(RecyclerView view, Model model);
}
