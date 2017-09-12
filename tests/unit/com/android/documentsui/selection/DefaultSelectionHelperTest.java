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
import static org.junit.Assert.assertFalse;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.SparseBooleanArray;

import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.dirlist.TestDocumentsAdapter;
import com.android.documentsui.selection.SelectionHelper.SelectionPredicate;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DefaultSelectionHelperTest {

    private static final List<String> ITEMS = TestData.create(100);

    private final Set<String> mIgnored = new HashSet<>();
    private TestDocumentsAdapter mAdapter;
    private DefaultSelectionHelper mManager;
    private TestSelectionObserver mListener;
    private SelectionProbe mSelection;

    @Before
    public void setUp() throws Exception {
        mListener = new TestSelectionObserver();
        mAdapter = new TestDocumentsAdapter(ITEMS);
        SelectionPredicate canSelect = new SelectionPredicate() {

            @Override
            public boolean canSetStateForId(String id, boolean nextState) {
                return !nextState || !mIgnored.contains(id);
            }

            @Override
            public boolean canSetStateAtPosition(int position, boolean nextState) {
                throw new UnsupportedOperationException("Not implemented.");
            }
        };
        mManager = new DefaultSelectionHelper(
                DefaultSelectionHelper.MODE_MULTIPLE, mAdapter, mAdapter, canSelect);
        mManager.addObserver(mListener);

        mSelection = new SelectionProbe(mManager, mListener);

        mIgnored.clear();
    }

    @Test
    public void testSelect() {
        mManager.select(ITEMS.get(7));

        mSelection.assertSelection(7);
    }

    @Test
    public void testDeselect() {
        mManager.select(ITEMS.get(7));
        mManager.deselect(ITEMS.get(7));

        mSelection.assertNoSelection();
    }

    @Test
    public void testSelection_DoNothingOnUnselectableItem() {
        mIgnored.add(ITEMS.get(7));
        boolean selected = mManager.select(ITEMS.get(7));

        assertFalse(selected);
        mSelection.assertNoSelection();
    }

    @Test
    public void testSelect_NotifiesListenersOfChange() {
        mManager.select(ITEMS.get(7));

        mListener.assertSelectionChanged();
    }


    @Test
    public void testSelect_NotifiesAdapterOfSelect() {
        mManager.select(ITEMS.get(7));

        mAdapter.assertSelectionChanged(7);
    }

    @Test
    public void testSelect_NotifiesAdapterOfDeselect() {
        mManager.select(ITEMS.get(7));
        mAdapter.resetSelectionChanged();
        mManager.deselect(ITEMS.get(7));
        mAdapter.assertSelectionChanged(7);
    }

    @Test
    public void testDeselect_NotifiesSelectionChanged() {
        mManager.select(ITEMS.get(7));
        mManager.deselect(ITEMS.get(7));

        mListener.assertSelectionChanged();
    }

    @Test
    public void testSelection_PersistsOnUpdate() {
        mManager.select(ITEMS.get(7));
        mAdapter.updateTestModelIds(ITEMS);

        mSelection.assertSelection(7);
    }

    @Test
    public void testSelection_IntersectsWithNewDataSet() {
        mManager.select(ITEMS.get(99));
        mManager.select(ITEMS.get(7));

        mAdapter.updateTestModelIds(TestData.create(50));

        mSelection.assertSelection(7);
    }

    @Test
    public void testSetItemsSelected() {
        mManager.setItemsSelected(getStringIds(6, 7, 8), true);

        mSelection.assertRangeSelected(6, 8);
    }

    @Test
    public void testSetItemsSelected_SkipUnselectableItem() {
        mIgnored.add(ITEMS.get(7));

        mManager.setItemsSelected(getStringIds(6, 7, 8), true);

        mSelection.assertSelected(6);
        mSelection.assertNotSelected(7);
        mSelection.assertSelected(8);
    }

    @Test
    public void testRangeSelection() {
        mManager.startRange(15);
        mManager.extendRange(19);
        mSelection.assertRangeSelection(15, 19);
    }

    @Test
    public void testRangeSelection_SkipUnselectableItem() {
        mIgnored.add(ITEMS.get(17));

        mManager.startRange(15);
        mManager.extendRange(19);

        mSelection.assertRangeSelected(15, 16);
        mSelection.assertNotSelected(17);
        mSelection.assertRangeSelected(18, 19);
    }

    @Test
    public void testRangeSelection_snapExpand() {
        mManager.startRange(15);
        mManager.extendRange(19);
        mManager.extendRange(27);
        mSelection.assertRangeSelection(15, 27);
    }

    @Test
    public void testRangeSelection_snapContract() {
        mManager.startRange(15);
        mManager.extendRange(27);
        mManager.extendRange(19);
        mSelection.assertRangeSelection(15, 19);
    }

    @Test
    public void testRangeSelection_snapInvert() {
        mManager.startRange(15);
        mManager.extendRange(27);
        mManager.extendRange(3);
        mSelection.assertRangeSelection(3, 15);
    }

    @Test
    public void testRangeSelection_multiple() {
        mManager.startRange(15);
        mManager.extendRange(27);
        mManager.endRange();
        mManager.startRange(42);
        mManager.extendRange(57);
        mSelection.assertSelectionSize(29);
        mSelection.assertRangeSelected(15, 27);
        mSelection.assertRangeSelected(42, 57);
    }

    @Test
    public void testProvisionalRangeSelection() {
        mManager.startRange(13);
        mManager.extendProvisionalRange(15);
        mSelection.assertRangeSelection(13, 15);
        mManager.getSelection().mergeProvisionalSelection();
        mManager.endRange();
        mSelection.assertSelectionSize(3);
    }

    @Test
    public void testProvisionalRangeSelection_endEarly() {
        mManager.startRange(13);
        mManager.extendProvisionalRange(15);
        mSelection.assertRangeSelection(13, 15);

        mManager.endRange();
        // If we end range selection prematurely for provision selection, nothing should be selected
        // except the first item
        mSelection.assertSelectionSize(1);
    }

    @Test
    public void testProvisionalRangeSelection_snapExpand() {
        mManager.startRange(13);
        mManager.extendProvisionalRange(15);
        mSelection.assertRangeSelection(13, 15);
        mManager.getSelection().mergeProvisionalSelection();
        mManager.extendRange(18);
        mSelection.assertRangeSelection(13, 18);
    }

    @Test
    public void testCombinationRangeSelection_IntersectsOldSelection() {
        mManager.startRange(13);
        mManager.extendRange(15);
        mSelection.assertRangeSelection(13, 15);

        mManager.startRange(11);
        mManager.extendProvisionalRange(18);
        mSelection.assertRangeSelected(11, 18);
        mManager.endRange();
        mSelection.assertRangeSelected(13, 15);
        mSelection.assertRangeSelected(11, 11);
        mSelection.assertSelectionSize(4);
    }

    @Test
    public void testProvisionalSelection() {
        Selection s = mManager.getSelection();
        mSelection.assertNoSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mListener.onItemStateChanged(ITEMS.get(1), true);
        mListener.onItemStateChanged(ITEMS.get(2), true);

        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(1, 2);
    }

    @Test
    public void testProvisionalSelection_Replace() {
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mListener.onItemStateChanged(ITEMS.get(1), true);
        mListener.onItemStateChanged(ITEMS.get(2), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));

        mListener.onItemStateChanged(ITEMS.get(1), false);
        mListener.onItemStateChanged(ITEMS.get(2), false);
        provisional.clear();

        mListener.onItemStateChanged(ITEMS.get(3), true);
        mListener.onItemStateChanged(ITEMS.get(4), true);
        provisional.append(3, true);
        provisional.append(4, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(3, 4);
    }

    @Test
    public void testProvisionalSelection_IntersectsExistingProvisionalSelection() {
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mListener.onItemStateChanged(ITEMS.get(1), true);
        mListener.onItemStateChanged(ITEMS.get(2), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));

        mListener.onItemStateChanged(ITEMS.get(1), false);
        mListener.onItemStateChanged(ITEMS.get(2), false);
        provisional.clear();

        mListener.onItemStateChanged(ITEMS.get(1), true);
        provisional.append(1, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(1);
    }

    @Test
    public void testProvisionalSelection_Apply() {
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mListener.onItemStateChanged(ITEMS.get(1), true);
        mListener.onItemStateChanged(ITEMS.get(2), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(1, true);
        provisional.append(2, true);
        s.setProvisionalSelection(getItemIds(provisional));
        s.mergeProvisionalSelection();

        mSelection.assertSelection(1, 2);
    }

    @Test
    public void testProvisionalSelection_Cancel() {
        mManager.select(ITEMS.get(1));
        mManager.select(ITEMS.get(2));
        Selection s = mManager.getSelection();

        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(3, true);
        provisional.append(4, true);
        s.setProvisionalSelection(getItemIds(provisional));
        s.clearProvisionalSelection();

        // Original selection should remain.
        mSelection.assertSelection(1, 2);
    }

    @Test
    public void testProvisionalSelection_IntersectsAppliedSelection() {
        mManager.select(ITEMS.get(1));
        mManager.select(ITEMS.get(2));
        Selection s = mManager.getSelection();

        // Mimicking band selection case -- BandController notifies item callback by itself.
        mListener.onItemStateChanged(ITEMS.get(3), true);
        SparseBooleanArray provisional = new SparseBooleanArray();
        provisional.append(2, true);
        provisional.append(3, true);
        s.setProvisionalSelection(getItemIds(provisional));
        mSelection.assertSelection(1, 2, 3);
    }

    private static Set<String> getItemIds(SparseBooleanArray selection) {
        Set<String> ids = new HashSet<>();

        int count = selection.size();
        for (int i = 0; i < count; ++i) {
            ids.add(ITEMS.get(selection.keyAt(i)));
        }

        return ids;
    }

    private static Iterable<String> getStringIds(int... ids) {
        List<String> stringIds = new ArrayList<>(ids.length);
        for (int id : ids) {
            stringIds.add(ITEMS.get(id));
        }
        return stringIds;
    }
}
