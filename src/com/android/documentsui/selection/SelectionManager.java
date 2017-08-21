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

import android.support.annotation.IntDef;
import android.support.v7.widget.RecyclerView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.Set;

/**
 *
 */
public interface SelectionManager {

    int MODE_MULTIPLE = 0;
    int MODE_SINGLE = 1;
    @IntDef(flag = true, value = {
            MODE_MULTIPLE,
            MODE_SINGLE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SelectionMode {}

    int RANGE_REGULAR = 0;
    int RANGE_PROVISIONAL = 1;
    @IntDef({
        RANGE_REGULAR,
        RANGE_PROVISIONAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RangeType {}

    /**
     * Reset allows fragment state to be utilized in the (re-)initialization of the
     * SelectionManager instance owned by the containing activity.
     *
     * This is an historically DocumentsUI specific behavior, and a relic
     * of the fact that we employ fragment transactions for directory navigation.
     * As application logic migrated up from the fragment to the activity,
     * less and less state from the fragment has been necessarily pushed out
     * of DirectoryFragment. But the migration of logic up to the activity
     * was never fully concluded leaving this less than desirable arrangement
     * where we depend on post-construction initialization.
     *
     * Ideally all of the information necessary to initialize this object can be initialized
     * at time of construction.
     *
     * @param adapter
     * @param canSetState
     * @return
     */
    SelectionManager reset(
            RecyclerView.Adapter<?> adapter,
            SelectionManager.Environment idLookup,
            SelectionManager.SelectionPredicate canSetState);

    /**
     * Adds {@code callback} such that it will be notified when {@code MultiSelectManager}
     * events occur.
     *
     * @param callback
     */
    void addEventListener(SelectionManager.EventListener callback);

    boolean hasSelection();

    /**
     * Returns a Selection object that provides a live view on the current selection.
     *
     * @see #getSelection(Selection) on how to get a snapshot
     *     of the selection that will not reflect future changes
     *     to selection.
     *
     * @return The current selection.
     */
    Selection getSelection();

    /**
     * Updates {@code dest} to reflect the current selection.
     * @param dest
     *
     * @return The Selection instance passed in, for convenience.
     */
    Selection getSelection(Selection dest);

    void replaceSelection(Iterable<String> ids);

    /**
     * Restores the selected state of specified items. Used in cases such as restore the selection
     * after rotation etc.
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

    void snapRangeSelection(int pos);

    /*
     * Starts and extends range selection in one go. This assumes item at startPos is not selected
     * beforehand.
     */
    void formNewSelectionRange(int startPos, int endPos);

    /**
     * Stops an in-progress range selection. All selection done with
     * {@link #snapRangeSelection(int, int)} with type RANGE_PROVISIONAL will be lost if
     * {@link Selection#applyProvisionalSelection()} is not called beforehand.
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
    void cancelProvisionalSelection();

    /**
     * @param pos
     */
    // TODO: This is smelly. Maybe this type of logic needs to move into range selection,
    // then selection manager can have a startProvisionalRange and startRange. Or
    // maybe ranges always start life as provisional.
    void snapProvisionalRangeSelection(int pos);

    /**
     * Binds band controller to this selection manager, endowing it with special
     * powers (like control of provisional selection).
     * @param controller
     */
    void bindContoller(BandController controller);

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
    interface Environment {

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

    interface SelectionPredicate {
        boolean test(String id, boolean nextState);
    }
}
