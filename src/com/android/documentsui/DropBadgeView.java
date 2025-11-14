/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static com.android.documentsui.util.Material3Config.getRes;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;

import com.android.documentsui.DragAndDropManager.State;
import com.android.documentsui.base.MimeTypes;

/**
 * Provides a way to encapsulate droppable badge toggling logic into a single class.
 */
public final class DropBadgeView extends ImageView {
    private final int[] mStateRejectDrop;
    private final int[] mStateCopy;

    private @State int mState;
    private LayerDrawable mBackground;

    public DropBadgeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mStateRejectDrop = new int[] {getRes(R.attr.state_reject_drop)};
        mStateCopy = new int[] {getRes(R.attr.state_copy)};

        final int badgeHeight =
                context.getResources().getDimensionPixelSize(getRes(R.dimen.drop_icon_height));
        final int badgeWidth =
                context.getResources().getDimensionPixelSize(getRes(R.dimen.drop_icon_width));
        final int iconSize =
                context.getResources().getDimensionPixelSize(getRes(R.dimen.root_icon_size));

        Drawable okBadge =
                context.getResources().getDrawable(getRes(R.drawable.drop_badge_states), null);
        Drawable defaultIcon = IconUtils.loadMimeIcon(context, MimeTypes.GENERIC_TYPE);

        Drawable[] list = {defaultIcon, okBadge};
        mBackground = new LayerDrawable(list);

        mBackground.setLayerGravity(1, Gravity.BOTTOM | Gravity.RIGHT);
        mBackground.setLayerGravity(0, Gravity.TOP | Gravity.LEFT);
        mBackground.setLayerSize(1, badgeWidth, badgeHeight);
        mBackground.setLayerSize(0, iconSize, iconSize);

        setBackground(mBackground);
    }

    @Override
    public int[] onCreateDrawableState(int extraSpace) {
        // STATE_REJECT_DROP and STATE_COPY can't exist at the same time.
        final int[] drawableState = super.onCreateDrawableState(extraSpace + 1);

        switch (mState) {
            case DragAndDropManager.STATE_NOT_ALLOWED:
                mergeDrawableStates(drawableState, mStateRejectDrop);
                break;
            case DragAndDropManager.STATE_COPY:
                mergeDrawableStates(drawableState, mStateCopy);
                break;
        }

        return drawableState;
    }

    void updateState(@State int state) {
        mState = state;
        refreshDrawableState();
    }

    void updateIcon(Drawable icon) {
        mBackground.setDrawable(0, icon);
    }
}
