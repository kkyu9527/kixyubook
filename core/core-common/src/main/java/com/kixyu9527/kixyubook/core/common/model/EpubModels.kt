package com.kixyu9527.kixyubook.core.common.model

sealed interface EpubLinkResult {
    data class Footnote(val title: String, val text: String) : EpubLinkResult
    data class Location(val chapterIndex: Int, val paragraphIndex: Int = 0) : EpubLinkResult
}

/** Display-only EPUB navigation; it never replaces a persisted chapter or text anchor. */
data class EpubNavigationEntry(
    val sourceIndex: Int,
    val title: String,
    val target: String,
    val depth: Int = 0,
    /** Heading-only resources preceding this readable section. Source IDs stay intact. */
    val titlePageSourceIndices: List<Int> = emptyList(),
)

/** Derived presentation plan only. Storage and text anchors remain the normal source-index model. */
data class EpubBrowserPlan(
    val spinePaths: List<String>,
    val fixedLayout: Boolean,
    val reason: String,
)
