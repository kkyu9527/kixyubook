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
    val category: String,
    val title: String,
    val description: String,
    val details: List<Pair<String, String>>,
)

internal fun parseDiagnosticEntry(rawLine: String): ReadableDiagnosticEntry {
    val parts = rawLine.split(" | ")
    if (parts.size < 3) {
        return ReadableDiagnosticEntry(
            time = "时间未知",
            category = "其他",
            title = "无法识别的日志记录",
            description = "这条记录使用了旧格式或内容不完整。",
            details = listOf("原始内容" to rawLine),
        )
    }

    val categoryKey = parts[1]
    val eventKey = parts[2]
    val values = parts.drop(3).mapNotNull { field ->
        val separator = field.indexOf('=')
        if (separator <= 0) null else field.substring(0, separator) to field.substring(separator + 1)
    }.toMap(LinkedHashMap())
    val (title, description) = eventDescription(eventKey, values["outcome"])
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
        category = categoryLabel(categoryKey),
        title = title,
        description = description,
        details = details,
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

private fun categoryLabel(category: String): String = when (category) {
    "SYNC" -> "云同步"
    "IMPORT" -> "书籍导入"
    "EPUB_PARSE" -> "EPUB 解析"
    "READER" -> "阅读"
    "PAGINATION" -> "页面排版"
    else -> "其他"
}

private fun eventDescription(event: String, outcome: String?): Pair<String, String> = when (event) {
    "full_sync_started" -> "开始云同步" to "开始检查本机与 Google Drive 中的数据。"
    "full_sync_skipped" -> when (outcome) {
        "not_ready" -> "本次同步未执行" to "同步功能尚未准备完成，未开始传输数据。"
        "conflict_waiting" -> "同步等待冲突处理" to "存在尚未处理的同步冲突，本次同步已暂停。"
        else -> "本次同步已跳过" to "当前条件不满足，未执行云同步。"
    }
    "authorization_ready" -> "Google 授权可用" to "已取得有效授权，可以访问应用的云端数据。"
    "remote_snapshot_loaded" -> "云端数据检查完成" to "已读取云端对象和本次发生变化的数据。"
    "conflicts_waiting" -> "发现同步冲突" to "本机和云端均有修改，需要等待用户选择。"
    "upload_queue_ready" -> "待上传数据已整理" to "已统计本次需要上传到云端的更改。"
    "full_sync_finished" -> if (outcome == "success") {
        "云同步完成" to "本机与云端数据已经完成本轮同步。"
    } else {
        "云同步失败" to "本轮同步未正常完成，可结合结果和耗时继续排查。"
    }
    "documents_selected" -> "已选择导入文件" to "用户选择了准备加入书库的文件。"
    "documents_registered" -> "书籍导入登记完成" to "文件已复制并登记到书库，后续解析会在后台进行。"
    "background_index_finished" -> if (outcome == "success") {
        "书籍后台解析完成" to "章节目录和阅读所需数据已经生成。"
    } else {
        "书籍后台解析失败" to "书籍未能完成解析，相关临时数据会被清理。"
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
    "chapter_loaded" -> "章节内容加载完成" to "阅读器已经取得该章节的正文内容。"
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
    "error" -> "失败"
    else -> "失败（$outcome）"
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
    "failures" -> "导入失败"
    "chapters" -> "章节数"
    "preferredBook" -> "优先同步当前书籍"
    "known" -> "云端对象数"
    "changed" -> "云端变更数"
    "uploaded" -> "已上传项目数"
    "remoteChanged" -> "已处理云端变更"
    else -> key
}

private fun readableValue(key: String, value: String): String = when (key) {
    "prefetch" -> if (value == "true") "后台预加载" else "当前阅读"
    "prefetched" -> if (value == "true") "已提前加载" else "现场加载"
    "preferredBook" -> if (value == "true") "是" else "否"
    "priority" -> when (value) {
        "PREFETCH" -> "后台预加载"
        "USER" -> "用户请求"
        else -> value
    }
    "source" -> when (value) {
        "epub_disk_cache" -> "EPUB 磁盘缓存"
        "epub_parse" -> "实时解析 EPUB"
        else -> value
    }
    "requested" -> if (value == "all") "全部" else value
    else -> when (value) {
        "true" -> "是"
        "false" -> "否"
        else -> value
    }
}
