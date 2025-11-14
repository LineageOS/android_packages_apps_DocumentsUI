/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.dirlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.database.Cursor;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.View;
import android.widget.Space;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.documentsui.R;
import com.android.documentsui.TestConfigStore;
import com.android.documentsui.base.State;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.CheckAndForceMaterial3Flag;
import com.android.documentsui.testing.TestRecyclerView;
import com.android.documentsui.testing.Views;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class AccessibilityTest {

    @Rule
    public final CheckAndForceMaterial3Flag mCheckFlagsRule = new CheckAndForceMaterial3Flag();

    private static final List<String> ITEMS = TestData.create(10);

    private TestRecyclerView mView;
    private AccessibilityEventRouter mAccessibilityDelegate;
    private boolean mClickCallbackCalled = false;
    private boolean mLongClickCallbackCalled = false;

    private void initAccessibilityDelegate(@State.ActionType int actionType) {
        mAccessibilityDelegate = new AccessibilityEventRouter(mView, (View v) -> {
            mClickCallbackCalled = true;
            return true;
        }, (View v) -> {
            mLongClickCallbackCalled = true;
            return true;
        }, actionType);
        mView.setAccessibilityDelegateCompat(mAccessibilityDelegate);
    }

    @Before
    public void setUp() throws Exception {
        mView = TestRecyclerView.create(ITEMS);
        mView.setLayoutManager(new LinearLayoutManager(mView.getContext()));
        initAccessibilityDelegate(State.ACTION_BROWSE);
    }

    @Test
    public void test_announceSelected() throws Exception {
        View item = Views.createTestView(/* activated= */ true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        assertTrue(info.isSelected());
    }

    @Test
    public void testNullItemDetails_NoActionClick_PrivateSpaceEnabled() throws Exception {
        View item = Views.createTestView(/* activated= */ true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();

        List<RecyclerView.ViewHolder> holders = new ArrayList<>();
        TestConfigStore testConfigStore = new TestConfigStore();
        testConfigStore.enablePrivateSpaceInPhotoPicker();
        holders.add(new MessageHolder(mView.getContext(), new Space(mView.getContext()),
                testConfigStore) {
            @Override
            public void bind(Cursor cursor, String modelId) {

            }
        });

        mView.setHolders(holders);

        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        assertFalse(info.isClickable());
    }

    @Test
    public void testNullItemDetails_NoActionClick_PrivateSpaceDisabled() throws Exception {
        View item = Views.createTestView(/* activated= */ true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();

        List<RecyclerView.ViewHolder> holders = new ArrayList<>();
        TestConfigStore testConfigStore = new TestConfigStore();
        testConfigStore.disablePrivateSpaceInPhotoPicker();
        holders.add(new MessageHolder(mView.getContext(), new Space(mView.getContext()),
                testConfigStore) {
            @Override
            public void bind(Cursor cursor, String modelId) {

            }
        });

        mView.setHolders(holders);

        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        assertFalse(info.isClickable());
    }

    @Test
    public void test_routesAccessibilityClicks() throws Exception {
        View item = Views.createTestView(/* activated= */ true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        mAccessibilityDelegate.getItemDelegate().performAccessibilityAction(
                item, AccessibilityNodeInfoCompat.ACTION_CLICK, null);
        assertTrue(mClickCallbackCalled);
    }

    @Test
    public void test_routesAccessibilityLongClicks() throws Exception {
        View item = Views.createTestView(/* activated= */ true);
        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);
        mAccessibilityDelegate.getItemDelegate().performAccessibilityAction(
                item, AccessibilityNodeInfoCompat.ACTION_LONG_CLICK, null);
        assertTrue(mLongClickCallbackCalled);
    }

    @Test
    public void test_accessibilityActions_DocumentHolder() throws Exception {
        View item = Views.createTestView(/* activated= */ false);

        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        addMockDocumentHolder();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);

        List<AccessibilityActionCompat> actions = getAccessibilityActions(info);
        assertEquals(2, actions.size());
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_USE_MATERIAL3})
    public void test_accessibilityActionDescription_DocumentHolder() throws Exception {
        View item = Views.createTestView(/* activated= */ false);

        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        addMockDocumentHolder();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);

        List<AccessibilityActionCompat> actions = getAccessibilityActions(info);
        assertEquals(mView.getContext().getString(R.string.document_click_action),
                actions.get(0).getLabel().toString());
        assertEquals(mView.getContext().getString(R.string.document_long_click_action),
                actions.get(1).getLabel().toString());
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_USE_MATERIAL3})
    public void test_accessibilityActionDescription_DocumentHolder_Selected() throws Exception {
        View item = Views.createTestView(/* activated= */ true);

        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        addMockDocumentHolder();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);

        List<AccessibilityActionCompat> actions = getAccessibilityActions(info);
        assertEquals(mView.getContext().getString(R.string.selected_document_click_action),
                actions.get(0).getLabel().toString());
        assertEquals(mView.getContext().getString(R.string.selected_document_long_click_action),
                actions.get(1).getLabel().toString());

    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_USE_MATERIAL3})
    public void test_accessibilityActionDescription_DocumentHolder_Picker() throws Exception {
        initAccessibilityDelegate(State.ACTION_GET_CONTENT);
        View item = Views.createTestView(/* activated= */ false);

        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        addMockDocumentHolder();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);

        List<AccessibilityActionCompat> actions = getAccessibilityActions(info);
        assertEquals(mView.getContext().getString(R.string.document_long_click_action),
                actions.get(0).getLabel().toString());
        assertEquals(mView.getContext().getString(R.string.document_long_click_action_picker),
                actions.get(1).getLabel().toString());
    }

    @Test
    @RequiresFlagsEnabled({Flags.FLAG_USE_MATERIAL3})
    public void test_accessibilityActionDescription_DocumentHolder_Selected_Picker()
            throws Exception {
        initAccessibilityDelegate(State.ACTION_GET_CONTENT);
        View item = Views.createTestView(/* activated= */ true);

        AccessibilityNodeInfoCompat info = AccessibilityNodeInfoCompat.obtain();
        addMockDocumentHolder();
        mAccessibilityDelegate.getItemDelegate().onInitializeAccessibilityNodeInfo(item, info);

        List<AccessibilityActionCompat> actions = getAccessibilityActions(info);
        assertEquals(mView.getContext().getString(R.string.selected_document_click_action),
                actions.get(0).getLabel().toString());
    }

    private List<AccessibilityActionCompat> getAccessibilityActions(
            AccessibilityNodeInfoCompat info) {
        List<AccessibilityActionCompat> actions = new ArrayList<>();


        final List<AccessibilityActionCompat> actionList = info.getActionList();
        final AccessibilityActionCompat clickAction = actionList.stream().filter(
                action -> action.getId()
                        == AccessibilityNodeInfoCompat.ACTION_CLICK).findAny().orElse(null);
        assertNotNull(clickAction);
        final AccessibilityActionCompat longClickAction = actionList.stream().filter(
                action -> action.getId()
                        == AccessibilityNodeInfoCompat.ACTION_LONG_CLICK).findAny().orElse(null);
        assertNotNull(longClickAction);

        actions.add(clickAction);
        actions.add(longClickAction);
        return actions;
    }

    private void addMockDocumentHolder() {
        List<RecyclerView.ViewHolder> holders = new ArrayList<>();
        final DocumentHolder holder = mock(DocumentHolder.class);
        when(holder.getItemDetails()).thenReturn(new DocumentItemDetails(holder));
        holders.add(holder);
        mView.setHolders(holders);
    }
}