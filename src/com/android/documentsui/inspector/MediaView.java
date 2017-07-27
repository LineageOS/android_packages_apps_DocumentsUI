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
import android.media.ExifInterface;
import android.media.MediaMetadata;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.support.annotation.VisibleForTesting;
import android.util.AttributeSet;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.MediaDisplay;
import com.android.documentsui.inspector.InspectorController.TableDisplay;

import javax.annotation.Nullable;

/**
 * Organizes and Displays the debug information about a file. This view
 * should only be made visible when build is debuggable and system policies
 * allow debug "stuff".
 */
public class MediaView extends TableView implements MediaDisplay {

    private static final String METADATA_KEY_AUDIO = "android.media.metadata.audio";
    private static final String METADATA_KEY_VIDEO = "android.media.metadata.video";

    public MediaView(Context context) {
        this(context, null);
    }

    public MediaView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public MediaView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void accept(DocumentInfo doc, Bundle metadata, @Nullable Runnable geoClickListener) {
        setTitle(R.string.inspector_metadata_section);

        Bundle exif = metadata.getBundle(DocumentsContract.METADATA_EXIF);
        if (exif != null) {
            showExifData(this, doc, exif, geoClickListener);
        }

        Bundle video = metadata.getBundle(METADATA_KEY_VIDEO);
        if (video != null) {
            showVideoData(doc, video);
        }

        setVisible(!isEmpty());
    }

    private void showVideoData(DocumentInfo doc, Bundle tags) {
        if (tags.containsKey(MediaMetadata.METADATA_KEY_DURATION)) {
            float seconds = tags.getInt(MediaMetadata.METADATA_KEY_DURATION) / 1000.0f;
            put(R.string.metadata_duration, seconds + "s");
        }
    }

    @VisibleForTesting
    public static void showExifData(
            TableDisplay table,
            DocumentInfo doc,
            Bundle tags,
            @Nullable Runnable geoClickListener) {

        if (tags.containsKey(ExifInterface.TAG_IMAGE_WIDTH)
            && tags.containsKey(ExifInterface.TAG_IMAGE_LENGTH)) {
            int width = tags.getInt(ExifInterface.TAG_IMAGE_WIDTH);
            int height = tags.getInt(ExifInterface.TAG_IMAGE_LENGTH);
            table.put(R.string.metadata_dimensions,
                    String.valueOf(width) + " x " + String.valueOf(height));
        }

        if (tags.containsKey(ExifInterface.TAG_DATETIME)) {
            String date = tags.getString(ExifInterface.TAG_DATETIME);
            table.put(R.string.metadata_date_time, date);
        }

        if (MetadataUtils.hasExifGpsFields(tags)) {
            double[] coords = MetadataUtils.getExifGpsCoords(tags);
            if (geoClickListener != null) {
                table.put(R.string.metadata_location,
                        String.valueOf(coords[0]) + ",  " + String.valueOf(coords[1]),
                        view -> {
                            geoClickListener.run();
                        }
                );
            } else {
                table.put(R.string.metadata_location,
                        String.valueOf(coords[0]) + ",  " + String.valueOf(coords[1]));
            }
        }

        if (tags.containsKey(ExifInterface.TAG_GPS_ALTITUDE)) {
            double altitude = tags.getDouble(ExifInterface.TAG_GPS_ALTITUDE);
            table.put(R.string.metadata_altitude, String.valueOf(altitude));
        }

        if (tags.containsKey(ExifInterface.TAG_MAKE)) {
            String make = tags.getString(ExifInterface.TAG_MAKE);
            table.put(R.string.metadata_make, make);
        }

        if (tags.containsKey(ExifInterface.TAG_MODEL)) {
            String model = tags.getString(ExifInterface.TAG_MODEL);
            table.put(R.string.metadata_model, model);
        }

        if (tags.containsKey(ExifInterface.TAG_APERTURE)) {
            String aperture = String.valueOf(tags.get(ExifInterface.TAG_APERTURE));
            table.put(R.string.metadata_aperture, aperture);
        }

        if (tags.containsKey(ExifInterface.TAG_SHUTTER_SPEED_VALUE)) {
            String shutterSpeed = String.valueOf(
                    formatShutterSpeed(tags.getDouble(ExifInterface.TAG_SHUTTER_SPEED_VALUE)));
            table.put(R.string.metadata_shutter_speed, shutterSpeed);
        }
    }

    /**
     *
     * @param speed a value n, where shutter speed equals 1/(2^n)
     * @return a String containing either a fraction that displays 1 over a positive integer, or a
     * double rounded to one decimal, depending on if 1/(2^n) is less than or greater than 1,
     * respectively.
     */
    private static String formatShutterSpeed(double speed) {
        if (speed <= 0) {
            double shutterSpeed = Math.pow(2, -1 * speed);
            String formattedSpeed = String.valueOf(Math.round(shutterSpeed * 10.0) / 10.0);
            return formattedSpeed;
        } else {
            int approximateSpeedDenom = (int) Math.pow(2, speed) + 1;
            String formattedSpeed = "1/" + String.valueOf(approximateSpeedDenom);
            return formattedSpeed;
        }
    }

}
