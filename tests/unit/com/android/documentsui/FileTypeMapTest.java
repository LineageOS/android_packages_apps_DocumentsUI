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

package com.android.documentsui;

import static junit.framework.Assert.assertEquals;

import android.annotation.StringRes;
import android.content.Context;
import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class FileTypeMapTest {

    private Resources mRes;
    private FileTypeMap mMap;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        mRes = context.getResources();
        mMap = new FileTypeMap(context);
    }

    @Test
    public void testPlainTextType() {
        String expected = mRes.getString(R.string.txt_file_type);
        assertEquals(expected, mMap.lookup("text/plain"));
    }

    @Test
    public void testPortableDocumentFormatType() {
        String expected = mRes.getString(R.string.pdf_file_type);
        assertEquals(expected, mMap.lookup("application/pdf"));
    }

    @Test
    public void testMsWordType() {
        String expected = mRes.getString(R.string.word_file_type);
        assertEquals(expected, mMap.lookup("application/msword"));
    }

    @Test
    public void testGoogleDocType() {
        String expected = mRes.getString(R.string.gdoc_file_type);
        assertEquals(expected, mMap.lookup("application/vnd.google-apps.document"));
    }

    @Test
    public void testZipType() {
        String expected = getExtensionType(R.string.archive_file_type, "Zip");
        assertEquals(expected, mMap.lookup("application/zip"));
    }

    @Test
    public void testMp3Type() {
        String expected = getExtensionType(R.string.audio_extension_file_type, "MP3");
        assertEquals(expected, mMap.lookup("audio/mpeg"));
    }

    @Test
    public void testMkvType() {
        String expected = getExtensionType(R.string.video_extension_file_type, "AVI");
        assertEquals(expected, mMap.lookup("video/avi"));
    }

    @Test
    public void testJpgType() {
        String expected = getExtensionType(R.string.image_extension_file_type, "JPG");
        assertEquals(expected, mMap.lookup("image/jpeg"));
    }

    @Test
    public void testOggType() {
        String expected = getExtensionType(R.string.audio_extension_file_type, "OGG");
        assertEquals(expected, mMap.lookup("application/ogg"));
    }

    @Test
    public void testFlacType() {
        String expected = getExtensionType(R.string.audio_extension_file_type, "FLAC");
        assertEquals(expected, mMap.lookup("application/x-flac"));
    }

    private String getExtensionType(@StringRes int formatStringId, String extension) {
        String format = mRes.getString(formatStringId);
        return String.format(format, extension);
    }
}
