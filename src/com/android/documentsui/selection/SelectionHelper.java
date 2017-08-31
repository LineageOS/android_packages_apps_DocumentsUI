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
 * @see DefaultSelectionHelper for details on instantiation.
 */
public interface SelectionHelper {

    /**
     * This value is included in the payload when SelectionHelper implementations
     * notify RecyclerView of changes. Clients can look for this in
     * {@code onBindViewHolder} to know if the bind event is occurring in response
     * to a selection state change.
     */
    public static final String SELECTION_CHANGED_MARKER = "Selection-Changed";

    /**
     * Adds {@code observe} to be notified when changes to selection occur.
     *
     * @param observer
     */
    void addObserver(SelectionObserver observer);

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
     * @return true if the item specified by its id is selected. Shorthand for
     * {@code getSelection().contains(String)}.
     */
    boolean isSelected(String id);

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
     * Attempts to select an item.
     *
     * @return true if the item was selected. False if the item was not selected, or was
     *         was already selected prior to the method being called.
     */
    boolean select(String itemId);

    /**
     * Attempts to deselect an item.
     *
     * @return true if the item was deselected. False if the item was not deselected, or was
     *         was already deselected prior to the method being called.
     */
    boolean deselect(String itemId);

    /**
     * Starts a range selection. If a range selection is already active, this will start a new range
     * selection (which will reset the range anchor).
     *
     * @param pos The anchor position for the selection range.
     */
    void startRange(int pos);

    /**
     * Sets the end point for the active range selection.
     *
     * <p>This function should only be called when a range selection is active
     * (see {@link #isRangeActive()}. Items in the range [anchor, end] will be
     * selected.
     *
     * @param pos The new end position for the selection range.
     * @param type The type of selection the range should utilize.
     *
     * @throws IllegalStateException if a range selection is not active. Range selection
     *         must have been started by a call to {@link #startRange(int)}.
     */
    void extendRange(int pos);

    /**
     * Stops an in-progress range selection. All selection done with
     * {@link #extendProvisionalRange(int)} will be lost if
     * {@link Selection#mergeProvisionalSelection()} is not called beforehand.
     */
    void endRange();

    /**
     * @return Whether or not there is a current range selection active.
     */
    boolean isRangeActive();

    /**
     * Sets the magic location at which a selection range begins (the selection anchor). This value
     * is consulted when determining how to extend, and modify selection ranges. Calling this when a
     * range selection is active will reset the range selection.
     */
    void anchorRange(int position);

    /**
     * @param pos
     */
    // TODO: This is smelly. Maybe this type of logic needs to move into range selection,
    // then selection manager can have a startProvisionalRange and startRange. Or
    // maybe ranges always start life as provisional.
    void extendProvisionalRange(int pos);

    /**
     * @param newSelection
     */
    void setProvisionalSelection(Set<String> newSelection);

    /**
     *
     */
    void clearProvisionalSelection();

    void mergeProvisionalSelection();

    /**
     * Observer interface providing access to information about Selection state changes.
     */
    interface SelectionObserver {

        /**
         * Called when state of an item has been changed.
         */
        void onItemStateChanged(String id, boolean selected);

        /**
         * Called when the underlying data set has change. After this method is called
         * the selection manager will attempt traverse the existing selection,
         * calling {@link #onItemStateChanged(String, boolean)} for each selected item,
         * and deselecting any items that cannot be selected given the updated dataset.
         */
        void onSelectionReset();

        /**
         * Called immediately after completion of any set of changes, excluding
         * those resulting in calls to {@link #onSelectionReset()} and
         * {@link #onSelectionRestored()}.
         */
        void onSelectionChanged();

        /**
         * Called immediately after selection is restored.
         * {@link #onItemStateChanged(String, boolean)} will not be called
         * for individual items in the selection.
         */
        void onSelectionRestored();
    }

    /**
     * Stub observer class that can be used either as a base class for observers
     * that are interested only in a subset of events, or as a test stub.
     */
    static class StubSelectionObserver implements SelectionObserver {

        @Override
        public void onItemStateChanged(String id, boolean selected) {
        }

        @Override
        public void onSelectionReset() {
        }

        @Override
        public void onSelectionChanged() {
        }

        @Override
        public void onSelectionRestored() {
        }
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
         * @return the position of a stable ID, or RecyclerView.NO_POSITION.
         */
        int getPosition(String id);

        /**
         * @return A list of all known stable IDs.
         */
        List<String> getStableIds();
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
