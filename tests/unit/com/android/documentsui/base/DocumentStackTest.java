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

package com.android.documentsui.base;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.TestCase.assertTrue;

import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DocumentStackTest {
    private static final DocumentInfo DIR_1;
    private static final DocumentInfo DIR_2;

    private DocumentStack mStack;

    static {
        DIR_1 = new DocumentInfo();
        DIR_1.displayName = "firstDirectory";
        DIR_2 = new DocumentInfo();
        DIR_2.displayName = "secondDirectory";
    }

    @Before
    public void setUp() {
        mStack = new DocumentStack();
    }

    @Test
    public void testInitialStateEmpty() {
        assertFalse(mStack.hasLocationChanged());
    }

    @Test
    public void testPushDocument_ChangesLocation() {
        mStack.push(DIR_1);
        mStack.push(DIR_2);
        assertTrue(mStack.hasLocationChanged());
    }

    @Test
    public void testPushDocument_ModifiesStack() {
        mStack.push(DIR_1);
        mStack.push(DIR_2);
        assertEquals(DIR_2, mStack.peek());
    }

    @Test
    public void testPopDocument_ModifiesStack() {
        mStack.push(DIR_1);
        mStack.push(DIR_2);
        mStack.pop();
        assertEquals(DIR_1, mStack.peek());
    }
}
