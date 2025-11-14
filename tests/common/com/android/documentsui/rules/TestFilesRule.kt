/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.documentsui.rules

import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.android.documentsui.DocumentsProviderHelper
import com.android.documentsui.StubProvider
import com.android.documentsui.base.RootInfo
import com.android.documentsui.base.UserId
import org.junit.rules.ExternalResource

/**
 * Rule that creates test files in a test.
 * When `skipCreation` is false, this essentially falls back to providing a `docsHelper`.
 */
class TestFilesRule(private val skipCreation: Boolean = false) : ExternalResource() {
    lateinit var docsHelper: DocumentsProviderHelper

    // A map of the URIs that are created, used to keep track of names of items that are created
    // as children of other items.
    private val createdUris = mutableMapOf<String, Uri>()

    // The creation operations are deferred as the rules are instantiated prior to the environment
    // being ready.
    private val deferredOperations = mutableListOf<() -> Unit>()

    override fun before() {
        docsHelper =
            DocumentsProviderHelper(
                UserId.DEFAULT_USER,
                StubProvider.DEFAULT_AUTHORITY,
                InstrumentationRegistry.getInstrumentation().context,
                StubProvider.DEFAULT_AUTHORITY,
            )

        docsHelper.clear(null, null)
        docsHelper.configure(null, Bundle.EMPTY)

        if (skipCreation) {
            require(
                deferredOperations.isEmpty()
            ) { "Have deferred operations yet requested to skip creation." }
            return
        }

        if (deferredOperations.isEmpty()) {
            createDefault()
        } else {
            deferredOperations.forEach { it() }
        }
    }

    /** Create a folder in `root`. */
    fun createFolderInRoot(root: String, folderName: String): TestFilesRule {
        deferredOperations.add {
            val rootInfo = docsHelper.getRoot(root)
            val uri = docsHelper.createFolder(rootInfo, folderName)
            require(!createdUris.containsKey(folderName)) { "$folderName has already been created" }
            createdUris[folderName] = uri
        }
        return this
    }

    /** Creates a folder in `root` with `parentName`. The `parentName` must be already created. */
    fun createFolderWithParent(parentName: String, folderName: String): TestFilesRule {
        deferredOperations.add {
            val parentUri = createdUris[parentName]
            requireNotNull(parentUri) { "Parent folder $parentName not initialized" }
            val uri = docsHelper.createFolder(parentUri, folderName)
            createdUris[folderName] = uri
        }
        return this
    }

    /** Creates a file in `root` with the specified `fileName` and `mimeType`. */
    fun createFileInRoot(root: String, fileName: String, mimeType: String): TestFilesRule {
        deferredOperations.add {
            val rootInfo = docsHelper.getRoot(root)
            val uri = docsHelper.createDocument(rootInfo, mimeType, fileName)
            createdUris[fileName] = uri
        }
        return this
    }

    /** Returns `Uri` of the file with `filename` within the `root`. */
    fun getUriInRoot(root: String, fileName: String): Uri? {
        return docsHelper.findDocument(getRoot(root).documentId, fileName).derivedUri
    }

    /** Returns`RootInfo` for the for the specified `root`. */
    fun getRoot(root: String): RootInfo {
        return docsHelper.getRoot(root)
    }

    /** Creates a default set of files for testing. */
    private fun createDefault() {
        val root0 = docsHelper.getRoot(StubProvider.ROOT_0_ID)
        val root1 = docsHelper.getRoot(StubProvider.ROOT_1_ID)

        docsHelper.createFolder(root0, DIR_NAME_1)
        docsHelper.createDocument(root0, "text/plain", FILE_NAME_1)
        docsHelper.createDocument(root0, "image/png", FILE_NAME_2)
        docsHelper.createDocumentWithFlags(
            root0.documentId,
            "text/plain",
            FILE_NAME_NO_RENAME,
            DocumentsContract.Document.FLAG_SUPPORTS_WRITE,
        )

        docsHelper.createDocument(root1, "text/plain", FILE_NAME_3)
        docsHelper.createDocument(root1, "text/plain", FILE_NAME_4)
    }

    override fun after() {
        docsHelper.cleanUp()
    }

    companion object {
        @JvmField
        val DIR_NAME_1: String = "Dir1"

        @JvmField
        val CHILD_DIR_1: String = "ChildDir1"

        @JvmField
        val FILE_NAME_1: String = "file1.log"

        @JvmField
        val FILE_NAME_2: String = "file12.png"

        @JvmField
        val FILE_NAME_3: String = "anotherFile0.log"

        @JvmField
        val FILE_NAME_4: String = "poodles.text"

        @JvmField
        val FILE_NAME_NO_RENAME: String = "NO_RENAMEfile.txt"
    }
}
