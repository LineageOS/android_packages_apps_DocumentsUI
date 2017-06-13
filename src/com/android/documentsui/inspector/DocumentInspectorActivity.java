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

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toolbar;
import com.android.documentsui.R;

public class DocumentInspectorActivity extends Activity {

  private DocumentInspectorFragment mFragment;

  @Override
  public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);

      setContentView(R.layout.document_inspector_activity);

      Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
      setActionBar(toolbar);

      FragmentManager fragmentManager = getFragmentManager();
      mFragment = (DocumentInspectorFragment) fragmentManager.findFragmentById(
          R.id.fragment_container);

      if (mFragment == null) {
          Intent intent = getIntent();
          Uri docUri = intent.getData();

          mFragment = DocumentInspectorFragment.newInstance(docUri);
          fragmentManager.beginTransaction()
                  .add(R.id.fragment_container, mFragment)
                  .commit();
       }
  }
}