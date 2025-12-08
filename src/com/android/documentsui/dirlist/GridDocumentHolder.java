/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.base.DocumentInfo.getCursorInt;
import static com.android.documentsui.base.DocumentInfo.getCursorLong;
import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.util.FlagUtils.isSingleClickToSelectEnabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.provider.DocumentsContract.Document;
import android.text.format.Formatter;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

import com.android.documentsui.ConfigStore;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Events;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.UserId;
import com.android.documentsui.roots.RootCursorWrapper;
import com.android.documentsui.ui.Views;
import com.android.modules.utils.build.SdkLevel;

import com.google.android.material.card.MaterialCardView;

import java.util.Map;
import java.util.function.Function;

final class GridDocumentHolder extends DocumentHolder {

    final TextView mTitle;
    final TextView mDate;
    final TextView mDetails;
    // Non-null only when useMaterial3 flag is ON.
    final @Nullable TextView mBullet;
    final ImageView mIconMimeLg;
    // Null when useMaterial3 flag is ON.
    final @Nullable ImageView mIconMimeSm;
    final ImageView mIconThumb;
    // Non-null only when useMaterial3 flag is ON.
    final @Nullable ImageView mSelectionCircle;
    final ImageView mIconCheck;
    final ImageView mIconBadge;
    final IconHelper mIconHelper;
    // Null when useMaterial3 flag is ON.
    final @Nullable View mIconLayout;
    final View mPreviewIcon;
    boolean mHasSelectionRegion;

    // This is used in as a convenience in our bind method.
    private final DocumentInfo mDoc = new DocumentInfo();

    // Non-null only when useMaterial3 flag is ON.
    private final @Nullable MaterialCardView mIconWrapper;

    GridDocumentHolder(Context context, ViewGroup parent, IconHelper iconHelper,
            ConfigStore configStore) {
        super(context, parent, getRes(R.layout.item_doc_grid), configStore);

        if (isUseMaterial3FlagEnabled()) {
            mBullet = itemView.findViewById(getRes(R.id.bullet));
            mIconWrapper = itemView.findViewById(getRes(R.id.icon_wrapper));
            mSelectionCircle = (ImageView) itemView.findViewById(getRes(R.id.selection_circle));
            mIconLayout = null;
            mIconMimeSm = null;
        } else {
            mBullet = null;
            mIconWrapper = null;
            mSelectionCircle = null;
            mIconLayout = itemView.findViewById(getRes(R.id.icon));
            mIconMimeSm = (ImageView) itemView.findViewById(getRes(R.id.icon_mime_sm));
        }

        mTitle = (TextView) itemView.findViewById(android.R.id.title);
        mDate = (TextView) itemView.findViewById(getRes(R.id.date));
        mDetails = (TextView) itemView.findViewById(getRes(R.id.details));
        mIconCheck = (ImageView) itemView.findViewById(getRes(R.id.icon_check));
        mIconMimeLg = (ImageView) itemView.findViewById(getRes(R.id.icon_mime_lg));
        mIconThumb = (ImageView) itemView.findViewById(getRes(R.id.icon_thumb));
        mIconBadge = (ImageView) itemView.findViewById(getRes(R.id.icon_profile_badge));
        mPreviewIcon = itemView.findViewById(getRes(R.id.preview_icon));
        mIconHelper = iconHelper;

        if (SdkLevel.isAtLeastT() && !mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
            setUpdatableWorkProfileIcon(context);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private void setUpdatableWorkProfileIcon(Context context) {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        Drawable drawable =
                dpm.getResources()
                        .getDrawable(
                                WORK_PROFILE_ICON,
                                SOLID_COLORED,
                                () -> context.getDrawable(getRes(R.drawable.ic_briefcase)));
        mIconBadge.setImageDrawable(drawable);
    }

    @Override
    public void setSelected(boolean selected, boolean animate) {
        float checkAlpha = selected ? 1f : 0f;
        float circleAlpha = (selected || !isUseMaterial3FlagEnabled()) ? 0f : 1f;
        // We always want to make sure our check box disappears if we're not selected,
        // even if the item is disabled. This is because this object can be reused
        // and this method will be called to setup initial state.
        if (animate) {
            if (!isUseMaterial3FlagEnabled()) {
                fade(mIconMimeSm, checkAlpha).start();
            }
            if (mHasSelectionRegion) {
                fade(mSelectionCircle, circleAlpha).start();
            }
            fade(mIconCheck, checkAlpha).start();
        } else {
            if (mHasSelectionRegion) {
                mSelectionCircle.setAlpha(circleAlpha);
            }
            mIconCheck.setAlpha(checkAlpha);

        }


        // But it should be an error to be set to selected && be disabled.
        if (!itemView.isEnabled()) {
            assert (!selected);
        }

        super.setSelected(selected, animate);

        if (!isUseMaterial3FlagEnabled()) {
            if (animate) {
                fade(mIconMimeSm, 1f - checkAlpha).start();
            } else {
                mIconMimeSm.setAlpha(1f - checkAlpha);
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);

        float imgAlpha = enabled ? 1f : DISABLED_ALPHA;

        if (isUseMaterial3FlagEnabled()) {
            itemView.setAlpha(imgAlpha);
        } else {
            mIconMimeLg.setAlpha(imgAlpha);
            mIconMimeSm.setAlpha(imgAlpha);
            mIconThumb.setAlpha(imgAlpha);
        }
    }

    @Override
    public void bindPreviewIcon(boolean show, Function<View, Boolean> clickCallback) {
        if (isUseMaterial3FlagEnabled() && mDoc.isDirectory()) {
            mPreviewIcon.setVisibility(View.GONE);
            return;
        }
        mPreviewIcon.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            mPreviewIcon.setContentDescription(
                    getPreviewIconContentDescription(
                            mIconHelper.shouldShowBadge(mDoc.userId.getIdentifier()),
                            mDoc.displayName, mDoc.userId));
            mPreviewIcon.setAccessibilityDelegate(
                    new PreviewAccessibilityDelegate(clickCallback));
        }
    }

    @Override
    public void bindBriefcaseIcon(boolean show) {
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    @RequiresApi(Build.VERSION_CODES.S)
    public void bindProfileIcon(boolean show, int userIdIdentifier) {
        Map<UserId, Drawable> userIdToBadgeMap = DocumentsApplication.getUserManagerState(
                mContext).getUserIdToBadgeMap();
        Drawable drawable = userIdToBadgeMap.get(UserId.of(userIdIdentifier));
        mIconBadge.setImageDrawable(drawable);
        mIconBadge.setVisibility(show ? View.VISIBLE : View.GONE);
        mIconBadge.setContentDescription(mIconHelper.getProfileLabel(userIdIdentifier));
    }

    @Override
    public boolean inDragRegion(MotionEvent event) {
        // Entire grid box should be draggable
        return true;
    }

    @Override
    public int classifySelectionHotspot(MotionEvent event) {
        if (!isUseMaterial3FlagEnabled()) {
            return Views.isEventOver(event, itemView.getParent(), mIconLayout)
                ? ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI
                : ItemDetails.SELECTION_HOTSPOT_OUTSIDE;

        } else if (!mHasSelectionRegion) {
            return ItemDetails.SELECTION_HOTSPOT_OUTSIDE;

        } else if (Views.isEventOver(event, itemView.getParent(), mSelectionCircle)) {
            return ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_MULTI;

        } else if (Events.isMousyEvent(event) && isSingleClickToSelectEnabled()) {
            return ItemDetails.SELECTION_HOTSPOT_INSIDE_TOGGLE_SOLO;
        }

        return ItemDetails.SELECTION_HOTSPOT_OUTSIDE;
    }

    @Override
    public boolean inPreviewIconRegion(MotionEvent event) {
        return Views.isEventOver(event, itemView.getParent(), mPreviewIcon);
    }

    /**
     * Bind this view to the given document for display.
     *
     * @param cursor  Pointing to the item to be bound.
     * @param modelId The model ID of the item.
     */
    @Override
    public void bind(Cursor cursor, String modelId) {
        assert (cursor != null);

        mModelId = modelId;

        mDoc.updateFromCursor(cursor,
                UserId.of(getCursorInt(cursor, RootCursorWrapper.COLUMN_USER_ID)),
                getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY));

        // Only have a selection region when the Material3 flag is on and if this is in non-browsing
        // mode, the item must not be a folder.
        mHasSelectionRegion =
                isUseMaterial3FlagEnabled() && (!mDoc.isDirectory()
                        || mAction == State.ACTION_BROWSE);

        if (isUseMaterial3FlagEnabled() && !mHasSelectionRegion) {
            mSelectionCircle.setVisibility(View.GONE);
        }

        mIconHelper.stopLoading(mIconThumb);

        mIconMimeLg.animate().cancel();
        mIconMimeLg.setAlpha(1f);
        mIconThumb.animate().cancel();
        mIconThumb.setAlpha(0f);

        mIconHelper.load(mDoc, mIconThumb, mIconMimeLg, mIconMimeSm);

        if (isUseMaterial3FlagEnabled()) {
            // Only Normal type works with ellipsize=middle.
            mTitle.setText(mDoc.displayName, TextView.BufferType.NORMAL);
        } else {
            mTitle.setText(mDoc.displayName, TextView.BufferType.SPANNABLE);
        }
        mTitle.setVisibility(View.VISIBLE);
        // Show the full name in a tooltip.
        itemView.setTooltipText(mDoc.displayName);

        // If file is partial, we want to show summary field as that's more relevant than fileSize
        // and date
        if (mDoc.isPartial()) {
            final String docSummary = getCursorString(cursor, Document.COLUMN_SUMMARY);
            mDetails.setVisibility(View.VISIBLE);
            if (isUseMaterial3FlagEnabled()) {
                mDate.setVisibility(View.GONE);
            } else {
                mDate.setText(null);
            }
            mDetails.setText(docSummary);
        } else {
            if (mDoc.lastModified == -1) {
                if (isUseMaterial3FlagEnabled()) {
                    mDate.setVisibility(View.GONE);
                } else {
                    mDate.setText(null);
                }
            } else {
                mDate.setText(Shared.formatTime(mContext, mDoc.lastModified));
            }

            final long docSize = getCursorLong(cursor, Document.COLUMN_SIZE);
            if (mDoc.isDirectory() || docSize == -1) {
                mDetails.setVisibility(View.GONE);
            } else {
                mDetails.setVisibility(View.VISIBLE);
                mDetails.setText(Formatter.formatFileSize(mContext, docSize));
            }
        }

        if (mBullet != null && (mDetails.getText() == null || mDetails.getText().length() == 0
                || mDate.getText() == null || mDate.getText().length() == 0)) {
            // There is no need for the bullet separating the details and date.
            mBullet.setVisibility(View.GONE);
        }
    }
}
