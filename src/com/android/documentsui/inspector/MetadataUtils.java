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

final class MetadataUtils {

    private MetadataUtils() {}

    static boolean hasExifGpsFields(Bundle exif) {
        return (exif.containsKey(ExifInterface.TAG_GPS_LATITUDE)
                && exif.containsKey(ExifInterface.TAG_GPS_LONGITUDE)
                && exif.containsKey(ExifInterface.TAG_GPS_LATITUDE_REF)
                && exif.containsKey(ExifInterface.TAG_GPS_LONGITUDE_REF));
    }

    static double[] getExifGpsCoords(Bundle exif) {
        String lat = exif.getString(ExifInterface.TAG_GPS_LATITUDE);
        String lon = exif.getString(ExifInterface.TAG_GPS_LONGITUDE);
        String latRef = exif.getString(ExifInterface.TAG_GPS_LATITUDE_REF);
        String lonRef = exif.getString(ExifInterface.TAG_GPS_LONGITUDE_REF);

        double round = 1000000.0;

        double[] coordinates = new double[2];

        coordinates[0] = Math.round(
                ExifInterface.convertRationalLatLonToFloat(lat, latRef) * round) / round;
        coordinates[1] = Math.round(
                ExifInterface.convertRationalLatLonToFloat(lon, lonRef) * round) / round;

        return coordinates;
    }
}
