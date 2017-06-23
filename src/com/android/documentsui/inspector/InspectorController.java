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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.view.View;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.ui.Snackbars;
import java.util.function.Consumer;
/**
 * A controller that coordinates retrieving document information and sending it to the view.
 */
public final class InspectorController {

    private final Loader mLoader;
    private final Consumer<DocumentInfo> mHeader;
    private final Consumer<DocumentInfo> mDetails;
    private final Consumer<DocumentInfo> mDebugView;
    private final boolean mShowDebug;
    private final Context mContext;
    private final ProvidersAccess mProviders;
    private final Runnable mShowSnackbar;

    /**
     * InspectorControllerTest relies on this controller.
     */
    @VisibleForTesting
    public InspectorController(Context context, Loader loader, ProvidersAccess providers,
            boolean showDebug, Consumer<DocumentInfo> header, Consumer<DocumentInfo> details,
            Consumer<DocumentInfo> debugView, Runnable showSnackbar) {

        checkArgument(context != null);
        checkArgument(loader != null);
        checkArgument(providers != null);
        checkArgument(header != null);
        checkArgument(details != null);
        checkArgument(debugView != null);
        checkArgument(showSnackbar != null);

        mContext = context;
        mLoader = loader;
        mShowDebug = showDebug;
        mProviders = providers;
        mHeader = header;
        mDetails = details;
        mDebugView = debugView;
        mShowSnackbar = showSnackbar;
    }

    public InspectorController(Activity activity, Loader loader, View layout, boolean showDebug) {

        this(activity,
                loader,
                DocumentsApplication.getProvidersCache (activity),
                showDebug,
                (HeaderView) layout.findViewById(R.id.inspector_header_view),
                (DetailsView) layout.findViewById(R.id.inspector_details_view),
                (DebugView) layout.findViewById(R.id.inspector_debug_view),
                () -> {
                    // using a runnable to support unit testing this feature.
                    Snackbars.showInspectorError(activity);
                }
        );
        if (showDebug) {
            layout.findViewById(R.id.inspector_debug_view).setVisibility(View.VISIBLE);
        }
    }

    public void reset() {
        mLoader.reset();
    }

    public void loadInfo(Uri uri) {
        mLoader.load(uri, this::updateView);
    }

    /**
     * Updates the view.
     */
    @Nullable
    public void updateView(@Nullable DocumentInfo docInfo) {

        if (docInfo == null) {
            mShowSnackbar.run();
        }
        else {
            mHeader.accept(docInfo);
            mDetails.accept(docInfo);

            if (mShowDebug) {
                mDebugView.accept(docInfo);
            }
        }
    }

    /**
     * Shows the selected document in it's content provider.
     *
     * @param DocumentInfo whose flag FLAG_SUPPORTS_SETTINGS is set.
     */
    public void showInProvider(Uri uri) {

        Intent intent = new Intent(DocumentsContract.ACTION_DOCUMENT_SETTINGS);
        intent.setPackage(mProviders.getPackageName(uri.getAuthority()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(uri);
        mContext.startActivity(intent);
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