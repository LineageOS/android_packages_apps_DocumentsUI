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
package com.android.documentsui.queries;

import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import com.android.documentsui.base.EventHandler;

import java.util.ArrayList;
import java.util.List;

final class DebugCommandProcessor implements EventHandler<String> {

    private final List<EventHandler<String[]>> mCommands = new ArrayList<>();

    public DebugCommandProcessor() {
        if (Build.IS_DEBUGGABLE) {
            mCommands.add(new SetQuickViewerCommand());
        }
    }

    @VisibleForTesting
    DebugCommandProcessor(EventHandler<String[]>... commands) {
        for (EventHandler<String[]> c : commands) {
            mCommands.add(c);
        }
    }

    @Override
    public boolean accept(String query) {
        if (query.length() > 6 && query.substring(0, 6).equals("#debug")) {
            String[] tokens = query.substring(7).split("\\s+");
            for (EventHandler<String[]> command : mCommands) {
                if (command.accept(tokens)) {
                    return true;
                }
            }
            Log.d(SearchViewManager.TAG, "Unrecognized debug command: " + query);
        }
        return false;
    }
}
