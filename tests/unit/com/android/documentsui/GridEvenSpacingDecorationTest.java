/*
 * Copyright (C) 2025 The Android Open Source Project
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

import static com.android.documentsui.flags.Flags.FLAG_USE_MATERIAL3;

import static junit.framework.Assert.assertEquals;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.graphics.Rect;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.documentsui.dirlist.GridEvenSpacingDecoration;
import com.android.documentsui.rules.CheckAndForceMaterial3Flag;
import com.android.documentsui.testing.TestRecyclerView;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@RequiresFlagsEnabled(FLAG_USE_MATERIAL3)
public class GridEvenSpacingDecorationTest {
    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    private static final int ITEM_WIDTH = 100;
    private static final int ITEM_HEIGHT = 100;
    private static final int ITEM_COUNT = 3;
    private TestRecyclerView mTestRecView;
    @Mock
    private GridLayoutManager mMockGridLayoutManager;
    private GridEvenSpacingDecoration mGridEvenSpacingDecoration;
    private ViewGroup.MarginLayoutParams mLayoutParamsForItem;
    private ArrayList<View> mMockItems;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        mTestRecView = TestRecyclerView.create(new ArrayList<>());
        mTestRecView.setLayoutManager(mMockGridLayoutManager);

        // All items have the same width.
        mLayoutParamsForItem = new ViewGroup.MarginLayoutParams(ITEM_WIDTH, ITEM_HEIGHT);
        mMockItems = new ArrayList<>();
        for (int i = 0; i < ITEM_COUNT; i++) {
            View mockItem = mock(View.class);
            when(mockItem.getLayoutParams()).thenReturn(mLayoutParamsForItem);
            mMockItems.add(mockItem);
        }

        mGridEvenSpacingDecoration = new GridEvenSpacingDecoration();
    }

    @Test
    public void testPerfectFit_noRecViewPadding_noItemMargins() {
        // ITEM_COUNT items per row.
        when(mMockGridLayoutManager.getSpanCount()).thenReturn(ITEM_COUNT);

        // Parent width fits the ITEM_COUNT items perfectly within one row.
        mTestRecView.setMeasuredWithAndHeight(ITEM_WIDTH * ITEM_COUNT, ITEM_HEIGHT);

        for (View mockItem : mMockItems) {
            Rect rect = new Rect();
            mGridEvenSpacingDecoration.getItemOffsets(rect, mockItem, mTestRecView,
                    new RecyclerView.State());
            // Offsets should be 0.
            Rect expectedRect = new Rect();
            assertEquals(expectedRect, rect);
        }
    }

    @Test
    public void testPerfectFit_recViewPadding_itemMargins() {
        // ITEM_COUNT items per row.
        when(mMockGridLayoutManager.getSpanCount()).thenReturn(ITEM_COUNT);

        int leftMargin = 1;
        int rightMargin = 2;
        int itemWidthWithMargins = leftMargin + ITEM_WIDTH + rightMargin;
        mLayoutParamsForItem.setMargins(leftMargin, 0, rightMargin, 0);

        // Parent width fits the ITEM_COUNT items perfectly within one row (between the padding).
        int leftPad = 10;
        int rightPad = 5;
        mTestRecView.setPadding(leftPad, 0, rightPad, 0);
        mTestRecView.setMeasuredWithAndHeight(
                leftPad + itemWidthWithMargins * ITEM_COUNT + rightPad, ITEM_HEIGHT);

        for (View mockItem : mMockItems) {
            Rect rect = new Rect();
            mGridEvenSpacingDecoration.getItemOffsets(rect, mockItem, mTestRecView,
                    new RecyclerView.State());
            // Offsets should be 0.
            Rect expectedRect = new Rect();
            assertEquals(expectedRect, rect);
        }
    }

    @Test
    public void testExtraSpace_SpaceLessThanOneItem() {
        // ITEM_COUNT items per row.
        when(mMockGridLayoutManager.getSpanCount()).thenReturn(ITEM_COUNT);

        int leftMargin = 1;
        int rightMargin = 2;
        int itemWidthWithMargins = leftMargin + ITEM_WIDTH + rightMargin;
        mLayoutParamsForItem.setMargins(leftMargin, 0, rightMargin, 0);

        // Parent width fits almost ITEM_COUNT+1 items (12 pixels too narrow).
        int leftPad = 10;
        int rightPad = 5;
        mTestRecView.setPadding(leftPad, 0, rightPad, 0);
        int spaceForItems = itemWidthWithMargins * (ITEM_COUNT + 1) - 12;
        mTestRecView.setMeasuredWithAndHeight(leftPad + spaceForItems + rightPad, ITEM_HEIGHT);

        // The space is divided into "span count" number of equal size buckets. The items will be
        // centred within their buckets.
        int spaceForItem = spaceForItems / ITEM_COUNT;
        int offset = (spaceForItem - itemWidthWithMargins) / 2;

        for (View mockItem : mMockItems) {
            Rect rect = new Rect();
            mGridEvenSpacingDecoration.getItemOffsets(rect, mockItem, mTestRecView,
                    new RecyclerView.State());
            Rect expectedRect = new Rect(offset, 0, offset, 0);
            assertEquals(expectedRect, rect);
        }
    }

    @Test
    public void testExtraSpace_SpaceMoreThanOneItem() {
        // ITEM_COUNT+1 items per row.
        int spanCount = ITEM_COUNT + 1;
        when(mMockGridLayoutManager.getSpanCount()).thenReturn(spanCount);

        int leftMargin = 1;
        int rightMargin = 2;
        int itemWidthWithMargins = leftMargin + ITEM_WIDTH + rightMargin;
        mLayoutParamsForItem.setMargins(leftMargin, 0, rightMargin, 0);

        // Parent width fits almost ITEM_COUNT+1 items (12 pixels extra).
        int leftPad = 10;
        int rightPad = 5;
        mTestRecView.setPadding(leftPad, 0, rightPad, 0);
        int spaceForItems = itemWidthWithMargins * (ITEM_COUNT + 1) + 12;
        mTestRecView.setMeasuredWithAndHeight(leftPad + spaceForItems + rightPad, ITEM_HEIGHT);

        // The space is divided into "span count" number of equal size buckets. The items will be
        // centred within their buckets.
        int spaceForItem = spaceForItems / spanCount;
        int offset = (spaceForItem - itemWidthWithMargins) / 2;

        for (View mockItem : mMockItems) {
            Rect rect = new Rect();
            mGridEvenSpacingDecoration.getItemOffsets(rect, mockItem, mTestRecView,
                    new RecyclerView.State());
            Rect expectedRect = new Rect(offset, 0, offset, 0);
            assertEquals(expectedRect, rect);
        }
    }

    @Test
    public void perfectFit_testMultipleRows() {
        // ITEM_COUNT-1 items per row.
        int spanCount = ITEM_COUNT - 1;
        when(mMockGridLayoutManager.getSpanCount()).thenReturn(spanCount);

        int leftMargin = 1;
        int rightMargin = 2;
        int itemWidthWithMargins = leftMargin + ITEM_WIDTH + rightMargin;
        mLayoutParamsForItem.setMargins(leftMargin, 0, rightMargin, 0);

        // Parent width fits ITEM_COUNT-1 items perfectly.
        int leftPad = 10;
        int rightPad = 5;
        mTestRecView.setPadding(leftPad, 0, rightPad, 0);
        int spaceForItems = itemWidthWithMargins * spanCount;
        mTestRecView.setMeasuredWithAndHeight(leftPad + spaceForItems + rightPad, ITEM_HEIGHT * 2);

        for (View mockItem : mMockItems) {
            Rect rect = new Rect();
            mGridEvenSpacingDecoration.getItemOffsets(rect, mockItem, mTestRecView,
                    new RecyclerView.State());
            Rect expectedRect = new Rect();
            // Offsets should be 0 even for the item on the second row since the bucket size is
            // exactly itemWidthWithMargins.
            assertEquals(expectedRect, rect);
        }
    }

    @Test
    public void testExtraSpace_testMultipleRows() {
        // ITEM_COUNT-1 items per row.
        int spanCount = ITEM_COUNT - 1;
        when(mMockGridLayoutManager.getSpanCount()).thenReturn(spanCount);

        int leftMargin = 1;
        int rightMargin = 2;
        int itemWidthWithMargins = leftMargin + ITEM_WIDTH + rightMargin;
        mLayoutParamsForItem.setMargins(leftMargin, 0, rightMargin, 0);

        // Parent width fits almost ITEM_COUNT+1 items (12 pixels extra).
        int leftPad = 10;
        int rightPad = 5;
        mTestRecView.setPadding(leftPad, 0, rightPad, 0);
        int spaceForItems = itemWidthWithMargins * (ITEM_COUNT + 1) + 12;
        mTestRecView.setMeasuredWithAndHeight(leftPad + spaceForItems + rightPad, ITEM_HEIGHT * 2);


        // The space is divided into "span count" number of equal size buckets. The items will be
        // centred within their buckets.
        int spaceForItem = spaceForItems / spanCount;
        int offset = (spaceForItem - itemWidthWithMargins) / 2;

        for (View mockItem : mMockItems) {
            Rect rect = new Rect();
            mGridEvenSpacingDecoration.getItemOffsets(rect, mockItem, mTestRecView,
                    new RecyclerView.State());
            Rect expectedRect = new Rect(offset, 0, offset, 0);
            assertEquals(expectedRect, rect);
        }
    }
}
