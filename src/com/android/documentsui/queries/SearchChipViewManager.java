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

import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.HorizontalScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.IconUtils;
import com.android.documentsui.R;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Shared;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.common.primitives.Ints;

import java.util.ArrayList;
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

    private static final int TYPE_IMAGES = 0;
    private static final int TYPE_DOCUMENTS = 1;
    private static final int TYPE_AUDIO = 2;
    private static final int TYPE_VIDEOS = 3;

    private static final ChipComparator CHIP_COMPARATOR = new ChipComparator();

    // we will get the icon drawable with the first mimeType
    private static final String[] IMAGES_MIMETYPES = new String[]{"image/*"};
    private static final String[] VIDEOS_MIMETYPES = new String[]{"video/*"};
    private static final String[] AUDIO_MIMETYPES =
            new String[]{"audio/*", "application/ogg", "application/x-flac"};
    private static final String[] DOCUMENTS_MIMETYPES = new String[]{"application/*", "text/*"};

    private static final Map<Integer, SearchChipData> sChipItems = new HashMap<>();

    private final ChipGroup mChipGroup;
    private SearchChipViewManagerListener mListener;

    @VisibleForTesting
    Set<SearchChipData> mCheckedChipItems = new HashSet<>();

    static {
        sChipItems.put(TYPE_IMAGES,
                new SearchChipData(TYPE_IMAGES, R.string.chip_title_images, IMAGES_MIMETYPES));
        sChipItems.put(TYPE_DOCUMENTS,
                new SearchChipData(TYPE_DOCUMENTS, R.string.chip_title_documents,
                        DOCUMENTS_MIMETYPES));
        sChipItems.put(TYPE_AUDIO,
                new SearchChipData(TYPE_AUDIO, R.string.chip_title_audio, AUDIO_MIMETYPES));
        sChipItems.put(TYPE_VIDEOS,
                new SearchChipData(TYPE_VIDEOS, R.string.chip_title_videos, VIDEOS_MIMETYPES));
    }



    public SearchChipViewManager(@NonNull ChipGroup chipGroup) {
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
                final SearchChipData chipData = sChipItems.get(chipType);
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
     * Get the mime types of checked chips
     *
     * @return the string array of mime types
     */
    public String[] getCheckedMimeTypes() {
        final ArrayList<String> args = new ArrayList<>();
        for (SearchChipData data : mCheckedChipItems) {
            for (String mimeType : data.getMimeTypes()) {
                args.add(mimeType);
            }
        }
        return args.toArray(new String[0]);
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
     * Update the search chips base on the mime types.
     *
     * @param acceptMimeTypes use this values to filter chips
     */
    public void updateChips(String[] acceptMimeTypes) {
        final Context context = mChipGroup.getContext();
        mChipGroup.removeAllViews();

        final LayoutInflater inflater = LayoutInflater.from(context);
        for (SearchChipData chipData : sChipItems.values()) {
            final String[] mimeTypes = chipData.getMimeTypes();
            final boolean isMatched = MimeTypes.mimeMatches(acceptMimeTypes, mimeTypes);
            if (isMatched) {
                Chip chip = (Chip) inflater.inflate(R.layout.search_chip_item, mChipGroup, false);
                bindChip(chip, chipData);
                mChipGroup.addView(chip);
            }
        }
        reorderCheckedChips(false /* hasAnim */);
    }


    /**
     * Set the listener.
     *
     * @param listener the listener
     */
    public void setSearchChipViewManagerListener(SearchChipViewManagerListener listener) {
        mListener = listener;
    }

    private static void setChipChecked(Chip chip, boolean isChecked) {
        chip.setChecked(isChecked);
        chip.setCheckedIconVisible(isChecked);
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

    private void onChipClick(View v) {
        final Chip chip = (Chip) v;
        final SearchChipData item = (SearchChipData) chip.getTag();
        if (chip.isChecked()) {
            mCheckedChipItems.add(item);
        } else {
            mCheckedChipItems.remove(item);
        }

        setChipChecked(chip, chip.isChecked());
        reorderCheckedChips(true /* hasAnim */);
        if (mListener != null) {
            mListener.onChipCheckStateChanged();
        }
    }

    private void bindChip(Chip chip, SearchChipData chipData) {
        chip.setTag(chipData);
        chip.setText(mChipGroup.getContext().getString(chipData.getTitleRes()));
        // get the icon drawable with the first mimeType
        chip.setChipIcon(
                IconUtils.loadMimeIcon(mChipGroup.getContext(), chipData.getMimeTypes()[0]));
        chip.setOnClickListener(this::onChipClick);

        if (mCheckedChipItems.contains(chipData)) {
            setChipChecked(chip, true);
        }
    }

    /**
     * Reorder the chips in chip group. The checked chip has higher order.
     *
     * @param hasAnim if true, play move animation. Otherwise, not.
     */
    private void reorderCheckedChips(boolean hasAnim) {
        final ArrayList<Chip> chipList = new ArrayList<>();
        final int count = mChipGroup.getChildCount();
        final boolean playAnimation = hasAnim && mChipGroup.isAttachedToWindow();
        final Map<String, Float> originalXList = new HashMap<>();
        Chip item;
        for (int i = 0; i < count; i++) {
            item = (Chip) mChipGroup.getChildAt(i);
            chipList.add(item);
            if (playAnimation) {
                originalXList.put(item.getText().toString(), item.getX());
            }
        }

        final int chipSpacing = mChipGroup.getChipSpacingHorizontal();
        float lastX = chipList.get(0).getX();
        Collections.sort(chipList, CHIP_COMPARATOR);

        mChipGroup.removeAllViews();
        for (Chip chip : chipList) {
            mChipGroup.addView(chip);
            if (playAnimation) {
                ObjectAnimator animator = ObjectAnimator.ofFloat(chip, "x",
                        originalXList.get(chip.getText().toString()), lastX);
                animator.setDuration(CHIP_MOVE_ANIMATION_DURATION);
                animator.start();
            }
            lastX += chipSpacing + chip.getMeasuredWidth();
        }

        if (playAnimation) {
            // Let the first checked chip can be seen.
            View parent = (View) mChipGroup.getParent();
            if (parent != null && parent instanceof HorizontalScrollView) {
                ((HorizontalScrollView) mChipGroup.getParent()).smoothScrollTo(0, 0);
            }
        }
    }

    /**
     * The listener of SearchChipViewManager.
     */
    public interface SearchChipViewManagerListener {
        /**
         * It will be triggered when the checked state of chips changes.
         */
        void onChipCheckStateChanged();
    }

    private static class ChipComparator implements Comparator<Chip> {

        @Override
        public int compare(Chip lhs, Chip rhs) {
            return (lhs.isChecked() == rhs.isChecked()) ? 0 : (lhs.isChecked() ? -1 : 1);
        }
    }
}
