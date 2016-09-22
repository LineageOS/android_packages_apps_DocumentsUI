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

package com.android.documentsui.testing;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.dirlist.DocumentDetails;

public class TestActionHandler extends ActionHandler<BaseActivity> {

    public final TestEventHandler<DocumentDetails> open = new TestEventHandler<>();
    public final TestEventHandler<DocumentDetails> view = new TestEventHandler<>();
    public final TestEventHandler<DocumentDetails> preview = new TestEventHandler<>();

    public TestActionHandler() {
        super(null);
    }

    @Override
    public boolean openDocument(DocumentDetails doc) {
        return open.accept(doc);
    }

    @Override
    public boolean viewDocument(DocumentDetails doc) {
        return view.accept(doc);
    }

    @Override
    public boolean previewDocument(DocumentDetails doc) {
        return preview.accept(doc);
    }
}
