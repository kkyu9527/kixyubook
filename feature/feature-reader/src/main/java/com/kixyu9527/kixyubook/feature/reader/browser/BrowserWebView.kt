package com.kixyu9527.kixyubook.feature.reader.browser

import android.annotation.SuppressLint
import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.kixyu9527.kixyubook.core.reader.engine.epub.browser.EPUB_BROWSER_ORIGIN
import com.kixyu9527.kixyubook.core.reader.engine.epub.browser.EpubBrowserDocuments
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream

@SuppressLint("SetJavaScriptEnabled") // Only the bundled bridge runs; publisher scripts are stripped and CSP-blocked.
internal class BrowserWebView(context: Context, file: File,
    onMessage: (String?) -> Unit, onLoaded: (BrowserWebView) -> Unit,
    onLink: (String) -> Unit, onError: () -> Unit,
) : WebView(context) {
    var config: String? = null
    var loaded = false
    var fontFile: File? = null
    init {
        require(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
        settings.configureEpubReader()
        WebViewCompat.addWebMessageListener(this, "KixyuReaderBridge", setOf(EPUB_BROWSER_ORIGIN)) { _, message, origin, mainFrame, _ ->
            if (mainFrame && origin.toString().trimEnd('/') == EPUB_BROWSER_ORIGIN) onMessage(message.data)
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                return runCatching {
                    val path = EpubBrowserDocuments.archivePath(request.url.toString()) ?: return@runCatching denied()
                    if (path == "__kixyu_font") {
                        val font = fontFile?.canonicalFile ?: return@runCatching denied()
                        val roots = listOf(context.filesDir.canonicalPath, context.noBackupFilesDir.canonicalPath)
                        if (!font.isFile || roots.none { font.path.startsWith("$it/") } || font.length() > 32 * 1024 * 1024) return@runCatching denied()
                        return@runCatching WebResourceResponse("font/ttf", null, FileInputStream(font))
                    }
                    val resource = EpubBrowserDocuments.resource(file, path) ?: return@runCatching denied()
                    WebResourceResponse(resource.mimeType, if (resource.mimeType == "text/css") "UTF-8" else null,
                        200, "OK", mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"), resource.stream)
                }.getOrElse { denied() }
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame) onLink(request.url.toString())
                return true
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null && EpubBrowserDocuments.archivePath(url) != null) onLoaded(this@BrowserWebView)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) onError()
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                // Android requires the dead WebView to be detached and destroyed, not re-used.
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                onError()
                return true
            }
        }
        setDownloadListener { _, _, _, _, _ -> } // Reading cannot launch arbitrary downloads/intents.
    }

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean = callback.onCreateActionMode(mode, menu).also { menu.clear() }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = callback.onPrepareActionMode(mode, menu).also { menu.clear() }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = callback.onActionItemClicked(mode, item)
            override fun onDestroyActionMode(mode: ActionMode) = callback.onDestroyActionMode(mode)
        }, type)

    fun clearReaderSelection() { evaluateJavascript("window.kixyuReader?.clearSelection()", null) }
    fun updateReader(value: String) {
        if (config == value) return
        config = value
        if (loaded) evaluateJavascript("window.kixyuReader?.update($value)", null)
    }

    companion object {
        private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
            mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
    }
}

@SuppressLint("SetJavaScriptEnabled") // Bundled bridge only; the document's CSP forbids publisher code.
internal fun WebSettings.configureEpubReader() {
    javaScriptEnabled = true
    allowFileAccess = false
    allowContentAccess = false
    blockNetworkLoads = true
    domStorageEnabled = false
    databaseEnabled = false
    javaScriptCanOpenWindowsAutomatically = false
    setSupportMultipleWindows(false)
    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    cacheMode = WebSettings.LOAD_NO_CACHE
    useWideViewPort = true
    loadWithOverviewMode = true
    displayZoomControls = false
    builtInZoomControls = true
    mediaPlaybackRequiresUserGesture = true
}
