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
package com.android.documentsui.testing;

import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract.Document;

import com.android.documentsui.FocusManager;
import com.android.documentsui.Injector;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.TestFocusHandler;
import com.android.documentsui.dirlist.TestModel;
import com.android.documentsui.selection.SelectionManager;
import com.android.documentsui.ui.TestDialogController;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public class TestEnv {

    public static DocumentInfo FOLDER_0;
    public static DocumentInfo FOLDER_1;
    public static DocumentInfo FOLDER_2;
    public static DocumentInfo FILE_TXT;
    public static DocumentInfo FILE_PNG;
    public static DocumentInfo FILE_JPG;
    public static DocumentInfo FILE_GIF;
    public static DocumentInfo FILE_PDF;
    public static DocumentInfo FILE_APK;
    public static DocumentInfo FILE_PARTIAL;
    public static DocumentInfo FILE_ARCHIVE;
    public static DocumentInfo FILE_VIRTUAL;

    public final TestScheduledExecutorService mExecutor;
    public final State state = new State();
    public final TestRootsAccess roots = new TestRootsAccess();
    public final TestDocumentsAccess docs = new TestDocumentsAccess();
    public final TestFocusHandler focusHandler = new TestFocusHandler();
    public final TestModel model;
    public final SelectionManager selectionMgr;
    public final TestSearchViewManager searchViewManager;
    public final Injector injector;

    private TestEnv(String authority) {
        mExecutor = new TestScheduledExecutorService();
        model = new TestModel(authority);
        selectionMgr = SelectionManagers.createTestInstance();
        searchViewManager = new TestSearchViewManager();
        injector = new Injector(
                null,      //a Config is not required for tests
                null,       //ScopedPreferences are not required for tests
                null,   //a MessageBuilder is not required for tests
                new TestDialogController());
        injector.selectionMgr = selectionMgr;
        injector.focusManager = new FocusManager(selectionMgr, null, null, 0);
    }

    public static TestEnv create() {
        return create(TestRootsAccess.HOME.authority);
    }

    public static TestEnv create(String authority) {
        TestEnv env = new TestEnv(authority);
        env.reset();
        return env;
    }

    public void clear() {
        model.reset();
        model.update();
    }

    public void reset() {
        model.reset();
        FOLDER_0 = model.createFolder("folder 0");
        FOLDER_1 = model.createFolder("folder 1");
        FOLDER_2 = model.createFolder("folder 2");
        FILE_TXT = model.createFile("woowoo.txt");
        FILE_PNG = model.createFile("peppey.png");
        FILE_JPG = model.createFile("jiffy.jpg");
        FILE_GIF = model.createFile("glibby.gif");
        FILE_PDF = model.createFile("busy.pdf");
        FILE_APK = model.createFile("becareful.apk");
        FILE_PARTIAL = model.createFile(
                "UbuntuFlappyBird.iso",
                Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_PARTIAL);
        FILE_ARCHIVE = model.createFile(
                "whatsinthere.zip",
                Document.FLAG_SUPPORTS_DELETE);
        FILE_VIRTUAL = model.createDocument(
                "virtualdoc.vnd",
                "application/vnd.google-apps.document",
                Document.FLAG_VIRTUAL_DOCUMENT
                        | Document.FLAG_SUPPORTS_DELETE
                        | Document.FLAG_SUPPORTS_RENAME);

        model.update();
    }

    public void populateStack() {
        DocumentInfo rootDoc = model.getDocument("1");
        Assert.assertNotNull(rootDoc);
        Assert.assertEquals(rootDoc.displayName, FOLDER_0.displayName);

        state.stack.changeRoot(TestRootsAccess.HOME);
        state.stack.push(rootDoc);
    }

    public void beforeAsserts() throws Exception {
        // We need to wait on all AsyncTasks to finish AND to post results back.
        // *** Results are posted on main thread ***, but tests run in their own
        // thread. So even with our test executor we still have races.
        //
        // To work around this issue post our own runnable to the main thread
        // which we presume will be the *last* runnable (after any from AsyncTasks)
        // and then wait for our runnable to be called.
        CountDownLatch latch = new CountDownLatch(1);
        mExecutor.runAll();
        new Handler(Looper.getMainLooper()).post(latch::countDown);
        latch.await();
    }

    public Executor lookupExecutor(String authority) {
        return mExecutor;
    }

    public void selectDocument(DocumentInfo info) {
        List<String> ids = new ArrayList<>(1);
        ids.add(info.documentId);
        selectionMgr.setItemsSelected(ids, true);
    }
}
