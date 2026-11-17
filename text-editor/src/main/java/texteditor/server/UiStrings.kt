package texteditor.server

// SPDX-License-Identifier: GPL-3.0-or-later

import android.content.Context
import texteditor.R

object UiStrings {
    fun all(context: Context): Map<String, String> = mapOf(
        "title_page" to context.getString(R.string.web_title_page),
        "label_no_file_open" to context.getString(R.string.web_label_no_file_open),
        "label_preview_on" to context.getString(R.string.web_label_preview_on),
        "label_preview_off" to context.getString(R.string.web_label_preview_off),
        "button_save" to context.getString(R.string.web_button_save),
        "button_new_file" to context.getString(R.string.web_button_new_file),
        "button_new_folder" to context.getString(R.string.web_button_new_folder),
        "button_upload" to context.getString(R.string.web_button_upload),
        "button_rename" to context.getString(R.string.web_button_rename),
        "button_delete" to context.getString(R.string.web_button_delete),
        "cancel_text" to context.getString(R.string.web_cancel_text),
        "placeholder_start_writing" to context.getString(R.string.web_placeholder_start_writing),
        "label_empty_state" to context.getString(R.string.web_label_empty_state),
        "status_not_text_file" to context.getString(R.string.web_status_not_text_file),
        "status_saving" to context.getString(R.string.web_status_saving),
        "status_saved" to context.getString(R.string.web_status_saved),
        "status_save_failed" to context.getString(R.string.web_status_save_failed),
        "prompt_new_file_name" to context.getString(R.string.web_prompt_new_file_name),
        "alert_create_file_failed" to context.getString(R.string.web_alert_create_file_failed),
        "prompt_new_folder_name" to context.getString(R.string.web_prompt_new_folder_name),
        "alert_create_folder_failed" to context.getString(R.string.web_alert_create_folder_failed),
        "status_uploading" to context.getString(R.string.web_status_uploading),
        "status_uploaded_one" to context.resources.getQuantityString(R.plurals.web_status_uploaded, 1, 1),
        "status_uploaded_other" to context.resources.getQuantityString(R.plurals.web_status_uploaded, 2, 2),
        "status_upload_failed" to context.getString(R.string.web_status_upload_failed),
        "alert_upload_failed" to context.getString(R.string.web_alert_upload_failed),
        "prompt_rename" to context.getString(R.string.web_prompt_rename),
        "alert_rename_failed" to context.getString(R.string.web_alert_rename_failed),
        "confirm_delete" to context.getString(R.string.web_confirm_delete),
        "alert_delete_failed" to context.getString(R.string.web_alert_delete_failed),
        "sse_file_changed_title" to context.getString(R.string.web_sse_file_changed_title),
        "sse_file_changed_body" to context.getString(R.string.web_sse_file_changed_body),
        "source_app" to context.getString(R.string.web_source_app),
        "source_web_ui" to context.getString(R.string.web_source_web_ui),
        "reload_text" to context.getString(R.string.web_reload_text),
        "keep_editing_text" to context.getString(R.string.web_keep_editing_text),
    )
}
