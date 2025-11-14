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
package com.android.documentsui.picker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.selection.SelectionTracker
import com.android.documentsui.DocsSelectionHelper
import com.android.documentsui.R
import com.android.documentsui.base.State.ACTION_GET_CONTENT
import com.android.documentsui.base.State.ACTION_OPEN
import com.android.documentsui.base.State.ActionType
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.button.MaterialButton

/**
 * Display pick confirmation bar for selecting one or more files. This is only used when the
 * useMaterial3 is enabled.
 */
class PickFilesFragment : Fragment() {
    private val resetObserver = object : DocsSelectionHelper.ResetObserver() {
        override fun onReset() {
            // Only add the selectionObserver once the selectionMgr is initialised (reset).
            selectionMgr!!.addObserver(selectionObserver)
        }
    }
    private val selectionObserver = object : SelectionTracker.SelectionObserver<String>() {
        override fun onSelectionChanged() {
            togglePickButton()
        }
    }
    private var selectionMgr: DocsSelectionHelper? = null
    private val pickListener: View.OnClickListener = View.OnClickListener {
        actionHandler!!.pickSelected()
    }

    private val cancelListener: View.OnClickListener = View.OnClickListener {
        actionHandler!!.cancelPicking()
    }

    private var actionHandler: ActionHandler<PickActivity?>? = null
    private var pick: MaterialButton? = null
    private var cancel: MaterialButton? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val containerView =
            inflater.inflate(getRes(R.layout.fragment_pick_files_m3), container, false)

        actionHandler = (getActivity() as PickActivity).getInjector()!!.actions

        pick = containerView!!.findViewById(getRes(R.id.button_pick))
        pick!!.setOnClickListener(pickListener)

        cancel = containerView!!.findViewById(getRes(R.id.button_cancel))
        cancel!!.setOnClickListener(cancelListener)

        selectionMgr = (getActivity() as PickActivity).getInjector().selectionMgr
        selectionMgr?.addResetObserver(resetObserver)

        togglePickButton()
        return containerView
    }

    override fun onDestroyView() {
        super.onDestroyView()
        selectionMgr?.removeResetObserver(resetObserver)
    }

    /**
     * Enables/disables the pick button based on the state of selection.
     */
    private fun togglePickButton() {
        if (!isUseMaterial3FlagEnabled()) {
            return
        }
        // The pick button is only available when the user has selected files.
        pick!!.setEnabled(selectionMgr?.hasSelection() ?: false)
    }

    companion object {
        private const val TAG: String = "PickFilesFragment"

        @JvmStatic
        fun show(
            fm: FragmentManager,
            @ActionType action: Int
        ) {
            if (action != ACTION_GET_CONTENT && action != ACTION_OPEN) {
                return
            }

            // Fragment will be restored by FragmentManager automatically.
            if (get(fm) != null) {
                return
            }

            val fragment = PickFilesFragment()
            val ft = fm.beginTransaction()
            ft.replace(R.id.container_save, fragment, TAG)
            ft.commitNowAllowingStateLoss()
        }

        @JvmStatic
        fun get(fm: FragmentManager): PickFilesFragment? {
            return fm.findFragmentByTag(TAG) as PickFilesFragment?
        }
    }
}
