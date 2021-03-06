/*
 * Copyright (C) 2020 The Android Open Source Project
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

import com.android.documentsui.base.UserId;

import java.util.ArrayList;
import java.util.List;

public class TestUserIdManager implements UserIdManager {
    public List<UserId> userIds = new ArrayList<>();
    public UserId systemUser = null;
    public UserId managedUser = null;

    @Override
    public List<UserId> getUserIds() {
        return userIds;
    }

    @Override
    public UserId getSystemUser() {
        return systemUser;
    }

    @Override
    public UserId getManagedUser() {
        return managedUser;
    }
}
