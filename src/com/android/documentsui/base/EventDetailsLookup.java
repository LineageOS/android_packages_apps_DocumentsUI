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
package com.android.documentsui.base;

import android.view.MotionEvent;

import com.android.documentsui.dirlist.DocumentDetails;

import javax.annotation.Nullable;

/**
 * Provides event handlers w/ access to details about documents details
 * view items Documents in the UI (RecyclerView).
 */
public interface EventDetailsLookup {

    /** @return true if there is an item under the finger/cursor. */
    boolean overItem(MotionEvent e);

    /**
     * @return true if there is a model backed item under the finger/cursor.
     * Resulting calls on the event instance should never return a null
     * DocumentDetails and DocumentDetails#hasModelId should always return true
     */
    boolean overModelItem(MotionEvent e);

    /**
     * @return true if the event is over an area that can be dragged via touch
     * or via mouse. List items have a white area that is not draggable.
     */
    boolean inItemDragRegion(MotionEvent e);

    /**
     * @return true if the event is in the "selection hot spot" region.
     * The hot spot region instantly selects in touch mode, vs launches.
     */
    boolean inItemSelectRegion(MotionEvent e);

    /**
     * @return the adapter position of the item under the finger/cursor.
     */
    int getItemPosition(MotionEvent e);


    /**
     * @return the DocumentDetails for the item under the event, or null.
     */
    @Nullable DocumentDetails getDocumentDetails(MotionEvent e);
}
