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

import android.support.annotation.IntDef;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.Adapter;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * SelectManager providing support traditional multi-item selection support to RecyclerView.
 * Additionally it can be configured to restrict selection to a single element, @see
 * #setSelectMode.
 */
public final class DefaultSelectionManager implements SelectionManager {

    public static final int MODE_MULTIPLE = 0;
    public static final int MODE_SINGLE = 1;
    @IntDef(flag = true, value = {
            MODE_MULTIPLE,
            MODE_SINGLE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SelectionMode {}

    private static final int RANGE_REGULAR = 0;
    /**
     * "Provisional" selection represents a overlay on the primary selection. A provisional
     * selection maybe be eventually added to the primary selection, or it may be abandoned.
     *
     *  <p>E.g. BandController creates a provisional selection while a user is actively
     *  selecting items with the band. Provisionally selected items are considered to be
     *  selected in {@link Selection#contains(String)} and related methods. A provisional
     *  may be abandoned or applied by selection components (like {@link BandController}).
     *
     *  <p>A provisional selection may intersect the primary selection, however clearing
     *  the provisional selection will not affect the primary selection where the two may
     *  intersect.
     */
    private static final int RANGE_PROVISIONAL = 1;
    @IntDef({
        RANGE_REGULAR,
        RANGE_PROVISIONAL
    })
    @Retention(RetentionPolicy.SOURCE)
    @interface RangeType {}

    private final Selection mSelection = new Selection();
    private final List<EventListener> mEventListeners = new ArrayList<>(1);
    private final RecyclerView.Adapter<?> mAdapter;
    private final StableIdProvider mStableIds;
    private final SelectionPredicate mCanSetState;
    private final RecyclerView.AdapterDataObserver mAdapterObserver;

    private final boolean mSingleSelect;

    private @Nullable Range mRanger;

    /**
     * Creates a new instance.
     *
     * @param mode single or multiple selection mode. In single selection mode
     *     users can only select a single item.
     * @param adapter {@link Adapter} for the RecyclerView this instance is coupled with.
     * @param stableIds client supplied class providing access to stable ids.
     * @param canSetState A predicate allowing the client to disallow selection
     *     of individual elements.
     */
    public DefaultSelectionManager(
            @SelectionMode int mode,
            RecyclerView.Adapter<?> adapter,
            StableIdProvider stableIds,
            SelectionPredicate canSetState) {

        assert adapter != null;
        assert stableIds != null;
        assert canSetState != null;

        mAdapter = adapter;
        mStableIds = stableIds;
        mCanSetState = canSetState;

        mSingleSelect = mode == MODE_SINGLE;

        mAdapterObserver = new RecyclerView.AdapterDataObserver() {

            private List<String> mModelIds;

            @Override
            public void onChanged() {
                // Update the selection to remove any disappeared IDs.
                mSelection.cancelProvisionalSelection();
                mSelection.intersect(mStableIds.getStableIds());

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
    }

    @Override
    public void addEventListener(EventListener callback) {
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
    public void copySelection(Selection dest) {
        dest.copyFrom(mSelection);
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

        Selection oldSelection = new Selection();
        copySelection(oldSelection);
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
        attemptSelect(mStableIds.getStableId(pos));
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
        assert !mSelection.contains(mStableIds.getStableId(startPos));
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

        if (mSelection.contains(mStableIds.getStableId(position))) {
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

    private boolean canSetState(String id, boolean nextState) {
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
    private void notifyItemStateChanged(String id, boolean selected) {
        assert id != null;
        int lastListenerIndex = mEventListeners.size() - 1;
        for (int i = lastListenerIndex; i >= 0; i--) {
            mEventListeners.get(i).onItemStateChanged(id, selected);
        }
        mStableIds.onSelectionStateChanged(id);
    }

    /**
     * Notifies registered listeners when the selection has changed. This
     * notification should be sent only once a full series of changes
     * is complete, e.g. clearingSelection, or updating the single
     * selection from one item to another.
     */
    private void notifySelectionChanged() {
        int lastListenerIndex = mEventListeners.size() - 1;
        for (int i = lastListenerIndex; i >= 0; i--) {
            mEventListeners.get(i).onSelectionChanged();
        }
    }

    private void notifySelectionRestored() {
        int lastListenerIndex = mEventListeners.size() - 1;
        for (int i = lastListenerIndex; i >= 0; i--) {
            mEventListeners.get(i).onSelectionRestored();
        }
    }

    private void notifySelectionReset() {
        int lastListenerIndex = mEventListeners.size() - 1;
        for (int i = lastListenerIndex; i >= 0; i--) {
            mEventListeners.get(i).onSelectionReset();
        }
    }

    private void updateForRange(int begin, int end, boolean selected, @RangeType int type) {
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
            String id = mStableIds.getStableId(i);
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
            String id = mStableIds.getStableId(i);
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
