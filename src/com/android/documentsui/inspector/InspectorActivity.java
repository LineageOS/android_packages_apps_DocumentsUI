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

import static androidx.core.util.Preconditions.checkArgument;

import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.loader.app.LoaderManager;

import com.android.documentsui.R;
import com.android.documentsui.base.Shared;
import com.android.documentsui.inspector.InspectorController.DataSupplier;

import com.google.android.material.appbar.AppBarLayout;

public class InspectorActivity extends AppCompatActivity {

    private InspectorController mController;
    private View mView;
    private Toolbar mToolbar;
    private @ColorInt int mTitleColor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.inspector_activity);

        mToolbar = findViewById(R.id.toolbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        initRes();

        AppBarLayout appBarLayout = findViewById(R.id.appBar);
        appBarLayout.addOnOffsetChangedListener(this::onOffsetChanged);

        final DataSupplier loader = new RuntimeDataSupplier(this, LoaderManager.getInstance(this));

        mView = findViewById(R.id.inspector_root);
        mController = new InspectorController(this, loader, mView,
                getIntent().getStringExtra(Intent.EXTRA_TITLE),
                getIntent().getBooleanExtra(Shared.EXTRA_SHOW_DEBUG, false));
    }

    private void onOffsetChanged(AppBarLayout layout, int offset) {
        int diff = layout.getTotalScrollRange() - Math.abs(offset);
        if (diff <= 0) {
            //Collapsing tool bar is collapsed, recover to original bar present.
            mToolbar.getBackground().setAlpha(0);
            setActionBarItemColor(mTitleColor);
        } else {
            float ratio = (float) diff / (float) layout.getTotalScrollRange();
            int alpha = (int) (ratio * 255);
            mToolbar.getBackground().setAlpha(alpha);
            setActionBarItemColor(Color.WHITE);
        }
    }

    private void setActionBarItemColor(int color) {
        mToolbar.setTitleTextColor(color);
        mToolbar.getNavigationIcon().setTint(color);
        mToolbar.getOverflowIcon().setTint(color);
    }

    private void initRes() {
        TypedArray ta =
                this.obtainStyledAttributes(R.style.ActionBarTheme, R.styleable.ActionBarView);
        mTitleColor = ta.getColor(R.styleable.ActionBarView_android_textColorPrimary, Color.BLACK);
        ta.recycle();
    }

    @Override
    protected void onStart() {
        super.onStart();
        Uri uri = getIntent().getData();
        checkArgument(uri.getScheme().equals("content"));
        mController.loadInfo(uri);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mController.reset();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
