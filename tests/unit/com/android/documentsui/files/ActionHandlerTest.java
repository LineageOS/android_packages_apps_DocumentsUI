/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.documentsui.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import android.content.Intent;
import android.net.Uri;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.testing.Roots;
import com.android.documentsui.testing.TestConfirmationCallback;
import com.android.documentsui.testing.TestEnv;
import com.android.documentsui.testing.TestRootsAccess;
import com.android.documentsui.ui.TestDialogController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class ActionHandlerTest {

    private TestEnv mEnv;
    private TestActivity mActivity;
    private TestDialogController mDialogs;
    private TestConfirmationCallback mCallback;
    private ActionHandler<TestActivity> mHandler;

    @Before
    public void setUp() {
        mEnv = TestEnv.create();
        mActivity = TestActivity.create();
        mDialogs = new TestDialogController();
        mCallback = new TestConfirmationCallback();
        mEnv.roots.configurePm(mActivity.packageMgr);

        mHandler = new ActionHandler<>(
                mActivity,
                mEnv.state,
                mEnv.roots,
                mEnv.docs,
                mEnv.selectionMgr,
                mEnv::lookupExecutor,
                mDialogs,
                null,  // tuner, not currently used.
                null,  // clipper, only used in drag/drop
                null  // clip storage, not utilized unless we venture into *jumbo* clip terratory.
                );

        mDialogs.confirmNext();

        mEnv.selectionMgr.toggleSelection("1");

        mHandler.reset(mEnv.model, false);
    }

    @Test
    public void testOpenSelectedInNewWindow() {
        mHandler.openSelectedInNewWindow();

        DocumentStack path = new DocumentStack(Roots.create("123"), mEnv.model.getDocument("1"));

        Intent expected = LauncherActivity.createLaunchIntent(mActivity);
        expected.putExtra(Shared.EXTRA_STACK, (Parcelable) path);

        Intent actual = mActivity.startActivity.getLastValue();
        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    public void testOpenDrawerOnLaunchingEmptyRoot() {
        mEnv.model.reset();
        // state should not say we've changed our location

        mEnv.model.update();

        mActivity.setRootsDrawerOpen.assertLastArgument(true);
    }

    @Test
    public void testDeleteDocuments() {
        mEnv.populateStack();

        mHandler.deleteSelectedDocuments(mEnv.model, mCallback);
        mDialogs.assertNoFileFailures();
        mActivity.startService.assertCalled();
        mCallback.assertConfirmed();
    }

    @Test
    public void testDeleteDocuments_Cancelable() {
        mEnv.populateStack();

        mDialogs.rejectNext();
        mHandler.deleteSelectedDocuments(mEnv.model, mCallback);
        mDialogs.assertNoFileFailures();
        mActivity.startService.assertNotCalled();
        mCallback.assertRejected();
    }

    @Test
    public void testDocumentPicked_DefaultsToView() throws Exception {
        mActivity.currentRoot = TestRootsAccess.HOME;

        mHandler.onDocumentPicked(TestEnv.FILE_GIF);
        mActivity.assertActivityStarted(Intent.ACTION_VIEW);
    }

    @Test
    public void testDocumentPicked_PreviewsWhenResourceSet() throws Exception {
        mActivity.resources.setQuickViewerPackage("corptropolis.viewer");
        mActivity.currentRoot = TestRootsAccess.HOME;

        mHandler.onDocumentPicked(TestEnv.FILE_GIF);
        mActivity.assertActivityStarted(Intent.ACTION_QUICK_VIEW);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesApks() throws Exception {
        mActivity.currentRoot = TestRootsAccess.DOWNLOADS;

        mHandler.onDocumentPicked(TestEnv.FILE_APK);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_Downloads_ManagesPartialFiles() throws Exception {
        mActivity.currentRoot = TestRootsAccess.DOWNLOADS;

        mHandler.onDocumentPicked(TestEnv.FILE_PARTIAL);
        mActivity.assertActivityStarted(DocumentsContract.ACTION_MANAGE_DOCUMENT);
    }

    @Test
    public void testDocumentPicked_OpensArchives() throws Exception {
        mActivity.currentRoot = TestRootsAccess.HOME;

        mHandler.onDocumentPicked(TestEnv.FILE_ARCHIVE);
        mActivity.openContainer.assertLastArgument(TestEnv.FILE_ARCHIVE);
    }

    @Test
    public void testDocumentPicked_OpensDirectories() throws Exception {
        mActivity.currentRoot = TestRootsAccess.HOME;

        mHandler.onDocumentPicked(TestEnv.FOLDER_1);
        mActivity.openContainer.assertLastArgument(TestEnv.FOLDER_1);
    }

    @Test
    public void testShowChooser() throws Exception {
        mActivity.currentRoot = TestRootsAccess.DOWNLOADS;

        mHandler.showChooserForDoc(TestEnv.FILE_PDF);
        mActivity.assertActivityStarted(Intent.ACTION_CHOOSER);
    }

    @Test
    public void testInitLocation_DefaultsToDownloads() throws Exception {
        mActivity.resources.bools.put(R.bool.productivity_device, false);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestRootsAccess.DOWNLOADS.getUri());
    }

    @Test
    public void testInitLocation_ProductivityDefaultsToHome() throws Exception {
        mActivity.resources.bools.put(R.bool.productivity_device, true);

        mHandler.initLocation(mActivity.getIntent());
        assertRootPicked(TestRootsAccess.HOME.getUri());
    }

    @Test
    public void testInitLocation_ViewDocument() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(Intent.ACTION_VIEW);

        // configure DocumentsAccess to return something.
        mEnv.docs.nextRootDocument = TestEnv.FOLDER_0;
        mEnv.docs.nextDocument = TestEnv.FILE_GIF;

        Uri destUri = mEnv.model.getItemUri(TestEnv.FILE_GIF.documentId);
        intent.setData(destUri);

        mEnv.state.stack.clear();  // Stack must be clear, we've even got an assert!
        mHandler.initLocation(intent);
        assertDocumentPicked(destUri);
    }

    private void assertDocumentPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.refreshCurrentRootAndDirectory.assertCalled();
        DocumentInfo doc = mEnv.state.stack.peekLast();
        Uri actualUri = mEnv.model.getItemUri(doc.documentId);
        assertEquals(expectedUri, actualUri);
    }

    @Test
    public void testInitLocation_BrowseRoot() throws Exception {
        Intent intent = mActivity.getIntent();
        intent.setAction(DocumentsContract.ACTION_BROWSE);
        intent.setData(TestRootsAccess.PICKLES.getUri());

        mHandler.initLocation(intent);
        assertRootPicked(TestRootsAccess.PICKLES.getUri());
    }

    private void assertRootPicked(Uri expectedUri) throws Exception {
        mEnv.beforeAsserts();

        mActivity.rootPicked.assertCalled();
        RootInfo root = mActivity.rootPicked.getLastValue();
        assertNotNull(root);
        assertEquals(expectedUri, root.getUri());
    }
}
