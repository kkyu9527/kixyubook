package com.kixyu9527.kixyubook.feature.reader

import com.kixyu9527.kixyubook.core.common.model.Chapter
import com.kixyu9527.kixyubook.core.common.model.EpubNavigationEntry
import com.kixyu9527.kixyubook.core.common.model.Bookmark

internal fun readerVisibleChapters(chapters: List<Chapter>, navigation: List<EpubNavigationEntry>): List<Chapter> {
    val sources = chapters.mapTo(hashSetOf()) { it.index }
    val titles = navigation.filter { it.sourceIndex in sources }.flatMapTo(hashSetOf()) { it.titlePageSourceIndices }
    return chapters.filterNot { it.index in titles }
}

internal fun readingSourceIndex(source: Int, navigation: List<EpubNavigationEntry>): Int =
    navigation.firstOrNull { source in it.titlePageSourceIndices }?.sourceIndex ?: source

/** View-only aliases: preserve bookmark UUID/data, including deletion identity, in the repository. */
internal fun readerVisibleBookmarks(bookmarks: List<Bookmark>, chapters: List<Chapter>,
    navigation: List<EpubNavigationEntry>,
): List<Bookmark> = bookmarks.map { bookmark ->
    val source = readingSourceIndex(bookmark.chapterIndex, navigation)
    val target = chapters.firstOrNull { it.index == source && source != bookmark.chapterIndex }
    if (target == null) bookmark else bookmark.copy(chapterId = target.id, chapterTitle = target.title,
        chapterIndex = target.index, chapterKey = target.chapterKey)
}

/** Chapter buttons follow publisher section boundaries; page turns still visit every resource. */
internal fun chapterButtonTarget(
    chapters: List<Chapter>,
    navigation: List<EpubNavigationEntry>,
    current: Int,
    delta: Int,
): Int {
    if (chapters.isEmpty()) return 0
    val positions = chapters.mapIndexed { position, chapter -> chapter.index to position }.toMap()
    val boundaries = navigation.mapNotNull { positions[it.sourceIndex] }.distinct().sorted()
    if (boundaries.isEmpty()) return (current + delta).coerceIn(chapters.indices)
    val section = boundaries.indexOfLast { it <= current }
    if (section < 0 && delta <= 0) return 0
    return boundaries[(section + delta).coerceIn(boundaries.indices)]
}
