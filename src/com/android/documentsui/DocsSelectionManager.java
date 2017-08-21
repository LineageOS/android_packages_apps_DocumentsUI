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

import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;

import com.android.documentsui.selection.BandController;
import com.android.documentsui.selection.DefaultSelectionManager;
import com.android.documentsui.selection.Selection;
import com.android.documentsui.selection.SelectionManager;

import java.util.Set;

import javax.annotation.Nullable;

/**
 * DocumentsUI SelectManager implementation that creates delegate instances
 * each time reset is called.
 */
public final class DocsSelectionManager implements SelectionManager {

    private final @SelectionMode int mSelectionMode;
    private @Nullable DefaultSelectionManager mDelegate;

    public DocsSelectionManager(@SelectionMode int mode) {
        mSelectionMode = mode;
    }

    public SelectionManager reset(
            RecyclerView.Adapter<?> adapter,
            SelectionManager.Environment idLookup,
            SelectionManager.SelectionPredicate canSetState) {

        if (mDelegate != null) {
            mDelegate.clearSelection();
        }

        mDelegate = new DefaultSelectionManager(mSelectionMode, adapter, idLookup, canSetState);
        return this;
    }

    @Override
    public void bindContoller(BandController controller) {
        mDelegate.bindContoller(controller);
    }

    @Override
    public void addEventListener(EventListener listener) {
        mDelegate.addEventListener(listener);
    }

    @Override
    public boolean hasSelection() {
        return mDelegate.hasSelection();
    }

    @Override
    public Selection getSelection() {
        return mDelegate.getSelection();
    }

    @Override
    public Selection getSelection(Selection dest) {
        return mDelegate.getSelection(dest);
    }

    @Override
    @VisibleForTesting
    public void replaceSelection(Iterable<String> ids) {
        mDelegate.replaceSelection(ids);
    }

    @Override
    public void restoreSelection(Selection other) {
        mDelegate.restoreSelection(other);
    }

    @Override
    public boolean setItemsSelected(Iterable<String> ids, boolean selected) {
        return mDelegate.setItemsSelected(ids, selected);
    }

    @Override
    public void clearSelection() {
        mDelegate.clearSelection();
    }

    @Override
    public void toggleSelection(String modelId) {
        mDelegate.toggleSelection(modelId);
    }

    @Override
    public void startRangeSelection(int pos) {
        mDelegate.startRangeSelection(pos);
    }

    @Override
    public void snapRangeSelection(int pos) {
        mDelegate.snapRangeSelection(pos);
    }

    @Override
    public void snapProvisionalRangeSelection(int pos) {
        mDelegate.snapProvisionalRangeSelection(pos);
    }

    @Override
    public void formNewSelectionRange(int startPos, int endPos) {
        mDelegate.formNewSelectionRange(startPos, endPos);
    }

    @Override
    public void cancelProvisionalSelection() {
        mDelegate.cancelProvisionalSelection();
    }

    @Override
    public void setProvisionalSelection(Set<String> newSelection) {
        mDelegate.setProvisionalSelection(newSelection);
    }

    @Override
    public void endRangeSelection() {
        mDelegate.endRangeSelection();
    }

    @Override
    public boolean isRangeSelectionActive() {
        return mDelegate.isRangeSelectionActive();
    }

    @Override
    public void setSelectionRangeBegin(int position) {
        mDelegate.setSelectionRangeBegin(position);
    }
}
