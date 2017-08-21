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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.graphics.Point;
import android.graphics.Rect;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.view.MotionEvent;

import com.android.documentsui.DirectoryReloadLock;
import com.android.documentsui.dirlist.TestData;
import com.android.documentsui.dirlist.TestDocumentsAdapter;
import com.android.documentsui.testing.SelectionManagers;
import com.android.documentsui.testing.TestEvent.Builder;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BandControllerTest {

    private static final List<String> ITEMS = TestData.create(10);
    private BandController mBandController;
    private boolean mIsActive;

    @Before
    public void setup() throws Exception {
        mIsActive = false;
        mBandController = new BandController(
                new TestSelectionEnvironment(),
                new TestDocumentsAdapter(ITEMS),
                SelectionManagers.createTestInstance(ITEMS),
                SelectionManagers.CAN_SELECT_ANYTHING,
                new DirectoryReloadLock(),
                null  // "gridItemTester"
                ) {
          @Override
          public boolean isActive() {
              return mIsActive;
          }
        };
    }

    @Test
    public void testGoodStart() {
        assertTrue(mBandController.shouldStart(goodStartEventBuilder().build()));
    }

    @Test
    public void testBadStart_NoButtons() {
        assertFalse(mBandController.shouldStart(
                goodStartEventBuilder().releaseButton(MotionEvent.BUTTON_PRIMARY).build()));
    }

    @Test
    public void testBadStart_SecondaryButton() {
        assertFalse(
                mBandController.shouldStart(goodStartEventBuilder().secondary().build()));
    }

    @Test
    public void testBadStart_TertiaryButton() {
        assertFalse(
                mBandController.shouldStart(goodStartEventBuilder().tertiary().build()));
    }

    @Test
    public void testBadStart_Touch() {
        assertFalse(mBandController.shouldStart(
                goodStartEventBuilder().touch().releaseButton(MotionEvent.BUTTON_PRIMARY).build()));
    }

    @Test
    public void testBadStart_inDragSpot() {
        assertFalse(
                mBandController.shouldStart(goodStartEventBuilder().at(1).inDragHotspot().build()));
    }

    @Test
    public void testBadStart_ActionDown() {
        assertFalse(mBandController
                .shouldStart(goodStartEventBuilder().action(MotionEvent.ACTION_DOWN).build()));
    }

    @Test
    public void testBadStart_ActionUp() {
        assertFalse(mBandController
                .shouldStart(goodStartEventBuilder().action(MotionEvent.ACTION_UP).build()));
    }

    @Test
    public void testBadStart_ActionPointerDown() {
        assertFalse(mBandController.shouldStart(
                goodStartEventBuilder().action(MotionEvent.ACTION_POINTER_DOWN).build()));
    }

    @Test
    public void testBadStart_ActionPointerUp() {
        assertFalse(mBandController.shouldStart(
                goodStartEventBuilder().action(MotionEvent.ACTION_POINTER_UP).build()));
    }

    @Test
    public void testBadStart_NoItems() {
        mBandController = new BandController(
                new TestSelectionEnvironment(),
                new TestDocumentsAdapter(Collections.EMPTY_LIST),
                SelectionManagers.createTestInstance(ITEMS),
                SelectionManagers.CAN_SELECT_ANYTHING,
                new DirectoryReloadLock(),
                null   // gridItemTester
                );
        assertFalse(mBandController.shouldStart(goodStartEventBuilder().build()));
    }

    @Test
    public void testBadStart_alreadyActive() {
        mIsActive = true;
        assertFalse(mBandController.shouldStart(goodStartEventBuilder().build()));
    }

    @Test
    public void testGoodStop() {
        mIsActive = true;
        assertTrue(mBandController.shouldStop(goodStopEventBuilder().build()));
    }

    @Test
    public void testGoodStop_PointerUp() {
        mIsActive = true;
        assertTrue(mBandController
                .shouldStop(goodStopEventBuilder().action(MotionEvent.ACTION_POINTER_UP).build()));
    }

    @Test
    public void testGoodStop_Cancel() {
        mIsActive = true;
        assertTrue(mBandController
                .shouldStop(goodStopEventBuilder().action(MotionEvent.ACTION_CANCEL).build()));
    }

    @Test
    public void testBadStop_NotActive() {
        assertFalse(mBandController.shouldStop(goodStopEventBuilder().build()));
    }

    @Test
    public void testBadStop_NonMouse() {
        mIsActive = true;
        assertFalse(mBandController.shouldStop(goodStopEventBuilder().touch().build()));
    }

    @Test
    public void testBadStop_Move() {
        mIsActive = true;
        assertFalse(mBandController.shouldStop(
                goodStopEventBuilder().action(MotionEvent.ACTION_MOVE).touch().build()));
    }

    @Test
    public void testBadStop_Down() {
        mIsActive = true;
        assertFalse(mBandController.shouldStop(
                goodStopEventBuilder().action(MotionEvent.ACTION_DOWN).touch().build()));
    }


    private Builder goodStartEventBuilder() {
        return new Builder().mouse().primary().action(MotionEvent.ACTION_MOVE).notInDragHotspot();
    }

    private Builder goodStopEventBuilder() {
        return new Builder().mouse().action(MotionEvent.ACTION_UP).notInDragHotspot();
    }

    private final class TestSelectionEnvironment implements BandController.SelectionEnvironment {
        @Override
        public void scrollBy(int dy) {
        }

        @Override
        public void runAtNextFrame(Runnable r) {
        }

        @Override
        public void removeCallback(Runnable r) {
        }

        @Override
        public void showBand(Rect rect) {
        }

        @Override
        public void hideBand() {
        }

        @Override
        public void addOnScrollListener(OnScrollListener listener) {
        }

        @Override
        public void removeOnScrollListener(OnScrollListener listener) {
        }

        @Override
        public int getHeight() {
            return 0;
        }

        @Override
        public void invalidateView() {
        }

        @Override
        public Point createAbsolutePoint(Point relativePoint) {
            return null;
        }

        @Override
        public Rect getAbsoluteRectForChildViewAt(int index) {
            return null;
        }

        @Override
        public int getAdapterPositionAt(int index) {
            return 0;
        }

        @Override
        public int getColumnCount() {
            return 0;
        }

        @Override
        public int getChildCount() {
            return 0;
        }

        @Override
        public int getVisibleChildCount() {
            return 0;
        }

        @Override
        public boolean hasView(int adapterPosition) {
            return false;
        }
    }
}
