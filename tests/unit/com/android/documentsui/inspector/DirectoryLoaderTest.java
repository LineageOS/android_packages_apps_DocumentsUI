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

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import com.android.documentsui.InspectorProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.testing.TestConsumer;
import com.android.documentsui.testing.TestEnv;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This test relies the inspector providers top folder in inspector root.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class DirectoryLoaderTest {

    private static final String DIR_TOP = "Top";
    private static final String DIR_BOTTOM = "Bottom";
    private static final String NOT_DIRECTORY = "OpenInProviderTest";

    private Context mContext;
    private ContentResolver mResolver;
    private TestEnv mEnv;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mResolver = mContext.getContentResolver();
        mEnv = TestEnv.create();

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    /**
     * Test the folder named top in the inspector provider. The Top folder should have 4 children
     * and a size 0f 900B.
     *
     * @throws Exception
     */
    @Test
    public void testTopDirectory() throws Exception {
        TestConsumer<DocumentInfo> consumer = testPrep(DIR_TOP);
        mEnv.beforeAsserts();

        assertNotNull(consumer.getLastValue());
        assertEquals(consumer.getLastValue().size, 900);
        assertEquals(consumer.getLastValue().numberOfChildren, 4);
    }

    /**
     * Test the folder named bottom in the inspector provider. The Bottom folder should have 3
     * Children and a size of 300B.
     *
     * @throws Exception
     */
    @Test
    public void testBottomDirectory() throws Exception {
        TestConsumer<DocumentInfo> consumer = testPrep(DIR_BOTTOM);
        mEnv.beforeAsserts();

        assertNotNull(consumer.getLastValue());
        assertEquals(consumer.getLastValue().size, 300);
        assertEquals(consumer.getLastValue().numberOfChildren, 3);
    }

    /**
     * Test a file that's not a directory.
     *
     * @throws Exception
     */
    @Test
    public void testNotDirectory() throws Exception {
        TestConsumer<DocumentInfo> consumer = testPrep(NOT_DIRECTORY);
        mEnv.beforeAsserts();

        assertNull(consumer.getLastValue());
    }

    private TestConsumer<DocumentInfo> testPrep(String testFile) throws Exception {
        Uri dirUri = DocumentsContract.buildDocumentUri(
            InspectorProvider.AUTHORITY, testFile);

        DocumentInfo info = DocumentInfo.fromUri(mResolver, dirUri);

        TestConsumer<DocumentInfo> consumer = new TestConsumer<>();
        new DirectoryLoader(mResolver, consumer).executeOnExecutor(
                mEnv.lookupExecutor(info.authority), info);
        return consumer;
    }
}