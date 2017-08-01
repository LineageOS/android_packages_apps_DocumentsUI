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

import android.media.ExifInterface;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.documentsui.R;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestResources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaViewTest {

    private TestResources mResources;
    private TestTable mTable;
    private Bundle mMetadata;

    @Before
    public void setUp() {
        mResources = TestResources.create();
        mResources.strings.put(R.string.metadata_dimensions_display, "%d x %d, %.1fMP");
        mTable = new TestTable();
        mMetadata = new Bundle();
        TestMetadata.populateExifData(mMetadata);
    }

    /**
     * Test that the updateMetadata method is printing metadata for selected items found in the
     * bundle.
     */
    @Test
    public void testPrintMetadata_BundleTags() throws Exception {
        Bundle exif = mMetadata.getBundle(DocumentsContract.METADATA_EXIF);
        MediaView.showExifData(mTable, mResources, TestEnv.FILE_JPG, exif, null);

        mTable.assertHasRow(R.string.metadata_dimensions, "3840 x 2160, 8.3MP");
        mTable.assertHasRow(R.string.metadata_date_time, "Jan 01, 1970, 12:16 AM");
        mTable.assertHasRow(R.string.metadata_location, "33.995918,  -118.475342");
        mTable.assertHasRow(R.string.metadata_altitude, "1244.0");
        mTable.assertHasRow(R.string.metadata_make, "Google");
        mTable.assertHasRow(R.string.metadata_model, "Pixel");
    }

    /**
     * Bundle only supplies half of the values for the pairs that print in printMetaData. No put
     * method should be called as the correct conditions have not been met.
     * @throws Exception
     */
    @Test
    public void testPrintMetadata_BundlePartialTags() throws Exception {
        Bundle exif = new Bundle();
        exif.putInt(ExifInterface.TAG_IMAGE_WIDTH, 3840);
        exif.putDouble(ExifInterface.TAG_GPS_LATITUDE, 37.7749);

        mMetadata.putBundle(DocumentsContract.METADATA_EXIF, exif);
        MediaView.showExifData(mTable, mResources, TestEnv.FILE_JPG, mMetadata, null);
        mTable.assertEmpty();
    }
}
