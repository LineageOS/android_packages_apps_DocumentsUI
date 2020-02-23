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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.android.documentsui.base.UserId;

/**
 * Forwards a cross-profile startActivityForResult intent.
 */
public class ForResultForwarderActivity extends Activity {

    private static final String TAG = "ForResultForwarderActiv";
    private static final String EXTRA_INTENT = "EXTRA_INTENT";
    private static final String EXTRA_USER = "EXTRA_USER";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();

        if (!intent.hasExtra(EXTRA_INTENT) || !intent.hasExtra(EXTRA_USER)) {
            Log.e(TAG, "Missing intent or user");
            setResult(Activity.RESULT_CANCELED);
            finish();
            return;
        }

        Intent targetIntent = intent.getParcelableExtra(EXTRA_INTENT);
        // We should never have the default value because of the above check
        UserId targetUserId = UserId.of(intent.getIntExtra(EXTRA_USER, /* defaultValue= */ -1));

        targetIntent.addFlags(
                Intent.FLAG_ACTIVITY_FORWARD_RESULT | Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);
        try {
            targetUserId.startActivityAsUser(this, targetIntent);
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to forward activity");
            setResult(Activity.RESULT_CANCELED);
        }
        finish();
    }

    public static Intent getIntent(Context context, Intent intent, UserId targetUserId) {
        Intent forwarder = new Intent(context, ForResultForwarderActivity.class);
        forwarder.putExtra(EXTRA_INTENT, intent);
        forwarder.putExtra(EXTRA_USER, targetUserId.getIdentifier());
        return forwarder;
    }
}
