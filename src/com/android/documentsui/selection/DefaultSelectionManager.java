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

package com.android.documentsui.selection;

import static com.android.documentsui.selection.Shared.DEBUG;
import static com.android.documentsui.selection.Shared.TAG;

import android.support.annotation.VisibleForTesting;
import android.support.v7.widget.RecyclerView;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * MultiSelectManager provides support traditional multi-item selection support to RecyclerView.
 * Additionally it can be configured to restrict selection to a single element, @see
 * #setSelectMode.
 */
public final class DefaultSelectionManager implements SelectionManager {

    private final Selection mSelection = new Selection();

    private final List<SelectionManager.EventListener> mEventListeners = new ArrayList<>(1);

    private @Nullable RecyclerView.Adapter<?> mAdapter;
    private @Nullable SelectionManager.Environment mIdLookup;
    private @Nullable Range mRanger;
    private boolean mSingleSelect;

    private RecyclerView.AdapterDataObserver mAdapterObserver;
    private SelectionManager.SelectionPredicate mCanSetState;

    public DefaultSelectionManager(@SelectionMode int mode) {
        mSingleSelect = mode == MODE_SINGLE;
    }

    @Override
    public SelectionManager reset(
            RecyclerView.Adapter<?> adapter,
            SelectionManager.Environment idLookup,
            SelectionManager.SelectionPredicate canSetState) {

        mEventListeners.clear();
        if (mAdapter != null && mAdapterObserver != null) {
            mAdapter.unregisterAdapterDataObserver(mAdapterObserver);
        }

        clearSelectionQuietly();

        assert adapter != null;
        assert idLookup != null;
        assert canSetState != null;

        mAdapter = adapter;
        mIdLookup = idLookup;
        mCanSetState = canSetState;

        mAdapterObserver = new RecyclerView.AdapterDataObserver() {

            private List<String> mModelIds;

            @Override
            public void onChanged() {
                // Update the selection to remove any disappeared IDs.
                mSelection.cancelProvisionalSelection();
                mSelection.intersect(mIdLookup.getStableIds());

                notifyDataChanged();
            }

            @Override
            public void onItemRangeChanged(
                    int startPosition, int itemCount, Object payload) {
                // No change in position. Ignoring.
            }

            @Override
            public void onItemRangeInserted(int startPosition, int itemCount) {
                mSelection.cancelProvisionalSelection();
            }

            @Override
            public void onItemRangeRemoved(int startPosition, int itemCount) {
                assert startPosition >= 0;
                assert itemCount > 0;

                mSelection.cancelProvisionalSelection();
                // Remove any disappeared IDs from the selection.
                mSelection.intersect(mModelIds);
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                throw new UnsupportedOperationException();
            }
        };

        mAdapter.registerAdapterDataObserver(mAdapterObserver);
        return this;
    }

    @Override
    public void bindContoller(BandController controller) {
        // Provides BandController with access to private mSelection state.
        controller.bindSelection(mSelection);
    }

    @Override
    public void addEventListener(SelectionManager.EventListener callback) {
        assert callback != null;
        mEventListeners.add(callback);
    }

    @Override
    public boolean hasSelection() {
        return !mSelection.isEmpty();
    }

    @Override
    public Selection getSelection() {
        return mSelection;
    }

    @Override
    public Selection getSelection(Selection dest) {
        dest.copyFrom(mSelection);
        return dest;
    }

    @Override
    @VisibleForTesting
    public void replaceSelection(Iterable<String> ids) {
        clearSelection();
        setItemsSelected(ids, true);
    }

    @Override
    public void restoreSelection(Selection other) {
        setItemsSelectedQuietly(other.mSelection, true);
        // NOTE: We intentionally don't restore provisional selection. It's provisional.
        notifySelectionRestored();
    }

    @Override
    public boolean setItemsSelected(Iterable<String> ids, boolean selected) {
        final boolean changed = setItemsSelectedQuietly(ids, selected);
        notifySelectionChanged();
        return changed;
    }

    private boolean setItemsSelectedQuietly(Iterable<String> ids, boolean selected) {
        boolean changed = false;
        for (String id: ids) {
            final boolean itemChanged =
                    selected
                    ? canSetState(id, true) && mSelection.add(id)
                    : canSetState(id, false) && mSelection.remove(id);
            if (itemChanged) {
                notifyItemStateChanged(id, selected);
            }
            changed |= itemChanged;
        }
        return changed;
    }

    @Override
    public void clearSelection() {
        if (!hasSelection()) {
            return;
        }

        clearSelectionQuietly();
        notifySelectionChanged();
    }

    /**
     * Clears the selection, without notifying selection listeners. UI elements still need to be
     * notified about state changes so that they can update their appearance.
     */
    private void clearSelectionQuietly() {
        mRanger = null;

        if (!hasSelection()) {
            return;
        }

        Selection oldSelection = getSelection(new Selection());
        mSelection.clear();

        for (String id: oldSelection.mSelection) {
            notifyItemStateChanged(id, false);
        }
        for (String id: oldSelection.mProvisionalSelection) {
            notifyItemStateChanged(id, false);
        }
    }

    @Override
    public void toggleSelection(String modelId) {
        assert modelId != null;

        final boolean changed = mSelection.contains(modelId)
                ? attemptDeselect(modelId)
                : attemptSelect(modelId);

        if (changed) {
            notifySelectionChanged();
        }
    }

    @Override
    public void startRangeSelection(int pos) {
        attemptSelect(mIdLookup.getStableId(pos));
        setSelectionRangeBegin(pos);
    }

    @Override
    public void snapRangeSelection(int pos) {
        snapRangeSelection(pos, RANGE_REGULAR);
    }

    @Override
    public void snapProvisionalRangeSelection(int pos) {
        snapRangeSelection(pos, RANGE_PROVISIONAL);
    }

    /*
     * Starts and extends range selection in one go. This assumes item at startPos is not selected
     * beforehand.
     */
    @Override
    public void formNewSelectionRange(int startPos, int endPos) {
        assert !mSelection.contains(mIdLookup.getStableId(startPos));
        startRangeSelection(startPos);
        snapRangeSelection(endPos);
    }

    /**
     * Sets the end point for the current range selection, started by a call to
     * {@link #startRangeSelection(int)}. This function should only be called when a range selection
     * is active (see {@link #isRangeSelectionActive()}. Items in the range [anchor, end] will be
     * selected or in provisional select, depending on the type supplied. Note that if the type is
     * provisional select, one should do {@link Selection#applyProvisionalSelection()} at some point
     * before calling on {@link #endRangeSelection()}.
     *
     * @param pos The new end position for the selection range.
     * @param type The type of selection the range should utilize.
     */
    private void snapRangeSelection(int pos, @RangeType int type) {
        if (!isRangeSelectionActive()) {
            throw new IllegalStateException("Range start point not set.");
        }

        mRanger.snapSelection(pos, type);

        // We're being lazy here notifying even when something might not have changed.
        // To make this more correct, we'd need to update the Ranger class to return
        // information about what has changed.
        notifySelectionChanged();
    }

    @Override
    public void cancelProvisionalSelection() {
        for (String id : mSelection.mProvisionalSelection) {
            notifyItemStateChanged(id, false);
        }
        mSelection.cancelProvisionalSelection();
    }

    @Override
    public void setProvisionalSelection(Set<String> newSelection) {
        Map<String, Boolean> delta = mSelection.setProvisionalSelection(newSelection);
        for (Map.Entry<String, Boolean> entry: delta.entrySet()) {
            notifyItemStateChanged(entry.getKey(), entry.getValue());
        }
        notifySelectionChanged();
    }

    @Override
    public void endRangeSelection() {
        mRanger = null;
        // Clean up in case there was any leftover provisional selection
        cancelProvisionalSelection();
    }

    @Override
    public boolean isRangeSelectionActive() {
        return mRanger != null;
    }

    @Override
    public void setSelectionRangeBegin(int position) {
        if (position == RecyclerView.NO_POSITION) {
            return;
        }

        if (mSelection.contains(mIdLookup.getStableId(position))) {
            mRanger = new Range(this::updateForRange, position);
        }
    }

    /**
     * @param modelId
     * @return True if the update was applied.
     */
    private boolean selectAndNotify(String modelId) {
        boolean changed = mSelection.add(modelId);
        if (changed) {
            notifyItemStateChanged(modelId, true);
        }
        return changed;
    }

    /**
     * @param id
     * @return True if the update was applied.
     */
    private boolean attemptDeselect(String id) {
        assert id != null;
        if (canSetState(id, false)) {
            mSelection.remove(id);
            notifyItemStateChanged(id, false);

            // if there's nothing in the selection and there is an active ranger it results
            // in unexpected behavior when the user tries to start range selection: the item
            // which the ranger 'thinks' is the already selected anchor becomes unselectable
            if (mSelection.isEmpty() && isRangeSelectionActive()) {
                endRangeSelection();
            }
            if (DEBUG) Log.d(TAG, "Selection after deselect: " + mSelection);
            return true;
        } else {
            if (DEBUG) Log.d(TAG, "Select cancelled by listener.");
            return false;
        }
    }

    /**
     * @param id
     * @return True if the update was applied.
     */
    private boolean attemptSelect(String id) {
        assert id != null;
        boolean canSelect = canSetState(id, true);
        if (!canSelect) {
            return false;
        }
        if (mSingleSelect && hasSelection()) {
            clearSelectionQuietly();
        }

        selectAndNotify(id);
        return true;
    }

    boolean canSetState(String id, boolean nextState) {
        return mCanSetState.test(id, nextState);
    }

    private void notifyDataChanged() {

        notifySelectionReset();

        final int lastListener = mEventListeners.size() - 1;
        for (String id : mSelection) {
            // TODO: Why do we deselect in notify changed.
            if (!canSetState(id, true)) {
                attemptDeselect(id);
            } else {
                for (int i = lastListener; i >= 0; i--) {
                    mEventListeners.get(i).onItemStateChanged(id, true);
                }
            }
        }
    }

    /**
     * Notifies registered listeners when the selection status of a single item
     * (identified by {@code position}) changes.
     */
    void notifyItemStateChanged(String id, boolean selected) {
        assert id != null;
        int lastListener = mEventListeners.size() - 1;
        for (int i = lastListener; i >= 0; i--) {
            mEventListeners.get(i).onItemStateChanged(id, selected);
        }
        mIdLookup.onSelectionStateChanged(id);
    }

    /**
     * Notifies registered listeners when the selection has changed. This
     * notification should be sent only once a full series of changes
     * is complete, e.g. clearingSelection, or updating the single
     * selection from one item to another.
     */
    void notifySelectionChanged() {
        int lastListener = mEventListeners.size() - 1;
        for (int i = lastListener; i > -1; i--) {
            mEventListeners.get(i).onSelectionChanged();
        }
    }

    private void notifySelectionRestored() {
        int lastListener = mEventListeners.size() - 1;
        for (int i = lastListener; i > -1; i--) {
            mEventListeners.get(i).onSelectionRestored();
        }
    }

    private void notifySelectionReset() {
        int lastListener = mEventListeners.size() - 1;
        for (int i = lastListener; i > -1; i--) {
            mEventListeners.get(i).onSelectionReset();
        }
    }

    void updateForRange(int begin, int end, boolean selected, @RangeType int type) {
        switch (type) {
            case RANGE_REGULAR:
                updateForRegularRange(begin, end, selected);
                break;
            case RANGE_PROVISIONAL:
                updateForProvisionalRange(begin, end, selected);
                break;
            default:
                throw new IllegalArgumentException("Invalid range type: " + type);
        }
    }

    private void updateForRegularRange(int begin, int end, boolean selected) {
        assert end >= begin;
        for (int i = begin; i <= end; i++) {
            String id = mIdLookup.getStableId(i);
            if (id == null) {
                continue;
            }

            if (selected) {
                boolean canSelect = canSetState(id, true);
                if (canSelect) {
                    if (mSingleSelect && hasSelection()) {
                        clearSelectionQuietly();
                    }
                    selectAndNotify(id);
                }
            } else {
                attemptDeselect(id);
            }
        }
    }

    private void updateForProvisionalRange(int begin, int end, boolean selected) {
        assert end >= begin;
        for (int i = begin; i <= end; i++) {
            String id = mIdLookup.getStableId(i);
            if (id == null) {
                continue;
            }

            boolean changedState = false;
            if (selected) {
                boolean canSelect = canSetState(id, true);
                if (canSelect && !mSelection.mSelection.contains(id)) {
                    mSelection.mProvisionalSelection.add(id);
                    changedState = true;
                }
            } else {
                mSelection.mProvisionalSelection.remove(id);
                changedState = true;
            }

            // Only notify item callbacks when something's state is actually changed in provisional
            // selection.
            if (changedState) {
                notifyItemStateChanged(id, selected);
            }
        }
        notifySelectionChanged();
    }
}
