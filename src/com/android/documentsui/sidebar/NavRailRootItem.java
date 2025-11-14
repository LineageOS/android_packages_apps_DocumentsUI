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

import android.view.View;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.R;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;

/**
 * Similar to {@link RootItem} but only used in the navigation rail.
 */
public class NavRailRootItem extends RootItem {

    public NavRailRootItem(RootInfo root, ActionHandler actionHandler, boolean maybeShowBadge) {
        super(
                getRes(R.layout.nav_rail_item_root),
                root,
                actionHandler,
                "" /* packageName */,
                maybeShowBadge);
    }

    public NavRailRootItem(
            RootInfo root,
            ActionHandler actionHandler,
            String packageName,
            boolean maybeShowBadge) {
        super(
                getRes(R.layout.nav_rail_item_root),
                root,
                actionHandler,
                packageName,
                maybeShowBadge);
    }

    @Override
    public void bindView(View convertView) {
        bindIconAndTitle(convertView);
    }

    @Override
    public String toString() {
        return "NavRailRootItem{"
                + "id=" + stringId
                + ", userId=" + userId
                + ", root=" + root
                + ", docInfo=" + docInfo
                + "}";
    }

    /**
     * Creates a stub root item for a user. A stub root item is used as a place holder when
     * there is no such root available. We can therefore show the item on the UI.
     */
    public static NavRailRootItem createStubItem(NavRailRootItem item, UserId targetUser) {
        RootInfo stubRootInfo = RootInfo.copyRootInfo(item.root);
        stubRootInfo.userId = targetUser;
        return new NavRailRootItem(stubRootInfo, item.mActionHandler, item.mMaybeShowBadge);
    }
}
