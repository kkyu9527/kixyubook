package com.kixyu9527.kixyubook.feature.library

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.LibrarySortMode
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuActionDialog
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

internal fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun LibrarySortMode.localizedLabel(): String = stringResource(
    when (this) {
        LibrarySortMode.RECENT -> R.string.library_sort_recent
        LibrarySortMode.IMPORTED -> R.string.library_sort_imported
        LibrarySortMode.TITLE -> R.string.library_sort_title
        LibrarySortMode.AUTHOR -> R.string.library_sort_author
        LibrarySortMode.PROGRESS -> R.string.library_sort_progress
        LibrarySortMode.CUSTOM -> R.string.library_sort_custom
    },
)

@Composable
internal fun LibraryDisplayDialog(
    selectedSortMode: LibrarySortMode,
    selectedLayoutMode: LibraryLayoutMode,
    onSortMode: (LibrarySortMode) -> Unit,
    onLayoutMode: (LibraryLayoutMode) -> Unit,
    onDismiss: () -> Unit,
) {
    KixyuActionDialog(
        show = true,
        title = stringResource(R.string.library_display_title),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.library_action_done),
        onConfirm = onDismiss,
        dismissLabel = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            Text(
                stringResource(R.string.library_layout),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
            ) {
                LibraryLayoutMode.entries.forEach { mode ->
                    val selected = mode == selectedLayoutMode
                    Surface(
                        onClick = { onLayoutMode(mode) },
                        modifier = Modifier.weight(1f),
                        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = KixyuSpacing.medium,
                                vertical = KixyuSpacing.small,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                        ) {
                            Icon(
                                imageVector = if (mode == LibraryLayoutMode.GRID) {
                                    KixyuSymbols.GridView
                                } else {
                                    KixyuSymbols.ViewList
                                },
                                contentDescription = null,
                                tint = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                stringResource(
                                    if (mode == LibraryLayoutMode.GRID) {
                                        R.string.library_layout_grid
                                    } else {
                                        R.string.library_layout_list
                                    },
                                ),
                            )
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.library_sort),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            librarySortDisplayOrder.forEach { mode ->
                Surface(
                    onClick = { onSortMode(mode) },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (mode == selectedSortMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = mode == selectedSortMode, onClick = null)
                        Column(Modifier.padding(start = KixyuSpacing.small)) {
                            Text(mode.localizedLabel(), style = MaterialTheme.typography.bodyLarge)
                            if (mode == LibrarySortMode.CUSTOM) {
                                Text(
                                    stringResource(R.string.library_custom_sort_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val librarySortDisplayOrder = listOf(
    LibrarySortMode.RECENT,
    LibrarySortMode.CUSTOM,
    LibrarySortMode.IMPORTED,
    LibrarySortMode.TITLE,
    LibrarySortMode.AUTHOR,
    LibrarySortMode.PROGRESS,
)

@Composable
internal fun CategoryVisibilityDialog(
    categories: List<String>,
    hiddenCategories: Set<String>,
    onHiddenChange: (String, Boolean) -> Unit,
    onOpenHiddenLibrary: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    KixyuActionDialog(
        show = true,
        title = stringResource(R.string.library_manage_categories),
        onDismissRequest = onDismiss,
        confirmLabel = stringResource(R.string.library_action_done),
        onConfirm = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
            Text(
                stringResource(R.string.library_hidden_categories_explanation),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            if (onOpenHiddenLibrary != null) {
                Surface(
                    onClick = onOpenHiddenLibrary,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(KixyuSpacing.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                    ) {
                        Icon(KixyuSymbols.VisibilityOff, null, Modifier.size(KixyuSize.icon))
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.library_hidden_title), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.library_hidden_category_count, hiddenCategories.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(stringResource(R.string.library_open_export_location), color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
            ) {
                categories.forEach { category ->
                    val hidden = category in hiddenCategories
                    val categoryState = stringResource(
                        if (hidden) R.string.library_category_hidden else R.string.library_category_visible,
                    )
                    Surface(
                        onClick = { onHiddenChange(category, !hidden) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.small),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    categoryState,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = !hidden,
                                onCheckedChange = { onHiddenChange(category, !it) },
                                modifier = Modifier.semantics { stateDescription = categoryState },
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun Modifier.revealHiddenCategoriesGesture(
    enabled: Boolean,
    onReveal: () -> Unit,
): Modifier = if (!enabled) this else pointerInput(onReveal) {
    awaitPointerEventScope {
        var distance = 0f
        var intercepting = false
        var revealed = false
        while (true) {
            // Observe before child click handlers. As soon as a second pointer joins the
            // gesture, consume the entire pointer stream so releasing either finger cannot
            // complete a pending book click.
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (!intercepting && pressed.size >= 2) {
                intercepting = true
                distance = 0f
            }
            if (intercepting) {
                event.changes.forEach { it.consume() }
            }
            if (pressed.size >= 2 && !revealed) {
                distance += pressed.sumOf { (it.position.y - it.previousPosition.y).toDouble() }.toFloat() / pressed.size
                if (distance > viewConfiguration.touchSlop * 4f) {
                    revealed = true
                    onReveal()
                }
            } else if (pressed.isEmpty()) {
                distance = 0f
                intercepting = false
                revealed = false
            }
        }
    }
}

internal fun exportFileName(item: LibraryBook): String {
    val extension = item.book.format.name.lowercase()
    val withoutExistingExtension = item.book.title.trim().replace(
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

internal fun openExportLocation(context: Context, uriString: String): Boolean {
    val uri = uriString.toUri()
    val parentUri = exportedDocumentParent(context, uri)
    if (parentUri != null) {
        val directoryIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(parentUri, DocumentsContract.Document.MIME_TYPE_DIR)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (context.tryStartActivity(directoryIntent)) return true
    }
    val fileIntent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/octet-stream")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    return context.tryStartActivity(fileIntent)
}

private fun exportedDocumentParent(context: Context, uri: Uri): Uri? {
    if (!DocumentsContract.isDocumentUri(context, uri)) return null
    val authority = uri.authority ?: return null
    val parentId = runCatching {
        DocumentsContract.findDocumentPath(context.contentResolver, uri)
            ?.path
            ?.dropLast(1)
            ?.lastOrNull()
    }.getOrNull() ?: if (authority == "com.android.externalstorage.documents") {
        // ExternalStorageProvider uses volume:path document IDs. Other providers are opaque and
        // must not be guessed by splitting their IDs.
        runCatching {
            DocumentsContract.getDocumentId(uri).substringBeforeLast('/', missingDelimiterValue = "")
                .takeIf(String::isNotBlank)
        }.getOrNull()
    } else {
        null
    }
    return parentId?.let { DocumentsContract.buildDocumentUri(authority, it) }
}

private fun Context.tryStartActivity(intent: Intent): Boolean = runCatching {
    startActivity(intent)
}.isSuccess

@Composable
internal fun BookManagementDialog(
    item: LibraryBook,
    dismiss: () -> Unit,
    save: (String, String, String, String) -> Unit,
) {
    var title by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.title) }
    var author by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.author) }
    var description by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.description) }
    var category by rememberSaveable(item.book.uuid) { mutableStateOf(item.book.category) }
    KixyuActionDialog(
        show = true,
        title = stringResource(R.string.library_edit_book),
        onDismissRequest = dismiss,
        confirmLabel = stringResource(R.string.library_action_save),
        onConfirm = { save(title, author, description, category) },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
        ) {
            OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.library_book_title)) }, singleLine = true)
            OutlinedTextField(author, { author = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.library_book_author)) }, singleLine = true)
            OutlinedTextField(
                description,
                { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.library_book_description)) },
                minLines = 3,
                maxLines = 6,
            )
            OutlinedTextField(category, { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.library_book_category)) }, singleLine = true)
        }
    }
}
