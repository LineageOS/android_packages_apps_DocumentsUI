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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.State.MODE_GRID;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.documentsui.R;
import com.android.documentsui.base.State.ViewMode;

/**
 * RecyclerView.ViewHolder class that displays at the top of the directory list when there
 * are more information from the Provider.
 * Used by {@link DirectoryAddonsAdapter}.
 */
final class HeaderMessageDocumentHolder extends MessageHolder {
    private final View mRoot;
    private final ImageView mIcon;
    private final TextView mTextView;
    private final Button mButton;
    private Message mMessage;

    public HeaderMessageDocumentHolder(Context context, ViewGroup parent) {
        super(context, parent, R.layout.item_doc_header_message);

        mRoot = itemView.findViewById(R.id.item_root);
        mIcon = (ImageView) itemView.findViewById(R.id.message_icon);
        mTextView = (TextView) itemView.findViewById(R.id.message_textview);
        mButton = (Button) itemView.findViewById(R.id.button_dismiss);
    }

    public void bind(Message message) {
        mMessage = message;
        mButton.setOnClickListener(this::onButtonClick);
        bind(null, null);
    }

    /**
     * We set different padding on directory parent in different mode by
     * {@link DirectoryFragment#onViewModeChanged()}. To avoid the layout shifting when
     * display mode changed, we set the opposite padding on the item.
     */
    public void setPadding(@ViewMode int mode) {
        int padding = itemView.getResources().getDimensionPixelSize(mode == MODE_GRID
                ? R.dimen.list_container_padding : R.dimen.grid_container_padding);
        mRoot.setPadding(padding, 0, padding, 0);
    }

    private void onButtonClick(View button) {
        mMessage.runCallback();
    }

    @Override
    public void bind(Cursor cursor, String modelId) {
        mTextView.setText(mMessage.getMessageString());
        mIcon.setImageDrawable(mMessage.getIcon());
        if (mMessage.getButtonString() != null) {
            mButton.setText(mMessage.getButtonString());
        }
    }
}