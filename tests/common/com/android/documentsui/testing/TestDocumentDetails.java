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
package com.android.documentsui.testing;

import android.text.TextUtils;
import android.view.MotionEvent;

import com.android.documentsui.dirlist.DocumentDetails;
import com.android.internal.widget.RecyclerView;

public final class TestDocumentDetails implements DocumentDetails {

    private int mPosition;
    private String mModelId;
    private boolean mInDragRegion;
    private boolean mInSelectRegion;

    public TestDocumentDetails() {
       mPosition = RecyclerView.NO_POSITION;
    }

    public TestDocumentDetails(TestDocumentDetails source) {
        mPosition = source.mPosition;
        mModelId = source.mModelId;
        mInDragRegion = source.mInDragRegion;
        mInSelectRegion = source.mInSelectRegion;
    }

    public void at(int position) {
        mPosition = position;  // this is both "adapter position" and "item position".
        mModelId = (position == RecyclerView.NO_POSITION)
                ? null
                : String.valueOf(position);
    }

    public void setInItemDragRegion(boolean inHotspot) {
        mInDragRegion = inHotspot;
    }

    public void setInItemSelectRegion(boolean over) {
        mInSelectRegion = over;
    }

    @Override
    public boolean hasModelId() {
        return !TextUtils.isEmpty(mModelId);
    }

    @Override
    public String getModelId() {
        return mModelId;
    }

    @Override
    public int getAdapterPosition() {
        return mPosition;
    }

    @Override
    public boolean inDragRegion(MotionEvent event) {
        return mInDragRegion;
    }

    @Override
    public boolean inSelectRegion(MotionEvent event) {
        return mInSelectRegion;
    }

    @Override
    public int hashCode() {
        return mPosition;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
          return true;
      }

      if (!(o instanceof TestDocumentDetails)) {
          return false;
      }

      TestDocumentDetails other = (TestDocumentDetails) o;
      return mPosition == other.mPosition
              && mModelId == other.mModelId;
    }
}
