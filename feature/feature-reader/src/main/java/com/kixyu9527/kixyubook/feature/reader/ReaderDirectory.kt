package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuListRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPopupSurface
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt


internal enum class DirectoryView { CHAPTERS, BOOKMARKS, ANNOTATIONS }

@Composable
internal fun DirectorySheet(
    state: ReaderUiState,
    selectChapter: (Int) -> Unit,
    selectNavigation: (String) -> Unit,
    selectBookmark: (Bookmark) -> Unit,
    selectAnnotation: (ReaderAnnotation) -> Unit,
    deleteBookmark: (String) -> Unit,
    updateAnnotationNote: (String, String) -> Unit,
    deleteAnnotation: (String) -> Unit,
    expandedLayout: Boolean = false,
) {
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    var directoryView by rememberSaveable { mutableStateOf(DirectoryView.CHAPTERS) }
    val bookmarkedChapterIds = remember(state.bookmarks) { state.bookmarks.mapTo(mutableSetOf(), Bookmark::chapterId) }
    val currentIndex = state.chapterIndex.coerceIn(0, state.chapters.lastIndex.coerceAtLeast(0))
    val currentChapterId = state.chapters.getOrNull(currentIndex)?.id
    val collapsedDirectoryRows = remember(state.chapters, state.epubNavigation) {
        buildDirectoryRows(state.chapters, emptyMap(), state.epubNavigation)
    }
    val currentVolumeRow = remember(collapsedDirectoryRows, currentChapterId) {
        collapsedDirectoryRows.filterIsInstance<DirectoryRow.Volume>()
            .firstOrNull { currentChapterId in it.chapterIds }
    }
    val currentVolume = currentVolumeRow?.index
    val expandedVolumes = remember(state.book?.uuid) {
        mutableStateMapOf<Int, Boolean>().apply {
            currentVolume?.let { this[it] = true }
        }
    }
    val directoryRows = remember(state.chapters, expandedVolumes.toMap(), state.epubNavigation) {
        buildDirectoryRows(state.chapters, expandedVolumes, state.epubNavigation)
    }
    val currentRowIndex = directoryRows.indexOfFirst { row ->
        when (row) {
            is DirectoryRow.ChapterRow -> row.index == currentIndex
            is DirectoryRow.Volume -> row.targetChapterIndex == currentIndex
        }
    }.coerceAtLeast(0)
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = directoryChapterScrollAnchor(directoryRows, currentRowIndex),
    )
    val scope = rememberCoroutineScope()
    val stickyVolume by remember(directoryRows, listState) {
        derivedStateOf {
            stickyVolumeFor(
                rows = directoryRows,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset,
            )
        }
    }
    LaunchedEffect(currentIndex, directoryView) {
        if (directoryView == DirectoryView.CHAPTERS && state.chapters.isNotEmpty()) {
            currentVolume?.let { expandedVolumes[it] = true }
            val targetRows = buildDirectoryRows(state.chapters, expandedVolumes, state.epubNavigation)
            val target = targetRows.indexOfFirst { row ->
                when (row) {
                    is DirectoryRow.ChapterRow -> row.index == currentIndex
                    is DirectoryRow.Volume -> row.targetChapterIndex == currentIndex
                }
            }.coerceAtLeast(0)
            if (targetRows.isNotEmpty()) {
                listState.scrollToItem(directoryChapterScrollAnchor(targetRows, target))
            }
        }
    }
    Column(if (expandedLayout) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = KixyuSpacing.large,
                top = KixyuSpacing.medium,
                end = KixyuSpacing.small,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                when (directoryView) {
                    DirectoryView.CHAPTERS -> androidx.compose.ui.res.pluralStringResource(R.plurals.reader_directory_title, state.chapters.size, state.chapters.size)
                    DirectoryView.BOOKMARKS -> stringResource(R.string.reader_bookmarks_title, state.bookmarks.size)
                    DirectoryView.ANNOTATIONS -> stringResource(R.string.reader_annotations_title, state.annotations.size)
                },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            KixyuIconButton(onClick = {
                directoryView = if (directoryView == DirectoryView.ANNOTATIONS) {
                    DirectoryView.CHAPTERS
                } else {
                    DirectoryView.ANNOTATIONS
                }
            }) {
                Icon(
                    if (directoryView == DirectoryView.ANNOTATIONS) KixyuSymbols.Toc
                    else KixyuSymbols.EditNoteRounded,
                    stringResource(
                        if (directoryView == DirectoryView.ANNOTATIONS) R.string.reader_view_directory
                        else R.string.reader_view_annotations,
                    ),
                    tint = if (directoryView == DirectoryView.ANNOTATIONS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            KixyuIconButton(onClick = {
                directoryView = if (directoryView == DirectoryView.BOOKMARKS) {
                    DirectoryView.CHAPTERS
                } else {
                    DirectoryView.BOOKMARKS
                }
            }) {
                Icon(
                    if (directoryView == DirectoryView.BOOKMARKS) KixyuSymbols.Toc
                    else KixyuSymbols.Bookmarks,
                    stringResource(
                        if (directoryView == DirectoryView.BOOKMARKS) R.string.reader_view_directory
                        else R.string.reader_view_bookmarks,
                    ),
                    tint = if (directoryView == DirectoryView.BOOKMARKS) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
        AnimatedContent(
            targetState = directoryView,
            modifier = if (expandedLayout) Modifier.weight(1f).fillMaxWidth() else Modifier.fillMaxWidth(),
            transitionSpec = {
                if (targetState == DirectoryView.BOOKMARKS) {
                    (slideInHorizontally(tween(KixyuMotion.ReaderPopupEnterMillis)) { it / 3 } +
                        fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis))) togetherWith
                        (slideOutHorizontally(tween(KixyuMotion.ReaderPopupExitMillis)) { -it / 3 } +
                            fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)))
                } else {
                    (slideInHorizontally(tween(KixyuMotion.ReaderPopupEnterMillis)) { -it / 3 } +
                        fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis))) togetherWith
                        (slideOutHorizontally(tween(KixyuMotion.ReaderPopupExitMillis)) { it / 3 } +
                            fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)))
                }
            },
            label = "directoryBookmarks",
        ) { view ->
            if (view == DirectoryView.CHAPTERS) {
                Box(
                    if (expandedLayout) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth().height(KixyuSize.readerSheetMaxContent),
                ) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.fillMaxWidth().padding(
                            end = if (directoryRows.size >= FAST_SCROLLER_MIN_CHAPTERS) {
                                KixyuSize.directoryFastScrollerWidth
                            } else 0.dp,
                        ),
                        state = listState,
                    ) {
                        items(
                            count = directoryRows.size,
                            key = { rowIndex -> directoryRows[rowIndex].key },
                        ) { rowIndex ->
                            when (val row = directoryRows[rowIndex]) {
                                is DirectoryRow.Volume -> {
                                    val expanded = expandedVolumes[row.index] == true
                                    val hasBookmark = row.chapterIds.any { it in bookmarkedChapterIds }
                                    val current = row.hasOwnContent && row.targetChapterIndex == state.chapterIndex
                                    KixyuListRow(
                                        title = row.title,
                                        supportingText = if (row.chapterCount > 0) {
                                            androidx.compose.ui.res.pluralStringResource(R.plurals.reader_chapter_count, row.chapterCount, row.chapterCount)
                                        } else {
                                            stringResource(R.string.reader_volume_content)
                                        },
                                        titleStyle = MaterialTheme.typography.bodyMedium,
                                        titleMaxLines = 2,
                                        supportingTextStyle = MaterialTheme.typography.bodySmall,
                                        selected = current,
                                        highlighted = hasBookmark,
                                        onClick = { selectChapter(row.targetChapterIndex) },
                                        leading = {
                                            KixyuIconButton(
                                                onClick = { expandedVolumes[row.index] = !expanded },
                                                modifier = Modifier.size(KixyuSize.readerControlButton),
                                            ) {
                                                Icon(
                                                    if (expanded) KixyuSymbols.KeyboardArrowDown else KixyuSymbols.KeyboardArrowRight,
                                                    stringResource(
                                                        if (expanded) R.string.reader_collapse_volume
                                                        else R.string.reader_expand_volume,
                                                        row.title,
                                                    ),
                                                    tint = if (current && isMiuix) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    },
                                                )
                                            }
                                        },
                                        trailing = {
                                            if (hasBookmark) Icon(
                                                KixyuSymbols.BookmarkFilled,
                                                stringResource(R.string.reader_volume_has_bookmark),
                                                tint = if (current && isMiuix) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                        },
                                        modifier = if (isMiuix) Modifier.animateItem().padding(
                                            horizontal = KixyuSpacing.medium,
                                            vertical = KixyuSpacing.extraSmall,
                                        ) else Modifier.animateItem(),
                                    )
                                }
                                is DirectoryRow.ChapterRow -> {
                                    val chapter = state.chapters[row.index]
                                    val current = row.index == state.chapterIndex
                                    val hasBookmark = chapter.id in bookmarkedChapterIds
                                    KixyuListRow(
                                        title = row.navigationTitle ?: chapter.title,
                                        titleStyle = MaterialTheme.typography.bodyMedium,
                                        titleMaxLines = 2,
                                        selected = current,
                                        highlighted = hasBookmark,
                                        onClick = {
                                            row.navigationTarget?.let(selectNavigation) ?: selectChapter(row.index)
                                        },
                                        leading = {
                                            Box(Modifier.size(KixyuSize.icon), contentAlignment = Alignment.Center) {
                                                if (current) Icon(
                                                    KixyuSymbols.PlayArrow,
                                                    null,
                                                    Modifier.size(KixyuSize.icon),
                                                    tint = if (isMiuix) {
                                                        MaterialTheme.colorScheme.onPrimary
                                                    } else {
                                                        MaterialTheme.colorScheme.primary
                                                    },
                                                )
                                            }
                                        },
                                        trailing = {
                                            if (hasBookmark) Icon(
                                                KixyuSymbols.BookmarkFilled,
                                                stringResource(R.string.reader_chapter_has_bookmark),
                                                tint = if (current && isMiuix) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.primary
                                                },
                                            )
                                        },
                                        modifier = if (isMiuix) Modifier.animateItem().padding(
                                            start = KixyuSpacing.extraLarge,
                                            end = KixyuSpacing.medium,
                                            top = KixyuSpacing.extraSmall,
                                            bottom = KixyuSpacing.extraSmall,
                                        ) else Modifier.animateItem().padding(start = KixyuSpacing.large),
                                    )
                                }
                            }
                        }
                    }
                    if (directoryRows.size >= FAST_SCROLLER_MIN_CHAPTERS) {
                        DirectoryFastScroller(
                            itemCount = directoryRows.size,
                            listState = listState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                    androidx.compose.animation.AnimatedVisibility(
                        visible = stickyVolume != null,
                        modifier = Modifier.align(Alignment.TopStart),
                        enter = fadeIn(tween(KixyuMotion.ReaderPopupEnterMillis)) +
                            slideInVertically(tween(KixyuMotion.ReaderPopupEnterMillis)) { -it / 3 },
                        exit = fadeOut(tween(KixyuMotion.ReaderPopupExitMillis)) +
                            slideOutVertically(tween(KixyuMotion.ReaderPopupExitMillis)) { -it / 3 },
                    ) {
                        stickyVolume?.let { volume ->
                            val expanded = expandedVolumes[volume.index] == true
                            KixyuPopupSurface(
                                modifier = Modifier.fillMaxWidth()
                                    .padding(
                                        start = KixyuSpacing.medium,
                                        end = KixyuSpacing.medium + if (
                                            directoryRows.size >= FAST_SCROLLER_MIN_CHAPTERS
                                        ) KixyuSize.directoryFastScrollerWidth else 0.dp,
                                        top = KixyuSpacing.extraSmall,
                                    ),
                                shape = MaterialTheme.shapes.extraLarge,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shadowElevation = KixyuSpacing.extraSmall,
                            ) {
                                KixyuListRow(
                                    title = volume.title,
                                    supportingText = androidx.compose.ui.res.pluralStringResource(R.plurals.reader_current_volume_chapters, volume.chapterCount, volume.chapterCount),
                                    titleStyle = MaterialTheme.typography.bodyMedium,
                                    titleMaxLines = 2,
                                    supportingTextStyle = MaterialTheme.typography.bodySmall,
                                    onClick = {
                                        if (expanded) {
                                            val headerIndex = directoryRows.indexOfFirst {
                                                it is DirectoryRow.Volume && it.index == volume.index
                                            }
                                            scope.launch {
                                                if (headerIndex >= 0) listState.scrollToItem(headerIndex)
                                                expandedVolumes[volume.index] = false
                                            }
                                        } else {
                                            expandedVolumes[volume.index] = true
                                        }
                                    },
                                    leading = {
                                        Icon(
                                            if (expanded) {
                                                KixyuSymbols.KeyboardArrowDown
                                            } else {
                                                KixyuSymbols.KeyboardArrowRight
                                            },
                                            stringResource(
                                                if (expanded) R.string.reader_collapse_volume
                                                else R.string.reader_expand_volume,
                                                volume.title,
                                            ),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            } else if (view == DirectoryView.BOOKMARKS && state.bookmarks.isEmpty()) {
                Box(
                    if (expandedLayout) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth().height(KixyuSize.readerSheetMaxContent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.reader_no_bookmarks), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (view == DirectoryView.BOOKMARKS) {
                androidx.compose.foundation.lazy.LazyColumn(
                    if (expandedLayout) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth().height(KixyuSize.readerSheetMaxContent),
                ) {
                    items(state.bookmarks, key = Bookmark::uuid) { bookmark ->
                        KixyuListRow(
                            title = bookmark.chapterTitle,
                            supportingText = if (bookmark.preview.isBlank()) {
                                stringResource(R.string.reader_paragraph_number, bookmark.position + 1)
                            } else {
                                bookmark.preview
                            },
                            titleStyle = MaterialTheme.typography.bodyMedium,
                            supportingTextStyle = MaterialTheme.typography.bodySmall,
                            onClick = { selectBookmark(bookmark) },
                            leading = { Icon(KixyuSymbols.Bookmark, null) },
                            trailing = {
                                KixyuIconButton(onClick = { deleteBookmark(bookmark.uuid) }) {
                                    Icon(KixyuSymbols.DeleteOutline, stringResource(R.string.reader_delete_bookmark))
                                }
                            },
                            modifier = if (isMiuix) {
                                Modifier.padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.extraSmall)
                            } else Modifier,
                        )
                    }
                }
            } else {
                ReaderAnnotationDirectory(
                    state = state,
                    expandedLayout = expandedLayout,
                    selectAnnotation = selectAnnotation,
                    updateAnnotationNote = updateAnnotationNote,
                    deleteAnnotation = deleteAnnotation,
                )
            }
        }
    }
}

internal sealed interface DirectoryRow {
    val key: String

    data class Volume(
        val index: Int,
        val title: String,
        val chapterCount: Int,
        val chapterIds: Set<Long>,
        val targetChapterIndex: Int,
        val hasOwnContent: Boolean,
    ) : DirectoryRow {
        override val key = "volume:$index:$title:$targetChapterIndex"
    }

    data class ChapterRow(
        val index: Int,
        val id: Long,
        val volumeIndex: Int? = null,
        val navigationTitle: String? = null,
        val navigationTarget: String? = null,
    ) : DirectoryRow {
        override val key = "chapter:$id" + navigationTarget?.let { ":$it" }.orEmpty()
    }
}

/**
 * Keeps the selected chapter below the frozen volume card when the directory first opens. Two
 * compact rows provide enough clearance for both Material and MIUIX volume-card measurements;
 * anchoring never crosses into the preceding volume.
 */
internal fun directoryChapterScrollAnchor(
    rows: List<DirectoryRow>,
    targetIndex: Int,
): Int {
    val target = rows.getOrNull(targetIndex) as? DirectoryRow.ChapterRow ?: return targetIndex
    val volumeIndex = target.volumeIndex ?: return targetIndex
    val volumeHeaderIndex = (targetIndex downTo 0).firstOrNull {
        (rows[it] as? DirectoryRow.Volume)?.index == volumeIndex
    } ?: return targetIndex
    return (targetIndex - CURRENT_CHAPTER_HEADER_CLEARANCE_ROWS).coerceAtLeast(volumeHeaderIndex)
}

/**
 * Resolves the volume represented by the list's current viewport. This intentionally follows the
 * directory scroll position rather than the chapter being read: after the user scrolls from volume
 * one into volume two, volume two is the header frozen at the top of the directory.
 */
internal fun stickyVolumeFor(
    rows: List<DirectoryRow>,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    minimumHeaderScrollOffset: Int = 24,
): DirectoryRow.Volume? {
    if (firstVisibleItemIndex !in rows.indices) return null
    val volumeIndex = when (rows[firstVisibleItemIndex]) {
        is DirectoryRow.Volume -> firstVisibleItemIndex
        is DirectoryRow.ChapterRow -> {
            val expectedVolumeIndex = (rows[firstVisibleItemIndex] as DirectoryRow.ChapterRow)
                .volumeIndex ?: return null
            (firstVisibleItemIndex downTo 0).firstOrNull {
                (rows[it] as? DirectoryRow.Volume)?.index == expectedVolumeIndex
            } ?: return null
        }
    }
    val volume = rows[volumeIndex] as DirectoryRow.Volume
    val headerHasLeftItsRestingPosition = firstVisibleItemIndex > volumeIndex ||
        firstVisibleItemScrollOffset > minimumHeaderScrollOffset
    return volume.takeIf { headerHasLeftItsRestingPosition }
}

internal fun buildDirectoryRows(
    chapters: List<Chapter>,
    expandedVolumes: Map<Int, Boolean>,
    navigation: List<EpubNavigationEntry> = emptyList(),
): List<DirectoryRow> {
    val bySource = navigation.groupBy(EpubNavigationEntry::sourceIndex)
    return buildSpineDirectoryRows(chapters, expandedVolumes).flatMap { row ->
        val entries = (row as? DirectoryRow.ChapterRow)?.let { bySource[chapters[it.index].index] }.orEmpty()
        if (row is DirectoryRow.ChapterRow && entries.isNotEmpty()) {
            entries.map { row.copy(navigationTitle = it.title, navigationTarget = it.target) }
        } else listOf(row)
    }
}

private fun buildSpineDirectoryRows(
    chapters: List<Chapter>,
    expandedVolumes: Map<Int, Boolean>,
): List<DirectoryRow> {
    val sections = buildVolumeSections(chapters)
    if (sections.isEmpty()) {
        return chapters.mapIndexed { index, chapter -> DirectoryRow.ChapterRow(index, chapter.id) }
    }
    val sectionsByStart = sections.associateBy(VolumeSection::startIndex)
    val hiddenStandaloneTargets = sections.flatMapTo(hashSetOf(), VolumeSection::openingChapterIndices)
    return buildList {
        var position = 0
        while (position < chapters.size) {
            val section = sectionsByStart[position]
            if (section == null) {
                if (position !in hiddenStandaloneTargets) {
                    add(DirectoryRow.ChapterRow(position, chapters[position].id))
                }
                position++
                continue
            }
            add(
                DirectoryRow.Volume(
                    index = section.volumeIndex,
                    title = section.title,
                    chapterCount = section.childChapterIndices.size,
                    chapterIds = buildSet {
                        section.chapterIndices.mapTo(this) { chapters[it].id }
                        section.openingChapterIndices.mapTo(this) { chapters[it].id }
                    },
                    targetChapterIndex = section.targetChapterIndex,
                    hasOwnContent = section.openingChapterIndices.isNotEmpty(),
                ),
            )
            if (expandedVolumes[section.volumeIndex] == true) {
                section.childChapterIndices.forEach { chapterIndex ->
                    add(
                        DirectoryRow.ChapterRow(
                            index = chapterIndex,
                            id = chapters[chapterIndex].id,
                            volumeIndex = section.volumeIndex,
                        ),
                    )
                }
            }
            position = section.endIndexExclusive
        }
    }
}

private data class VolumeSection(
    val volumeIndex: Int,
    val title: String,
    val startIndex: Int,
    val endIndexExclusive: Int,
    val chapterIndices: List<Int>,
    val childChapterIndices: List<Int>,
    val targetChapterIndex: Int,
    /** Front-matter pages (plate, foreword) claimed by this volume and hidden from the list. */
    val openingChapterIndices: Set<Int>,
)

/**
 * Builds display-only volume sections without changing the underlying reading order. A publisher
 * supplied EPUB volume opening, or TXT prose between a volume heading and its first chapter, becomes
 * the row target. When no such page exists, the volume starts at its first child chapter.
 */
private fun buildVolumeSections(chapters: List<Chapter>): List<VolumeSection> = buildList {
    var position = 0
    var previousSectionEnd = 0
    val claimedStandaloneTargets = hashSetOf<Int>()
    while (position < chapters.size) {
        val chapter = chapters[position]
        val volumeIndex = chapter.volumeIndex
        val volumeTitle = chapter.volumeTitle
        if (volumeIndex == null || volumeTitle.isNullOrBlank()) {
            position++
            continue
        }
        val start = position
        while (
            position < chapters.size &&
            chapters[position].volumeIndex == volumeIndex &&
            chapters[position].volumeTitle == volumeTitle
        ) {
            position++
        }
        val end = position
        val normalizedTitle = volumeTitle.normalizedDirectoryTitle()
        // A publisher's volume opening is often two consecutive pages (a plate and a foreword).
        // Claim the whole contiguous front-matter run so the directory shows one volume row whose
        // target is the earliest page and the rest are read in order without extra rows.
        val precedingRun = (start - 1 downTo previousSectionEnd).takeWhile { candidate ->
            candidate !in claimedStandaloneTargets &&
                chapters[candidate].volumeIndex == null &&
                chapters[candidate].title.normalizedDirectoryTitle() == normalizedTitle
        }.toList()
        precedingRun.forEach(claimedStandaloneTargets::add)
        val inlineOpening = start.takeIf {
            chapters[it].title.normalizedDirectoryTitle() == normalizedTitle
        }
        val opening = (precedingRun + listOfNotNull(inlineOpening)).toSortedSet()
        val target = opening.firstOrNull() ?: start
        val chapterIndices = (start until end).toList()
        add(
            VolumeSection(
                volumeIndex = volumeIndex,
                title = volumeTitle,
                startIndex = start,
                endIndexExclusive = end,
                chapterIndices = chapterIndices,
                childChapterIndices = chapterIndices.filterNot { it in opening },
                targetChapterIndex = target,
                openingChapterIndices = opening,
            ),
        )
        previousSectionEnd = end
    }
}

internal fun String.normalizedDirectoryTitle(): String =
    trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')

internal const val FAST_SCROLLER_MIN_CHAPTERS = 30
private const val CURRENT_CHAPTER_HEADER_CLEARANCE_ROWS = 2

@Composable
internal fun DirectoryFastScroller(
    itemCount: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { KixyuSize.directoryFastScrollerThumbHeight.toPx() }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var scrollJob by remember { mutableStateOf<Job?>(null) }
    val listFraction by remember(itemCount, listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex.toFloat() / (itemCount - 1).coerceAtLeast(1)
        }
    }

    fun scrollToFraction(value: Float) {
        dragFraction = value.coerceIn(0f, 1f)
        val target = ((itemCount - 1) * dragFraction).roundToInt()
        scrollJob?.cancel()
        scrollJob = scope.launch { listState.scrollToItem(target) }
    }

    val visibleFraction = if (dragging) dragFraction else listFraction
    val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
    Box(
        modifier.width(KixyuSize.directoryFastScrollerWidth)
            .onSizeChanged { trackHeightPx = it.height.toFloat() }
            .pointerInput(itemCount, trackHeightPx) {
                detectTapGestures { point ->
                    val travel = (size.height - thumbHeightPx).coerceAtLeast(1f)
                    scrollToFraction((point.y - thumbHeightPx / 2f) / travel)
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier.align(Alignment.Center)
                .fillMaxHeight()
                .width(KixyuSize.directoryFastScrollerTrackWidth)
                .background(MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraLarge),
        )
        Surface(
            modifier = Modifier
                .offset { IntOffset(0, (travelPx * visibleFraction).roundToInt()) }
                .size(
                    KixyuSize.directoryFastScrollerThumbWidth,
                    KixyuSize.directoryFastScrollerThumbHeight,
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        val travel = travelPx.coerceAtLeast(1f)
                        scrollToFraction(dragFraction + delta / travel)
                    },
                    onDragStarted = {
                        dragging = true
                        dragFraction = listFraction
                    },
                    onDragStopped = { dragging = false },
                ),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer,
            tonalElevation = KixyuSpacing.extraSmall,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(KixyuSymbols.DragHandle, stringResource(R.string.reader_fast_scroll_directory), Modifier.size(KixyuSize.icon))
            }
        }
    }
}
