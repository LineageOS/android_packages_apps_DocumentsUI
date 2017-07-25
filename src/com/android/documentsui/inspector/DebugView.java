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
package com.android.documentsui.inspector;

import android.annotation.StringRes;
import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;

import java.util.function.Consumer;

/**
 * Organizes and Displays the basic details about a file
 */
public class DebugView extends TableView implements Consumer<DocumentInfo> {

    private final Resources mRes;

    public DebugView(Context context) {
        this(context, null);
    }

    public DebugView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DebugView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mRes = context.getResources();
    }

    @Override
    public void accept(DocumentInfo info) {
        setTitle(R.string.inspector_debug_section);

        put(R.string.debug_content_uri, info.derivedUri.toString());
        put(R.string.debug_document_id, info.documentId);
        put(R.string.debug_mimetype, info.mimeType);
        put(R.string.debug_is_archive, info.isArchive());
        put(R.string.debug_is_container, info.isContainer());
        put(R.string.debug_is_partial, info.isPartial());
        put(R.string.debug_is_virtual, info.isVirtual());
        put(R.string.debug_supports_create, info.isCreateSupported());
        put(R.string.debug_supports_delete, info.isDeleteSupported());
        put(R.string.debug_supports_metadata, info.isMetadataSupported());
        put(R.string.debug_supports_rename, info.isRenameSupported());
        put(R.string.debug_supports_settings, info.isSettingsSupported());
        put(R.string.debug_supports_thumbnail, info.isThumbnailSupported());
        put(R.string.debug_supports_weblink, info.isWeblinkSupported());
        put(R.string.debug_supports_write, info.isWriteSupported());
    }

    private void put(@StringRes int key, boolean value) {
        KeyValueRow row = put(mRes.getString(key), String.valueOf(value));
        TextView valueView = ((TextView) row.findViewById(R.id.table_row_value));
        valueView.setTextColor(value ? 0xFF006400 : 0xFF9A2020);
    }
}
