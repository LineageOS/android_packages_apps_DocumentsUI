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

import android.net.Uri;
import android.support.annotation.Nullable;
import android.util.Log;
import com.android.documentsui.base.DocumentInfo;
import com.android.internal.util.Preconditions;
import java.util.function.Consumer;

/**
 * A controller that coordinates retrieving document information and sending it to the view.
 */
public final class InspectorController {

    private final Loader mLoader;

    public InspectorController(Loader loader) {
        checkArgument(loader != null);
        mLoader = loader;
    }

    public void reset() {
        mLoader.reset();
    }

    public void loadInfo(Uri uri) {
        mLoader.load(uri, this::updateView);
    }

    /**
     * Updates the view.
     *
     */
    @Nullable
    private void updateView(@Nullable DocumentInfo docInfo) {
        //TODO: update the view.
        Log.d("DocInfo", docInfo.toString());
    }

    /**
     * Interface for loading document metadata.
     */
    public interface Loader {

        /**
         * Starts the Asynchronous process of loading file data.
         *
         * @param uri - A content uri to query metadata from.
         * @param callback - Function to be called when the loader has finished loading metadata. A
         * DocumentInfo will be sent to this method. DocumentInfo may be null.
         */
        void load(Uri uri, Consumer<DocumentInfo> callback);

        /**
         * Deletes all loader id's when android lifecycle ends.
         */
        void reset();
    }
}
