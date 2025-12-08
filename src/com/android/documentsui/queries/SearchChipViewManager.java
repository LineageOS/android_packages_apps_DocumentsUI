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

package com.android.documentsui.queries;

import static com.android.documentsui.util.FlagUtils.isSearchV2Enabled;
import static com.android.documentsui.util.FlagUtils.isUseMaterial3FlagEnabled;
import static com.android.documentsui.util.Material3Config.getRes;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.widget.HorizontalScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.IconUtils;
import com.android.documentsui.MetricConsts;
import com.android.documentsui.R;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Shared;
import com.android.documentsui.util.VersionUtils;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.primitives.Ints;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages search chip behavior.
 */
public class SearchChipViewManager {
    private static final int CHIP_MOVE_ANIMATION_DURATION = 250;
    // Defined large file as the size is larger than 10 MB.
    private static final long LARGE_FILE_SIZE_BYTES = 10000000L;

    private static final int TYPE_IMAGES = MetricConsts.TYPE_CHIP_IMAGES;
    private static final int TYPE_DOCUMENTS = MetricConsts.TYPE_CHIP_DOCS;
    private static final int TYPE_AUDIO = MetricConsts.TYPE_CHIP_AUDIOS;
    private static final int TYPE_VIDEOS = MetricConsts.TYPE_CHIP_VIDEOS;
    private static final int TYPE_LARGE_FILES = MetricConsts.TYPE_CHIP_LARGE_FILES;
    private static final int TYPE_FROM_THIS_WEEK = MetricConsts.TYPE_CHIP_FROM_THIS_WEEK;

    private static final ChipComparator CHIP_COMPARATOR = new ChipComparator();

    // we will get the icon drawable with the first mimeType
    public static final String[] IMAGES_MIMETYPES = new String[]{"image/*"};
    public static final String[] VIDEOS_MIMETYPES = new String[]{"video/*"};
    public static final String[] AUDIO_MIMETYPES =
            new String[]{"audio/*", "application/ogg", "application/x-flac"};
    public static final String[] DOCUMENTS_MIMETYPES = MimeTypes.getDocumentMimeTypeArray();
    private static final String[] EMPTY_MIMETYPES = new String[]{""};

    private static final Map<Integer, SearchChipData> sMimeTypesChipItems = new HashMap<>();
    private static final Map<Integer, SearchChipData> sDefaultChipItems = new HashMap<>();

    private final ViewGroup mChipGroup;
    private final List<Integer> mDefaultChipTypes = new ArrayList<>();
    private SearchChipViewManagerListener mListener;
    private String[] mCurrentUpdateMimeTypes;
    private boolean mIsFirstUpdateChipsReady;
    private final List<SearchChipData> mMimeDataStack = new ArrayList<>(4);

    @VisibleForTesting
    Set<SearchChipData> mCheckedChipItems = new HashSet<>();

    static {
        sMimeTypesChipItems.put(
                TYPE_IMAGES,
                new SearchChipData(TYPE_IMAGES, R.string.chip_title_images, IMAGES_MIMETYPES));
        if (VersionUtils.isAtLeastR()) {
            sMimeTypesChipItems.put(
                    TYPE_DOCUMENTS,
                    new SearchChipData(
                            TYPE_DOCUMENTS, R.string.chip_title_documents, DOCUMENTS_MIMETYPES));
        }
        sMimeTypesChipItems.put(
                TYPE_AUDIO,
                new SearchChipData(TYPE_AUDIO, R.string.chip_title_audio, AUDIO_MIMETYPES));
        sMimeTypesChipItems.put(
                TYPE_VIDEOS,
                new SearchChipData(TYPE_VIDEOS, R.string.chip_title_videos, VIDEOS_MIMETYPES));
        sDefaultChipItems.put(
                TYPE_LARGE_FILES,
                new SearchChipData(
                        TYPE_LARGE_FILES, R.string.chip_title_large_files, EMPTY_MIMETYPES));
        sDefaultChipItems.put(
                TYPE_FROM_THIS_WEEK,
                new SearchChipData(
                        TYPE_FROM_THIS_WEEK, R.string.chip_title_from_this_week, EMPTY_MIMETYPES));
    }

    public SearchChipViewManager(@NonNull ViewGroup chipGroup) {
        mChipGroup = chipGroup;
    }

    /**
     * Restore the checked chip items by the saved state.
     *
     * @param savedState the saved state to restore.
     */
    public void restoreCheckedChipItems(Bundle savedState) {
        final int[] chipTypes = savedState.getIntArray(Shared.EXTRA_QUERY_CHIPS);
        if (chipTypes != null) {
            clearCheckedChips();
            for (int chipType : chipTypes) {
                SearchChipData chipData = null;
                if (sMimeTypesChipItems.containsKey(chipType)) {
                    chipData = sMimeTypesChipItems.get(chipType);
                } else {
                    chipData = sDefaultChipItems.get(chipType);
                }

                mCheckedChipItems.add(chipData);
                setCheckedChip(chipData.getChipType());
            }
        }
    }

    /**
     * Set the visibility of the chips row. If the count of chips is less than 2,
     * we will hide the chips row.
     *
     * @param show the value to show/hide the chips row.
     */
    public void setChipsRowVisible(boolean show) {
        // if there is only one matched chip, hide the chip group.
        mChipGroup.setVisibility(show && mChipGroup.getChildCount() > 1 ? View.VISIBLE : View.GONE);
    }

    /**
     * Check Whether the checked item list has contents.
     *
     * @return True, if the checked item list is not empty. Otherwise, return false.
     */
    public boolean hasCheckedItems() {
        return !mCheckedChipItems.isEmpty();
    }

    /**
     * Clear the checked state of Chips and the checked list.
     */
    public void clearCheckedChips() {
        final int count = mChipGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            Chip child = (Chip) mChipGroup.getChildAt(i);
            setChipChecked(child, false /* isChecked */);
        }
        mCheckedChipItems.clear();
    }

    /**
     * Get the query arguments of the checked chips.
     *
     * @return the bundle of query arguments
     */
    public Bundle getCheckedChipQueryArgs() {
        final Bundle queryArgs = new Bundle();
        final ArrayList<String> checkedMimeTypes = new ArrayList<>();
        for (SearchChipData data : mCheckedChipItems) {
            if (data.getChipType() == MetricConsts.TYPE_CHIP_LARGE_FILES) {
                queryArgs.putLong(DocumentsContract.QUERY_ARG_FILE_SIZE_OVER,
                        LARGE_FILE_SIZE_BYTES);
            } else if (data.getChipType() == MetricConsts.TYPE_CHIP_FROM_THIS_WEEK) {
                // Calculate a week ago from now.
                long aWeekAgoFromNowInMillis =
                        LocalDate.now().minusDays(7).atStartOfDay(ZoneId.systemDefault())
                                .toInstant()
                                .toEpochMilli();
                queryArgs.putLong(DocumentsContract.QUERY_ARG_LAST_MODIFIED_AFTER,
                        aWeekAgoFromNowInMillis);
            } else {
                Collections.addAll(checkedMimeTypes, data.getMimeTypes());
            }
        }

        if (!checkedMimeTypes.isEmpty()) {
            queryArgs.putStringArray(DocumentsContract.QUERY_ARG_MIME_TYPES,
                    checkedMimeTypes.toArray(new String[0]));
        }

        return queryArgs;
    }

    /**
     * Called when owning activity is saving state to be used to restore state during creation.
     *
     * @param state Bundle to save state
     */
    public void onSaveInstanceState(Bundle state) {
        List<Integer> checkedChipList = new ArrayList<>();

        for (SearchChipData item : mCheckedChipItems) {
            checkedChipList.add(item.getChipType());
        }

        if (checkedChipList.size() > 0) {
            state.putIntArray(Shared.EXTRA_QUERY_CHIPS, Ints.toArray(checkedChipList));
        }
    }

    /**
     * Initialize the search chips base on the mime types.
     *
     * @param acceptMimeTypes use this values to filter chips
     */
    public void initChipSets(String[] acceptMimeTypes) {
        mDefaultChipTypes.clear();
        for (SearchChipData chipData : sMimeTypesChipItems.values()) {
            final String[] mimeTypes = chipData.getMimeTypes();
            final boolean isMatched = MimeTypes.mimeMatches(acceptMimeTypes, mimeTypes);
            if (isMatched) {
                mDefaultChipTypes.add(chipData.getChipType());
            }
        }
    }

    /**
     * Update the search chips base on the mime types.
     *
     * @param acceptMimeTypes use this values to filter chips
     */
    public void updateChips(String[] acceptMimeTypes) {
        if (mIsFirstUpdateChipsReady && Arrays.equals(mCurrentUpdateMimeTypes, acceptMimeTypes)) {
            return;
        }

        final Context context = mChipGroup.getContext();
        mChipGroup.removeAllViews();

        final List<SearchChipData> mimeChipDataList = new ArrayList<>();
        for (int i = 0; i < mDefaultChipTypes.size(); i++) {
            final SearchChipData chipData = sMimeTypesChipItems.get(mDefaultChipTypes.get(i));
            final String[] mimeTypes = chipData.getMimeTypes();
            final boolean isMatched = MimeTypes.mimeMatches(acceptMimeTypes, mimeTypes);
            if (isMatched) {
                mimeChipDataList.add(chipData);
            }
        }

        final LayoutInflater inflater = LayoutInflater.from(context);
        if (mimeChipDataList.size() > 1) {
            for (int i = 0; i < mimeChipDataList.size(); i++) {
                addChipToGroup(mChipGroup, mimeChipDataList.get(i), inflater);
            }
        }

        for (SearchChipData chipData : sDefaultChipItems.values()) {
            addChipToGroup(mChipGroup, chipData, inflater);
        }

        reorderCheckedChips(null /* clickedChip */, false /* hasAnim */);
        mIsFirstUpdateChipsReady = true;
        mCurrentUpdateMimeTypes = acceptMimeTypes;
    }

    private void addChipToGroup(ViewGroup group, SearchChipData data, LayoutInflater inflater) {
        Chip chip = (Chip) inflater.inflate(getRes(R.layout.search_chip_item), mChipGroup, false);
        bindChip(chip, data);
        group.addView(chip);
    }

    /**
     * Mirror chip group here for another chip group
     *
     * @param chipGroup target view group for mirror
     */
    public void bindMirrorGroup(ViewGroup chipGroup) {
        final int size = mChipGroup.getChildCount();
        if (size <= 1) {
            chipGroup.setVisibility(View.GONE);
            return;
        }

        chipGroup.setVisibility(View.VISIBLE);
        chipGroup.removeAllViews();
        final LayoutInflater inflater = LayoutInflater.from(chipGroup.getContext());
        for (int i = 0; i < size; i++) {
            Chip child = (Chip) mChipGroup.getChildAt(i);
            SearchChipData item = (SearchChipData) child.getTag();
            addChipToGroup(chipGroup, item, inflater);
        }
    }

    /**
     * Click behavior handle here when mirror chip clicked.
     *
     * @param data SearchChipData synced in mirror group
     */
    public void onMirrorChipClick(SearchChipData data) {
        for (int i = 0, size = mChipGroup.getChildCount(); i < size; i++) {
            Chip chip = (Chip) mChipGroup.getChildAt(i);
            if (chip.getTag().equals(data)) {
                chip.setChecked(!chip.isChecked());
                onChipClick(chip);
                return;
            }
        }
    }

    /**
     * Set the listener.
     *
     * @param listener the listener
     */
    public void setSearchChipViewManagerListener(SearchChipViewManagerListener listener) {
        mListener = listener;
    }

    /**
     * Returns the ID of the leading checked chip or null, if no chips are checked.
     */
    public Integer getLeadingMimeChipType() {
        int stackSize = mMimeDataStack.size();
        return !isSearchV2Enabled() || stackSize == 0 ? null
                : mMimeDataStack.get(stackSize - 1).getChipType();
    }

    private static void setChipChecked(Chip chip, boolean isChecked) {
        chip.setChecked(isChecked);
        chip.setChipIconVisible(!isChecked);
    }

    private void setCheckedChip(int chipType) {
        final int count = mChipGroup.getChildCount();
        for (int i = 0; i < count; i++) {
            Chip child = (Chip) mChipGroup.getChildAt(i);
            SearchChipData item = (SearchChipData) child.getTag();
            if (item.getChipType() == chipType) {
                setChipChecked(child, true /* isChecked */);
                break;
            }
        }
    }

    /**
     * Checks if the given integer corresponds to constants representing one of the four
     * generic file types: audio, docs, images or videos. MetricConst collects all sort of
     * constants, so this method is added as a check that we are handing one of MIME type
     * chips and not, for example, file size chip.
     * @param chipType An integer that may or may not be a type constant.
     * @return Whether the given integer corresponds to one of the generic file types.
     */
    private boolean isMimeChip(@MetricConsts.SearchType int chipType) {
        return chipType == MetricConsts.TYPE_CHIP_AUDIOS
                || chipType == MetricConsts.TYPE_CHIP_DOCS
                || chipType == MetricConsts.TYPE_CHIP_IMAGES
                || chipType == MetricConsts.TYPE_CHIP_VIDEOS;
    }

    private void onChipClick(View v) {
        final Chip chip = (Chip) v;

        // We need to show/hide the chip icon in our design.
        // When we show/hide the chip icon or do reorder animation,
        // the ripple effect will be interrupted. So, skip ripple
        // effect when the chip is clicked.
        chip.getBackground().setVisible(false /* visible */, false /* restart */);

        final SearchChipData item = (SearchChipData) chip.getTag();
        int chipType = item.getChipType();
        if (chip.isChecked()) {
            if (mCheckedChipItems.add(item) && isMimeChip(chipType)) {
                mMimeDataStack.add(item);
            }
        } else {
            if (mCheckedChipItems.remove(item) && isMimeChip(chipType)) {
                mMimeDataStack.remove(item);
            }
        }

        setChipChecked(chip, chip.isChecked());
        reorderCheckedChips(chip, true /* hasAnim */);

        if (mListener != null) {
            mListener.onChipCheckStateChanged(v);
        }
    }

    /**
     * When the chip is focused, adding a focus ring indicator using Stroke.
     * TODO(b/381957932): Remove this once Material Chip supports focus ring.
     */
    private void onChipFocusChange(View v, boolean hasFocus) {
        Chip chip = (Chip) v;
        if (hasFocus) {
            final int focusRingWidth =
                    mChipGroup
                            .getResources()
                            .getDimensionPixelSize(getRes(R.dimen.focus_ring_width));
            chip.setChipStrokeWidth(focusRingWidth);
        } else {
            final int strokeWidth =
                    mChipGroup
                            .getResources()
                            .getDimensionPixelSize(
                                    getRes(R.dimen.search_chip_inactive_stroke_width));
            chip.setChipStrokeWidth(strokeWidth);
        }
    }

    private void bindChip(Chip chip, SearchChipData chipData) {
        final Context context = mChipGroup.getContext();
        chip.setTag(chipData);
        chip.setText(context.getString(chipData.getTitleRes()));
        Drawable chipIcon = getChipIcon(chipData);
        chip.setChipIcon(chipIcon);
        chip.setOnClickListener(this::onChipClick);

        if (isUseMaterial3FlagEnabled()) {
            chip.setOnFocusChangeListener(this::onChipFocusChange);
        }

        if (mCheckedChipItems.contains(chipData)) {
            setChipChecked(chip, true);
        }
    }

    private Drawable getChipIcon(SearchChipData chipData) {
        final Context context = mChipGroup.getContext();
        int chipType = chipData.getChipType();
        if (chipType == TYPE_LARGE_FILES) {
            return context.getDrawable(getRes(R.drawable.ic_chip_large_files));
        }
        if (chipType == TYPE_FROM_THIS_WEEK) {
            return context.getDrawable(getRes(R.drawable.ic_chip_from_this_week));
        }

        // When use_material3 flag is ON, we don't want to use MIME type icons for
        // image/audio/video/document from the system.
        if (isUseMaterial3FlagEnabled()) {
            switch (chipType) {
                case TYPE_IMAGES:
                    return context.getDrawable(getRes(R.drawable.ic_chip_image));
                case TYPE_AUDIO:
                    return context.getDrawable(getRes(R.drawable.ic_chip_audio));
                case TYPE_VIDEOS:
                    return context.getDrawable(getRes(R.drawable.ic_chip_video));
                case TYPE_DOCUMENTS:
                    return context.getDrawable(getRes(R.drawable.ic_chip_document));
                default:
                    return null;
            }
        }

        if (chipType == TYPE_DOCUMENTS) {
            return IconUtils.loadMimeIcon(context, MimeTypes.GENERIC_TYPE);
        }
        // get the icon drawable with the first mimeType in chipData
        return IconUtils.loadMimeIcon(context, chipData.getMimeTypes()[0]);
    }

    /**
     * Reorder the chips in chip group. The checked chip has higher order.
     *
     * @param clickedChip the clicked chip, may be null.
     * @param hasAnim     if true, play move animation. Otherwise, not.
     */
    private void reorderCheckedChips(@Nullable Chip clickedChip, boolean hasAnim) {
        boolean supportChipMove = mChipGroup.getResources().getBoolean(
                getRes(R.bool.move_search_chip_when_selected));
        if (!supportChipMove) {
            return;
        }

        final ArrayList<Chip> chipList = new ArrayList<>();
        final int count = mChipGroup.getChildCount();

        // if the size of chips is less than 2, no need to reorder chips
        if (count < 2) {
            return;
        }

        Chip item;
        // get the default order
        for (int i = 0; i < count; i++) {
            item = (Chip) mChipGroup.getChildAt(i);
            chipList.add(item);
        }

        // sort chips
        Collections.sort(chipList, CHIP_COMPARATOR);

        if (isChipOrderMatched(mChipGroup, chipList)) {
            // the order of chips is not changed
            return;
        }

        final int chipSpacing =
                isUseMaterial3FlagEnabled()
                        ? ((ChipGroup) mChipGroup).getChipSpacingHorizontal()
                        : mChipGroup
                                .getResources()
                                .getDimensionPixelSize(getRes(R.dimen.search_chip_spacing));
        final boolean isRtl = mChipGroup.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
        final float chipGroupPaddingStart =
                isUseMaterial3FlagEnabled()
                        ? mChipGroup.getPaddingStart()
                        : mChipGroup
                                .getResources()
                                .getDimensionPixelSize(getRes(R.dimen.search_chip_half_spacing));
        float lastX = isRtl ? mChipGroup.getWidth() - chipGroupPaddingStart : chipGroupPaddingStart;

        // remove all chips except current clicked chip to avoid losing
        // accessibility focus.
        for (int i = count - 1; i >= 0; i--) {
            item = (Chip) mChipGroup.getChildAt(i);
            if (!item.equals(clickedChip)) {
                mChipGroup.removeView(item);
            }
        }

        // add sorted chips
        for (int i = 0; i < count; i++) {
            item = chipList.get(i);
            if (!item.equals(clickedChip)) {
                mChipGroup.addView(item, i);
            }
        }

        if (hasAnim && mChipGroup.isAttachedToWindow()) {
            // start animation
            for (Chip chip : chipList) {
                if (isRtl) {
                    lastX -= chip.getMeasuredWidth();
                }

                ObjectAnimator animator = ObjectAnimator.ofFloat(chip, "x", chip.getX(), lastX);

                if (isRtl) {
                    lastX -= chipSpacing;
                } else {
                    lastX += chip.getMeasuredWidth() + chipSpacing;
                }
                animator.setDuration(CHIP_MOVE_ANIMATION_DURATION);
                animator.start();
            }

            // Let the first checked chip can be shown.
            View parent = (View) mChipGroup.getParent().getParent();
            if (parent instanceof HorizontalScrollView) {
                final int scrollToX = isRtl ? parent.getWidth() : 0;
                ((HorizontalScrollView) parent).smoothScrollTo(scrollToX, 0);
                if (mChipGroup.getChildCount() > 0) {
                    mChipGroup.getChildAt(0)
                            .sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                }
            }
        }
    }

    private static boolean isChipOrderMatched(ViewGroup chipGroup, ArrayList<Chip> chipList) {
        if (chipGroup == null || chipList == null) {
            return false;
        }

        final int chipCount = chipList.size();
        if (chipGroup.getChildCount() != chipCount) {
            return false;
        }
        for (int i = 0; i < chipCount; i++) {
            if (!chipList.get(i).equals(chipGroup.getChildAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The listener of SearchChipViewManager.
     */
    public interface SearchChipViewManagerListener {
        /**
         * It will be triggered when the checked state of chips changes.
         */
        void onChipCheckStateChanged(View v);
    }

    private static class ChipComparator implements Comparator<Chip> {

        @Override
        public int compare(Chip lhs, Chip rhs) {
            return (lhs.isChecked() == rhs.isChecked()) ? 0 : (lhs.isChecked() ? -1 : 1);
        }
    }
}
