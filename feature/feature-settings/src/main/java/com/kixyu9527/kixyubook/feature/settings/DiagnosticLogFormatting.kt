package com.kixyu9527.kixyubook.feature.settings

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
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
)

internal fun filterDiagnosticEntries(
    entries: List<ReadableDiagnosticEntry>,
    categoryKey: String? = null,
    onlyFailures: Boolean = false,
): List<ReadableDiagnosticEntry> = entries.filter { entry ->
    (!onlyFailures || entry.isFailure) && (categoryKey == null || entry.categoryKey == categoryKey)
}

internal fun parseDiagnosticEntry(rawLine: String): ReadableDiagnosticEntry {
    val parts = rawLine.split(" | ")
    if (parts.size < 3) {
        return ReadableDiagnosticEntry(
            time = "时间未知",
            categoryKey = "OTHER",
            category = "其他",
            title = "无法识别的日志记录",
            description = "这条记录内容不完整或已经损坏。",
            details = listOf("原始内容" to rawLine),
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
        values["outcome"]?.let { add("结果" to readableOutcome(it)) }
        values["elapsedMs"]?.let { add("耗时" to readableDuration(it)) }
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

internal suspend fun createReadableDiagnosticExport(
    context: Context,
    rawLines: List<String>,
): File = withContext(Dispatchers.IO) {
    val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    File(directory, "kixyu-diagnostics-readable.log").apply {
        bufferedWriter().use { writer ->
            rawLines.asReversed().forEachIndexed { index, rawLine ->
                val entry = parseDiagnosticEntry(rawLine)
                writer.append(entry.time).append("  [").append(entry.category).appendLine("]")
                writer.appendLine(entry.title)
                writer.append("说明：").appendLine(entry.description)
                entry.details.forEach { (label, value) ->
                    writer.append(label).append("：").appendLine(value)
                }
                if (index < rawLines.lastIndex) writer.appendLine()
            }
        }
    }
}

private fun deviceTime(raw: String): String {
    val instant = runCatching { Instant.parse(raw) }.getOrNull() ?: return raw
    return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(instant)
}

internal fun diagnosticCategoryLabel(category: String): String = when (category) {
    "LIBRARY" -> "书库"
    "SYNC" -> "云同步"
    "IMPORT" -> "书籍导入"
    "EPUB_PARSE" -> "EPUB 解析"
    "READER" -> "阅读"
    "PAGINATION" -> "页面排版"
    else -> "其他"
}

private fun eventDescription(event: String, outcome: String?, category: String): Pair<String, String> = when (event) {
    "book_open_activity_failed" ->
        "最近打开时间保存失败" to "书架已即时调整顺序，但持久化最近打开时间时发生错误。"
    "book_exported" -> "书籍导出完成" to "原始书籍文件已经复制到用户选择的位置。"
    "book_export_failed" -> "书籍导出失败" to "复制原始书籍文件时发生错误，可结合错误原因和书籍标识排查。"
    "books_deleted" -> "书籍删除完成" to "书籍、本地进度、书签和派生缓存已经清理，并已登记云端删除。"
    "full_sync_started" -> "开始云同步" to "开始检查本机与 Google Drive 中的数据。"
    "full_sync_skipped" -> when (outcome) {
        "not_ready" -> "本次同步未执行" to "同步功能尚未准备完成，未开始传输数据。"
        "conflict_waiting" -> "同步等待冲突处理" to "存在尚未处理的同步冲突，本次同步已暂停。"
        else -> "本次同步已跳过" to "当前条件不满足，未执行云同步。"
    }
    "authorization_ready" -> "Google 授权可用" to "已取得有效授权，可以访问应用的云端数据。"
    "priority_pull_skipped" -> "已忽略云端旧数据" to "本机已删除对应数据，优先同步不会将其重新恢复。"
    "remote_snapshot_loaded" -> "云端数据检查完成" to "已读取云端对象和本次发生变化的数据。"
    "conflicts_waiting" -> "发现同步冲突" to "本机和云端均有修改，需要等待用户选择。"
    "upload_queue_ready" -> "待上传数据已整理" to "已统计本次需要上传到云端的更改。"
    "full_sync_finished" -> when {
        outcome == "success" -> "云同步完成" to "本机与云端数据已经完成本轮同步。"
        isInterruptedSyncOutcome(outcome) ->
            "云同步被系统中断" to "同步任务未发生数据错误，系统会在条件合适时自动继续。"
        else -> "云同步失败" to "本轮同步未正常完成，可结合阶段、结果和原因继续排查。"
    }
    "documents_selected" -> "已选择导入文件" to "用户选择了准备加入书库的文件。"
    "documents_registered" -> if (outcome == "success") {
        "书籍导入登记完成" to "文件已复制并登记到书库，后续解析会在后台进行。"
    } else {
        "书籍导入部分完成" to "部分文件未能导入，可结合失败类型和首个失败原因排查。"
    }
    "directory_upgrade_finished" -> if (outcome == "success") {
        "EPUB 目录更新完成" to "已根据新版解析规则自动补齐并更新已导入书籍的目录信息。"
    } else {
        "EPUB 目录部分更新" to "部分书籍的目录信息未能更新，可结合处理失败数量继续排查。"
    }
    "background_index_finished" -> if (outcome == "success") {
        if (category == "IMPORT") {
            "TXT 后台解析完成" to "TXT 章节已经解析并写入本地书库。"
        } else {
            "书籍后台索引完成" to "全文检索所需的章节数据已经在后台生成。"
        }
    } else {
        if (category == "IMPORT") {
            "TXT 后台解析失败" to "TXT 章节解析或写入本地书库时发生错误。"
        } else {
            "书籍后台索引失败" to "后台生成全文检索数据时发生错误。"
        }
    }
    "bulk_parse_finished" -> if (outcome == "success") {
        "EPUB 批量解析完成" to "指定范围内的 EPUB 章节已经解析完成。"
    } else {
        "EPUB 批量解析失败" to "批量读取 EPUB 章节时发生错误。"
    }
    "chapter_parse_finished" -> when (outcome) {
        "success" -> "EPUB 章节解析完成" to "章节正文和图片信息已经读取完成。"
        "missing" -> "未找到 EPUB 章节" to "EPUB 中没有找到请求的章节内容。"
        else -> "EPUB 章节解析失败" to "读取该章节时发生错误。"
    }
    "chapter_loaded" -> if (outcome == "success") {
        "章节内容加载完成" to "阅读器已经取得该章节的正文内容。"
    } else {
        "章节内容加载失败" to "阅读器读取章节正文、缓存或本地索引时发生错误。"
    }
    "priority_sync_failed" ->
        "当前书籍优先同步失败" to "快速同步阅读进度、书签或阅读设置时发生错误，后续完整同步仍会重试。"
    "chapter_navigation_finished" -> when (outcome) {
        "success" -> "章节切换完成" to "阅读器已经切换到目标章节。"
        "missing" -> "章节切换失败" to "没有找到目标章节，无法完成切换。"
        else -> "章节切换异常" to "切换章节时发生异常。"
    }
    "restore" -> "分页缓存读取完成" to "直接使用之前保存的分页结果，无需重新排版。"
    "measure" -> "章节分页完成" to "章节正文已经排版为可阅读的页面。"
    "failed" -> "章节分页失败" to "排版章节正文时发生错误。"
    else -> "诊断事件：$event" to "这是尚未添加中文说明的新诊断事件。"
}

private fun readableOutcome(outcome: String): String = when (outcome) {
    "success" -> "成功"
    "partial" -> "部分成功"
    "missing" -> "未找到内容"
    "disk_cache" -> "命中磁盘缓存"
    "not_ready" -> "尚未准备完成"
    "conflict_waiting" -> "等待处理冲突"
    "user_action" -> "等待用户处理"
    "local_delete" -> "本机已删除"
    "interrupted",
    "CancellationException",
    "JobCancellationException",
    -> "被系统中断"
    "authorization_required" -> "需要重新授权"
    "drive_http_error" -> "Google Drive 请求失败"
    "network_error" -> "网络连接异常"
    "local_data_error" -> "本地数据异常"
    "cloud_data_error" -> "云端数据异常"
    "unexpected_error" -> "未预期错误"
    "invalid_archive" -> "压缩文件损坏"
    "truncated_input" -> "文件内容不完整"
    "missing_file" -> "文件不存在"
    "constraint_error" -> "本地数据约束冲突"
    "permission_error" -> "没有访问权限"
    "memory_error" -> "可用内存不足"
    "io_error" -> "文件读写异常"
    "invalid_data" -> "数据格式异常"
    "invalid_state" -> "数据状态异常"
    "SQLiteConstraintException" -> "本地数据约束冲突"
    "SQLiteException" -> "本地数据异常"
    "ZipException" -> "压缩文件损坏"
    "EOFException" -> "文件内容不完整"
    "FileNotFoundException" -> "文件不存在"
    "IOException" -> "文件读写异常"
    "SecurityException" -> "没有访问权限"
    "OutOfMemoryError" -> "可用内存不足"
    "IllegalArgumentException" -> "数据格式异常"
    "IllegalStateException" -> "数据状态异常"
    "failure" -> "失败"
    "error" -> "失败"
    else -> "失败（$outcome）"
}

private fun isFailureOutcome(outcome: String?): Boolean = when (outcome) {
    null,
    "success",
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
    val milliseconds = raw.toLongOrNull() ?: return "$raw 毫秒"
    return if (milliseconds < 1_000) {
        "$milliseconds 毫秒"
    } else {
        String.format(Locale.getDefault(), "%.2f 秒", milliseconds / 1_000.0)
    }
}

private fun fieldLabel(key: String, category: String): String = when (key) {
    "chapter" -> if (category == "PAGINATION") "章节 ID" else "章节索引"
    "paragraphs" -> "段落数"
    "pages" -> "生成页数"
    "prefetch" -> "执行方式"
    "prefetched" -> "预加载状态"
    "format" -> "书籍格式"
    "priority" -> "加载类型"
    "source" -> "内容来源"
    "images" -> "图片数"
    "requested" -> "请求章节数"
    "emitted" -> "完成章节数"
    "count" -> "数量"
    "imported" -> "导入成功"
    "duplicates" -> "重复文件"
    "failures" -> if (category == "IMPORT") "导入失败" else "处理失败"
    "inserted" -> "新增目录项"
    "updated" -> "更新目录项"
    "chapters" -> "章节数"
    "preferredBook" -> "优先同步当前书籍"
    "known" -> "云端对象数"
    "changed" -> "云端变更数"
    "uploaded" -> "已上传项目数"
    "remoteChanged" -> "已处理云端变更"
    "book" -> "书籍标识"
    "purpose" -> "解析用途"
    "progressRecords" -> "已删除进度"
    "indexed" -> "完成索引章节"
    "preempted" -> "向前台让路次数"
    "reason" -> "错误原因"
    "stage" -> "中断或失败阶段"
    "statusCode" -> "HTTP 状态码"
    "followedByFullSync" -> "后续安排完整同步"
    "run" -> when (category) {
        "SYNC" -> "同步批次"
        "IMPORT" -> "导入批次"
        else -> "任务批次"
    }
    "failureTypes" -> "失败类型"
    "failureType" -> "失败类型"
    "firstFailureReason" -> "首个失败原因"
    "firstFailedBook" -> "首个失败书籍"
    "entity" -> "数据类型"
    else -> key
}

private fun readableValue(key: String, value: String): String = when (key) {
    "prefetch" -> if (value == "true") "后台预加载" else "当前阅读"
    "prefetched" -> if (value == "true") "已提前加载" else "现场加载"
    "preferredBook" -> if (value == "true") "是" else "否"
    "followedByFullSync" -> if (value == "true") "是" else "否"
    "priority" -> when (value) {
        "PREFETCH" -> "后台预加载"
        "USER" -> "用户请求"
        else -> value
    }
    "source" -> when (value) {
        "database" -> "本地数据库"
        "epub_disk_cache" -> "EPUB 磁盘缓存"
        "epub_parse" -> "实时解析 EPUB"
        "unknown" -> "尚未确定"
        else -> value
    }
    "purpose" -> when (value) {
        "index" -> "后台全文索引"
        "reader" -> "前台阅读请求"
        "interactive" -> "即时解析"
        else -> value
    }
    "entity" -> when (value) {
        "progress" -> "阅读进度"
        "bookmarks" -> "书签"
        else -> value
    }
    "requested" -> if (value == "all") "全部" else value
    "failureType",
    "failureTypes",
    -> value.split(',').joinToString("、") { readableOutcome(it) }
    "stage" -> when (value) {
        "preparing" -> "准备同步"
        "authorization" -> "检查 Google 授权"
        "remote_snapshot" -> "检查云端变更"
        "applying_remote" -> "处理云端删除和进度"
        "conflict_check" -> "检查同步冲突"
        "downloading" -> "应用云端更改"
        "preparing_uploads" -> "整理待上传数据"
        "uploading" -> "上传本机更改"
        "finalizing" -> "保存同步结果"
        else -> value
    }
    else -> when (value) {
        "true" -> "是"
        "false" -> "否"
        else -> value
    }
}

private fun isInterruptedSyncOutcome(outcome: String?): Boolean =
    outcome == "interrupted" ||
        outcome == "CancellationException" ||
        outcome == "JobCancellationException"
