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

final class TestMetadata {
    private TestMetadata() {}

    static void populateExifData(Bundle container) {
        Bundle exif = new Bundle();
        exif.putInt(ExifInterface.TAG_IMAGE_WIDTH, 3840);
        exif.putInt(ExifInterface.TAG_IMAGE_LENGTH, 2160);
        exif.putString(ExifInterface.TAG_DATETIME, "Jan 01, 1970, 12:16 AM");
        exif.putString(ExifInterface.TAG_GPS_LATITUDE, "33/1,59/1,4530/100");
        exif.putString(ExifInterface.TAG_GPS_LONGITUDE, "118/1,28/1,3124/100");
        exif.putString(ExifInterface.TAG_GPS_LATITUDE_REF, "N");
        exif.putString(ExifInterface.TAG_GPS_LONGITUDE_REF, "W");
        exif.putDouble(ExifInterface.TAG_GPS_ALTITUDE, 1244);
        exif.putString(ExifInterface.TAG_MAKE, "Google");
        exif.putString(ExifInterface.TAG_MODEL, "Pixel");
        exif.putDouble(ExifInterface.TAG_SHUTTER_SPEED_VALUE, 6.643);
        exif.putDouble(ExifInterface.TAG_APERTURE, 2.0);
        container.putBundle(DocumentsContract.METADATA_EXIF, exif);
    }
}
