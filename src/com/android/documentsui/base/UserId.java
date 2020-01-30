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

package com.android.documentsui.base;

import static androidx.core.util.Preconditions.checkNotNull;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.os.UserHandle;

import androidx.annotation.VisibleForTesting;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ProtocolException;
import java.util.Objects;
/**
 * Representation of a {@link UserHandle}.
 */
public final class UserId {

    // A unspecified user is used as when the user's value is uninitialized. e.g. rootInfo.reset()
    public static UserId UNSPECIFIED_USER = UserId.of(UserHandle.of(-1000));
    // A current user represents the user of the app's process. It is mainly used for comparison.
    public static UserId CURRENT_USER = UserId.of(Process.myUserHandle());
    // A default user represents the user of the app's process. It is mainly used for operation
    // which supports only the current user only.
    public static UserId DEFAULT_USER = CURRENT_USER;

    private static final int VERSION_INIT = 1;

    private final UserHandle mUserHandle;

    private UserId(UserHandle userHandle) {
        checkNotNull(userHandle);
        mUserHandle = userHandle;
    }

    /**
     * Returns a {@link UserId} for a given {@link UserHandle}.
     */
    @VisibleForTesting
    static UserId of(UserHandle userHandle) {
        return new UserId(userHandle);
    }

    /**
     * Returns the given context if the user is the current user or unspecified. Otherwise, returns
     * an "android" package context as the user.
     *
     * @throws IllegalStateException if android package of the other user does not exist
     */
    @VisibleForTesting
    Context asContext(Context context) {
        if (CURRENT_USER.equals(this) || isUnspecified()) {
            return context;
        }
        try {
            return context.createPackageContextAsUser("android", /* flags= */ 0, mUserHandle);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException("android package not found.");
        }
    }

    /**
     * Return a package manager instance of this user.
     */
    public PackageManager getPackageManager(Context context) {
        return asContext(context).getPackageManager();
    }

    /**
     * Return a content resolver instance of this user.
     */
    public ContentResolver getContentResolver(Context context) {
        return asContext(context).getContentResolver();
    }

    private boolean isUnspecified() {
        return UNSPECIFIED_USER.equals(this);
    }

    @Override
    public String toString() {
        return "UserId{"
                + (isUnspecified() ? "UNSPECIFIED" : mUserHandle.getIdentifier())
                + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (this == o) {
            return true;
        }

        if (o instanceof UserId) {
            UserId other = (UserId) o;
            return Objects.equals(mUserHandle, other.mUserHandle);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mUserHandle);
    }

    /**
     * Reads a {@link UserId} from an input stream.
     */
    public static UserId read(DataInputStream in) throws IOException {
        final int version = in.readInt();
        switch (version) {
            case VERSION_INIT:
                int userId = in.readInt();
                return UserId.of(UserHandle.of(userId));
            default:
                throw new ProtocolException("Unknown version " + version);
        }
    }

    /**
     * Writes a {@link UserId} to an output stream.
     */
    public static void write(DataOutputStream out, UserId userId) throws IOException {
        out.writeInt(VERSION_INIT);
        out.writeInt(userId.mUserHandle.getIdentifier());
    }
}
