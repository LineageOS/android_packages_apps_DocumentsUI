/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.documentsui.queries;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import static org.mockito.Mockito.mock;

import android.os.Bundle;
import android.view.ViewGroup;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Shared;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class SearchChipViewManagerTest {

    private static final String[] TEST_MIMETYPES = new String[]{"image/png", "video/mpeg"};
    private static int CHIP_TYPE = 1000;

    private SearchChipViewManager mSearchChipViewManager;

    @Before
    public void setUp() {
        ViewGroup chipGroup = mock(ViewGroup.class);
        mSearchChipViewManager = new SearchChipViewManager(chipGroup);
    }

    @Test
    public void testGetCheckedChipMimeTypes_HasCorrectValue() throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        final String[] checkedMimeTypes = mSearchChipViewManager.getCheckedMimeTypes();
        assertTrue(MimeTypes.mimeMatches(TEST_MIMETYPES, checkedMimeTypes[0]));
        assertTrue(MimeTypes.mimeMatches(TEST_MIMETYPES, checkedMimeTypes[1]));
    }

    @Test
    public void testRestoreCheckedChipItems_HasCorrectValue() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putIntArray(Shared.EXTRA_QUERY_CHIPS, new int[]{2});
        mSearchChipViewManager.restoreCheckedChipItems(bundle);

        assertEquals(1, mSearchChipViewManager.mCheckedChipItems.size());
        Iterator<SearchChipData> iterator = mSearchChipViewManager.mCheckedChipItems.iterator();
        assertEquals(2, iterator.next().getChipType());
    }

    @Test
    public void testSaveInstanceState_HasCorrectValue() throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        Bundle bundle = new Bundle();
        mSearchChipViewManager.onSaveInstanceState(bundle);
        final int[] chipTypes = bundle.getIntArray(Shared.EXTRA_QUERY_CHIPS);
        assertEquals(1, chipTypes.length);
        assertEquals(CHIP_TYPE, chipTypes[0]);
    }

    private static Set<SearchChipData> getFakeSearchChipDataList() {
        final Set<SearchChipData> chipDataList = new HashSet<>();
        chipDataList.add(new SearchChipData(CHIP_TYPE, 0, TEST_MIMETYPES));
        return chipDataList;
    }
}
