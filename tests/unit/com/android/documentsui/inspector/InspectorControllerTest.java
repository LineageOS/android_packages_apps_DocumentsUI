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
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.View.OnClickListener;
import com.android.documentsui.InspectorProvider;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.inspector.InspectorController.DetailsDisplay;
import com.android.documentsui.inspector.InspectorController.Loader;
import com.android.documentsui.inspector.InspectorController.TableDisplay;
import com.android.documentsui.inspector.actions.Action;
import com.android.documentsui.inspector.InspectorController.ActionDisplay;
import com.android.documentsui.testing.TestConsumer;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestLoaderManager;
import com.android.documentsui.testing.TestPackageManager;
import com.android.documentsui.testing.TestPackageManager.TestResolveInfo;
import com.android.documentsui.testing.TestProvidersAccess;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class InspectorControllerTest  {

    private static final String OPEN_IN_PROVIDER_DOC = "OpenInProviderTest";

    private TestActivity mContext;
    private TestLoaderManager mLoaderManager;
    private Loader mLoader;
    private TestPackageManager mPm;
    private InspectorController mController;
    private TestEnv mEnv;
    private TestConsumer<DocumentInfo> mHeaderTestDouble;
    private TestDetails mDetailsTestDouble;
    private TestTable mMetadata;
    private TestAction mShowInProvider;
    private TestAction mDefaultsTestDouble;
    private TestConsumer<DocumentInfo> mDebugTestDouble;
    private Boolean mShowSnackbarsCalled;

    @Before
    public void setUp() throws Exception {

        //Needed to create a non null loader for the InspectorController.
        Context loader = InstrumentationRegistry.getTargetContext();
        mEnv = TestEnv.create();
        mPm = TestPackageManager.create();
        mLoaderManager = new TestLoaderManager();
        mLoader = new DocumentLoader(loader, mLoaderManager);
        mHeaderTestDouble = new TestConsumer<>();
        mDetailsTestDouble = new TestDetails();
        mMetadata = new TestTable();
        mShowInProvider = new TestAction();
        mDefaultsTestDouble = new TestAction();
        mDebugTestDouble = new TestConsumer<>();

        mShowSnackbarsCalled = false;

        //Crashes if not called before "new TestActivity".
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        mContext = new TestActivity();

        mController = new InspectorController(
            mContext,
            mLoader,
            mPm,
            new TestProvidersAccess(),
            false,
            mHeaderTestDouble,
            mDetailsTestDouble,
            mMetadata,
            mShowInProvider,
            mDefaultsTestDouble,
            mDebugTestDouble,
            ProviderExecutor::forAuthority,
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
        mDebugTestDouble.assertNotCalled();
    }

    /**
     * Tests Debug view should be updated when visible.
     */
    @Test
    public void testShowDebugUpdatesView() throws Exception {
        mController = new InspectorController(
            mContext,
            mLoader,
            mPm,
            new TestProvidersAccess(),
            true,
            mHeaderTestDouble,
            mDetailsTestDouble,
            mMetadata,
            mShowInProvider,
            mDefaultsTestDouble,
            mDebugTestDouble,
            ProviderExecutor::forAuthority,
            () -> {
                mShowSnackbarsCalled = true;
            }
        );

        mController.updateView(new DocumentInfo());
        mDebugTestDouble.assertCalled();
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

        Uri uri = DocumentsContract.buildDocumentUri(InspectorProvider.AUTHORITY,
            OPEN_IN_PROVIDER_DOC);
        mController.showInProvider(uri);

        assertNotNull(mContext.started);
        assertEquals("com.android.documentsui", mContext.started.getPackage());
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
        mHeaderTestDouble.assertCalled();
        mDetailsTestDouble.assertCalled();
    }

    /**
     * Test that when a document has the FLAG_SUPPORTS_SETTINGS set the showInProvider view will
     * be visible.
     *
     * @throws Exception
     */
    @Test
    public void testShowInProvider_visible() throws Exception {
        DocumentInfo doc = new DocumentInfo();
        doc.derivedUri =
            DocumentsContract.buildDocumentUri(InspectorProvider.AUTHORITY, OPEN_IN_PROVIDER_DOC);

        doc.flags = doc.flags | Document.FLAG_SUPPORTS_SETTINGS;
        mController.updateView(doc);
        assertTrue(mShowInProvider.becameVisible);
    }

    /**
     * Test that when a document does not have the FLAG_SUPPORTS_SETTINGS set the view will be
     * invisible.
     * @throws Exception
     */
    @Test
    public void testShowInProvider_invisible() throws Exception {
        DocumentInfo doc = new DocumentInfo();
        doc.derivedUri =
            DocumentsContract.buildDocumentUri(InspectorProvider.AUTHORITY, OPEN_IN_PROVIDER_DOC);

        mController.updateView(doc);
        assertFalse(mShowInProvider.becameVisible);
    }

    /**
     * Test that the action clear app defaults is visible when conditions are met.
     * @throws Exception
     */
    @Test
    public void testAppDefaults_visible() throws Exception {

        mPm.queryIntentProvidersResults = new ArrayList<>();
        mPm.queryIntentProvidersResults.add(new TestResolveInfo());
        mPm.queryIntentProvidersResults.add(new TestResolveInfo());
        DocumentInfo doc = new DocumentInfo();
        doc.derivedUri =
            DocumentsContract.buildDocumentUri(InspectorProvider.AUTHORITY, OPEN_IN_PROVIDER_DOC);

        mController.updateView(doc);
        assertTrue(mDefaultsTestDouble.becameVisible);
    }

    /**
     * Test that action clear app defaults is invisible when conditions have not been met.
     * @throws Exception
     */
    @Test
    public void testAppDefaults_invisible() throws Exception {
        mPm.queryIntentProvidersResults = new ArrayList<>();
        mPm.queryIntentProvidersResults.add(new TestResolveInfo());
        DocumentInfo doc = new DocumentInfo();
        doc.derivedUri =
            DocumentsContract.buildDocumentUri(InspectorProvider.AUTHORITY, OPEN_IN_PROVIDER_DOC);

        mController.updateView(doc);
        assertFalse(mDefaultsTestDouble.becameVisible);
    }

    /**
     * Test that update view will handle a null value properly. It uses a runnable to verify that
     * the static method Snackbars.showInspectorError(Activity activity) is called.
     *
     * @throws Exception
     */
    @Test
    public void testUpdateView_withNullValue() throws Exception {
        mController.updateView(null);
        assertTrue(mShowSnackbarsCalled);
        mHeaderTestDouble.assertNotCalled();
        mDetailsTestDouble.assertNotCalled();
    }

    /**
     * Test that the updateMetadata method in the controller will not print anything if there are no
     * values for specific keys.
     * @throws Exception
     */
    @Test
    public void testPrintMetadata_noBundleTags() throws Exception {
        mController.showExifData("No Name", createTestMetadataEmptyBundle());
        assertTrue(mMetadata.calledBundleKeys.isEmpty());
    }

    /**
     * Test that the updateMetadata method is printing metadata for selected items found in the
     * bundle.
     */
    @Test
    public void testPrintMetadata_BundleTags() throws Exception {
        mController.showExifData("No Name", createTestMetadataBundle());

        Map<Integer, String> expected = new HashMap<>();
        expected.put(R.string.metadata_dimensions, "3840 x 2160");
        expected.put(R.string.metadata_location, "37.7749,  -122.4194");
        expected.put(R.string.metadata_altitude, "1244.0");
        expected.put(R.string.metadata_make, "Google");
        expected.put(R.string.metadata_model, "Pixel");

        for (Integer key : expected.keySet()) {
            assertTrue(expected.get(key).equals(mMetadata.calledBundleKeys.get(key)));
        }
    }

    /**
     * Bundle only supplies half of the values for the pairs that print in printMetaData. No put
     * method should be called as the correct conditions have not been met.
     * @throws Exception
     */
    @Test
    public void testPrintMetadata_BundlePartialTags() throws Exception {
        mController.showExifData("No Name", createTestMetadataPartialBundle());
        assertTrue(mMetadata.calledBundleKeys.isEmpty());
    }

    private static Bundle createTestMetadataEmptyBundle() {
        return new Bundle();
    }

    private static Bundle createTestMetadataBundle() {
        Bundle data = new Bundle();
        data.putInt(ExifInterface.TAG_IMAGE_WIDTH, 3840);
        data.putInt(ExifInterface.TAG_IMAGE_LENGTH, 2160);
        data.putDouble(ExifInterface.TAG_GPS_LATITUDE, 37.7749);
        data.putDouble(ExifInterface.TAG_GPS_LONGITUDE, -122.4194);
        data.putDouble(ExifInterface.TAG_GPS_ALTITUDE, 1244);
        data.putString(ExifInterface.TAG_MAKE, "Google");
        data.putString(ExifInterface.TAG_MODEL, "Pixel");
        return data;
    }

    private static Bundle createTestMetadataPartialBundle() {
        Bundle data = new Bundle();
        data.putInt(ExifInterface.TAG_IMAGE_WIDTH, 3840);
        data.putDouble(ExifInterface.TAG_GPS_LATITUDE, 37.7749);
        return data;
    }

    private static class TestActivity extends Activity {

        private @Nullable Intent started;

        @Override
        public void startActivity(Intent intent) {
            started = intent;
        }
    }

    private static class TestAction implements ActionDisplay {

        private TestAction() {
            becameVisible = false;
        }

        private boolean becameVisible;

        @Override
        public void init(Action action, OnClickListener listener) {

        }

        @Override
        public void setVisible(boolean visible) {
            becameVisible = visible;
        }

        @Override
        public void setActionHeader(String header) {

        }

        @Override
        public void setAppIcon(Drawable icon) {

        }

        @Override
        public void setAppName(String name) {

        }

        @Override
        public void showAction(boolean visible) {

        }
    }

    private static class TestDetails implements DetailsDisplay {

        private boolean mCalled = false;

        @Override
        public void accept(DocumentInfo info) {
            mCalled = true;
        }

        @Override
        public void setChildrenCount(int count) {
        }

        public void assertCalled() {
            Assert.assertTrue(mCalled);
        }

        public void assertNotCalled() {
            Assert.assertFalse(mCalled);
        }
    }

    private static class TestTable implements TableDisplay {

        private Map<Integer, String> calledBundleKeys;

        public TestTable() {
            calledBundleKeys = new HashMap<>();
        }

        @Override
        public void setTitle(int title) {

        }

        @Override
        public void put(int keyId, String value) {
            calledBundleKeys.put(keyId, value);
        }


        @Override
        public void put(int keyId, String value, OnClickListener callback) {
            calledBundleKeys.put(keyId, value);
        }

        @Override
        public void setVisible(boolean visible) {
        }
    }
}