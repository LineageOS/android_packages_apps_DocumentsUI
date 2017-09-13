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
package com.android.documentsui.selection.addons;

import android.support.v7.widget.RecyclerView;
import android.view.MotionEvent;
import android.view.View;

/**
 * Provides clients a means of controlling when and where band selection is initiated.
 * This can be used to permit band initiation in non-empty areas, like in the whitespace of
 * a bound view.
 */
public abstract class BandPredicate {
    /**
     * @return true if band selection can be initiated in repsonse to the {@link MotionEvent}.
     */
    public abstract boolean canInitiate(MotionEvent e);

    /**
     * A BandPredicate that allows initiation of band selection only in areas of RecyclerView
     * where there are no items or the item position is {@link RecyclerView#NO_POSITION}.
     */
    public static final class NoPositionBandPredicate extends BandPredicate {

        private final RecyclerView mView;

        public NoPositionBandPredicate(RecyclerView view) {
            mView = view;
        }

        @Override
        public boolean canInitiate(MotionEvent e) {
            View itemView = mView.findChildViewUnder(e.getX(), e.getY());
            int position = itemView != null
                    ? mView.getChildAdapterPosition(itemView)
                    : RecyclerView.NO_POSITION;

            return position == RecyclerView.NO_POSITION;
        }
    };
}
