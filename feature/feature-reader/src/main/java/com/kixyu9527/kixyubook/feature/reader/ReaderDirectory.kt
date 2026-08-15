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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.*
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuIconButton
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuListRow
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMotion
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import com.kixyu9527.kixyubook.core.reader.engine.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlin.math.roundToInt


internal enum class DirectoryView { CHAPTERS, BOOKMARKS }

@Composable
internal fun DirectorySheet(
    state: ReaderUiState,
    selectChapter: (Int) -> Unit,
    selectBookmark: (Bookmark) -> Unit,
    deleteBookmark: (String) -> Unit,
    expandedLayout: Boolean = false,
) {
    val isMiuix = LocalAppUiStyle.current == AppUiStyle.MIUIX
    var directoryView by rememberSaveable { mutableStateOf(DirectoryView.CHAPTERS) }
    val bookmarkedChapterIds = remember(state.bookmarks) { state.bookmarks.mapTo(mutableSetOf(), Bookmark::chapterId) }
    val currentIndex = state.chapterIndex.coerceIn(0, state.chapters.lastIndex.coerceAtLeast(0))
    val currentChapterId = state.chapters.getOrNull(currentIndex)?.id
    val collapsedDirectoryRows = remember(state.chapters) {
        buildDirectoryRows(state.chapters, emptyMap())
    }
    val currentVolume = remember(collapsedDirectoryRows, currentChapterId) {
        collapsedDirectoryRows.filterIsInstance<DirectoryRow.Volume>()
            .firstOrNull { currentChapterId in it.chapterIds }
            ?.index
    }
    val expandedVolumes = remember(state.book?.uuid) {
        mutableStateMapOf<Int, Boolean>().apply {
            currentVolume?.let { this[it] = true }
        }
    }
    val directoryRows = remember(state.chapters, expandedVolumes.toMap()) {
        buildDirectoryRows(state.chapters, expandedVolumes)
    }
    val currentRowIndex = directoryRows.indexOfFirst { row ->
        when (row) {
            is DirectoryRow.ChapterRow -> row.index == currentIndex
            is DirectoryRow.Volume -> row.targetChapterIndex == currentIndex
        }
    }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentRowIndex)
    LaunchedEffect(currentIndex, directoryView) {
        if (directoryView == DirectoryView.CHAPTERS && state.chapters.isNotEmpty()) {
            currentVolume?.let { expandedVolumes[it] = true }
            val targetRows = buildDirectoryRows(state.chapters, expandedVolumes)
            val target = targetRows.indexOfFirst { row ->
                when (row) {
                    is DirectoryRow.ChapterRow -> row.index == currentIndex
                    is DirectoryRow.Volume -> row.targetChapterIndex == currentIndex
                }
            }.coerceAtLeast(0)
            if (targetRows.isNotEmpty()) listState.scrollToItem(target)
        }
    }
    Column(if (expandedLayout) Modifier.fillMaxSize() else Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = KixyuSpacing.large, end = KixyuSpacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (directoryView == DirectoryView.CHAPTERS) "目录 · ${state.chapters.size} 章" else "书签 · ${state.bookmarks.size}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            KixyuIconButton(onClick = {
                directoryView = if (directoryView == DirectoryView.CHAPTERS) DirectoryView.BOOKMARKS else DirectoryView.CHAPTERS
            }) {
                Icon(
                    if (directoryView == DirectoryView.CHAPTERS) KixyuSymbols.Bookmarks else KixyuSymbols.Toc,
                    if (directoryView == DirectoryView.CHAPTERS) "查看书签" else "查看目录",
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
                                        supportingText = if (row.chapterCount > 0) "${row.chapterCount} 章" else "卷内容",
                                        titleStyle = MaterialTheme.typography.bodyMedium,
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
                                                    if (expanded) "收起${row.title}" else "展开${row.title}",
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
                                                "本卷有书签",
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
                                        title = chapter.title,
                                        titleStyle = MaterialTheme.typography.bodyMedium,
                                        selected = current,
                                        highlighted = hasBookmark,
                                        onClick = { selectChapter(row.index) },
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
                                                "本章有书签",
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
                        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                    }
                    if (directoryRows.size >= FAST_SCROLLER_MIN_CHAPTERS) {
                        DirectoryFastScroller(
                            itemCount = directoryRows.size,
                            listState = listState,
                            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                        )
                    }
                }
            } else if (state.bookmarks.isEmpty()) {
                Box(
                    if (expandedLayout) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth().height(KixyuSize.readerSheetMaxContent),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("还没有书签", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(
                    if (expandedLayout) Modifier.fillMaxSize()
                    else Modifier.fillMaxWidth().height(KixyuSize.readerSheetMaxContent),
                ) {
                    items(state.bookmarks, key = Bookmark::uuid) { bookmark ->
                        KixyuListRow(
                            title = bookmark.chapterTitle,
                            supportingText = bookmark.preview.ifBlank { "第 ${bookmark.position + 1} 段" },
                            titleStyle = MaterialTheme.typography.bodyMedium,
                            supportingTextStyle = MaterialTheme.typography.bodySmall,
                            onClick = { selectBookmark(bookmark) },
                            leading = { Icon(KixyuSymbols.Bookmark, null) },
                            trailing = {
                                KixyuIconButton(onClick = { deleteBookmark(bookmark.uuid) }) {
                                    Icon(KixyuSymbols.DeleteOutline, "删除书签")
                                }
                            },
                            modifier = if (isMiuix) {
                                Modifier.padding(horizontal = KixyuSpacing.medium, vertical = KixyuSpacing.extraSmall)
                            } else Modifier,
                        )
                    }
                    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
                }
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

    data class ChapterRow(val index: Int, val id: Long) : DirectoryRow {
        override val key = "chapter:$id"
    }
}

internal fun buildDirectoryRows(
    chapters: List<Chapter>,
    expandedVolumes: Map<Int, Boolean>,
): List<DirectoryRow> {
    val sections = buildVolumeSections(chapters)
    if (sections.isEmpty()) {
        return chapters.mapIndexed { index, chapter -> DirectoryRow.ChapterRow(index, chapter.id) }
    }
    val sectionsByStart = sections.associateBy(VolumeSection::startIndex)
    val hiddenStandaloneTargets = sections.mapNotNullTo(hashSetOf()) { section ->
        section.targetChapterIndex.takeIf { it !in section.startIndex until section.endIndexExclusive }
    }
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
                        add(chapters[section.targetChapterIndex].id)
                    },
                    targetChapterIndex = section.targetChapterIndex,
                    hasOwnContent = section.targetChapterIndex !in section.childChapterIndices,
                ),
            )
            if (expandedVolumes[section.volumeIndex] == true) {
                section.childChapterIndices.forEach { chapterIndex ->
                    add(DirectoryRow.ChapterRow(chapterIndex, chapters[chapterIndex].id))
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
)

/**
 * Builds display-only volume sections without changing the underlying reading order. A publisher
 * supplied EPUB volume page, or TXT prose between a volume heading and its first chapter, becomes
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
        val inlineOpening = start.takeIf {
            chapters[it].title.normalizedDirectoryTitle() == normalizedTitle
        }
        val standaloneOpening = if (inlineOpening == null) {
            (start - 1 downTo previousSectionEnd).firstOrNull { candidate ->
                candidate !in claimedStandaloneTargets &&
                    chapters[candidate].volumeIndex == null &&
                    chapters[candidate].title.normalizedDirectoryTitle() == normalizedTitle
            }
        } else {
            null
        }
        standaloneOpening?.let(claimedStandaloneTargets::add)
        val target = inlineOpening ?: standaloneOpening ?: start
        val chapterIndices = (start until end).toList()
        add(
            VolumeSection(
                volumeIndex = volumeIndex,
                title = volumeTitle,
                startIndex = start,
                endIndexExclusive = end,
                chapterIndices = chapterIndices,
                childChapterIndices = chapterIndices.filterNot { it == inlineOpening },
                targetChapterIndex = target,
            ),
        )
        previousSectionEnd = end
    }
}

internal fun String.normalizedDirectoryTitle(): String =
    trim().replace(Regex("[\\s　]+"), "").trim('：', ':', '-', '—')

internal const val FAST_SCROLLER_MIN_CHAPTERS = 30

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
                Icon(KixyuSymbols.DragHandle, "快速滚动目录", Modifier.size(KixyuSize.icon))
            }
        }
    }
}
