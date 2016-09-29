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
package com.android.documentsui.testing;

import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.roots.RootsAccess;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

public class TestRootsAccess implements RootsAccess {

    public static final RootInfo DOWNLOADS;
    public static final RootInfo HOME;

    static {
        DOWNLOADS = new RootInfo();
        DOWNLOADS.authority = "com.android.providers.downloads.documents";
        DOWNLOADS.rootId = "downloads";

        HOME = new RootInfo();
        HOME.authority = "com.android.externalstorage.documents";
        HOME.rootId = "home";
    }

    public final Map<String, Collection<RootInfo>> roots = new HashMap<>();
    private @Nullable RootInfo nextRoot;

    public TestRootsAccess() {
        add(DOWNLOADS);
        add(HOME);
    }

    public void add(RootInfo root) {
        if (!roots.containsKey(root.authority)) {
            roots.put(root.authority, new ArrayList<>());
        }
        roots.get(root.authority).add(root);
    }

    @Override
    public RootInfo getRootOneshot(String authority, String rootId) {
        if (roots.containsKey(authority)) {
            for (RootInfo root : roots.get(authority)) {
                if (rootId.equals(root.rootId)) {
                    return root;
                }
            }
        }
        return null;
    }

    @Override
    public Collection<RootInfo> getMatchingRootsBlocking(State state) {
        List<RootInfo> allRoots = new ArrayList<>();
        for (String authority : roots.keySet()) {
            allRoots.addAll(roots.get(authority));
        }
        return RootsAccess.getMatchingRoots(allRoots, state);
    }

    @Override
    public Collection<RootInfo> getRootsForAuthorityBlocking(String authority) {
        return roots.get(authority);
    }
}
