package com.kixyu9527.kixyubook

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat

internal data class ExternalBookImportRequest(val id: Long, val uris: List<String>)

internal fun Intent.supportedBookImportUris(): List<String> {
    val receivedUris = when (action) {
        Intent.ACTION_VIEW -> listOfNotNull(data)
        Intent.ACTION_SEND -> buildList {
            IntentCompat.getParcelableExtra(
                this@supportedBookImportUris,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )?.let(::add)
            addClipDataUris(this@supportedBookImportUris)
        }
        Intent.ACTION_SEND_MULTIPLE -> buildList {
            IntentCompat.getParcelableArrayListExtra(
                this@supportedBookImportUris,
                Intent.EXTRA_STREAM,
                Uri::class.java,
            )?.let(::addAll)
            addClipDataUris(this@supportedBookImportUris)
        }
        else -> emptyList()
    }.distinct()

    return receivedUris.filter { uri ->
        val displayPath = uri.lastPathSegment.orEmpty()
        type in setOf("text/plain", "application/epub+zip") ||
            displayPath.endsWith(".txt", ignoreCase = true) ||
            displayPath.endsWith(".epub", ignoreCase = true)
    }.map(Uri::toString)
}

private fun MutableList<Uri>.addClipDataUris(intent: Intent) {
    val clipData = intent.clipData ?: return
    repeat(clipData.itemCount) { index -> clipData.getItemAt(index).uri?.let(::add) }
}
