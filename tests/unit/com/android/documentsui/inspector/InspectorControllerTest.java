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

import static android.provider.DocumentsContract.Document.FLAG_SUPPORTS_SETTINGS;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import com.android.documentsui.InspectorProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.Loader;
import com.android.documentsui.testing.TestConsumer;
import com.android.documentsui.testing.TestLoaderManager;
import com.android.documentsui.testing.TestProvidersAccess;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InspectorControllerTest  {

    private static final String TEST_DOC_NAME = "OpenInProviderTest";

    private TestActivity mContext;
    private TestLoaderManager mLoaderManager;
    private Loader mLoader;
    private InspectorController mController;
    private TestConsumer<DocumentInfo> headerTestDouble;
    private TestConsumer<DocumentInfo> detailsTestDouble;
    private TestConsumer<DocumentInfo> debugTestDouble;
    private Boolean mShowSnackbarsCalled;

    @Before
    public void setUp() throws Exception {


        //Needed to create a non null loader for the InspectorController.
        Context loader = InstrumentationRegistry.getTargetContext();
        mLoaderManager = new TestLoaderManager();
        mLoader = new DocumentLoader(loader, mLoaderManager);
        headerTestDouble = new TestConsumer<>();
        detailsTestDouble = new TestConsumer<>();
        debugTestDouble = new TestConsumer<>();
        mShowSnackbarsCalled = false;

        //Crashes if not called before "new TestActivity".
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mContext = new TestActivity();

        mController = new InspectorController(
                mContext,
                mLoader,
                new TestProvidersAccess(),
                false,
                headerTestDouble,
                detailsTestDouble,
                debugTestDouble,
                () -> {
                    mShowSnackbarsCalled = true;
                }
        );
    }

    /**
     * Tests Debug view should be hidden and therefore not updated by default.
     */
    @Test
    public void testHideDebugByDefault() throws Exception {
        mController.updateView(new DocumentInfo());
        debugTestDouble.assertNotCalled();
    }

    /**
     * Tests Debug view should be updated when visible.
     */
    @Test
    public void testShowDebugUpdatesView() throws Exception {
        mController = new InspectorController(
                mContext,
                mLoader,
                new TestProvidersAccess(),
                true,
                headerTestDouble,
                detailsTestDouble,
                debugTestDouble,
                () -> {
                    mShowSnackbarsCalled = true;
                }
        );

        mController.updateView(new DocumentInfo());
        debugTestDouble.assertCalled();
    }

    /**
     * Tests show in provider feature of the controller. This test loads a documentInfo from a uri.
     *  calls showInProvider on the documentInfo and verifies that the TestProvider activity has
     *  started.
     *
     *  @see InspectorProvider
     *  @see TestProviderActivity
     *
     * @throws Exception
     */
    @Test
    public void testShowInProvider() throws Exception {

        Uri uri = DocumentsContract.buildDocumentUri(InspectorProvider.AUTHORITY, TEST_DOC_NAME);
        mController.showInProvider(uri);

        assertNotNull(mContext.started);
        assertEquals( "com.android.documentsui",mContext.started.getPackage());
        assertEquals(uri, mContext.started.getData());
    }

    /**
     * Test that valid input will update the view properly. The test uses a test double for header
     * and details view and asserts that .accept was called on both.
     *
     * @throws Exception
     */
    @Test
    public void testUpdateViewWithValidInput() throws Exception {
        mController.updateView(new DocumentInfo());
        headerTestDouble.assertCalled();
        detailsTestDouble.assertCalled();
    }

    /**
     * Test that update view will handle a null value properly. It uses a runnable to verify that
     * the static method Snackbars.showInspectorError(Activity activity) is called.
     *
     * @throws Exception
     */
    @Test
    public void testUpdateViewWithNullValue() throws Exception {
        mController.updateView(null);
        assertTrue(mShowSnackbarsCalled);
        headerTestDouble.assertNotCalled();
        detailsTestDouble.assertNotCalled();
    }

    private static class TestActivity extends Activity {

        private @Nullable Intent started;

        @Override
        public void startActivity(Intent intent) {
            started = intent;
        }
    }
}