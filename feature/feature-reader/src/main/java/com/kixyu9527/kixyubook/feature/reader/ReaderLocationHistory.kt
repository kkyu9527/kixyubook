package com.kixyu9527.kixyubook.feature.reader

internal data class ReaderLocation(
    val chapterPosition: Int,
    val paragraphIndex: Int,
    val charOffset: Int,
)

/**
 * Browser-style history for explicit reader jumps.
 *
 * Page turns and natural chapter boundaries deliberately bypass this stack. Directory entries,
 * bookmarks, search results and document links all share it so every non-linear jump has the same
 * predictable way back.
 */
internal class ReaderLocationHistory(
    private val capacity: Int = 64,
) {
    private val backStack = ArrayDeque<ReaderLocation>()
    private val forwardStack = ArrayDeque<ReaderLocation>()

    val canGoBack: Boolean get() = backStack.isNotEmpty()
    val canGoForward: Boolean get() = forwardStack.isNotEmpty()

    fun record(origin: ReaderLocation, destination: ReaderLocation) {
        if (origin == destination) return
        pushDistinct(backStack, origin)
        forwardStack.clear()
    }

    fun goBack(current: ReaderLocation): ReaderLocation? {
        val target = backStack.removeLastOrNull() ?: return null
        pushDistinct(forwardStack, current)
        return target
    }

    fun goForward(current: ReaderLocation): ReaderLocation? {
        val target = forwardStack.removeLastOrNull() ?: return null
        pushDistinct(backStack, current)
        return target
    }

    private fun pushDistinct(stack: ArrayDeque<ReaderLocation>, location: ReaderLocation) {
        if (stack.lastOrNull() == location) return
        stack.addLast(location)
        while (stack.size > capacity) stack.removeFirst()
    }
}
