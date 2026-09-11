package com.kixyu9527.kixyubook.feature.settings

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class ReadableDiagnosticEntry(
    val time: String,
    val categoryKey: String,
    val category: String,
    val title: String,
    val description: String,
    val details: List<Pair<String, String>>,
    val isFailure: Boolean,
    val id: Int = 0,
)

internal fun filterDiagnosticEntries(
    entries: List<ReadableDiagnosticEntry>,
    categoryKey: String? = null,
    onlyFailures: Boolean = false,
): List<ReadableDiagnosticEntry> = entries.filter { entry ->
    (!onlyFailures || entry.isFailure) && (categoryKey == null || entry.categoryKey == categoryKey)
}

/** Resolves app-authored log labels at display time; raw event codes and values stay intact. */
internal class DiagnosticLogFormatter(private val resources: android.content.res.Resources) {
    private val timeFormatter = DateTimeFormatter.ofPattern(
        "yyyy-MM-dd HH:mm:ss.SSS", resources.configuration.locales[0],
    ).withZone(ZoneId.systemDefault())
    fun parseDiagnosticEntry(rawLine: String): ReadableDiagnosticEntry {
        val parts = rawLine.split(" | ")
        if (parts.size < 3) {
            return ReadableDiagnosticEntry(
                time = resources.getString(R.string.diag_unknown_time),
                categoryKey = "OTHER",
                category = resources.getString(R.string.diag_other),
                title = resources.getString(R.string.diag_unrecognized_log_entry),
                description = resources.getString(R.string.diag_this_entry_is_incomplete_or_damaged),
                details = listOf(resources.getString(R.string.diag_raw_content) to rawLine),
                isFailure = false,
            )
        }

        val categoryKey = parts[1]
        val eventKey = parts[2]
        val values = parts.drop(3).mapNotNull { field ->
            val separator = field.indexOf('=')
            if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
        }.toMap(LinkedHashMap())
        val (title, description) = eventDescription(eventKey, values["outcome"], categoryKey)
        val details = buildList {
            values["outcome"]?.let { add(resources.getString(R.string.diag_outcome) to readableOutcome(it)) }
            values["elapsedMs"]?.let { add(resources.getString(R.string.diag_duration) to readableDuration(it)) }
            values.forEach { (key, value) ->
                if (key != "outcome" && key != "elapsedMs") {
                    add(fieldLabel(key, categoryKey) to readableValue(key, value))
                }
            }
        }
        return ReadableDiagnosticEntry(
            time = deviceTime(parts[0]),
            categoryKey = categoryKey,
            category = diagnosticCategoryLabel(categoryKey),
            title = title,
            description = description,
            details = details,
            isFailure = isFailureOutcome(values["outcome"]),
        )
    }

    private fun deviceTime(raw: String): String {
        val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return raw
        return timeFormatter.format(instant)
    }

    fun diagnosticCategoryLabel(category: String): String = when (category) {
        "LIBRARY" -> resources.getString(R.string.diag_library)
        "SYNC" -> resources.getString(R.string.diag_cloud_sync)
        "IMPORT" -> resources.getString(R.string.diag_book_import)
        "EPUB_PARSE" -> resources.getString(R.string.diag_epub_parsing)
        "READER" -> resources.getString(R.string.diag_reading)
        "PAGINATION" -> resources.getString(R.string.diag_pagination)
        else -> resources.getString(R.string.diag_other)
    }

    private fun eventDescription(event: String, outcome: String?, category: String): Pair<String, String> = when (event) {
        "book_open_activity_failed" ->
            resources.getString(R.string.diag_could_not_save_last_opened_time) to resources.getString(R.string.diag_the_library_order_was_updated_but_saving_the_last_opened_time_failed)
        "book_exported" -> resources.getString(R.string.diag_book_exported) to resources.getString(R.string.diag_the_original_book_file_was_copied_to_the_chosen_location)
        "book_export_failed" -> resources.getString(R.string.diag_book_export_failed) to resources.getString(R.string.diag_copying_the_original_file_failed_check_the_error_and_book_identifier)
        "books_deleted" -> resources.getString(R.string.diag_books_deleted) to resources.getString(R.string.diag_books_local_progress_bookmarks_and_derived_caches_were_removed_cloud_deletions_w)
        "full_sync_started" -> resources.getString(R.string.diag_cloud_sync_started) to resources.getString(R.string.diag_checking_local_and_google_drive_data)
        "full_sync_skipped" -> when (outcome) {
            "not_ready" -> resources.getString(R.string.diag_sync_not_started) to resources.getString(R.string.diag_sync_is_not_ready_no_data_transfer_started)
            "conflict_waiting" -> resources.getString(R.string.diag_sync_waiting_for_conflict_resolution) to resources.getString(R.string.diag_sync_paused_because_of_unresolved_conflicts)
            else -> resources.getString(R.string.diag_sync_skipped) to resources.getString(R.string.diag_conditions_for_cloud_sync_were_not_met)
        }
        "authorization_ready" -> resources.getString(R.string.diag_google_authorization_ready) to resources.getString(R.string.diag_authorization_is_valid_app_cloud_data_can_be_accessed)
        "priority_pull_skipped" -> resources.getString(R.string.diag_stale_cloud_data_ignored) to resources.getString(R.string.diag_the_data_was_deleted_locally_and_will_not_be_restored_by_priority_sync)
        "remote_snapshot_loaded" -> resources.getString(R.string.diag_cloud_data_checked) to resources.getString(R.string.diag_cloud_objects_and_changes_were_read)
        "conflicts_waiting" -> resources.getString(R.string.diag_sync_conflicts_found) to resources.getString(R.string.diag_both_local_and_cloud_data_changed_user_input_is_required)
        "upload_queue_ready" -> resources.getString(R.string.diag_upload_queue_ready) to resources.getString(R.string.diag_changes_to_upload_were_collected)
        "full_sync_finished" -> when {
            outcome == "success" -> resources.getString(R.string.diag_cloud_sync_complete) to resources.getString(R.string.diag_local_and_cloud_data_are_synchronized_for_this_run)
            isInterruptedSyncOutcome(outcome) ->
                resources.getString(R.string.diag_cloud_sync_interrupted) to resources.getString(R.string.diag_no_data_error_occurred_the_system_will_resume_sync_when_conditions_allow)
            else -> resources.getString(R.string.diag_cloud_sync_failed) to resources.getString(R.string.diag_sync_did_not_complete_check_the_stage_outcome_and_reason)
        }
        "documents_selected" -> resources.getString(R.string.diag_import_files_selected) to resources.getString(R.string.diag_files_were_selected_to_add_to_the_library)
        "documents_registered" -> if (outcome == "success") {
            resources.getString(R.string.diag_book_registration_complete) to resources.getString(R.string.diag_files_were_copied_and_registered_parsing_continues_in_the_background)
        } else {
            resources.getString(R.string.diag_book_import_partially_complete) to resources.getString(R.string.diag_some_files_could_not_be_imported_check_the_failure_type_and_first_error)
        }
        "directory_upgrade_finished" -> if (outcome == "success") {
            resources.getString(R.string.diag_epub_contents_updated) to resources.getString(R.string.diag_imported_book_contents_were_updated_using_the_new_parsing_rules)
        } else {
            resources.getString(R.string.diag_epub_contents_partially_updated) to resources.getString(R.string.diag_some_book_contents_could_not_be_updated_check_the_failure_count)
        }
        "background_index_finished" -> if (outcome == "success") {
            if (category == "IMPORT") {
                resources.getString(R.string.diag_background_txt_parsing_complete) to resources.getString(R.string.diag_txt_chapters_were_parsed_and_saved_to_the_local_library)
            } else {
                resources.getString(R.string.diag_background_indexing_complete) to resources.getString(R.string.diag_chapter_data_for_full_text_search_was_generated_in_the_background)
            }
        } else {
            if (category == "IMPORT") {
                resources.getString(R.string.diag_background_txt_parsing_failed) to resources.getString(R.string.diag_parsing_txt_chapters_or_saving_them_to_the_library_failed)
            } else {
                resources.getString(R.string.diag_background_indexing_failed) to resources.getString(R.string.diag_generating_full_text_search_data_in_the_background_failed)
            }
        }
        "bulk_parse_finished" -> if (outcome == "success") {
            resources.getString(R.string.diag_batch_epub_parsing_complete) to resources.getString(R.string.diag_the_requested_epub_chapters_were_parsed)
        } else {
            resources.getString(R.string.diag_batch_epub_parsing_failed) to resources.getString(R.string.diag_batch_reading_epub_chapters_failed)
        }
        "chapter_parse_finished" -> when (outcome) {
            "success" -> resources.getString(R.string.diag_epub_chapter_parsed) to resources.getString(R.string.diag_chapter_text_and_image_information_were_read)
            "missing" -> resources.getString(R.string.diag_epub_chapter_not_found) to resources.getString(R.string.diag_the_requested_chapter_was_not_found_in_the_epub)
            else -> resources.getString(R.string.diag_epub_chapter_parsing_failed) to resources.getString(R.string.diag_reading_the_chapter_failed)
        }
        "chapter_loaded" -> if (outcome == "success") {
            resources.getString(R.string.diag_chapter_loaded) to resources.getString(R.string.diag_the_reader_received_the_chapter_text)
        } else {
            resources.getString(R.string.diag_chapter_load_failed) to resources.getString(R.string.diag_reading_chapter_text_cache_or_the_local_index_failed)
        }
        "memory_pressure_handled" -> if (outcome == "success") {
            resources.getString(R.string.diag_memory_warning_handled) to resources.getString(R.string.diag_reading_state_was_saved_and_rebuildable_caches_released_as_requested_by_the_syst)
        } else {
            resources.getString(R.string.diag_memory_warning_handling_failed) to resources.getString(R.string.diag_releasing_caches_saving_reading_state_or_replying_to_the_system_failed)
        }
        "priority_sync_failed" ->
            resources.getString(R.string.diag_priority_book_sync_failed) to resources.getString(R.string.diag_fast_sync_of_progress_bookmarks_or_settings_failed_full_sync_will_retry_later)
        "chapter_navigation_finished" -> when (outcome) {
            "success" -> resources.getString(R.string.diag_chapter_navigation_complete) to resources.getString(R.string.diag_the_reader_switched_to_the_target_chapter)
            "missing" -> resources.getString(R.string.diag_chapter_navigation_failed) to resources.getString(R.string.diag_the_target_chapter_was_not_found)
            else -> resources.getString(R.string.diag_chapter_navigation_error) to resources.getString(R.string.diag_an_error_occurred_while_switching_chapters)
        }
        "location_ready" -> if (outcome == "ready") {
            resources.getString(R.string.diag_location_ready) to resources.getString(R.string.diag_the_requested_reading_position_is_now_visible)
        } else {
            resources.getString(R.string.diag_location_not_ready) to resources.getString(R.string.diag_jumping_to_the_requested_reading_position_did_not_finish)
        }
        "page_turn" -> if (outcome == "failed") {
            resources.getString(R.string.diag_page_turn_failed) to resources.getString(R.string.diag_crossing_the_chapter_boundary_failed)
        } else {
            resources.getString(R.string.diag_page_turn_complete) to resources.getString(R.string.diag_the_reader_crossed_a_chapter_boundary)
        }
        "restore" -> resources.getString(R.string.diag_pagination_cache_restored) to resources.getString(R.string.diag_previously_saved_pages_were_reused_without_new_layout_work)
        "measure" -> resources.getString(R.string.diag_chapter_pagination_complete) to resources.getString(R.string.diag_chapter_text_was_laid_out_into_readable_pages)
        "failed" -> resources.getString(R.string.diag_chapter_pagination_failed) to resources.getString(R.string.diag_laying_out_the_chapter_text_failed)
        else -> resources.getString(R.string.diag_unknown_event, event) to resources.getString(R.string.diag_no_description_is_available_for_this_diagnostic_event_yet)
    }

    private fun readableOutcome(outcome: String): String = when (outcome) {
        "success" -> resources.getString(R.string.diag_success)
        "ready" -> resources.getString(R.string.diag_ready)
        "partial" -> resources.getString(R.string.diag_partial_success)
        "missing" -> resources.getString(R.string.diag_content_not_found)
        "disk_cache" -> resources.getString(R.string.diag_disk_cache_hit)
        "not_ready" -> resources.getString(R.string.diag_not_ready)
        "conflict_waiting" -> resources.getString(R.string.diag_waiting_for_conflict_resolution)
        "user_action" -> resources.getString(R.string.diag_waiting_for_user_action)
        "local_delete" -> resources.getString(R.string.diag_deleted_locally)
        "interrupted",
        "CancellationException",
        "JobCancellationException",
        -> resources.getString(R.string.diag_interrupted_by_system)
        "authorization_required" -> resources.getString(R.string.diag_reauthorization_required)
        "drive_http_error" -> resources.getString(R.string.diag_google_drive_request_failed)
        "network_error" -> resources.getString(R.string.diag_network_error)
        "local_data_error" -> resources.getString(R.string.diag_local_data_error)
        "cloud_data_error" -> resources.getString(R.string.diag_cloud_data_error)
        "unexpected_error" -> resources.getString(R.string.diag_unexpected_error)
        "invalid_archive" -> resources.getString(R.string.diag_damaged_archive)
        "truncated_input" -> resources.getString(R.string.diag_incomplete_file)
        "missing_file" -> resources.getString(R.string.diag_file_not_found)
        "constraint_error" -> resources.getString(R.string.diag_local_data_constraint_conflict)
        "permission_error" -> resources.getString(R.string.diag_access_denied)
        "memory_error" -> resources.getString(R.string.diag_insufficient_memory)
        "memory_release_failed" -> resources.getString(R.string.diag_memory_release_failed)
        "memory_callback_failed" -> resources.getString(R.string.diag_system_response_failed)
        "io_error" -> resources.getString(R.string.diag_file_read_or_write_error)
        "invalid_data" -> resources.getString(R.string.diag_invalid_data_format)
        "invalid_state" -> resources.getString(R.string.diag_invalid_data_state)
        "SQLiteConstraintException" -> resources.getString(R.string.diag_local_data_constraint_conflict)
        "SQLiteException" -> resources.getString(R.string.diag_local_data_error)
        "ZipException" -> resources.getString(R.string.diag_damaged_archive)
        "EOFException" -> resources.getString(R.string.diag_incomplete_file)
        "FileNotFoundException" -> resources.getString(R.string.diag_file_not_found)
        "IOException" -> resources.getString(R.string.diag_file_read_or_write_error)
        "SecurityException" -> resources.getString(R.string.diag_access_denied)
        "OutOfMemoryError" -> resources.getString(R.string.diag_insufficient_memory)
        "IllegalArgumentException" -> resources.getString(R.string.diag_invalid_data_format)
        "IllegalStateException" -> resources.getString(R.string.diag_invalid_data_state)
        "failure" -> resources.getString(R.string.diag_failure)
        "error" -> resources.getString(R.string.diag_failure)
        else -> resources.getString(R.string.diag_unknown_failure, outcome)
    }

    private fun isFailureOutcome(outcome: String?): Boolean = when (outcome) {
        null,
        "success",
        "ready",
        "disk_cache",
        "not_ready",
        "conflict_waiting",
        "user_action",
        "local_delete",
        "interrupted",
        "CancellationException",
        "JobCancellationException",
        -> false
        else -> true
    }

    private fun readableDuration(raw: String): String {
        val milliseconds = raw.toLongOrNull() ?: return resources.getString(R.string.diag_duration_ms, raw)
        return if (milliseconds < 1_000) {
            resources.getString(R.string.diag_duration_ms, milliseconds.toString())
        } else {
            resources.getString(R.string.diag_duration_seconds, milliseconds / 1_000.0)
        }
    }

    private fun fieldLabel(key: String, category: String): String = when (key) {
        "chapter" -> if (category == "PAGINATION") resources.getString(R.string.diag_chapter_id) else resources.getString(R.string.diag_chapter_index)
        "fromChapter" -> resources.getString(R.string.diag_from_chapter)
        "direction" -> resources.getString(R.string.diag_page_turn_direction)
        "paragraphs" -> resources.getString(R.string.diag_paragraph_count)
        "pages" -> resources.getString(R.string.diag_pages_generated)
        "prefetch" -> resources.getString(R.string.diag_execution_mode)
        "prefetched" -> resources.getString(R.string.diag_prefetch_state)
        "format" -> resources.getString(R.string.diag_book_format)
        "priority" -> resources.getString(R.string.diag_load_type)
        "source" -> resources.getString(R.string.diag_content_source)
        "images" -> resources.getString(R.string.diag_image_count)
        "requested" -> resources.getString(R.string.diag_chapters_requested)
        "emitted" -> resources.getString(R.string.diag_chapters_completed)
        "count" -> resources.getString(R.string.diag_count)
        "imported" -> resources.getString(R.string.diag_imported)
        "duplicates" -> resources.getString(R.string.diag_duplicate_files)
        "failures" -> if (category == "IMPORT") resources.getString(R.string.diag_import_failed) else resources.getString(R.string.diag_processing_failed)
        "inserted" -> resources.getString(R.string.diag_contents_entries_added)
        "updated" -> resources.getString(R.string.diag_contents_entries_updated)
        "chapters" -> resources.getString(R.string.diag_chapter_count)
        "preferredBook" -> resources.getString(R.string.diag_prioritize_current_book)
        "known" -> resources.getString(R.string.diag_cloud_object_count)
        "changed" -> resources.getString(R.string.diag_cloud_change_count)
        "uploaded" -> resources.getString(R.string.diag_items_uploaded)
        "remoteChanged" -> resources.getString(R.string.diag_cloud_changes_processed)
        "book" -> resources.getString(R.string.diag_book_identifier)
        "purpose" -> resources.getString(R.string.diag_parsing_purpose)
        "progressRecords" -> resources.getString(R.string.diag_progress_entries_deleted)
        "indexed" -> resources.getString(R.string.diag_chapters_indexed)
        "preempted" -> resources.getString(R.string.diag_times_yielded_to_foreground)
        "reason" -> resources.getString(R.string.diag_error_reason)
        "stage" -> resources.getString(R.string.diag_interrupted_or_failed_stage)
        "statusCode" -> resources.getString(R.string.diag_http_status_code)
        "followedByFullSync" -> resources.getString(R.string.diag_full_sync_scheduled)
        "run" -> when (category) {
            "SYNC" -> resources.getString(R.string.diag_sync_batch)
            "IMPORT" -> resources.getString(R.string.diag_import_batch)
            else -> resources.getString(R.string.diag_task_batch)
        }
        "failureTypes" -> resources.getString(R.string.diag_failure_type)
        "failureType" -> resources.getString(R.string.diag_failure_type)
        "firstFailureReason" -> resources.getString(R.string.diag_first_failure_reason)
        "firstFailedBook" -> resources.getString(R.string.diag_first_failed_book)
        "entity" -> resources.getString(R.string.diag_data_type)
        "pressure" -> resources.getString(R.string.diag_memory_pressure_level)
        "action" -> resources.getString(R.string.diag_system_action)
        "notifyType" -> resources.getString(R.string.diag_notification_type)
        "notifyId" -> resources.getString(R.string.diag_notification_id)
        "listeners" -> resources.getString(R.string.diag_components_notified)
        "listenerFailures" -> resources.getString(R.string.diag_failed_components)
        "callbackReplied" -> resources.getString(R.string.diag_system_notified)
        "trimLevel" -> resources.getString(R.string.diag_android_trim_level)
        "heapAlloc" -> resources.getString(R.string.diag_java_heap_used_kb)
        "heapCapacity" -> resources.getString(R.string.diag_java_heap_limit_kb)
        "pss" -> resources.getString(R.string.diag_physical_memory_used_kb)
        "pssLimit" -> resources.getString(R.string.diag_physical_memory_limit_kb)
        else -> key
    }

    private fun readableValue(key: String, value: String): String = when (key) {
        "prefetch" -> if (value == "true") resources.getString(R.string.diag_background_prefetch) else resources.getString(R.string.diag_current_reading)
        "prefetched" -> if (value == "true") resources.getString(R.string.diag_prefetched) else resources.getString(R.string.diag_on_demand_load)
        "preferredBook" -> if (value == "true") resources.getString(R.string.diag_yes) else resources.getString(R.string.diag_no)
        "followedByFullSync" -> if (value == "true") resources.getString(R.string.diag_yes) else resources.getString(R.string.diag_no)
        "priority" -> when (value) {
            "PREFETCH" -> resources.getString(R.string.diag_background_prefetch)
            "USER" -> resources.getString(R.string.diag_user_request)
            else -> value
        }
        "source" -> when (value) {
            "database" -> resources.getString(R.string.diag_local_database)
            "epub_disk_cache" -> resources.getString(R.string.diag_epub_disk_cache)
            "epub_parse" -> resources.getString(R.string.diag_live_epub_parsing)
            "unknown" -> resources.getString(R.string.diag_undetermined)
            "hyperos" -> resources.getString(R.string.diag_xiaomi_hyperos)
            "android" -> resources.getString(R.string.diag_android_memory_trimming)
            "android_low_memory" -> resources.getString(R.string.diag_android_low_memory_warning)
            "DIRECTORY" -> resources.getString(R.string.diag_location_source_directory)
            "BOOKMARK" -> resources.getString(R.string.diag_location_source_bookmark)
            "ANNOTATION" -> resources.getString(R.string.diag_location_source_annotation)
            "SEARCH" -> resources.getString(R.string.diag_location_source_search)
            "DOCUMENT_LINK" -> resources.getString(R.string.diag_location_source_document_link)
            "HISTORY" -> resources.getString(R.string.diag_location_source_history)
            "RESTORE" -> resources.getString(R.string.diag_location_source_restore)
            "pager" -> resources.getString(R.string.diag_page_turn_source_pager)
            "chapter_button" -> resources.getString(R.string.diag_page_turn_source_chapter_button)
            "page_boundary" -> resources.getString(R.string.diag_page_turn_source_page_boundary)
            else -> value
        }
        "direction" -> when (value) {
            "forward" -> resources.getString(R.string.diag_page_turn_forward)
            "backward" -> resources.getString(R.string.diag_page_turn_backward)
            else -> value
        }
        "pressure" -> when (value) {
            "BACKGROUND" -> resources.getString(R.string.diag_app_backgrounded)
            "MODERATE" -> resources.getString(R.string.diag_memory_warning)
            "CRITICAL" -> resources.getString(R.string.diag_process_reclaim_imminent)
            else -> value
        }
        "action" -> when (value.uppercase()) {
            "TRIM" -> resources.getString(R.string.diag_release_memory)
            "KILL" -> resources.getString(R.string.diag_save_state_and_end_process)
            else -> value
        }
        "notifyType" -> when (value) {
            "1000" -> resources.getString(R.string.diag_physical_memory_warning)
            "2000" -> resources.getString(R.string.diag_java_heap_warning)
            else -> value
        }
        "callbackReplied" -> if (value == "true") resources.getString(R.string.diag_yes) else resources.getString(R.string.diag_no)
        "purpose" -> when (value) {
            "index" -> resources.getString(R.string.diag_background_full_text_index)
            "reader" -> resources.getString(R.string.diag_foreground_reading_request)
            "interactive" -> resources.getString(R.string.diag_on_demand_parsing)
            else -> value
        }
        "entity" -> when (value) {
            "progress" -> resources.getString(R.string.diag_reading_progress)
            "bookmarks" -> resources.getString(R.string.diag_bookmarks)
            else -> value
        }
        "requested" -> if (value == "all") resources.getString(R.string.diag_all) else value
        "failureType",
        "failureTypes",
        -> value.split(',').joinToString(resources.getString(R.string.diag_)) { readableOutcome(it) }
        "stage" -> when (value) {
            "preparing" -> resources.getString(R.string.diag_preparing_sync)
            "authorization" -> resources.getString(R.string.diag_checking_google_authorization)
            "remote_snapshot" -> resources.getString(R.string.diag_checking_cloud_changes)
            "applying_remote" -> resources.getString(R.string.diag_applying_cloud_deletions_and_progress)
            "conflict_check" -> resources.getString(R.string.diag_checking_sync_conflicts)
            "downloading" -> resources.getString(R.string.diag_applying_cloud_changes)
            "preparing_uploads" -> resources.getString(R.string.diag_preparing_upload_data)
            "uploading" -> resources.getString(R.string.diag_uploading_local_changes)
            "finalizing" -> resources.getString(R.string.diag_saving_sync_results)
            else -> value
        }
        else -> when (value) {
            "true" -> resources.getString(R.string.diag_yes)
            "false" -> resources.getString(R.string.diag_no)
            else -> value
        }
    }

    private fun isInterruptedSyncOutcome(outcome: String?): Boolean =
        outcome == "interrupted" ||
            outcome == "CancellationException" ||
            outcome == "JobCancellationException"
}

internal suspend fun createReadableDiagnosticExport(
    context: Context,
    rawLines: List<String>,
): File = withContext(Dispatchers.IO) {
    val formatter = DiagnosticLogFormatter(context.resources)
    val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    File(directory, "kixyu-diagnostics-readable.log").apply {
        bufferedWriter().use { writer ->
            rawLines.asReversed().forEachIndexed { index, rawLine ->
                val entry = formatter.parseDiagnosticEntry(rawLine)
                writer.append(entry.time).append("  [").append(entry.category).appendLine("]")
                writer.appendLine(entry.title)
                writer.append(context.getString(R.string.diag_description)).appendLine(entry.description)
                entry.details.forEach { (label, value) ->
                    writer.append(label).append("：").appendLine(value)
                }
                if (index < rawLines.lastIndex) writer.appendLine()
            }
        }
    }
}
