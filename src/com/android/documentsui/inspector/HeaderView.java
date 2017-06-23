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

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.R;
import java.util.function.Consumer;

/**
 * Organizes and displays the title and thumbnail for a given document
 */
public final class HeaderView extends RelativeLayout implements Consumer<DocumentInfo> {

    private final View mHeader;
    private final TextView mTitle;

    public HeaderView(Context context) {
        this(context, null);
    }

    public HeaderView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HeaderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        mHeader = inflater.inflate(R.layout.inspector_header, null);
        mTitle = (TextView) mHeader.findViewById(R.id.inspector_file_title);
    }

    @Override
    public void accept(DocumentInfo info) {
        if (!hasHeader()) {
            addView(mHeader);
        }
        mTitle.setText(info.displayName);
    }

    private boolean hasHeader() {
        for (int i = 0; i < getChildCount(); i++) {
            if (getChildAt(i).equals(mHeader)) {
                return true;
            }
        }
        return false;
    }
}
