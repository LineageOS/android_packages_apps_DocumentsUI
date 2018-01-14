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

import static com.android.documentsui.base.Shared.VERBOSE;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.PERMISSION_ASK_AGAIN;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.PERMISSION_NEVER_ASK;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.getAllPackages;
import static com.android.documentsui.prefs.ScopedAccessLocalPreferences.getAllPermissions;

import android.app.ActivityManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.ArraySet;
import android.util.Log;

import com.android.documentsui.prefs.ScopedAccessLocalPreferences.Permission;
import com.android.internal.util.ArrayUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
 * (column ({@link #COL_PACKAGE}) that had a scoped access directory permission granted or denied.
 * <li>{@link #TABLE_PERMISSIONS}: writable table with the name of all packages
 * (column ({@link #COL_PACKAGE}) that had a scoped access directory
 * (column ({@link #COL_DIRECTORY}) permission for a volume (column {@link #COL_VOLUME_UUID}, which
 * contains the volume UUID or {@code null} if it's the primary partition) granted or denied
 * (column ({@link #COL_GRANTED}).
 * </ul>
 *
 * <p><b>Note:</b> the {@code query()} methods return all entries; it does not support selection or
 * projections.
 */
// TODO(b/63720392): add unit tests
public class ScopedAccessProvider extends ContentProvider {

    private static final String TAG = "ScopedAccessProvider";
    private static final UriMatcher sMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    // TODO(b/63720392): move constants below to @hide values on DocumentsContract so Settings can
    // use them

    // Packages that have scoped access permissions
    private static final int URI_PACKAGES = 1;
    private static final String TABLE_PACKAGES = "packages";

    // Permissions per packages
    private static final int URI_PERMISSIONS = 2;
    private static final String TABLE_PERMISSIONS = "permissions";

    // Columns
    private static final String COL_PACKAGE = "package_name";
    private static final String COL_VOLUME_UUID = "volume_uuid";
    private static final String COL_DIRECTORY = "directory";
    private static final String COL_GRANTED = "granted";

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
        if (VERBOSE) {
            Log.v(TAG, "query(" + uri + "): proj=" + Arrays.toString(projection)
                + ", sel=" + selection);
        }
        switch (sMatcher.match(uri)) {
            case URI_PACKAGES:
                return getPackagesCursor();
            case URI_PERMISSIONS:
                return getPermissionsCursor();
            default:
                throw new UnsupportedOperationException("Unsupported Uri " + uri);
        }
    }

    private Cursor getPackagesCursor() {
        // First get the packages that were denied
        final ArraySet<String> pkgs = getAllPackages(getContext());

        if (ArrayUtils.isEmpty(pkgs)) {
            if (VERBOSE) Log.v(TAG, "getPackagesCursor(): ignoring " + pkgs);
            return null;
        }

        // TODO(b/63720392): also need to query AM for granted permissions

        // Then create the cursor
        final MatrixCursor cursor = new MatrixCursor(new String[] {COL_PACKAGE}, pkgs.size());
        final Object[] column = new Object[1];
        for (int i = 0; i < pkgs.size(); i++) {
            final String pkg = pkgs.valueAt(i);
            column[0] = pkg;
            cursor.addRow(column);
        }

        return cursor;
    }

    // TODO(b/63720392): decide how to handle ROOT_DIRECTORY - convert to null?
    private Cursor getPermissionsCursor() {
        // First get the packages that were denied
        final ArrayList<Permission> rawPermissions = getAllPermissions(getContext());

        if (ArrayUtils.isEmpty(rawPermissions)) {
            if (VERBOSE) Log.v(TAG, "getPermissionsCursor(): ignoring " + rawPermissions);
            return null;
        }

        final List<Object[]> permissions = rawPermissions.stream()
                .filter(permission -> permission.status == PERMISSION_ASK_AGAIN
                        || permission.status == PERMISSION_NEVER_ASK)
                .map(permission ->
                        new Object[] { permission.pkg, permission.uuid, permission.directory,
                                Integer.valueOf(1) })
                .collect(Collectors.toList());

        // TODO(b/63720392): also need to query AM for granted permissions

        // Then create the cursor
        final MatrixCursor cursor = new MatrixCursor(
                new String[] {COL_PACKAGE, COL_VOLUME_UUID, COL_DIRECTORY, COL_GRANTED},
                permissions.size());
        for (int i = 0; i < permissions.size(); i++) {
            cursor.addRow(permissions.get(i));
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (sMatcher.match(uri) != URI_PERMISSIONS) {
            throw new UnsupportedOperationException("insert(): unsupported " + uri);
        }

        if (VERBOSE) Log.v(TAG, "insert(" + uri + "): " + values);

        // TODO(b/63720392): implement
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        if (sMatcher.match(uri) != URI_PERMISSIONS) {
            throw new UnsupportedOperationException("delete(): unsupported " + uri);
        }

        if (VERBOSE) Log.v(TAG, "delete(" + uri + "): " + selection);

        // TODO(b/63720392): implement
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (sMatcher.match(uri) != URI_PERMISSIONS) {
            throw new UnsupportedOperationException("update(): unsupported " + uri);
        }

        if (VERBOSE) Log.v(TAG, "update(" + uri + "): " + selection + " = " + values);

        // TODO(b/63720392): implement
        return 0;
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        final String prefix = "  ";

        pw.print("Packages: ");
        try (Cursor cursor = getPackagesCursor()) {
            if (cursor == null) {
                pw.println("N/A");
            } else {
                pw.println(cursor.getCount());
                while (cursor.moveToNext()) {
                    pw.print(prefix); pw.println(cursor.getString(0));
                }
            }
        }

        pw.print("Permissions: ");
        try (Cursor cursor = getPermissionsCursor()) {
            if (cursor == null) {
                pw.println("N/A");
            } else {
                pw.println(cursor.getCount());
                while (cursor.moveToNext()) {
                    pw.print(prefix); pw.print(cursor.getString(0)); pw.print('/');
                    final String uuid = cursor.getString(1);
                    if (uuid != null) {
                        pw.print(uuid); pw.print('>');
                    }
                    pw.print(cursor.getString(2));
                    pw.print(": "); pw.println(cursor.getInt(3) == 1);
                }
            }
        }

        pw.print("Raw permissions: ");
        final ArrayList<Permission> rawPermissions = getAllPermissions(getContext());
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
