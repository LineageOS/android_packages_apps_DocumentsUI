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

package com.android.documentsui;

import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.dirlist.DocumentDetails;
import com.android.documentsui.dirlist.Model;

public interface ActionHandler {

    void openSettings(RootInfo root);

    /**
     * Drops documents on a root.
     */
    boolean dropOn(ClipData data, RootInfo root);

    /**
     * Attempts to eject the identified root. Returns a boolean answer to listener.
     */
    void ejectRoot(RootInfo root, BooleanConsumer listener);

    void showAppDetails(ResolveInfo info);

    void openRoot(RootInfo root);

    void openRoot(ResolveInfo app);

    void loadRoot(Uri uri);

    void openSelectedInNewWindow();

    void openInNewWindow(DocumentStack path);

    void pasteIntoFolder(RootInfo root);

    boolean viewDocument(DocumentDetails doc);

    boolean previewDocument(DocumentDetails doc);

    boolean openDocument(DocumentDetails doc);

    void showChooserForDoc(DocumentInfo doc);

    void openContainerDocument(DocumentInfo doc);

    void cutToClipboard();

    void copyToClipboard();

    /**
     * In general, selected = selection or single focused item
     */
    void deleteSelectedDocuments();

    void shareSelectedDocuments();

    /**
     * Called when initial activity setup is complete. Implementations
     * should override this method to set the initial location of the
     * app.
     */
    void initLocation(Intent intent);

    /**
     * Allow action handler to be initialized in a new scope.
     * @return
     */
    <T extends ActionHandler> T reset(Model model, boolean searchMode);
}
