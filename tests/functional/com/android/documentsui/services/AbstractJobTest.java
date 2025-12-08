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

package com.android.documentsui.services;

import static com.android.documentsui.StubProvider.ROOT_0_ID;
import static com.android.documentsui.StubProvider.ROOT_1_ID;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.DocumentsProviderHelper;
import com.android.documentsui.R;
import com.android.documentsui.StubProvider;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.UserId;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.testing.DocsProviders;
import com.android.documentsui.testing.TestFeatures;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public abstract class AbstractJobTest<T extends Job> {

    static final String AUTHORITY = StubProvider.DEFAULT_AUTHORITY;
    static final byte[] HAM_BYTES = "ham and cheese".getBytes();
    static final byte[] FRUITY_BYTES = "I love fruit cakes!".getBytes();

    UserId mUserId;
    Context mContext;
    ContentResolver mResolver;
    DocumentsProviderHelper mDocs;
    TestJobListener mJobListener;
    RootInfo mSrcRoot;
    RootInfo mDestRoot;

    private TestFeatures mFeatures;

    @Before
    public void setUp() throws Exception {
        // NOTE: Must be the "target" context, else security checks in content provider will fail.
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mFeatures = new TestFeatures();
        mFeatures.notificationChannel = mContext.getResources()
                .getBoolean(R.bool.feature_notification_channel);

        mUserId = UserId.DEFAULT_USER;
        mResolver = mContext.getContentResolver();

        mJobListener = new TestJobListener();

        mDocs = new DocumentsProviderHelper(mUserId, AUTHORITY, mContext, AUTHORITY);

        // Reset storage before starting the tests.
        resetStorage();

        initTestFiles();
    }

    @After
    public void tearDown() throws Exception {
        if (mDocs != null) {
            resetStorage();
            mDocs.cleanUp();
        }
    }

    private void resetStorage() throws RemoteException {
        mDocs.clear(null, null);
    }

    private void initTestFiles() throws RemoteException {
        mSrcRoot = mDocs.getRoot(ROOT_0_ID);
        mDestRoot = mDocs.getRoot(ROOT_1_ID);
    }

    FileOperation createOperation(@OpType int opType, List<Uri> srcs, Uri srcParent,
            Uri destination) throws Exception {
        DocumentStack stack =
                new DocumentStack(mSrcRoot, DocumentInfo.fromUri(mResolver, destination, mUserId));

        UrisSupplier urisSupplier = DocsProviders.createDocsProvider(srcs);
        FileOperation operation = new FileOperation.Builder()
                .withOpType(opType)
                .withSrcs(urisSupplier)
                .withDestination(stack)
                .withSrcParent(srcParent)
                .build();
        return operation;
    }

    final T createJob(FileOperation operation) {
        return createJob(operation, mJobListener);
    }

    final T createJob(FileOperation operation, Job.Listener listener) {
        return (T) operation.createJob(
                mContext, listener, FileOperations.createJobId(), mFeatures);
    }

    final T createJob(@OpType int opType, List<Uri> srcs, Uri srcParent, Uri destination)
            throws Exception {
        DocumentStack stack =
                new DocumentStack(mDestRoot, DocumentInfo.fromUri(mResolver, destination, mUserId));

        UrisSupplier urisSupplier = DocsProviders.createDocsProvider(srcs);
        FileOperation operation = new FileOperation.Builder()
                .withOpType(opType)
                .withSrcs(urisSupplier)
                .withDestination(stack)
                .withSrcParent(srcParent)
                .build();
        return (T) operation.createJob(
                mContext, mJobListener, FileOperations.createJobId(), mFeatures);
    }
}
