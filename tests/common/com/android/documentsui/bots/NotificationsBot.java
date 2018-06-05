/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.documentsui.bots;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.provider.Settings;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObjectNotFoundException;
import android.support.test.uiautomator.UiSelector;
import android.text.TextUtils;
import android.view.KeyEvent;

/**
 * A test helper class for controlling notification items.
 */
public class NotificationsBot extends Bots.BaseBot {
    private static final String SETTINGS_PACKAGE_NAME = "com.android.settings";
    private static final String allow_res_name = "allow";
    private static final String turn_off_res_name = "notification_listener_disable_warning_confirm";

    public NotificationsBot(UiDevice device, Context context, int timeout) {
        super(device, context, timeout);
    }

    public void setNotificationAccess(Activity activity, boolean enabled)
            throws UiObjectNotFoundException, NameNotFoundException {
        Context testContext = InstrumentationRegistry.getContext();

        if(isNotificationAccessEnabled(
                mContext.getContentResolver(), testContext.getPackageName()) == enabled) {
            return;
        }

        Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
        activity.startActivity(intent);
        mDevice.waitForIdle();

        String appName = testContext.getPackageManager().getApplicationLabel(
                testContext.getApplicationInfo()).toString();
        clickLabel(appName);

        Context settings_context = mContext.createPackageContext(SETTINGS_PACKAGE_NAME,
                Context.CONTEXT_RESTRICTED);
        String label_res_name = enabled ? allow_res_name : turn_off_res_name;
        int res_id = settings_context.getResources().getIdentifier(label_res_name,
                "string", SETTINGS_PACKAGE_NAME);

        clickLabel(settings_context.getResources().getString(res_id));
        mDevice.pressKeyCode(KeyEvent.KEYCODE_BACK);
        mDevice.waitForIdle();
    }

    private boolean isNotificationAccessEnabled(ContentResolver resolver, String pkgName) {
        String listeners = Settings.Secure.getString(resolver, "enabled_notification_listeners");
        if (!TextUtils.isEmpty(listeners)) {
            String[] list = listeners.split(":");
            for(String item : list) {
                if(item.startsWith(pkgName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void clickLabel(String label) throws UiObjectNotFoundException {
        UiSelector selector = new UiSelector().textMatches("(?i)" + label);
        mDevice.findObject(selector).click();
        mDevice.waitForIdle();
    }
}
