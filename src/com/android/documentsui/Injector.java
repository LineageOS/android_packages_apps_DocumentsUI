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

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

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
import com.android.documentsui.ui.MessageBuilder;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Provides access to runtime dependencies.
 */
public class Injector<T extends ActionHandler> {

    public final ActivityConfig config;
    public final ScopedPreferences prefs;
    public final MessageBuilder messages;

    public MenuManager menuManager;
    public DialogController dialogs;

    @ContentScoped
    public ActionModeController actionModeController;

    @ContentScoped
    public T actions;

    @ContentScoped
    public FocusManager focusManager;

    @ContentScoped
    public SelectionManager selectionMgr;

    // must be initialized before calling super.onCreate because prefs
    // are used in State initialization.
    public Injector(
            ActivityConfig config,
            ScopedPreferences prefs,
            MessageBuilder messages,
            DialogController dialogs) {

        this.config = config;
        this.prefs = prefs;
        this.messages = messages;
        this.dialogs = dialogs;
    }

    public FocusManager getFocusManager(RecyclerView view, Model model) {
        assert (focusManager != null);
        return focusManager.reset(view, model);
    }

    public SelectionManager getSelectionManager(
            DocumentsAdapter adapter, SelectionPredicate canSetState) {
        return selectionMgr.reset(adapter, canSetState);
    }

    public final ActionModeController getActionModeController(
            SelectionDetails selectionDetails, EventHandler<MenuItem> menuItemClicker, View view) {
        return actionModeController.reset(selectionDetails, menuItemClicker, view);
    }

    public T getActionHandler(
            @Nullable Model model, boolean searchMode) {

        // provide our friend, RootsFragment, early access to this special feature!
        if (model == null) {
            return actions;
        }

        return actions.reset(model, searchMode);
    }

    /**
     * Decorates a field that that is injected.
     */
    @Retention(SOURCE)
    @Target(FIELD)
    public @interface Injected {

    }

    /**
     * Decorates a field that holds an object that must be reset in the current content scope
     * (i.e. DirectoryFragment). Fields decorated with this must have an associated
     * accessor on Injector that, when call, reset the object for the calling context.
     */
    @Retention(SOURCE)
    @Target(FIELD)
    public @interface ContentScoped {

    }
}
