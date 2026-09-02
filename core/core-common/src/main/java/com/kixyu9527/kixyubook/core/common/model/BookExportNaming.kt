package com.kixyu9527.kixyubook.core.common.model

fun correctedExportFileName(title: String, sourceFormat: String): String {
    val extension = sourceFormat.lowercase()
    val withoutExistingExtension = title.trim().replace(
        Regex("\\.${Regex.escape(extension)}$", RegexOption.IGNORE_CASE),
        "",
    )
    val safeTitle = withoutExistingExtension
        .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_")
        .trim(' ', '.')
        .take(120)
        .ifBlank { "未命名书籍" }
    return "$safeTitle-纠错版.txt"
}
