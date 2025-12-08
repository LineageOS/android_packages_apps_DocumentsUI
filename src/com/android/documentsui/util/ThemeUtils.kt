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

@file:Suppress("ktlint:standard:filename")

package com.android.documentsui.util

import android.util.Log
import androidx.annotation.AnyRes
import com.android.documentsui.R
import com.android.documentsui.base.SharedMinimal.DEBUG
import com.android.documentsui.util.FlagUtils.Companion.isUseMaterial3FlagEnabled

/**
 * Mapping of resource IDs from the pre-material3 (aka legacy) theme to the new resource ID in the
 * material3 theme.
 */
private var idMapping: Map<Int, Int> = mapOf()
private var initialized = false

private const val TAG = "ThemeUtils"

/**
 * Only initialize the mapping when the use_material3 flag is enabled, because the IDs for the
 * Material3 version only exists then.
 */
@Suppress("ktlint:standard:max-line-length")
private fun initializeIdMapping() {
  idMapping = mapOf(
    R.bool.full_bar_search_view to R.bool.full_bar_search_view_m3,
    R.bool.show_docked_search to R.bool.show_docked_search_m3,
    R.color.app_background_color to R.color.app_background_color_m3,
    R.color.app_icon_background to R.color.app_icon_background_m3,
    R.color.background_floating to R.color.background_floating_m3,
    R.color.band_select_background to R.color.band_select_background_m3,
    R.color.band_select_border to R.color.band_select_border_m3,
    R.color.chip_background_disable_color to R.color.chip_background_disable_color_m3,
    R.color.color_surface_header to R.color.color_surface_header_m3,
    R.color.doc_list_item_subtitle_color to R.color.doc_list_item_subtitle_color_m3,
    R.color.downloads_icon_background to R.color.downloads_icon_background_m3,
    R.color.error_image_color to R.color.error_image_color_m3,
    R.color.hairline to R.color.hairline_m3,
    R.color.horizontal_breadcrumb_color to R.color.horizontal_breadcrumb_color_m3,
    R.color.item_action_icon to R.color.item_action_icon_m3,
    R.color.item_breadcrumb_background_hovered to R.color.item_breadcrumb_background_hovered_m3,
    R.color.item_details to R.color.item_details_m3,
    R.color.item_doc_grid_border to R.color.item_doc_grid_border_m3,
    R.color.item_doc_grid_tint to R.color.item_doc_grid_tint_m3,
    R.color.item_drag_shadow_background to R.color.item_drag_shadow_background_m3,
    R.color.item_drag_shadow_container_background to R.color.item_drag_shadow_container_background_m3,
    R.color.item_root_icon to R.color.item_root_icon_m3,
    R.color.item_root_primary_text to R.color.item_root_primary_text_m3,
    R.color.item_root_secondary_text to R.color.item_root_secondary_text_m3,
    R.color.list_divider_color to R.color.list_divider_color_m3,
    R.color.list_item_selected_background_color to R.color.list_item_selected_background_color_m3,
    R.color.menu_search_background to R.color.menu_search_background_m3,
    R.color.nav_bar_translucent to R.color.nav_bar_translucent_m3,
    R.color.primary to R.color.primary_m3,
    R.color.search_chip_background_color to R.color.search_chip_background_color_m3,
    R.color.search_chip_ripple_color to R.color.search_chip_ripple_color_m3,
    R.color.search_chip_stroke_color to R.color.search_chip_stroke_color_m3,
    R.color.search_chip_text_color to R.color.search_chip_text_color_m3,
    R.color.search_chip_text_selected_color to R.color.search_chip_text_selected_color_m3,
    R.color.secondary to R.color.secondary_m3,
    R.color.shortcut_background to R.color.shortcut_background_m3,
    R.color.shortcut_foreground to R.color.shortcut_foreground_m3,
    R.color.sort_list_text to R.color.sort_list_text_m3,
    R.color.tool_bar_gradient_max to R.color.tool_bar_gradient_max_m3,
    R.dimen.action_bar_elevation to R.dimen.action_bar_elevation_m3,
    R.dimen.action_bar_margin to R.dimen.action_bar_margin_m3,
    R.dimen.action_mode_text_size to R.dimen.action_mode_text_size_m3,
    R.dimen.apps_row_app_icon_size to R.dimen.apps_row_app_icon_size_m3,
    R.dimen.apps_row_exit_icon_margin_bottom to R.dimen.apps_row_exit_icon_margin_bottom_m3,
    R.dimen.apps_row_exit_icon_margin_top to R.dimen.apps_row_exit_icon_margin_top_m3,
    R.dimen.apps_row_item_width to R.dimen.apps_row_item_width_m3,
    R.dimen.bottom_bar_button_corner_radius to R.dimen.bottom_bar_button_corner_radius_m3,
    R.dimen.bottom_bar_button_height to R.dimen.bottom_bar_button_height_m3,
    R.dimen.bottom_bar_button_horizontal_padding to R.dimen.bottom_bar_button_horizontal_padding_m3,
    R.dimen.bottom_bar_height to R.dimen.bottom_bar_height_m3,
    R.dimen.bottom_bar_padding to R.dimen.bottom_bar_padding_m3,
    R.dimen.breadcrumb_item_height to R.dimen.breadcrumb_item_height_m3,
    R.dimen.breadcrumb_item_padding to R.dimen.breadcrumb_item_padding_m3,
    R.dimen.briefcase_icon_margin to R.dimen.briefcase_icon_margin_m3,
    R.dimen.briefcase_icon_size to R.dimen.briefcase_icon_size_m3,
    R.dimen.briefcase_icon_size_photo to R.dimen.briefcase_icon_size_photo_m3,
    R.dimen.button_corner_radius to R.dimen.button_corner_radius_m3,
    R.dimen.button_touch_size to R.dimen.button_touch_size_m3,
    R.dimen.check_icon_size to R.dimen.check_icon_size_m3,
    R.dimen.cross_profile_button_message_margin_top to R.dimen.cross_profile_button_message_margin_top_m3,
    R.dimen.dialog_content_padding_bottom to R.dimen.dialog_content_padding_bottom_m3,
    R.dimen.dialog_content_padding_top to R.dimen.dialog_content_padding_top_m3,
    R.dimen.dir_elevation to R.dimen.dir_elevation_m3,
    R.dimen.doc_header_height to R.dimen.doc_header_height_m3,
    R.dimen.doc_header_sort_icon_size to R.dimen.doc_header_sort_icon_size_m3,
    R.dimen.drag_shadow_height to R.dimen.drag_shadow_height_m3,
    R.dimen.drag_shadow_padding to R.dimen.drag_shadow_padding_m3,
    R.dimen.drag_shadow_radius to R.dimen.drag_shadow_radius_m3,
    R.dimen.drag_shadow_size to R.dimen.drag_shadow_size_m3,
    R.dimen.drag_shadow_width to R.dimen.drag_shadow_width_m3,
    R.dimen.drawer_edge_width to R.dimen.drawer_edge_width_m3,
    R.dimen.drop_icon_height to R.dimen.drop_icon_height_m3,
    R.dimen.drop_icon_width to R.dimen.drop_icon_width_m3,
    R.dimen.dropdown_sort_text_size to R.dimen.dropdown_sort_text_size_m3,
    R.dimen.dropdown_sort_widget_margin to R.dimen.dropdown_sort_widget_margin_m3,
    R.dimen.dropdown_sort_widget_size to R.dimen.dropdown_sort_widget_size_m3,
    R.dimen.fastscroll_default_thickness to R.dimen.fastscroll_default_thickness_m3,
    R.dimen.fastscroll_margin to R.dimen.fastscroll_margin_m3,
    R.dimen.fastscroll_minimum_range to R.dimen.fastscroll_minimum_range_m3,
    R.dimen.grid_container_padding to R.dimen.grid_container_padding_m3,
    R.dimen.grid_item_elevation to R.dimen.grid_item_elevation_m3,
    R.dimen.grid_item_icon_size to R.dimen.grid_item_icon_size_m3,
    R.dimen.grid_item_margin to R.dimen.grid_item_margin_m3,
    R.dimen.grid_item_radius to R.dimen.grid_item_radius_m3,
    R.dimen.grid_padding_horiz to R.dimen.grid_padding_horiz_m3,
    R.dimen.grid_padding_vert to R.dimen.grid_padding_vert_m3,
    R.dimen.grid_section_separator_height to R.dimen.grid_section_separator_height_m3,
    R.dimen.grid_width to R.dimen.grid_width_m3,
    R.dimen.header_message_horizontal_padding to R.dimen.header_message_horizontal_padding_m3,
    R.dimen.icon_size to R.dimen.icon_size_m3,
    R.dimen.inspector_header_height to R.dimen.inspector_header_height_m3,
    R.dimen.list_container_padding to R.dimen.list_container_padding_m3,
    R.dimen.list_divider_inset to R.dimen.list_divider_inset_m3,
    R.dimen.list_item_height to R.dimen.list_item_height_m3,
    R.dimen.list_item_icon_padding to R.dimen.list_item_icon_padding_m3,
    R.dimen.list_item_padding to R.dimen.list_item_padding_m3,
    R.dimen.list_item_thumbnail_size to R.dimen.list_item_thumbnail_size_m3,
    R.dimen.list_item_width to R.dimen.list_item_width_m3,
    R.dimen.material_round_radius to R.dimen.material_round_radius_m3,
    R.dimen.max_drawer_width to R.dimen.max_drawer_width_m3,
    R.dimen.profile_tab_margin_side to R.dimen.profile_tab_margin_side_m3,
    R.dimen.profile_tab_margin_top to R.dimen.profile_tab_margin_top_m3,
    R.dimen.profile_tab_padding to R.dimen.profile_tab_padding_m3,
    R.dimen.progress_bar_height to R.dimen.progress_bar_height_m3,
    R.dimen.refresh_icon_range to R.dimen.refresh_icon_range_m3,
    R.dimen.root_action_icon_size to R.dimen.root_action_icon_size_m3,
    R.dimen.root_icon_disabled_alpha to R.dimen.root_icon_disabled_alpha_m3,
    R.dimen.root_icon_margin to R.dimen.root_icon_margin_m3,
    R.dimen.root_icon_size to R.dimen.root_icon_size_m3,
    R.dimen.root_info_header_height to R.dimen.root_info_header_height_m3,
    R.dimen.root_info_header_horizontal_padding to R.dimen.root_info_header_horizontal_padding_m3,
    R.dimen.root_spacer_padding to R.dimen.root_spacer_padding_m3,
    R.dimen.search_bar_background_margin_end to R.dimen.search_bar_background_margin_end_m3,
    R.dimen.search_bar_background_margin_start to R.dimen.search_bar_background_margin_start_m3,
    R.dimen.search_bar_elevation to R.dimen.search_bar_elevation_m3,
    R.dimen.search_bar_icon_padding to R.dimen.search_bar_icon_padding_m3,
    R.dimen.search_bar_margin to R.dimen.search_bar_margin_m3,
    R.dimen.search_bar_radius to R.dimen.search_bar_radius_m3,
    R.dimen.search_bar_text_margin_end to R.dimen.search_bar_text_margin_end_m3,
    R.dimen.search_bar_text_margin_start to R.dimen.search_bar_text_margin_start_m3,
    R.dimen.search_bar_text_size to R.dimen.search_bar_text_size_m3,
    R.dimen.search_chip_group_margin to R.dimen.search_chip_group_margin_m3,
    R.dimen.search_chip_half_spacing to R.dimen.search_chip_half_spacing_m3,
    R.dimen.search_chip_icon_padding to R.dimen.search_chip_icon_padding_m3,
    R.dimen.search_chip_radius to R.dimen.search_chip_radius_m3,
    R.dimen.search_chip_spacing to R.dimen.search_chip_spacing_m3,
    R.dimen.tab_container_height to R.dimen.tab_container_height_m3,
    R.dimen.tab_height to R.dimen.tab_height_m3,
    R.dimen.tab_selector_indicator_height to R.dimen.tab_selector_indicator_height_m3,
    R.dimen.zoom_icon_size to R.dimen.zoom_icon_size_m3,
    R.drawable.band_select_overlay to R.drawable.band_select_overlay_m3,
    R.drawable.bottom_sheet_dialog_background to R.drawable.bottom_sheet_dialog_background_m3,
    R.drawable.breadcrumb_item_background to R.drawable.breadcrumb_item_background_m3,
    R.drawable.circle_button_background to R.drawable.circle_button_background_m3,
    R.drawable.drag_shadow_background to R.drawable.drag_shadow_background_m3,
    R.drawable.drop_badge_states to R.drawable.drop_badge_states_m3,
    R.drawable.dropdown_sort_widget_background to R.drawable.dropdown_sort_widget_background_m3,
    R.drawable.empty to R.drawable.empty_m3,
    R.drawable.fast_scroll_thumb_drawable to R.drawable.fast_scroll_thumb_drawable_m3,
    R.drawable.fast_scroll_track_drawable to R.drawable.fast_scroll_track_drawable_m3,
    R.drawable.generic_ripple_background to R.drawable.generic_ripple_background_m3,
    R.drawable.grid_item_background to R.drawable.grid_item_background_m3,
    R.drawable.hourglass to R.drawable.hourglass_m3,
    R.drawable.ic_action_clear to R.drawable.ic_action_clear_m3,
    R.drawable.ic_action_open to R.drawable.ic_action_open_m3,
    R.drawable.ic_arrow_back to R.drawable.ic_arrow_back_m3,
    R.drawable.ic_arrow_upward to R.drawable.ic_arrow_upward_m3,
    R.drawable.ic_breadcrumb_arrow to R.drawable.ic_breadcrumb_arrow_m3,
    R.drawable.ic_briefcase to R.drawable.ic_briefcase_m3,
    R.drawable.ic_briefcase_white to R.drawable.ic_briefcase_white_m3,
    R.drawable.ic_cab_cancel to R.drawable.ic_cab_cancel_m3,
    R.drawable.ic_check to R.drawable.ic_check_m3,
    R.drawable.ic_check_circle to R.drawable.ic_check_circle_m3,
    R.drawable.ic_chip_from_this_week to R.drawable.ic_chip_from_this_week_m3,
    R.drawable.ic_chip_large_files to R.drawable.ic_chip_large_files_m3,
    R.drawable.ic_create_new_folder to R.drawable.ic_create_new_folder_m3,
    R.drawable.ic_debug_menu to R.drawable.ic_debug_menu_m3,
    R.drawable.ic_dialog_alert to R.drawable.ic_dialog_alert_m3,
    R.drawable.ic_dialog_info to R.drawable.ic_dialog_info_m3,
    R.drawable.ic_done to R.drawable.ic_done_m3,
    R.drawable.ic_drop_copy_badge to R.drawable.ic_drop_copy_badge_m3,
    R.drawable.ic_exit_to_app to R.drawable.ic_exit_to_app_m3,
    R.drawable.ic_folder_shortcut to R.drawable.ic_folder_shortcut_m3,
    R.drawable.ic_hamburger to R.drawable.ic_hamburger_m3,
    R.drawable.ic_history to R.drawable.ic_history_m3,
    R.drawable.ic_menu_compress to R.drawable.ic_menu_compress_m3,
    R.drawable.ic_menu_copy to R.drawable.ic_menu_copy_m3,
    R.drawable.ic_menu_delete to R.drawable.ic_menu_delete_m3,
    R.drawable.ic_menu_extract to R.drawable.ic_menu_extract_m3,
    R.drawable.ic_menu_search to R.drawable.ic_menu_search_m3,
    R.drawable.ic_menu_share to R.drawable.ic_menu_share_m3,
    R.drawable.ic_menu_view_grid to R.drawable.ic_menu_view_grid_m3,
    R.drawable.ic_menu_view_list to R.drawable.ic_menu_view_list_m3,
    R.drawable.ic_reject_drop_badge to R.drawable.ic_reject_drop_badge_m3,
    R.drawable.ic_root_bugreport to R.drawable.ic_root_bugreport_m3,
    R.drawable.ic_root_recent to R.drawable.ic_root_recent_m3,
    R.drawable.ic_sort to R.drawable.ic_sort_m3,
    R.drawable.ic_sort_arrow to R.drawable.ic_sort_arrow_m3,
    R.drawable.ic_subdirectory_arrow to R.drawable.ic_subdirectory_arrow_m3,
    R.drawable.ic_usb_shortcut to R.drawable.ic_usb_shortcut_m3,
    R.drawable.ic_usb_storage to R.drawable.ic_usb_storage_m3,
    R.drawable.ic_user_profile to R.drawable.ic_user_profile_m3,
    R.drawable.ic_zoom_out to R.drawable.ic_zoom_out_m3,
    R.drawable.inspector_separator to R.drawable.inspector_separator_m3,
    R.drawable.item_doc_grid_border to R.drawable.item_doc_grid_border_m3,
    R.drawable.item_doc_grid_border_rounded to R.drawable.item_doc_grid_border_rounded_m3,
    R.drawable.launcher_screen to R.drawable.launcher_screen_m3,
    R.drawable.list_checker to R.drawable.list_checker_m3,
    R.drawable.list_divider to R.drawable.list_divider_m3,
    R.drawable.list_item_background to R.drawable.list_item_background_m3,
    R.drawable.menu_dropdown_panel to R.drawable.menu_dropdown_panel_m3,
    R.drawable.progress_indeterminate_horizontal_material_trimmed to R.drawable.progress_indeterminate_horizontal_material_trimmed_m3,
    R.drawable.root_item_background to R.drawable.root_item_background_m3,
    R.drawable.root_list_selector to R.drawable.root_list_selector_m3,
    R.drawable.roots_list_border to R.drawable.roots_list_border_m3,
    R.drawable.search_bar_background to R.drawable.search_bar_background_m3,
    R.drawable.share_off to R.drawable.share_off_m3,
    R.drawable.sort_widget_background to R.drawable.sort_widget_background_m3,
    R.drawable.splash_screen to R.drawable.splash_screen_m3,
    R.drawable.tab_border_rounded to R.drawable.tab_border_rounded_m3,
    R.drawable.vector_drawable_progress_indeterminate_horizontal_trimmed to R.drawable.vector_drawable_progress_indeterminate_horizontal_trimmed_m3,
    R.drawable.work_off to R.drawable.work_off_m3,
    R.layout.apps_item to R.layout.apps_item_m3,
    R.layout.apps_row to R.layout.apps_row_m3,
    R.layout.column_headers to R.layout.column_headers_m3,
    R.layout.dialog_delete_confirmation to R.layout.dialog_delete_confirmation_m3,
    R.layout.dialog_file_name to R.layout.dialog_file_name_m3,
    R.layout.dialog_sorting to R.layout.dialog_sorting_m3,
    R.layout.directory_app_bar to R.layout.directory_app_bar_m3,
    R.layout.directory_header to R.layout.directory_header_m3,
    R.layout.documents_activity to R.layout.documents_activity_m3,
    R.layout.drag_shadow_layout to R.layout.drag_shadow_layout_m3,
    R.layout.drawer_layout to R.layout.drawer_layout_m3,
    R.layout.drop_badge to R.layout.drop_badge_m3,
    R.layout.files_activity to R.layout.files_activity_m3,
    R.layout.fixed_layout to R.layout.fixed_layout_m3,
    R.layout.fragment_directory to R.layout.fragment_directory_m3,
    R.layout.fragment_pick_directory to R.layout.fragment_pick_directory_m3,
    R.layout.fragment_roots to R.layout.fragment_roots_m3,
    R.layout.fragment_save to R.layout.fragment_save_m3,
    R.layout.fragment_search to R.layout.fragment_search_m3,
    R.layout.inspector_action_view to R.layout.inspector_action_view_m3,
    R.layout.inspector_activity to R.layout.inspector_activity_m3,
    R.layout.inspector_header to R.layout.inspector_header_m3,
    R.layout.inspector_section_title to R.layout.inspector_section_title_m3,
    R.layout.item_dir_grid to R.layout.item_dir_grid_m3,
    R.layout.item_doc_grid to R.layout.item_doc_grid_m3,
    R.layout.item_doc_header_message to R.layout.item_doc_header_message_m3,
    R.layout.item_doc_inflated_message to R.layout.item_doc_inflated_message_m3,
    R.layout.item_doc_inflated_message_content to R.layout.item_doc_inflated_message_content_m3,
    R.layout.item_doc_inflated_message_cross_profile to R.layout.item_doc_inflated_message_cross_profile_m3,
    R.layout.item_doc_list to R.layout.item_doc_list_m3,
    R.layout.item_history to R.layout.item_history_m3,
    R.layout.item_photo_grid to R.layout.item_photo_grid_m3,
    R.layout.item_root to R.layout.item_root_m3,
    R.layout.item_root_header to R.layout.item_root_header_m3,
    R.layout.item_root_spacer to R.layout.item_root_spacer_m3,
    R.layout.navigation_breadcrumb_item to R.layout.navigation_breadcrumb_item_m3,
    R.layout.root_vertical_divider to R.layout.root_vertical_divider_m3,
    R.layout.search_chip_item to R.layout.search_chip_item_m3,
    R.layout.search_chip_row to R.layout.search_chip_row_m3,
    R.layout.shared_cell_content to R.layout.shared_cell_content_m3,
    R.layout.sort_list_item to R.layout.sort_list_item_m3,
    R.layout.table_key_value_row to R.layout.table_key_value_row_m3,
    R.menu.action_mode_menu to R.menu.action_mode_menu_m3,
    R.menu.activity to R.menu.activity_m3,
    R.menu.container_context_menu to R.menu.container_context_menu_m3,
    R.menu.dir_context_menu to R.menu.dir_context_menu_m3,
    R.menu.file_context_menu to R.menu.file_context_menu_m3,
    R.menu.mixed_context_menu to R.menu.mixed_context_menu_m3,
    R.menu.root_context_menu to R.menu.root_context_menu_m3,
    R.string.scrolling_behavior to R.string.scrolling_behavior_m3,
    R.style.ActionBarTheme to R.style.ActionBarThemeM3,
    R.style.ActionBarThemeCommon to R.style.ActionBarThemeCommonM3,
    R.style.AppsItemSubText to R.style.AppsItemSubTextM3,
    R.style.AutoCompleteText to R.style.AutoCompleteTextM3,
    R.style.AutoCompleteTextViewStyle to R.style.AutoCompleteTextViewStyleM3,
    R.style.BottomSheet to R.style.BottomSheetM3,
    R.style.BottomSheetDialogStyle to R.style.BottomSheetDialogStyleM3,
    R.style.BreadcrumbText to R.style.BreadcrumbTextM3,
    R.style.CardPrimaryText to R.style.CardPrimaryTextM3,
    R.style.DialogTextButton to R.style.DialogTextButtonM3,
    R.style.DocumentsDefaultTheme to R.style.DocumentsDefaultThemeM3,
    R.style.DocumentsTheme to R.style.DocumentsThemeM3,
    R.style.DrawerMenuHeader to R.style.DrawerMenuHeaderM3,
    R.style.DrawerMenuPrimary to R.style.DrawerMenuPrimaryM3,
    R.style.DrawerMenuSecondary to R.style.DrawerMenuSecondaryM3,
    R.style.EmptyStateTitleText to R.style.EmptyStateTitleTextM3,
    R.style.InspectorKeySubTitle to R.style.InspectorKeySubTitleM3,
    R.style.ItemCaptionText to R.style.ItemCaptionTextM3,
    R.style.LauncherTheme to R.style.LauncherThemeM3,
    R.style.MaterialAlertDialogTheme to R.style.MaterialAlertDialogThemeM3,
    R.style.MaterialAlertDialogTitleStyle to R.style.MaterialAlertDialogTitleStyleM3,
    R.style.MaterialButton to R.style.MaterialButtonM3,
    R.style.MaterialButtonTextAppearance to R.style.MaterialButtonTextAppearanceM3,
    R.style.MaterialOutlinedButton to R.style.MaterialOutlinedButtonM3,
    R.style.MenuItemTextAppearance to R.style.MenuItemTextAppearanceM3,
    R.style.OverflowButtonStyle to R.style.OverflowButtonStyleM3,
    R.style.OverflowMenuStyle to R.style.OverflowMenuStyleM3,
    R.style.SearchBarTitle to R.style.SearchBarTitleM3,
    R.style.SearchChipText to R.style.SearchChipTextM3,
    R.style.SnackbarButtonStyle to R.style.SnackbarButtonStyleM3,
    R.style.SortList to R.style.SortListM3,
    R.style.Subhead to R.style.SubheadM3,
    R.style.TabTextAppearance to R.style.TabTextAppearanceM3,
    R.style.ToolbarTitle to R.style.ToolbarTitleM3,
  )
}

abstract class Material3Config private constructor() {
  companion object {
    /**
     * Convert the resource ID from non-Material3 to Material3 version if the Material3 is enabled,
     * otherwise it returns the given ID as is.
     */
    @JvmStatic
    @AnyRes
    fun getRes(@AnyRes originalResourceId: Int): Int {
      if (!isUseMaterial3FlagEnabled()) {
        return originalResourceId
      }

      if (!initialized) {
        initializeIdMapping()
      }

      val newId = idMapping[originalResourceId] ?: originalResourceId
      if (DEBUG) {
        if (newId != originalResourceId) {
          Log.d(
            TAG,
            "Replacing R ID from ${
              Integer.toHexString(
                originalResourceId
              )
            } to ${Integer.toHexString(newId)}",
          )
        }
      }

      return newId
    }

    @JvmStatic
    fun overrideMappingForTest(overrides: Map<Int, Int>) {
      initialized = true
      idMapping = overrides
    }
  }
}
