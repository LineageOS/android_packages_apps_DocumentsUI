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
import android.net.Uri;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.Loader;
import com.android.documentsui.testing.TestLoaderManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;

public class DocumentLoaderTest extends TestCase {

    private static final String AUTHORITY = "com.android.documentsui.inspectorprovider";
    private static final String TEST_DOC_NAME = "test.txt";

    private Context mContext;
    private TestLoaderManager mLoaderManager;
    private Loader mLoader;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mContext = InstrumentationRegistry.getTargetContext();
        mLoaderManager = new TestLoaderManager();

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
    }

    /**
     * Tests the loader using the Inspector Content provider. This test that we got valid info back
     * from the loader.
     *
     * @throws Exception
     */
    @Test
    public void testLoadsDocument() throws Exception {
        mLoader = new DocumentLoader(mContext, mLoaderManager);
        Uri validUri = DocumentsContract.buildDocumentUri(AUTHORITY, TEST_DOC_NAME);
        TestConsumer consumer = new TestConsumer(1);
        mLoader.load(validUri, consumer);

        // this is a test double that requires explicitly loading. @see TestLoaderManager
        mLoaderManager.getLoader(0).startLoading();

        consumer.latch.await(1000, TimeUnit.MILLISECONDS);

        assertNotNull(consumer.info);
        assertEquals(consumer.info.displayName, TEST_DOC_NAME);
        assertEquals(consumer.info.size, 0);
    }

    /**
     * Test invalid uri, DocumentInfo returned should be null.
     *
     * @throws Exception
     */
    @Test
    public void testInvalidInput() throws Exception {
        mLoader = new DocumentLoader(mContext, mLoaderManager);
        Uri invalidUri = Uri.parse("content://poodles/chuckleberry/ham");
        TestConsumer consumer = new TestConsumer(1);
        mLoader.load(invalidUri, consumer);

        // this is a test double that requires explicitly loading. @see TestLoaderManager
        mLoaderManager.getLoader(0).startLoading();

        consumer.latch.await(1000, TimeUnit.MILLISECONDS);
        assertNull(consumer.info);
    }

    @Test
    public void testNonContentUri() {

        mLoader = new DocumentLoader(mContext, mLoaderManager);
        Uri invalidUri = Uri.parse("http://poodles/chuckleberry/ham");
        TestConsumer consumer = new TestConsumer(1);

        try {
            mLoader.load(invalidUri, consumer);

            // this is a test double that requires explicitly loading. @see TestLoaderManager
            mLoaderManager.getLoader(0).startLoading();
            fail("Should have thrown exception.");
        } catch (Exception expected) {}
    }

    /**
     * Helper function for testing async processes.
     */
    private static class TestConsumer implements Consumer<DocumentInfo> {

        private DocumentInfo info;
        private CountDownLatch latch;

        public TestConsumer(int expectedCount) {
            latch = new CountDownLatch(expectedCount);
        }

        @Nullable
        @Override
        public void accept(DocumentInfo documentInfo) {
            info = documentInfo;
            latch.countDown();
        }
    }
}