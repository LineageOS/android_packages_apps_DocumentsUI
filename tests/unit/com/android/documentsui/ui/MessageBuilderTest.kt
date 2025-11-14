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
package com.android.documentsui.ui

import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.provider.DocumentsContract.Document.MIME_TYPE_DIR
import androidx.test.filters.SmallTest
import com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_CONVERTED
import com.android.documentsui.OperationDialogFragment.DIALOG_TYPE_FAILURE
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.services.FileOperationService.OPERATION_COMPRESS
import com.android.documentsui.services.FileOperationService.OPERATION_COPY
import com.android.documentsui.services.FileOperationService.OPERATION_DELETE
import com.android.documentsui.services.FileOperationService.OPERATION_EXTRACT
import com.android.documentsui.services.FileOperationService.OPERATION_MOVE
import com.android.documentsui.services.FileOperationService.OPERATION_UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Suite
import org.junit.runners.Suite.SuiteClasses
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.eq
import org.mockito.Mockito.`when` as whenever
import org.mockito.MockitoAnnotations

@SmallTest
@RunWith(Suite::class)
@SuiteClasses(
    MessageBuilderTest.GenerateDeleteMessage::class,
    MessageBuilderTest.GenerateListMessage::class
)
open class MessageBuilderTest() {
    companion object {
        const val EXPECTED_MESSAGE = "Delete message"
    }

    class GenerateDeleteMessage() {
        private lateinit var messageBuilder: MessageBuilder

        @Mock
        private lateinit var resources: Resources

        @Mock
        private lateinit var context: Context

        @Before
        fun setUp() {
            MockitoAnnotations.openMocks(this)
            whenever(context.resources).thenReturn(resources)
            messageBuilder = MessageBuilder(context)
        }

        private fun assertDeleteMessage(docInfo: DocumentInfo, resId: Int) {
            whenever(
                resources.getString(
                    eq(resId),
                    eq(docInfo.displayName)
                )
            ).thenReturn(EXPECTED_MESSAGE)
            assertEquals(messageBuilder.generateDeleteMessage(listOf(docInfo)), EXPECTED_MESSAGE)
        }

        private fun assertQuantityDeleteMessage(docInfos: List<DocumentInfo>, resId: Int) {
            whenever(
                resources.getQuantityString(
                    eq(resId),
                    eq(docInfos.size),
                    eq(docInfos.size)
                )
            ).thenReturn(EXPECTED_MESSAGE)
            assertEquals(messageBuilder.generateDeleteMessage(docInfos), EXPECTED_MESSAGE)
        }

        @Test
        fun testGenerateDeleteMessage_singleFile() {
            assertDeleteMessage(
                createFile("Test doc"),
                R.string.delete_filename_confirmation_message
            )
        }

        @Test
        fun testGenerateDeleteMessage_singleDirectory() {
            assertDeleteMessage(
                createDirectory("Test doc"),
                R.string.delete_foldername_confirmation_message
            )
        }

        @Test
        fun testGenerateDeleteMessage_multipleFiles() {
            assertQuantityDeleteMessage(
                listOf(createFile("File 1"), createFile("File 2")),
                R.plurals.delete_files_confirmation_message
            )
        }

        @Test
        fun testGenerateDeleteMessage_multipleDirectories() {
            assertQuantityDeleteMessage(
                listOf(
                    createDirectory("Directory 1"),
                    createDirectory("Directory 2")
                ),
                R.plurals.delete_folders_confirmation_message
            )
        }

        @Test
        fun testGenerateDeleteMessage_mixedFilesAndDirectories() {
            assertQuantityDeleteMessage(
                listOf(createFile("File 1"), createDirectory("Directory 1")),
                R.plurals.delete_items_confirmation_message
            )
        }
    }

    @RunWith(Parameterized::class)
    class GenerateListMessage() {
        private lateinit var messageBuilder: MessageBuilder

        @Mock
        private lateinit var resources: Resources

        @Mock
        private lateinit var context: Context

        @Before
        fun setUp() {
            MockitoAnnotations.openMocks(this)
            whenever(context.resources).thenReturn(resources)
            messageBuilder = MessageBuilder(context)
        }

        data class ListMessageData(
            val dialogType: Int,
            val opType: Int = OPERATION_UNKNOWN,
            val resId: Int
        )

        companion object {
            @Parameterized.Parameters(name = "{0}")
            @JvmStatic
            fun parameters() =
                listOf(
                    ListMessageData(
                        dialogType = DIALOG_TYPE_CONVERTED,
                        resId = R.plurals.copy_converted_warning_content,
                    ),
                    ListMessageData(
                        dialogType = DIALOG_TYPE_FAILURE,
                        opType = OPERATION_COPY,
                        resId = R.plurals.copy_failure_alert_content,
                    ),
                    ListMessageData(
                        dialogType = DIALOG_TYPE_FAILURE,
                        opType = OPERATION_COMPRESS,
                        resId = R.plurals.compress_failure_alert_content,
                    ),
                    ListMessageData(
                        dialogType = DIALOG_TYPE_FAILURE,
                        opType = OPERATION_EXTRACT,
                        resId = R.plurals.extract_failure_alert_content,
                    ),
                    ListMessageData(
                        dialogType = DIALOG_TYPE_FAILURE,
                        opType = OPERATION_DELETE,
                        resId = R.plurals.delete_failure_alert_content,
                    ),
                    ListMessageData(
                        dialogType = DIALOG_TYPE_FAILURE,
                        opType = OPERATION_MOVE,
                        resId = R.plurals.move_failure_alert_content,
                    ),
                )
        }

        @Parameterized.Parameter(0)
        lateinit var testData: ListMessageData

        @Test
        fun testGenerateListMessage() {
            whenever(
                resources.getQuantityString(
                    eq(testData.resId),
                    eq(2),
                    anyString(),
                )
            ).thenReturn(EXPECTED_MESSAGE)
            assertEquals(
                messageBuilder.generateListMessage(
                    testData.dialogType,
                    testData.opType,
                    listOf(createFile("File 1")),
                    listOf(Uri.parse("content://random-uri")),
                    listOf("/a/path")
                ),
                EXPECTED_MESSAGE
            )
        }
    }
}

fun createFile(displayName: String): DocumentInfo {
    val doc = DocumentInfo()
    doc.displayName = displayName
    return doc
}

fun createDirectory(displayName: String): DocumentInfo {
    val doc = DocumentInfo()
    doc.displayName = displayName
    doc.mimeType = MIME_TYPE_DIR
    return doc
}
