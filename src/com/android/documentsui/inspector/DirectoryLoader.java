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

import static com.android.internal.util.Preconditions.checkArgument;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import com.android.documentsui.base.DocumentInfo;
import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

public class DirectoryLoader extends AsyncTask<DocumentInfo, Integer, DocumentInfo> {

    private static int MAXIMUM_FILE_COUNT = 5000;

    private final ContentResolver mResolver;
    private final Consumer<DocumentInfo> mCallback;

    public DirectoryLoader(ContentResolver resolver, Consumer<DocumentInfo> callback) {
        mResolver = resolver;
        mCallback = callback;
    }

    /**
     * Finds the size and number of children.
     */
    @Override
    protected DocumentInfo doInBackground(DocumentInfo... documentInfos) {
        checkArgument(documentInfos.length == 1);

        if (documentInfos[0].isDirectory()) {
            DocumentInfo directory = documentInfos[0];
            directory.numberOfChildren = getChildrenCount(directory);
            directory.size = getDirectorySize(directory);
            return directory;
        }
        else {
            return null;
        }
    }

    @Override
    protected void onPostExecute(DocumentInfo result) {
        mCallback.accept(result);
    }

    private int getChildrenCount(DocumentInfo directory) {
        return getCursor(directory).getCount();
    }

    private long getDirectorySize(DocumentInfo directory) {

        Long size = 0L;
        Queue<DocumentInfo> directories = new LinkedList<>();
        directories.add(directory);
        int count = 0;

        while(directories.size() > 0) {

            //makes a cursor from first directory in queue.
            Cursor cursor = getCursor(directories.remove());
            while (cursor.moveToNext()) {

                //hard stop if we have processed a large amount of files.
                if(count >= MAXIMUM_FILE_COUNT) {
                    return size;
                }

                //iterate through the directory.
                DocumentInfo info = DocumentInfo.fromCursor(cursor, directory.authority);
                if (info.isDirectory()) {
                    directories.add(info);
                } else {
                    size += info.size;
                }
                count++;
            }
            //done checking this directory, close the cursor.
            cursor.close();
        }
        return size;
    }

    private Cursor getCursor(DocumentInfo directory) {
        checkArgument(directory.isDirectory());

        Uri children = DocumentsContract.buildChildDocumentsUri(
            directory.authority, directory.documentId);

        return mResolver
                .query(children, null, null, null, Document.COLUMN_SIZE + " DESC", null);
    }
}
