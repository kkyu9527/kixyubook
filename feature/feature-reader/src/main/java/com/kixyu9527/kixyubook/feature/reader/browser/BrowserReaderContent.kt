package com.kixyu9527.kixyubook.feature.reader.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuTextButton
import com.kixyu9527.kixyubook.core.reader.engine.*
import com.kixyu9527.kixyubook.core.reader.engine.epub.browser.EpubBrowserDocuments
import com.kixyu9527.kixyubook.core.reader.engine.epub.browser.EpubBrowserDocument
import com.kixyu9527.kixyubook.feature.reader.*
import com.kixyu9527.kixyubook.feature.reader.R
import com.kixyu9527.kixyubook.core.common.diagnostics.DiagnosticLog
import com.kixyu9527.kixyubook.core.common.cache.ReaderCacheBudget
import com.kixyu9527.kixyubook.core.common.model.ParagraphKind
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureListener
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.util.UUID

private data class PreparedBrowserPage(val document: EpubBrowserDocument, val script: String)

/** Three bounded browser leaves share the normal chapter window and native reader operation host. */
@Composable
internal fun BrowserReaderContent(
    state: ReaderContentState,
    palette: ReaderRenderPalette,
    resumeAnchor: ReaderLocation?,
    savePosition: ReaderPositionSettler,
    moveChapterFromPage: (Int, Int, Boolean) -> Unit,
    middleTap: () -> Unit,
    dismissControls: () -> Unit,
    volumeTurns: SharedFlow<Int>, chapterTurns: SharedFlow<Int>,
    chapterRendered: (Int) -> Unit,
    onDocumentLink: (String) -> Unit,
    directChapterTurn: (Int) -> Unit,
    setPageInteractionActive: (Boolean) -> Unit,
    presentationCurrent: (Int, Long) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val views = remember { mutableMapOf<Int, BrowserWebView>() }
    val latestState by rememberUpdatedState(state)
    val latestChapterTurn by rememberUpdatedState(directChapterTurn)
    val latestInteraction by rememberUpdatedState(setPageInteractionActive)
    val scope = rememberCoroutineScope()
    var retainNeighbours by remember { mutableStateOf(true) }
    DisposableEffect(Unit) {
        val listener = MemoryPressureListener { scope.launch { retainNeighbours = false } }
        MemoryPressureRegistry.register(listener)
        onDispose { MemoryPressureRegistry.unregister(listener); latestInteraction(false) }
    }
    LaunchedEffect(chapterTurns) { chapterTurns.collect { delta ->
        latestChapterTurn(delta)
    } }
    LaunchedEffect(volumeTurns) { volumeTurns.collect { delta ->
        views[latestState.chapterIndex]?.evaluateJavascript("window.kixyuReader?.turn($delta)", null)
    } }
    // Collectors stay long-lived; never capture a chapter that was active before a boundary turn.
    val indices = if (retainNeighbours) listOf(state.chapterIndex, state.chapterIndex - 1, state.chapterIndex + 1)
        .take(ReaderCacheBudget.MAX_BROWSER_LEAVES) else listOf(state.chapterIndex)
    Box(modifier.background(palette.background)) {
        for (index in indices.filter { it in state.chapters.indices }) {
            val chapter = if (index == state.chapterIndex) state.chapter else state.prefetchedChapters[index]
            if (chapter == null) continue
            key(chapter.id, state.book?.contentHash) {
                BrowserLeaf(state, chapter, index, palette, resumeAnchor, savePosition,
                    moveChapterFromPage, middleTap, dismissControls, chapterRendered,
                    onDocumentLink, setPageInteractionActive, presentationCurrent, views)
            }
        }
    }
}

@Composable
private fun BrowserLeaf(
    state: ReaderContentState, chapter: ReaderChapter, index: Int, palette: ReaderRenderPalette,
    anchor: ReaderLocation?, savePosition: ReaderPositionSettler,
    moveChapterFromPage: (Int, Int, Boolean) -> Unit, middleTap: () -> Unit, dismissControls: () -> Unit,
    chapterRendered: (Int) -> Unit, onDocumentLink: (String) -> Unit,
    setPageInteractionActive: (Boolean) -> Unit,
    presentationCurrent: (Int, Long) -> Boolean,
    views: MutableMap<Int, BrowserWebView>,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val file = state.book?.storagePath?.let(::File) ?: return
    val path = state.browserPlan?.spinePaths?.getOrNull(chapter.index)?.takeIf { it.isNotBlank() } ?: return
    val active = state.chapterIndex == index
    val token = remember(chapter.id) { UUID.randomUUID().toString() }
    var retry by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf(false) }
    var ready by remember(retry) { mutableStateOf(false) }
    val preparedPage by produceState<PreparedBrowserPage?>(null, path, file.path, retry) {
        try { value = withContext(Dispatchers.IO) {
            PreparedBrowserPage(EpubBrowserDocuments.prepare(file, path),
                listOf("image-theme.js", "reader.js").joinToString("\n") { asset ->
                    context.assets.open("reader/browser/$asset").bufferedReader().use { it.readText() }
                })
        } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = true; DiagnosticLog.record(DiagnosticLog.Category.READER, "browser_document_failed", details = mapOf("source" to chapter.index, "reason" to e.javaClass.simpleName)) }
    }
    val latestState by rememberUpdatedState(state)
    val latestActive by rememberUpdatedState(active)
    val latestChapter by rememberUpdatedState(chapter)
    val latestIndex by rememberUpdatedState(index)
    val select = LocalBrowserSelectionHandler.current
    val latestSelect by rememberUpdatedState(select)
    val clearRequest = LocalReaderSelectionClearRequest.current
    fun follow(url: String) {
        val archivePath = EpubBrowserDocuments.archivePath(url) ?: return
        val fragment = java.net.URI(url).rawFragment?.let { URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }.orEmpty()
        onDocumentLink(archivePath + if (fragment.isBlank()) "" else "#$fragment")
    }
    if (error) {
        if (active) Column(Modifier.fillMaxSize().zIndex(2f).background(palette.background).padding(24.dp)) {
            Text(stringResource(R.string.reader_error_chapter), color = palette.body)
            KixyuTextButton(text = stringResource(R.string.reader_retry), onClick = { error = false; retry++ })
        }
        return
    }
    val prepared = preparedPage?.document ?: return
    val config = browserConfiguration(state, chapter, prepared, palette, token, active, anchor, LocalDensity.current.fontScale)
    var viewRef by remember { mutableStateOf<BrowserWebView?>(null) }
    DisposableEffect(viewRef, index) {
        val view = viewRef
        if (view != null) views[index] = view
        onDispose { if (views[index] === view) views.remove(index) }
    }
    DisposableEffect(viewRef, lifecycleOwner) {
        val view = viewRef
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view?.onPause()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(viewRef, active, ready, retry) {
        if (viewRef != null && active && !ready) {
            delay(15_000)
            error = true
            DiagnosticLog.record(DiagnosticLog.Category.READER, "browser_ready_timeout", details = mapOf("source" to chapter.index))
        }
    }
    LaunchedEffect(clearRequest) { viewRef?.clearReaderSelection() }
    AndroidView(
        modifier = Modifier.fillMaxSize().zIndex(if (active) 1f else 0f),
        factory = { ctx ->
            val container = FrameLayout(ctx)
            try {
            lateinit var view: BrowserWebView
            view = BrowserWebView(ctx, file, onMessage = message@{ message ->
                if (latestActive && presentationCurrent(latestState.navigationVersion, latestChapter.id))
                    when (val value = parseBrowserMessage(message, token, latestChapter,
                        latestState.navigationVersion, latestState.layoutVersion)) {
                    BrowserMessage.Ready -> { ready = true; chapterRendered(latestState.navigationVersion) }
                    BrowserMessage.ClearSelection -> latestSelect(null)
                    is BrowserMessage.Interaction -> setPageInteractionActive(value.active)
                    is BrowserMessage.Position -> savePosition(value.paragraph, value.offset, value.complete, value.end, latestState.layoutVersion)
                    is BrowserMessage.Boundary -> { dismissControls(); moveChapterFromPage(latestIndex, value.delta, value.delta < 0) }
                    is BrowserMessage.Tap -> if (value.fraction in .33f..67f) middleTap() else dismissControls()
                    is BrowserMessage.Link -> follow(value.href)
                    is BrowserMessage.Selection -> {
                        val p = latestChapter.paragraphs.firstOrNull { it.index == value.paragraph && it.kind == ParagraphKind.TEXT } ?: return@message
                        // Persisted annotations are single-paragraph ranges. Cross-paragraph selection
                        // remains copyable, but must not manufacture a corrupt annotation coordinate.
                        val target = if (value.paragraph == value.endParagraph)
                            ReaderTextActionTarget(latestChapter.index, p.index, p.text, value.start, value.end)
                        else ReaderTextActionTarget(latestChapter.index, p.index, value.copyText)
                        val location = IntArray(2); view.getLocationInWindow(location)
                        val scale = view.resources.displayMetrics.density
                        latestSelect(BrowserSelection(target, Rect(location[0] + value.x * scale, location[1] + value.y * scale,
                            location[0] + (value.x + value.width) * scale, location[1] + (value.y + value.height) * scale),
                            copyToClipboard = { (context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(ClipData.newPlainText(null, value.copyText)) },
                            clear = view::clearReaderSelection))
                    }
                    null -> Unit
                }
            }, onLoaded = { loaded ->
                loaded.loaded = true
                loaded.evaluateJavascript(requireNotNull(preparedPage).script) {
                    loaded.config?.let { loaded.evaluateJavascript("window.kixyuReader?.open($it)", null) }
                }
            }, onLink = ::follow, onError = { error = true })
            viewRef = view
            view.config = config
            view.fontFile = state.fontPath?.let(::File)
            view.setBackgroundColor(palette.background.toArgb())
            view.alpha = 0f
            container.addView(view, FrameLayout.LayoutParams(-1, -1))
            view.loadDataWithBaseURL(EpubBrowserDocuments.url(path), prepared.html, "text/html", "UTF-8", null)
            } catch (e: Exception) { error = true }
            container
        },
        update = { container ->
            val view = container.getChildAt(0) as? BrowserWebView
            if (view != null) {
            view.alpha = if (active && ready) 1f else 0f
            view.isEnabled = active
            container.visibility = if (active) View.VISIBLE else View.INVISIBLE
            view.importantForAccessibility = if (active) View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            view.fontFile = state.fontPath?.let(::File)
            view.setBackgroundColor(palette.background.toArgb())
            view.updateReader(config)
            }
        },
        onRelease = { container ->
            val view = container.getChildAt(0) as? BrowserWebView
            if (view != null) {
            if (views[index] === view) views.remove(index)
            if (viewRef === view) viewRef = null
            view.stopLoading(); view.destroy()
            container.removeAllViews()
            }
        },
    )
}
