/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.os.storage.StorageVolume.ScopedAccessProviderContract.COL_GRANTED;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PACKAGES;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PACKAGES_COLUMNS;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PACKAGES_COL_PACKAGE;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PERMISSIONS;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PERMISSIONS_COLUMNS;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PERMISSIONS_COL_DIRECTORY;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PERMISSIONS_COL_GRANTED;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PERMISSIONS_COL_PACKAGE;
import static android.os.storage.StorageVolume.ScopedAccessProviderContract.TABLE_PERMISSIONS_COL_VOLUME_UUID;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.getInternalDirectoryName;
import static com.android.documentsui.base.SharedMinimal.getExternalDirectoryName;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.PERMISSION_ASK;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.PERMISSION_ASK_AGAIN;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.PERMISSION_NEVER_ASK;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.getAllPackages;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.getAllPermissions;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.setScopedAccessPermissionStatus;

import static com.android.internal.util.Preconditions.checkArgument;

import android.app.ActivityManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import com.android.documentsui.prefs.ScopedAccessLocalPreferences.Permission;
import com.android.internal.util.ArrayUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

//TODO(b/72055774): update javadoc once implementation is finished
/**
 * Provider used to manage scoped access directory permissions.
 *
 * <p>It fetches data from 2 sources:
 *
 * <ul>
 * <li>{@link com.android.documentsui.prefs.ScopedAccessLocalPreferences} for denied permissions.
 * <li>{@link ActivityManager} for allowed permissions.
 * </ul>
 *
 * <p>And returns the results in 2 tables:
 *
 * <ul>
 * <li>{@link #TABLE_PACKAGES}: read-only table with the name of all packages
 * (column ({@link android.os.storage.StorageVolume.ScopedAccessProviderContract#COL_PACKAGE}) that
 * had a scoped access directory permission granted or denied.
 * <li>{@link #TABLE_PERMISSIONS}: writable table with the name of all packages
 * (column ({@link android.os.storage.StorageVolume.ScopedAccessProviderContract#COL_PACKAGE}) that
 * had a scoped access directory
 * (column ({@link android.os.storage.StorageVolume.ScopedAccessProviderContract#COL_DIRECTORY})
 * permission for a volume (column
 * {@link android.os.storage.StorageVolume.ScopedAccessProviderContract#COL_VOLUME_UUID}, which
 * contains the volume UUID or {@code null} if it's the primary partition) granted or denied
 * (column ({@link android.os.storage.StorageVolume.ScopedAccessProviderContract#COL_GRANTED}).
 * </ul>
 *
 * <p><b>Note:</b> the {@code query()} methods return all entries; it does not support selection or
 * projections.
 */
// TODO(b/72055774): add unit tests
public class ScopedAccessProvider extends ContentProvider {

    private static final String TAG = "ScopedAccessProvider";
    private static final UriMatcher sMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    private static final int URI_PACKAGES = 1;
    private static final int URI_PERMISSIONS = 2;

    public static final String AUTHORITY = "com.android.documentsui.scopedAccess";

    static {
        sMatcher.addURI(AUTHORITY, TABLE_PACKAGES + "/*", URI_PACKAGES);
        sMatcher.addURI(AUTHORITY, TABLE_PERMISSIONS + "/*", URI_PERMISSIONS);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        if (DEBUG) {
            Log.v(TAG, "query(" + uri + "): proj=" + Arrays.toString(projection)
                + ", sel=" + selection);
        }
        switch (sMatcher.match(uri)) {
            case URI_PACKAGES:
                return getPackagesCursor();
            case URI_PERMISSIONS:
                return getPermissionsCursor(selectionArgs);
            default:
                throw new UnsupportedOperationException("Unsupported Uri " + uri);
        }
    }

    private Cursor getPackagesCursor() {
        // First get the packages that were denied
        final Set<String> pkgs = getAllPackages(getContext());

        if (ArrayUtils.isEmpty(pkgs)) {
            if (DEBUG) Log.v(TAG, "getPackagesCursor(): ignoring " + pkgs);
            return null;
        }

        // TODO(b/63720392): also need to query AM for granted permissions

        // Then create the cursor
        final MatrixCursor cursor = new MatrixCursor(TABLE_PACKAGES_COLUMNS, pkgs.size());
        pkgs.forEach((pkg) -> cursor.addRow( new Object[] { pkg }));
        return cursor;
    }

    private Cursor getPermissionsCursor(String[] packageNames) {
        // First get the packages that were denied
        final List<Permission> rawPermissions = getAllPermissions(getContext());

        if (ArrayUtils.isEmpty(rawPermissions)) {
            if (DEBUG) Log.v(TAG, "getPermissionsCursor(): ignoring " + rawPermissions);
            return null;
        }

        // TODO(b/72055774): unit tests for filters (permissions and/or package name);
        final List<Object[]> permissions = rawPermissions.stream()
                .filter(permission -> ArrayUtils.contains(packageNames, permission.pkg)
                        && permission.status == PERMISSION_NEVER_ASK)
                .map(permission -> new Object[] {
                        permission.pkg,
                        permission.uuid,
                        getExternalDirectoryName(permission.directory),
                        Integer.valueOf(0)
                })
                .collect(Collectors.toList());

        // TODO(b/63720392): need to add logic to handle scenarios where the root permission of
        // a secondary volume mismatches a child permission (for example, child is allowed by root
        // is denied).

        // TODO(b/63720392): also need to query AM for granted permissions

        // Then create the cursor
        final MatrixCursor cursor = new MatrixCursor(TABLE_PERMISSIONS_COLUMNS, permissions.size());
        permissions.forEach((row) -> cursor.addRow(row));
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("insert(): unsupported " + uri);
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("delete(): unsupported " + uri);
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (sMatcher.match(uri) != URI_PERMISSIONS) {
            throw new UnsupportedOperationException("update(): unsupported " + uri);
        }

        if (DEBUG) {
            Log.v(TAG, "update(" + uri + "): " + Arrays.toString(selectionArgs) + " = " + values);
        }

        final boolean newValue = values.getAsBoolean(COL_GRANTED);

        if (!newValue) {
            // TODO(b/63720392): need to call AM to disable it
            Log.w(TAG, "Disabling permission is not supported yet");
            return 0;
        }

        // TODO(b/72055774): add unit tests for invalid input
        checkArgument(selectionArgs != null && selectionArgs.length == 3,
                "Must have exactly 3 args: package_name, (nullable) uuid, (nullable) directory: "
                        + Arrays.toString(selectionArgs));
        final String packageName = selectionArgs[0];
        final String uuid = selectionArgs[1];
        final String dir = getInternalDirectoryName(selectionArgs[2]);

        // TODO(b/63720392): for now just set it as ASK so it's still listed on queries.
        // But the right approach is to call AM to grant the permission and then remove the entry
        // from our preferences
        setScopedAccessPermissionStatus(getContext(), packageName, uuid, dir, PERMISSION_ASK);

        return 1;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        final String prefix = "  ";

        final List<String> packages = new ArrayList<>();
        pw.print("Packages: ");
        try (Cursor cursor = getPackagesCursor()) {
            if (cursor == null || cursor.getCount() == 0) {
                pw.println("N/A");
            } else {
                pw.println(cursor.getCount());
                while (cursor.moveToNext()) {
                    final String pkg = cursor.getString(TABLE_PACKAGES_COL_PACKAGE);
                    packages.add(pkg);
                    pw.print(prefix);
                    pw.println(pkg);
                }
            }
        }

        pw.print("Permissions: ");
        final String[] selection = new String[packages.size()];
        packages.toArray(selection);
        try (Cursor cursor = getPermissionsCursor(selection)) {
            if (cursor == null) {
                pw.println("N/A");
            } else {
                pw.println(cursor.getCount());
                while (cursor.moveToNext()) {
                    pw.print(prefix); pw.print(cursor.getString(TABLE_PERMISSIONS_COL_PACKAGE));
                    pw.print('/');
                    final String uuid = cursor.getString(TABLE_PERMISSIONS_COL_VOLUME_UUID);
                    if (uuid != null) {
                        pw.print(uuid); pw.print('>');
                    }
                    pw.print(cursor.getString(TABLE_PERMISSIONS_COL_DIRECTORY));
                    pw.print(": "); pw.println(cursor.getInt(TABLE_PERMISSIONS_COL_GRANTED) == 1);
                }
            }
        }

        pw.print("Raw permissions: ");
        final List<Permission> rawPermissions = getAllPermissions(getContext());
        if (rawPermissions.isEmpty()) {
            pw.println("N/A");
        } else {
            final int size = rawPermissions.size();
            pw.println(size);
            for (int i = 0; i < size; i++) {
                final Permission permission = rawPermissions.get(i);
                pw.print(prefix); pw.println(permission);
            }
        }
    }
}
