/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.documentsui.peek

import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import com.android.documentsui.R
import com.android.documentsui.base.DocumentInfo
import com.android.documentsui.util.Material3Config.Companion.getRes

/** Manager that controls the Peek UI. */
open class PeekViewManager(
    private val viewModel: PeekViewModel,
    private val container: FrameLayout,
    val fm: FragmentManager
) : Observer<Boolean?> {
    companion object {
        private const val TAG = "PeekViewManager"
    }

    private lateinit var peekFragment: PeekFragment

    init {
        initialize()
    }

    protected open fun initialize() {
        // Restore the Peek overlay if it was active.
        if (viewModel.overlayActive.value == true) {
            maybeInitializeFragment()
            setContainerVisibility(true)
        }
    }

    /**
     * Sets the Peek fragment. By either querying it if it has been restored by the fragment
     * manager, or by initializing it.
     */
    private fun maybeInitializeFragment() {
        // The fragment manager automatically handles state restoration: the fragment might already
        // exist.
        val existingFragment = fm.findFragmentById(getRes(R.id.peek_overlay))
        if (existingFragment == null) {
            peekFragment = PeekFragment()
            val ft: FragmentTransaction = fm.beginTransaction()
            ft.add(getRes(R.id.peek_overlay), peekFragment)
            ft.commitAllowingStateLoss()
        } else {
            peekFragment = existingFragment as PeekFragment
        }

        // Restore visibility.
        setContainerVisibility(viewModel.overlayActive.value == true)
    }

    /** This method is called every time viewModel.overlayActive changes its value. */
    override fun onChanged(value: Boolean?) {
        setContainerVisibility(value ?: false)
    }

    private fun setContainerVisibility(visible: Boolean) {
        container.visibility = if (visible) View.VISIBLE else View.GONE
    }

    open fun peekDocument(doc: DocumentInfo) {
        maybeInitializeFragment()
        if (!::peekFragment.isInitialized) {
            Log.e(TAG, "PeekFragment has not been initialized")
            return
        }
        viewModel.setDocInfoAndActivateOverlay(doc)
    }
}
