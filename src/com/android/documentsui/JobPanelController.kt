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
package com.android.documentsui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.text.format.Formatter
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.android.documentsui.JobPanelViewModel.MenuIconState
import com.android.documentsui.JobPanelViewModel.ProgressViewModel
import com.android.documentsui.base.Menus
import com.android.documentsui.services.FileOperationService
import com.android.documentsui.services.FileOperations
import com.android.documentsui.services.Job
import com.android.documentsui.services.JobProgress
import com.android.documentsui.util.FormatUtils
import com.android.documentsui.util.Material3Config.Companion.getRes
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.shape.ShapeAppearanceModel

/**
 * Adds a gap between items in a vertical Recycler View.
 */
private class VerticalMarginItemDecoration(
    private val marginSize: Int
) : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        if (parent.getChildAdapterPosition(view) > 0) {
            outRect.top = marginSize
        }
    }
}

/**
 * JobPanelController is responsible for receiving broadcast updates from the [FileOperationService]
 * and updating a given menu item to reflect the current progress.
 *
 * @param activityContext Context used to receive broadcasts and access resources for UI.
 * @param viewModel View model containing the state of the job panel.
 */
class JobPanelController(
    private val activityContext: Context,
    private val actions: ActionHandler,
    private val viewModel: JobPanelViewModel,
) : BroadcastReceiver(),
    DefaultLifecycleObserver {
    companion object {
        private const val TAG = "JobPanelController"
        private const val MAX_PROGRESS = 100
    }

    private class ProgressItemHolder(
        private val controller: JobPanelController,
        private val cardView: View,
    ) : RecyclerView.ViewHolder(cardView) {

        private val context = cardView.context

        // Header elements.
        private val titleView = cardView.findViewById<TextView>(
            getRes(R.id.job_progress_item_title)
        )
        private val toggleExpandButton =
            cardView.findViewById<MaterialButton>(getRes(R.id.job_progress_item_expand))

        // Body elements.
        private val progressView =
            cardView.findViewById<LinearProgressIndicator>(getRes(R.id.job_progress_item_progress))
        private val primaryStatusView =
            cardView.findViewById<TextView>(getRes(R.id.job_progress_item_primary_status))
        private val statusSeparator =
            cardView.findViewById<TextView>(getRes(R.id.job_progress_item_status_separator))
        private val secondaryStatusView =
            cardView.findViewById<TextView>(getRes(R.id.job_progress_item_secondary_status))

        // Buttons
        private val cancelButton = cardView.findViewById<Button>(
            getRes(R.id.job_progress_item_cancel)
        )
        private val showInFolderButton =
            cardView.findViewById<Button>(getRes(R.id.job_progress_item_show_in_folder))
        private val dismissButton = cardView.findViewById<Button>(
            getRes(R.id.job_progress_item_dismiss)
        )

        fun setJobProgress(jobProgress: JobProgress, expanded: Boolean) {
            titleView.text = jobProgress.msg
            if (expanded) {
                titleView.isSingleLine = false
                toggleExpandButton.icon =
                    context.getDrawable(getRes(R.drawable.ic_job_progress_collapse))
            } else {
                titleView.isSingleLine = true
                toggleExpandButton.icon =
                    context.getDrawable(getRes(R.drawable.ic_job_progress_expand))
            }

            updateProgressBar(jobProgress)
            setStatusText(jobProgress, expanded)

            cardView.setOnClickListener { controller.toggleExpanded(jobProgress.id) }
            toggleExpandButton.setOnClickListener { controller.toggleExpanded(jobProgress.id) }

            cancelButton.isVisible = expanded && !jobProgress.isFinal
            if (cancelButton.isVisible) {
                cancelButton.setOnClickListener { FileOperations.cancel(context, jobProgress.id) }
            }

            dismissButton.isVisible = expanded && jobProgress.isFinal
            if (dismissButton.isVisible) {
                dismissButton.setOnClickListener { controller.dismissProgress(jobProgress.id) }
            }

            if (expanded && jobProgress.isFinal && jobProgress.destination != null) {
                showInFolderButton.isVisible = true
                showInFolderButton.setOnClickListener {
                    controller.showInFolder(jobProgress)
                }
            } else {
                showInFolderButton.isVisible = false
            }
        }

        private fun updateProgressBar(jobProgress: JobProgress) {
            progressView.let {
                it.isGone = jobProgress.isFinal
                it.isIndeterminate = jobProgress.isIndeterminate
                if (!it.isIndeterminate) {
                    it.progress = jobProgress.toPercent().toInt()
                }
            }
        }

        private fun setStatusText(jobProgress: JobProgress, expanded: Boolean) {
            primaryStatusView.isGone = false
            secondaryStatusView.isGone = false
            if (jobProgress.state == Job.STATE_COMPLETED) {
                if (jobProgress.hasFailures) {
                    primaryStatusView.setTextAppearance(
                        getRes(R.style.JobProgressItemStatusText_Failure)
                    )
                    primaryStatusView.text = context.getString(
                        getRes(R.string.job_progress_item_failed)
                    )
                    secondaryStatusView.isGone = expanded
                    secondaryStatusView.text =
                        context.getString(getRes(R.string.job_progress_item_see_details))
                } else {
                    primaryStatusView.setTextAppearance(
                        getRes(R.style.JobProgressItemStatusText_Success)
                    )
                    primaryStatusView.text =
                        context.getString(getRes(R.string.job_progress_item_completed))
                    secondaryStatusView.text = getCompletionStatusString(jobProgress.operationType)
                }
            } else if (expanded && jobProgress.state == Job.STATE_SET_UP &&
                !jobProgress.isIndeterminate) {
                primaryStatusView.setTextAppearance(getRes(R.style.JobProgressItemStatusText))
                primaryStatusView.text = context.getString(
                    getRes(R.string.job_progress_item_byte_progress),
                    Formatter.formatFileSize(context, jobProgress.currentBytes),
                    Formatter.formatFileSize(context, jobProgress.requiredBytes),
                )
                secondaryStatusView.text = context.getString(getRes(R.string.copy_remaining),
                    FormatUtils.formatDuration(jobProgress.msRemaining))
            } else {
                primaryStatusView.isGone = true
                secondaryStatusView.isGone = true
            }
            statusSeparator.isGone = primaryStatusView.isGone || secondaryStatusView.isGone
        }

        private fun getCompletionStatusString(@FileOperationService.OpType opType: Int): String {
            return when (opType) {
                FileOperationService.OPERATION_COPY -> context.getString(
                    getRes(R.string.copy_completed)
                )

                FileOperationService.OPERATION_MOVE -> context.getString(
                    getRes(R.string.move_completed)
                )
                FileOperationService.OPERATION_DELETE ->
                    context.getString(getRes(R.string.delete_completed))
                FileOperationService.OPERATION_COMPRESS ->
                    context.getString(getRes(R.string.compress_completed))
                FileOperationService.OPERATION_EXTRACT ->
                    context.getString(getRes(R.string.extract_completed))
                else -> ""
            }
        }
    }

    private class ProgressListAdapter(private val controller: JobPanelController) :
        ListAdapter<ProgressViewModel, ProgressItemHolder>(JobDiffCallback) {

        companion object {
            // Constants for the different view types created by this adapter. The type depends on
            // the position of the item in the list.
            private const val VIEW_MIDDLE = 0
            private const val VIEW_TOP = 1
            private const val VIEW_BOTTOM = 2
            private const val VIEW_TOP_BOTTOM = 3
        }

        override fun getItemViewType(position: Int): Int {
            if (itemCount == 1) return VIEW_TOP_BOTTOM
            if (position == 0) return VIEW_TOP
            if (position == itemCount - 1) return VIEW_BOTTOM
            return VIEW_MIDDLE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProgressItemHolder {
            val context = parent.context
            val view = LayoutInflater.from(context)
                .inflate(getRes(R.layout.job_progress_item), parent, false) as MaterialCardView
            if (viewType != 0) {
                val outerRadius =
                    context.resources.getDimension(getRes(R.dimen.job_progress_list_corner_radius))
                view.shapeAppearanceModel = ShapeAppearanceModel
                    .builder(context, getRes(R.style.JobProgressItemCardBaseShape), 0)
                    .apply {
                        if (viewType == VIEW_TOP_BOTTOM || viewType == VIEW_TOP) {
                            setTopLeftCornerSize(outerRadius)
                            setTopRightCornerSize(outerRadius)
                        }
                        if (viewType == VIEW_TOP_BOTTOM || viewType == VIEW_BOTTOM) {
                            setBottomLeftCornerSize(outerRadius)
                            setBottomRightCornerSize(outerRadius)
                        }
                    }.build()
            }
            return ProgressItemHolder(controller, view)
        }

        override fun onBindViewHolder(holder: ProgressItemHolder, position: Int) {
            val (jobProgress, expanded) = getItem(position)
            holder.setJobProgress(jobProgress, expanded)
        }

        object JobDiffCallback : DiffUtil.ItemCallback<ProgressViewModel>() {
            override fun areItemsTheSame(oldModel: ProgressViewModel, newModel: ProgressViewModel) =
                oldModel.jobProgress.id == newModel.jobProgress.id

            override fun areContentsTheSame(
                oldModel: ProgressViewModel,
                newModel: ProgressViewModel
            ) = oldModel == newModel
        }
    }

    /** Current menu item being controlled by this class. */
    private var menuItem: MenuItem? = null

    /** Current panel popup shown if any. */
    private var popup: PopupWindow? = null

    /** Adapter used to display JobProgresses in the recycler list. */
    private var progressListAdapter: ProgressListAdapter? = null

    init {
        val filter = IntentFilter(FileOperationService.ACTION_PROGRESS)
        activityContext.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun updateMenuItem(menuIconState: MenuIconState, animate: Boolean) {
        if (menuIconState is MenuIconState.INVISIBLE) {
            popup?.dismiss()
        }

        menuItem?.let {
            Menus.setEnabledAndVisible(it, menuIconState !is MenuIconState.INVISIBLE)
            val icon = it.actionView!!
                .findViewById<ProgressBar>(R.id.job_progress_toolbar_indicator)
            when (menuIconState) {
                is MenuIconState.INDETERMINATE -> icon.isIndeterminate = true
                is MenuIconState.VISIBLE -> icon.apply {
                    isIndeterminate = false
                    setProgress(menuIconState.totalProgress, animate)
                }
                is MenuIconState.INVISIBLE -> {}
            }
            val badge = it.actionView!!.findViewById<ImageView>(R.id.job_progress_toolbar_badge)
            badge.isVisible = menuIconState.hasFailures
        }
    }

    /**
     * Sets the menu item controlled by this class. The item's actionView must be a [ProgressBar].
     */
    @Suppress("ktlint:standard:comment-wrapping")
    fun setMenuItem(newMenuItem: MenuItem) {
        val progressIcon = newMenuItem.actionView!!
            .findViewById<ProgressBar>(R.id.job_progress_toolbar_indicator)
        progressIcon.max = MAX_PROGRESS
        progressIcon.setOnClickListener { view ->
            val panel = LayoutInflater.from(activityContext).inflate(
                getRes(R.layout.job_progress_panel),
                /* root= */ null
            )
            val listAdapter = ProgressListAdapter(this)
            listAdapter.submitList(ArrayList(viewModel.currentJobs.values))
            panel.findViewById<RecyclerView>(getRes(R.id.job_progress_list)).apply {
                layoutManager = LinearLayoutManager(context)
                addItemDecoration(VerticalMarginItemDecoration(
                    context.resources.getDimensionPixelSize(getRes(R.dimen.job_progress_list_gap))
                ))
                itemAnimator = null
                adapter = listAdapter
                if (viewModel.listState != null) {
                    layoutManager?.onRestoreInstanceState(viewModel.listState)
                    viewModel.listState = null
                }
            }
            progressListAdapter = listAdapter
            val popupWidth =
                activityContext.resources.getDimension(
                    getRes(R.dimen.job_progress_panel_width)
                ) + activityContext.resources.getDimension(
                    getRes(R.dimen.job_progress_panel_margin)
                )
            popup = PopupWindow(
                /* contentView= */ panel,
                /* width= */ popupWidth.toInt(),
                /* height= */ ViewGroup.LayoutParams.WRAP_CONTENT,
                /* focusable= */ true
            ).apply {
                setOnDismissListener {
                    progressListAdapter = null
                    popup = null
                }
                showAsDropDown(
                    /* anchor= */ view,
                    /* xoff= */ 0,
                    /* yoff= */ 0,
                    /* gravity= */ Gravity.TOP or Gravity.END,
                )
            }
        }
        // Restore the popup if we had saved state.
        if (viewModel.listState != null) {
            progressIcon.callOnClick()
        }
        menuItem = newMenuItem
        // Don't animate for the initial state update.
        updateMenuItem(viewModel.getMenuState(), animate = false)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // We need to save the popup's UI state and manually dismiss the popup, as it somehow
        // stays alive even if the activity is destroyed due to a configuration change.
        viewModel.listState = popup?.contentView
            ?.findViewById<RecyclerView>(R.id.job_progress_list)?.layoutManager
            ?.onSaveInstanceState()
        popup?.dismiss()
    }

    override fun onReceive(context: Context?, intent: Intent) {
        val progresses = intent.getParcelableArrayListExtra(
            FileOperationService.EXTRA_PROGRESS,
            JobProgress::class.java
        )
        viewModel.updateProgress(progresses!!)
        updateMenuItem(viewModel.getMenuState(), animate = true)
        progressListAdapter?.submitList(ArrayList(viewModel.currentJobs.values))
    }

    private fun dismissProgress(id: String) {
        viewModel.dismissProgress(id)
        updateMenuItem(viewModel.getMenuState(), animate = true)
        progressListAdapter?.submitList(ArrayList(viewModel.currentJobs.values))
    }

    private fun toggleExpanded(id: String) {
        viewModel.toggleExpanded(id)
        progressListAdapter?.submitList(ArrayList(viewModel.currentJobs.values))
    }

    private fun showInFolder(jobProgress: JobProgress) {
        actions.jumpToDirectory(jobProgress.destination)
        popup?.dismiss()
    }
}
