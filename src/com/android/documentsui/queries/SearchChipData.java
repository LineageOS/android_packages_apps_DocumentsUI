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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;

/**
 * A data class stored data which search chip row required.
 * Used by {@link SearchChipViewManager}.
 */
public class SearchChipData {

    private final int mChipType;
    private final int mTitleRes;
    private final String[] mMimeTypes;

    public SearchChipData(int chipType, int titleRes, String[] mimeTypes) {
        mChipType = chipType;
        mTitleRes = titleRes;
        mMimeTypes = mimeTypes;
    }

    public final int getTitleRes() {
        return mTitleRes;
    }

    public final String[] getMimeTypes() {
        return mMimeTypes;
    }

    public final int getChipType() {
        return mChipType;
    }

    /**
     * Returns if the given object is equal to this object. Only chip type and title resource
     * are used for comparison.
     *
     * @param o The object to be compared to this object.
     * @return Whether or not the objects are equal.
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof SearchChipData)) return false;
        SearchChipData that = (SearchChipData) o;
        return mChipType == that.mChipType && mTitleRes == that.mTitleRes
                && new HashSet<>(Arrays.asList(mMimeTypes)).equals(
                        new HashSet<>(Arrays.asList(that.mMimeTypes)));
    }

    /**
     * Returns the hash code of this object. Only chip type and title resource
     * are used for hash computations.
     *
     * @return The hash code of this object.
     */
    @Override
    public int hashCode() {
        return Objects.hash(mChipType, mTitleRes, new HashSet<>(Arrays.asList(mMimeTypes)));
    }
}
