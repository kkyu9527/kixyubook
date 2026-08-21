package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.TextCorrection
import com.kixyu9527.kixyubook.core.common.model.TextCorrectionStatus
import com.kixyu9527.kixyubook.core.common.repository.BookRepository
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CorrectionManagementState(
    val corrections: List<TextCorrection> = emptyList(),
    val chapters: List<Chapter> = emptyList(),
)

@HiltViewModel
class CorrectionManagementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TextCorrectionRepository,
    books: BookRepository,
) : ViewModel() {
    private val bookUuid: String = checkNotNull(savedStateHandle["bookUuid"])
    private val _state = MutableStateFlow(CorrectionManagementState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(chapters = books.getChapters(bookUuid)) }
            repository.observeBookCorrections(bookUuid).collect { corrections ->
                _state.update { it.copy(corrections = corrections) }
            }
        }
    }

    fun update(uuid: String, replacement: String) = viewModelScope.launch {
        repository.updateCorrection(uuid, replacement)
    }

    fun delete(uuid: String) = viewModelScope.launch { repository.deleteCorrection(uuid) }
    fun resolve(uuid: String) = viewModelScope.launch { repository.resolveConflict(uuid) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionManagementRoute(
    onBack: () -> Unit,
    viewModel: CorrectionManagementViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TextCorrection?>(null) }
    // System Back belongs to NavHost here so the previous reader destination participates in the
    // platform predictive preview. The explicit callback remains only for the toolbar button.
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文字纠错") },
                navigationIcon = {
                    KixyuIconButton(onClick = onBack) {
                        Icon(KixyuSymbols.ArrowBack, "返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.corrections.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(KixyuSpacing.extraLarge),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("还没有文字纠错", style = MaterialTheme.typography.titleLarge)
                Text("在阅读页长按正文，然后选择“纠正段落”即可添加。")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                items(state.corrections, key = TextCorrection::uuid) { correction ->
                    val title = state.chapters.firstOrNull {
                        it.chapterKey == correction.chapterKey || it.index == correction.chapterIndex
                    }?.title ?: "第 ${correction.chapterIndex + 1} 章"
                    Column(
                        Modifier.fillMaxWidth().clickable { editing = correction }
                            .padding(horizontal = KixyuSpacing.large, vertical = KixyuSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (correction.status != TextCorrectionStatus.ACTIVE) {
                                Icon(
                                    KixyuSymbols.ErrorOutline,
                                    correction.status.name,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        val difference = remember(correction.exactText, correction.replacementText) {
                            correctionDifferenceMasks(correction.exactText, correction.replacementText)
                        }
                        CorrectionDifferenceText(
                            text = correction.exactText,
                            changed = difference.original,
                            changedColor = MaterialTheme.colorScheme.error,
                            changedBackground = MaterialTheme.colorScheme.error.copy(alpha = 0.10f),
                            changedDecoration = TextDecoration.LineThrough,
                            maxLines = 2,
                        )
                        CorrectionDifferenceText(
                            text = correction.replacementText,
                            prefix = "→ ",
                            changed = difference.replacement,
                            changedColor = if (correction.status == TextCorrectionStatus.ACTIVE) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            changedBackground = if (correction.status == TextCorrectionStatus.ACTIVE) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
                            },
                            maxLines = 2,
                        )
                        if (correction.status == TextCorrectionStatus.CONFLICT) {
                            KixyuButton(
                                text = "采用此版本",
                                onClick = { viewModel.resolve(correction.uuid) },
                            )
                        }
                        if (correction.status == TextCorrectionStatus.UNRESOLVED) {
                            Text(
                                "原文已变化，已停止覆盖；可撤销后重新添加。",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    editing?.let { correction ->
        CorrectionEditDialog(
            original = correction.exactText,
            initialReplacement = correction.replacementText,
            existing = correction,
            onDismiss = { editing = null },
            onSave = { viewModel.update(correction.uuid, it); editing = null },
            onDelete = { viewModel.delete(correction.uuid); editing = null },
        )
    }
}

@Composable
private fun CorrectionDifferenceText(
    text: String,
    changed: BooleanArray,
    changedColor: Color,
    changedBackground: Color,
    maxLines: Int,
    prefix: String = "",
    changedDecoration: TextDecoration? = null,
) {
    val normalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated = remember(
        text,
        changed,
        prefix,
        normalColor,
        changedColor,
        changedBackground,
        changedDecoration,
    ) {
        buildAnnotatedString {
            if (prefix.isNotEmpty()) {
                withStyle(SpanStyle(color = normalColor)) { append(prefix) }
            }
            var start = 0
            while (start < text.length) {
                val isChanged = changed.getOrElse(start) { true }
                var end = start + 1
                while (end < text.length && changed.getOrElse(end) { true } == isChanged) end++
                withStyle(
                    if (isChanged) {
                        SpanStyle(
                            color = changedColor,
                            background = changedBackground,
                            textDecoration = changedDecoration,
                        )
                    } else {
                        SpanStyle(color = normalColor)
                    },
                ) {
                    append(text, start, end)
                }
                start = end
            }
        }
    }
    Text(annotated, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

internal data class CorrectionDifferenceMasks(
    val original: BooleanArray,
    val replacement: BooleanArray,
)

/**
 * Marks only inserted, deleted, and replaced characters. A bounded LCS keeps multiple edits in
 * one paragraph precise; exceptionally large replacements fall back to their common prefix and
 * suffix so opening the management screen never allocates an unbounded matrix.
 */
internal fun correctionDifferenceMasks(original: String, replacement: String): CorrectionDifferenceMasks {
    val originalChanged = BooleanArray(original.length)
    val replacementChanged = BooleanArray(replacement.length)
    var prefix = 0
    val sharedLimit = minOf(original.length, replacement.length)
    while (prefix < sharedLimit && original[prefix] == replacement[prefix]) prefix++

    var suffix = 0
    while (
        suffix < original.length - prefix &&
        suffix < replacement.length - prefix &&
        original[original.lastIndex - suffix] == replacement[replacement.lastIndex - suffix]
    ) {
        suffix++
    }
    val originalEnd = original.length - suffix
    val replacementEnd = replacement.length - suffix
    val originalMiddleLength = originalEnd - prefix
    val replacementMiddleLength = replacementEnd - prefix
    if (originalMiddleLength == 0 && replacementMiddleLength == 0) {
        return CorrectionDifferenceMasks(originalChanged, replacementChanged)
    }

    originalChanged.fill(true, prefix, originalEnd)
    replacementChanged.fill(true, prefix, replacementEnd)
    if (originalMiddleLength.toLong() * replacementMiddleLength > MAX_CORRECTION_DIFF_CELLS) {
        return CorrectionDifferenceMasks(originalChanged, replacementChanged)
    }

    val lcs = Array(originalMiddleLength + 1) { IntArray(replacementMiddleLength + 1) }
    for (oldIndex in originalMiddleLength - 1 downTo 0) {
        for (newIndex in replacementMiddleLength - 1 downTo 0) {
            lcs[oldIndex][newIndex] = if (original[prefix + oldIndex] == replacement[prefix + newIndex]) {
                lcs[oldIndex + 1][newIndex + 1] + 1
            } else {
                maxOf(lcs[oldIndex + 1][newIndex], lcs[oldIndex][newIndex + 1])
            }
        }
    }
    var oldIndex = 0
    var newIndex = 0
    while (oldIndex < originalMiddleLength && newIndex < replacementMiddleLength) {
        if (original[prefix + oldIndex] == replacement[prefix + newIndex]) {
            originalChanged[prefix + oldIndex] = false
            replacementChanged[prefix + newIndex] = false
            oldIndex++
            newIndex++
        } else if (lcs[oldIndex + 1][newIndex] >= lcs[oldIndex][newIndex + 1]) {
            oldIndex++
        } else {
            newIndex++
        }
    }
    return CorrectionDifferenceMasks(originalChanged, replacementChanged)
}

private const val MAX_CORRECTION_DIFF_CELLS = 250_000L
