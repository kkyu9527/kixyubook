package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.common.operation.UserOperationController
import com.kixyu9527.kixyubook.core.common.repository.ReaderAnnotationRepository
import com.kixyu9527.kixyubook.core.common.repository.TextCorrectionRepository
import kotlinx.coroutines.flow.StateFlow

/** Editing commands own no pager or navigation state; all writes use the shared operation policy. */
internal class ReaderAnnotationActions(
    private val bookUuid: String,
    private val _uiState: StateFlow<ReaderUiState>,
    private val annotations: ReaderAnnotationRepository,
    private val textCorrections: TextCorrectionRepository,
    private val operations: UserOperationController,
) {
    fun saveParagraphCorrection(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        replacementText: String,
    ) {
        val state = _uiState.value
        val chapterPosition = state.chapters.indexOfFirst { it.index == chapterIndex }
        val chapter = state.chapters.getOrNull(chapterPosition) ?: return
        val existing = state.corrections.firstOrNull {
            it.chapterKey == chapter.chapterKey &&
                it.paragraphIndex == paragraphIndex && it.status != TextCorrectionStatus.UNRESOLVED
        }
        operations.submit {
            if (existing != null) {
                textCorrections.updateCorrection(existing.uuid, replacementText)
            } else {
                textCorrections.createParagraphCorrection(
                    bookUuid = bookUuid,
                    chapterKey = chapter.chapterKey,
                    chapterIndex = chapter.index,
                    paragraphIndex = paragraphIndex,
                    originalText = displayedText,
                    replacementText = replacementText,
                )
            }
        }
    }

    fun deleteCorrection(uuid: String) {
        operations.confirmDelete {
            textCorrections.deleteCorrection(uuid)
        }
    }

    fun saveParagraphHighlight(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
    ) {
        saveAnnotation(chapterIndex, paragraphIndex, displayedText, startOffset, endOffset, ReaderAnnotationStyle.HIGHLIGHT)
    }

    fun saveParagraphUnderline(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
    ) {
        saveAnnotation(chapterIndex, paragraphIndex, displayedText, startOffset, endOffset, ReaderAnnotationStyle.UNDERLINE)
    }

    fun saveParagraphNote(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
        note: String,
    ) {
        operations.submit {
            val existing = findAnnotation(chapterIndex, paragraphIndex, startOffset, endOffset)
            if (existing != null) {
                checkNotNull(annotations.updateNote(existing.uuid, note))
            } else {
                createAnnotation(chapterIndex, paragraphIndex, displayedText, startOffset, endOffset, ReaderAnnotationStyle.HIGHLIGHT, note)
            }
        }
    }

    fun deleteAnnotation(uuid: String) {
        operations.confirmDelete { annotations.deleteAnnotation(uuid) }
    }

    fun updateAnnotationNote(uuid: String, note: String) {
        operations.submit { checkNotNull(annotations.updateNote(uuid, note.trim())) }
    }

    private fun saveAnnotation(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
        style: ReaderAnnotationStyle,
    ) {
        operations.submit {
            val existing = findAnnotation(chapterIndex, paragraphIndex, startOffset, endOffset)
            if (existing?.style == style && existing.note.isBlank()) {
                annotations.deleteAnnotation(existing.uuid)
            } else {
                createAnnotation(chapterIndex, paragraphIndex, displayedText, startOffset, endOffset, style, existing?.note.orEmpty())
            }
        }
    }

    private suspend fun createAnnotation(
        chapterIndex: Int,
        paragraphIndex: Int,
        displayedText: String,
        startOffset: Int,
        endOffset: Int,
        style: ReaderAnnotationStyle,
        note: String,
    ) {
        val chapter = checkNotNull(_uiState.value.chapters.firstOrNull { it.index == chapterIndex })
        annotations.createAnnotation(
            bookUuid = bookUuid,
            chapterKey = chapter.chapterKey,
            chapterIndex = chapter.index,
            paragraphIndex = paragraphIndex,
            originalText = displayedText,
            startOffset = startOffset,
            endOffset = endOffset,
            style = style,
            note = note,
        )
    }

    private fun findAnnotation(
        chapterIndex: Int,
        paragraphIndex: Int,
        startOffset: Int,
        endOffset: Int,
    ): ReaderAnnotation? =
        _uiState.value.annotations.firstOrNull {
            it.chapterIndex == chapterIndex && it.paragraphIndex == paragraphIndex &&
                it.startOffset == startOffset && it.endOffset == endOffset
        }

}
