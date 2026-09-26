package com.kixyu9527.kixyubook.core.common.model

fun originalExportFileName(title: String, sourceFormat: String): String {
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
    return "$safeTitle.$extension"
}

fun exportMimeType(sourceFormat: String): String = when (sourceFormat.uppercase()) {
    "EPUB" -> "application/epub+zip"
    "TXT" -> "text/plain"
    "MOBI" -> "application/x-mobipocket-ebook"
    "AZW3" -> "application/vnd.amazon.ebook"
    else -> "application/octet-stream"
}

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
