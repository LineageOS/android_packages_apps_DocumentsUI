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

package com.android.documentsui.selection;

import java.util.List;
import java.util.Set;

/**
 * SelectionManager provides support for managing selection within a RecyclerView instance.
 *
 * @see DefaultSelectionManager for details on usage.
 */
public interface SelectionManager {

    /**
     * Adds {@code callback} such that it will be notified when {@code MultiSelectManager}
     * events occur.
     *
     * @param callback
     */
    void addEventListener(EventListener listener);

    boolean hasSelection();

    /**
     * Returns a Selection object that provides a live view on the current selection.
     *
     * @see #copySelection(Selection) on how to get a snapshot
     *     of the selection that will not reflect future changes
     *     to selection.
     *
     * @return The current selection.
     */
    Selection getSelection();

    /**
     * Updates {@code dest} to reflect the current selection.
     * @param dest
     */
    void copySelection(Selection dest);

    /**
     * Restores the selected state of specified items. Used in cases such as restore the selection
     * after rotation etc. Provisional selection, being provisional 'n all, isn't restored.
     */
    void restoreSelection(Selection other);

    /**
     * Sets the selected state of the specified items. Note that the callback will NOT
     * be consulted to see if an item can be selected.
     *
     * @param ids
     * @param selected
     * @return
     */
    boolean setItemsSelected(Iterable<String> ids, boolean selected);

    /**
     * Clears the selection and notifies (if something changes).
     */
    void clearSelection();

    /**
     * Toggles selection on the item with the given model ID.
     *
     * @param modelId
     */
    void toggleSelection(String modelId);

    /**
     * Starts a range selection. If a range selection is already active, this will start a new range
     * selection (which will reset the range anchor).
     *
     * @param pos The anchor position for the selection range.
     */
    void startRangeSelection(int pos);

    /**
     * Sets the end point for the active range selection.
     *
     * <p>This function should only be called when a range selection is active
     * (see {@link #isRangeSelectionActive()}. Items in the range [anchor, end] will be
     * selected.
     *
     * @param pos The new end position for the selection range.
     * @param type The type of selection the range should utilize.
     *
     * @throws IllegalStateException if a range selection is not active. Range selection
     *         must have been started by a call to {@link #startRangeSelection(int)}.
     */
    void snapRangeSelection(int pos);

    /*
     * Creates a fully formed range selection in one go. This assumes item at
     * {@code startPos} is not selected beforehand.
     */
    void formNewSelectionRange(int startPos, int endPos);

    /**
     * Stops an in-progress range selection. All selection done with
     * {@link #snapRangeSelection(int, int)} with type RANGE_PROVISIONAL will be lost if
     * {@link Selection#mergeProvisionalSelection()} is not called beforehand.
     */
    void endRangeSelection();

    /**
     * @return Whether or not there is a current range selection active.
     */
    boolean isRangeSelectionActive();

    /**
     * Sets the magic location at which a selection range begins (the selection anchor). This value
     * is consulted when determining how to extend, and modify selection ranges. Calling this when a
     * range selection is active will reset the range selection.
     */
    void setSelectionRangeBegin(int position);

    /**
     * @param newSelection
     */
    void setProvisionalSelection(Set<String> newSelection);

    /**
     *
     */
    void clearProvisionalSelection();

    /**
     * @param pos
     */
    // TODO: This is smelly. Maybe this type of logic needs to move into range selection,
    // then selection manager can have a startProvisionalRange and startRange. Or
    // maybe ranges always start life as provisional.
    void snapProvisionalRangeSelection(int pos);

    void mergeProvisionalSelection();

    /**
     * Interface allowing clients access to information about Selection state change.
     */
    interface EventListener {

        /**
         * Called when state of an item has been changed.
         */
        void onItemStateChanged(String id, boolean selected);

        /**
         * Called when selection is reset (cleared).
         */
        void onSelectionReset();

        /**
         * Called immediately after completion of any set of changes.
         */
        void onSelectionChanged();

        /**
         * Called immediately after selection is restored.
         */
        void onSelectionRestored();
    }

    /**
     * Facilitates the use of stable ids.
     */
    interface StableIdProvider {
        /**
         * @return The model ID of the item at the given adapter position.
         */
        String getStableId(int position);

        /**
         * Returns a list of model IDs of items currently in the adapter.
         *
         * @return A list of all known stable IDs.
         */
        List<String> getStableIds();

        /**
         * Triggers item-change notifications by stable ID (as opposed to position).
         */
        void onSelectionStateChanged(String id);
    }

    /**
     * Implement SelectionPredicate to control when items can be selected or unselected.
     */
    interface SelectionPredicate {

        /** @return true if the item at {@code id} can be set to {@code nextState}. */
        boolean canSetStateForId(String id, boolean nextState);

        /** @return true if the item at {@code id} can be set to {@code nextState}. */
        boolean canSetStateAtPosition(int position, boolean nextState);
    }
}
