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

import android.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import com.android.documentsui.R;
import com.android.documentsui.inspector.InspectorController.Loader;

/**
 * Displays the Properties view in Files.
 */
public class DocumentInspectorFragment extends Fragment {

    private static final String DOC_URI_ARG = "docUri";
    private InspectorController mController;
    private LinearLayout mView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        final Loader loader = new DocumentLoader(getActivity(), getLoaderManager());
        mView = (LinearLayout) inflater.inflate(R.layout.document_inspector_fragment,
                container, false);
        mController = new InspectorController(getActivity(), loader, mView);
        return mView;
    }

    @Override
    public void onStart() {
        super.onStart();
        Uri uri = (Uri) getArguments().get(DOC_URI_ARG);
        mController.loadInfo(uri);
    }

    @Override
    public void onStop() {
        super.onStop();
        mController.reset();
    }

    /**
     * Creates a fragment and sets a Uri as an argument.
     */
    public static DocumentInspectorFragment newInstance(Uri uri) {
        checkArgument(uri.getScheme().equals("content"));
        Bundle args = new Bundle();
        args.putParcelable(DOC_URI_ARG, uri);
        DocumentInspectorFragment fragment = new DocumentInspectorFragment();
        fragment.setArguments(args);
        return fragment;
    }
}
