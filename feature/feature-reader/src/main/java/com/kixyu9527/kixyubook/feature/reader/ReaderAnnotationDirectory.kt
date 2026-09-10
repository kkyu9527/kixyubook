package com.kixyu9527.kixyubook.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotation
import com.kixyu9527.kixyubook.core.common.model.ReaderAnnotationStyle
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuDropdownRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuListRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSearchField
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

private enum class AnnotationFilter { ALL, NOTE, HIGHLIGHT, UNDERLINE }

/** Search, filter, edit and navigation for reader-owned source-anchored annotations. */
@Composable
internal fun ReaderAnnotationDirectory(
    state: ReaderUiState,
    expandedLayout: Boolean,
    selectAnnotation: (ReaderAnnotation) -> Unit,
    updateAnnotationNote: (String, String) -> Unit,
    deleteAnnotation: (String) -> Unit,
) {
    val filterLabels = mapOf(
        AnnotationFilter.ALL to stringResource(R.string.reader_annotation_filter_all),
        AnnotationFilter.NOTE to stringResource(R.string.reader_annotation_note),
        AnnotationFilter.HIGHLIGHT to stringResource(R.string.reader_annotation_highlight),
        AnnotationFilter.UNDERLINE to stringResource(R.string.reader_annotation_underline),
    )
    var query by rememberSaveable { mutableStateOf("") }
    var editor by remember { mutableStateOf<ReaderAnnotation?>(null) }
    var filterName by rememberSaveable { mutableStateOf(AnnotationFilter.ALL.name) }
    val filter = AnnotationFilter.entries.firstOrNull { it.name == filterName } ?: AnnotationFilter.ALL
    val visible = remember(state.annotations, query, filter) {
        state.annotations.filter { annotation ->
            (query.isBlank() || annotation.exactText.contains(query, ignoreCase = true) ||
                annotation.note.contains(query, ignoreCase = true)) &&
                when (filter) {
                    AnnotationFilter.ALL -> true
                    AnnotationFilter.NOTE -> annotation.note.isNotBlank()
                    AnnotationFilter.HIGHLIGHT -> annotation.style == ReaderAnnotationStyle.HIGHLIGHT &&
                        annotation.note.isBlank()
                    AnnotationFilter.UNDERLINE -> annotation.style == ReaderAnnotationStyle.UNDERLINE &&
                        annotation.note.isBlank()
                }
        }
    }

    Column(
        if (expandedLayout) Modifier.fillMaxSize()
        else Modifier.fillMaxWidth().height(KixyuSize.readerSheetMaxContent),
    ) {
        KixyuSearchField(
            query = query,
            onQueryChange = { query = it },
            onSearch = { submittedQuery -> query = submittedQuery },
            expanded = true,
            onExpandedChange = {},
            placeholder = stringResource(R.string.reader_search_annotations),
            modifier = Modifier.fillMaxWidth().padding(horizontal = KixyuSpacing.medium),
        )
        KixyuDropdownRow(
            title = stringResource(R.string.reader_annotation_filter),
            selected = filter,
            options = AnnotationFilter.entries,
            optionLabel = { filterLabels.getValue(it) },
            onSelected = { filterName = it.name },
        )
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.reader_no_annotations),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = ReaderAnnotation::uuid) { annotation ->
                    val chapterTitle = state.chapters.firstOrNull {
                        it.index == annotation.chapterIndex
                    }?.title.orEmpty()
                    KixyuListRow(
                        title = annotation.exactText,
                        supportingText = listOf(chapterTitle, annotation.note)
                            .filter(String::isNotBlank).joinToString(" · "),
                        onClick = { selectAnnotation(annotation) },
                        leading = {
                            Icon(
                                if (annotation.note.isNotBlank()) {
                                    KixyuSymbols.EditNoteRounded
                                } else if (annotation.style == ReaderAnnotationStyle.HIGHLIGHT) {
                                    KixyuSymbols.Edit
                                } else {
                                    KixyuSymbols.FormatUnderlined
                                },
                                null,
                            )
                        },
                        trailing = {
                            Row {
                                KixyuIconButton(onClick = { editor = annotation }) {
                                    Icon(
                                        KixyuSymbols.EditNoteRounded,
                                        stringResource(R.string.reader_edit_annotation_note),
                                    )
                                }
                                KixyuIconButton(onClick = { deleteAnnotation(annotation.uuid) }) {
                                    Icon(
                                        KixyuSymbols.DeleteOutline,
                                        stringResource(R.string.reader_delete_annotation),
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    editor?.let { annotation ->
        AnnotationNoteDialog(
            excerpt = annotation.exactText,
            initialNote = annotation.note,
            onDismiss = { editor = null },
            onSave = { note ->
                updateAnnotationNote(annotation.uuid, note)
            },
            onDelete = {
                deleteAnnotation(annotation.uuid)
            },
        )
    }
}
