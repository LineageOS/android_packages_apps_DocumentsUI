/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.documentsui.sidebar;

import static com.android.documentsui.util.Material3Config.getRes;

import android.content.pm.ResolveInfo;
import android.view.View;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;

/**
 * Similar to {@link RootAndAppItem} but only used in the navigation rail.
 */
public class NavRailRootAndAppItem extends RootAndAppItem {

    public NavRailRootAndAppItem(
            RootInfo root, ResolveInfo info, ActionHandler actionHandler, boolean maybeShowBadge) {
        super(getRes(R.layout.nav_rail_item_root), root, info, actionHandler, maybeShowBadge);
    }

    @Override
    public void bindView(View convertView) {
        bindIconAndTitle(convertView);
    }

    @Override
    public String toString() {
        return "NavRailRootAndAppItem{"
                + "id=" + stringId
                + ", userId=" + userId
                + ", root=" + root
                + ", resolveInfo=" + resolveInfo
                + ", docInfo=" + docInfo
                + "}";
    }
}
