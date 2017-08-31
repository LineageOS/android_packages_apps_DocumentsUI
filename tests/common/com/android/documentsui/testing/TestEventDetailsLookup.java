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

import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;

import com.android.documentsui.base.EventDetailsLookup;
import com.android.documentsui.dirlist.DocumentDetails;

import javax.annotation.Nullable;

/**
 * Test impl of EventDocLookup.
 */
public class TestEventDetailsLookup implements EventDetailsLookup {

    private @Nullable DocumentDetails mDoc;

    @Override
    public boolean overItem(MotionEvent e) {
        return getItemPosition(e) != RecyclerView.NO_POSITION;
    }

    @Override
    public boolean overModelItem(MotionEvent e) {
        return mDoc.getModelId() != null;
    }

    @Override
    public boolean inItemDragRegion(MotionEvent e) {
        return mDoc.inDragRegion(e);
    }

    @Override
    public int getItemPosition(MotionEvent e) {
        return mDoc.getAdapterPosition();
    }

    @Override
    public boolean inItemSelectRegion(MotionEvent e) {
        return mDoc.inSelectRegion(e);
    }

    @Override
    public DocumentDetails getDocumentDetails(MotionEvent e) {
        return mDoc;
    }

    /**
     * Creates/installs/returns a new test document. Subsequent calls to
     * any EventDocLookup methods will consult the newly created doc.
     */
    public TestDocumentDetails initAt(int position) {
        TestDocumentDetails doc = new TestDocumentDetails();
        doc.at(position);
        mDoc = doc;
        return doc;
    }
}
