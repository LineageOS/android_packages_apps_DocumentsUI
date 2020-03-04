/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.documentsui;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.documentsui.testing.TestProvidersAccess;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class ForResultForwarderActivityTest {

    private Context mTargetContext;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testActivityNotExported() {
        Intent originalIntent = new Intent("some_action");
        Intent intent = ForResultForwarderActivity.getIntent(mTargetContext, originalIntent,
                TestProvidersAccess.USER_ID);

        ResolveInfo info = mTargetContext.getPackageManager().resolveActivity(intent, 0);
        assertThat(info.activityInfo.getComponentName())
                .isEqualTo(new ComponentName(mTargetContext, ForResultForwarderActivity.class));
        assertThat(info.activityInfo.exported).isFalse();
    }
}
