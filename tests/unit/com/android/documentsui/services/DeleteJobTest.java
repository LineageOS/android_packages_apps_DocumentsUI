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

import static com.android.documentsui.services.FileOperationService.OPERATION_DELETE;

import static com.google.common.collect.Lists.newArrayList;

import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.support.test.filters.MediumTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@MediumTest
public class DeleteJobTest extends AbstractJobTest<DeleteJob> {

    public void testDeleteFiles() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        createJob(newArrayList(testFile1, testFile2),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId)).run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    public void testDeleteFiles_NoSrcParent() throws Exception {
        Uri testFile1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        mDocs.writeDocument(testFile1, HAM_BYTES);

        Uri testFile2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        mDocs.writeDocument(testFile2, FRUITY_BYTES);

        createJob(newArrayList(testFile1, testFile2), null).run();
        mJobListener.waitForFinished();

        mDocs.assertChildCount(mSrcRoot, 0);
    }

    public void testDeleteFile_SendDeletionFailedUris() throws Exception {
        Uri invalidUri1 = Uri.parse("content://poodles/chuckleberry/ham");
        Uri validUri = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        Uri invalidUri2 = Uri.parse("content://poodles/chuckleberry/ham2");
        mDocs.writeDocument(validUri, FRUITY_BYTES);

        Uri stack = DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId);
        FileOperation operation = createOperation(OPERATION_DELETE,
                newArrayList(invalidUri1, validUri, invalidUri2),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId), stack);

        CountDownLatch latch = new CountDownLatch(1);
        final ArrayList<Uri> deletionFailedUris = new ArrayList<>();
        operation.addMessageListener(
                new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message message) {
                        if (message.what == FileOperationService.MESSAGE_FINISH) {
                            operation.removeMessageListener(this);
                            deletionFailedUris.addAll(message.getData()
                                    .getParcelableArrayList(DeleteJob.KEY_FAILED_URIS));
                            latch.countDown();
                            return true;
                        }
                        return false;
                    }
                }
        );

        createJob(operation).run();
        latch.await(10, TimeUnit.SECONDS);

        assertTrue("Not received failed uri:" + invalidUri1,
                deletionFailedUris.contains(invalidUri1));
        assertTrue("Not received failed uri:" + invalidUri2,
                deletionFailedUris.contains(invalidUri2));
        assertFalse("Received valid uri:" + validUri,
                deletionFailedUris.contains(validUri));
    }

    public void testDeleteFile_SendDeletionCanceledUris() throws Exception {
        Uri testUri1 = mDocs.createDocument(mSrcRoot, "text/plain", "test1.txt");
        Uri testUri2 = mDocs.createDocument(mSrcRoot, "text/plain", "test2.txt");
        Uri testUri3 = mDocs.createDocument(mSrcRoot, "text/plain", "test3.txt");
        mDocs.writeDocument(testUri1, FRUITY_BYTES);
        mDocs.writeDocument(testUri2, FRUITY_BYTES);
        mDocs.writeDocument(testUri3, FRUITY_BYTES);

        Uri stack = DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId);
        FileOperation operation = createOperation(OPERATION_DELETE,
                newArrayList(testUri1, testUri2, testUri3),
                DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId), stack);

        CountDownLatch latch = new CountDownLatch(1);
        final AtomicInteger cancelCount = new AtomicInteger();
        operation.addMessageListener(
                new Handler.Callback() {
                    @Override
                    public boolean handleMessage(Message message) {
                        if (message.what == FileOperationService.MESSAGE_FINISH) {
                            operation.removeMessageListener(this);
                            cancelCount.set(message.arg1);
                            latch.countDown();
                            return true;
                        }
                        return false;
                    }
                }
        );

        // Cancel the deletion job at onStart to ensure that none of the files will be deleted
        TestJobListener listener = new TestJobListener() {
            @Override
            public void onStart(Job job) {
                super.onStart(job);
                job.cancel();
            }
        };
        createJob(operation, listener).run();
        latch.await(10, TimeUnit.SECONDS);

        assertEquals(3, cancelCount.get());
    }

    /**
     * Creates a job with a stack consisting to the default src directory.
     */
    private final DeleteJob createJob(List<Uri> srcs, Uri srcParent) throws Exception {
        Uri stack = DocumentsContract.buildDocumentUri(AUTHORITY, mSrcRoot.documentId);
        return createJob(OPERATION_DELETE, srcs, srcParent, stack);
    }
}
