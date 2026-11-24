package com.kixyu9527.kixyubook.core.reader.engine
import com.kixyu9527.kixyubook.core.common.cache.ReaderCacheBudget
import com.kixyu9527.kixyubook.core.common.cache.CacheDiagnostics

import android.app.ActivityManager
import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureListener
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max
import kotlin.math.roundToInt

private data class EpubImageAsset(val bitmap: ImageBitmap, val themeInk: Boolean, val profile: ReaderImageProfile)

private object EpubImageCache : MemoryPressureListener {
    val diagnostics = CacheDiagnostics.named("epub-images")
    private var maxBytes = ReaderCacheBudget.DEFAULT_IMAGE_MEMORY_BYTES
    private val images = object : LruCache<String, EpubImageAsset>(maxBytes) {
        override fun sizeOf(key: String, value: EpubImageAsset): Int = value.bitmap.width * value.bitmap.height * 4
    }

    init {
        MemoryPressureRegistry.register(this)
    }

    operator fun get(key: String): EpubImageAsset? = images.get(key).also {
        if (it == null) diagnostics.misses.incrementAndGet() else diagnostics.hits.incrementAndGet()
    }
    fun put(key: String, image: EpubImageAsset) = images.put(key, image)

    @Synchronized
    fun configure(context: Context) {
        val memory = context.getSystemService(ActivityManager::class.java)
        val target = ReaderCacheBudget.imageMemoryBytes(memory.memoryClass, memory.isLowRamDevice)
        if (target != maxBytes) {
            maxBytes = target
            images.resize(target)
        }
    }

    override fun onMemoryPressure(level: MemoryPressureLevel) {
        when (level) {
            MemoryPressureLevel.BACKGROUND -> images.trimToSize(maxBytes / 4)
            MemoryPressureLevel.MODERATE,
            MemoryPressureLevel.CRITICAL,
            -> images.evictAll()
        }
    }

}

/** Reuses the EPUB central directory and serializes bitmap decode to avoid parallel memory spikes. */
private object EpubArchivePool : MemoryPressureListener {
    private data class OpenArchive(
        val signature: String,
        val zip: ZipFile,
    )

    private val archives = object : LinkedHashMap<String, OpenArchive>(2, .75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, OpenArchive>): Boolean {
            if (size <= MAX_OPEN_ARCHIVES) return false
            eldest.value.zip.close()
            return true
        }
    }

    init {
        MemoryPressureRegistry.register(this)
    }

    fun <T> read(file: File, block: (ZipFile) -> T): T? = synchronized(archives) {
        if (!file.isFile) return@synchronized null
        val signature = "${file.length()}:${file.lastModified()}"
        val current = archives[file.absolutePath]
        val archive = if (current?.signature == signature) {
            current
        } else {
            current?.zip?.close()
            OpenArchive(signature, ZipFile(file)).also { archives[file.absolutePath] = it }
        }
        block(archive.zip)
    }

    override fun onMemoryPressure(level: MemoryPressureLevel) = synchronized(archives) {
        archives.values.forEach { it.zip.close() }
        archives.clear()
    }

    private const val MAX_OPEN_ARCHIVES = 2
}

/** One decode lane shared by visible rendering and speculative prefetch, with cache re-check. */
private object EpubImageLoader {
    private val decodeMutex = Mutex()

    suspend fun load(
        epubPath: String,
        resourcePath: String,
        targetWidthPx: Int,
        targetHeightPx: Int,
    ): EpubImageAsset? {
        val cacheKey = epubImageCacheKey(epubPath, resourcePath, targetWidthPx, targetHeightPx)
        EpubImageCache[cacheKey]?.let { return it }
        return withContext(Dispatchers.IO) {
            decodeMutex.withLock {
                EpubImageCache[cacheKey] ?: decodeEpubImage(
                    epubPath,
                    resourcePath,
                    targetWidthPx,
                    targetHeightPx,
                )?.also {
                    EpubImageCache.diagnostics.parsed(cacheKey)
                    EpubImageCache.put(cacheKey, it)
                    // Decode alone does not warm the GPU texture. Queue its upload off the UI
                    // thread, but do not add GPU work after a speculative request was cancelled.
                    if (coroutineContext.isActive) it.bitmap.asAndroidBitmap().prepareToDraw()
                }
            }
        }
    }
}

/** Preloads the nearest image leaves without creating a second decode path or duplicate request. */
suspend fun prefetchReaderEpubImages(
    epubPath: String?,
    pages: List<ReaderPage>,
    density: Float,
    maxImages: Int = 3,
    fullPageViewportWidthDp: Float? = null,
    fullPageViewportHeightDp: Float? = null,
) {
    if (epubPath.isNullOrBlank() || maxImages <= 0) return
    val requests = readerEpubImagePrefetchRequests(pages, density, maxImages, fullPageViewportWidthDp, fullPageViewportHeightDp)
    requests.forEach { request ->
        coroutineContext.ensureActive()
        EpubImageLoader.load(
            epubPath,
            request.resourcePath,
            request.widthPx,
            request.heightPx,
        )
    }
}

internal fun readerEpubImagePrefetchRequests(
    pages: List<ReaderPage>,
    density: Float,
    maxImages: Int,
    fullPageViewportWidthDp: Float? = null,
    fullPageViewportHeightDp: Float? = null,
): List<EpubImagePrefetchRequest> = pages.asSequence()
        .flatMap(ReaderPage::blocks)
        .filter { it.kind == com.kixyu9527.kixyubook.core.common.model.ParagraphKind.IMAGE }
        .mapNotNull { block ->
            block.resourcePath?.let { resourcePath ->
                EpubImagePrefetchRequest(
                    resourcePath = resourcePath,
                    widthPx = ((if (block.isFullPageImage) fullPageViewportWidthDp ?: block.imageWidthDp else block.imageWidthDp) * density).roundToInt().coerceAtLeast(1),
                    heightPx = ((if (block.isFullPageImage) fullPageViewportHeightDp ?: block.imageHeightDp else block.imageHeightDp) * density).roundToInt().coerceAtLeast(1),
                )
            }
        }
        .distinctBy { "${it.resourcePath}:${it.widthPx}x${it.heightPx}" }
        .take(maxImages)
        .toList()

internal data class EpubImagePrefetchRequest(
    val resourcePath: String,
    val widthPx: Int,
    val heightPx: Int,
)

@Composable
internal fun ReaderEpubImage(
    epubPath: String?,
    resourcePath: String?,
    altText: String,
    layout: ReaderImageLayout,
    placeholderColor: Color,
    onTapFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
    fullPage: Boolean = false,
    cropToFill: Boolean = false,
    pageColor: Color = MaterialTheme.colorScheme.background,
    inkColor: Color = MaterialTheme.colorScheme.onBackground,
) {
    val context = LocalContext.current.applicationContext
    val illustrationDescription = stringResource(R.string.reader_book_illustration)
    val illustrationLabel = stringResource(R.string.reader_illustration)
    DisposableEffect(context) {
        EpubImageCache.configure(context)
        onDispose { }
    }
    val targetWidth = layout.widthDp.dp
    val targetHeight = layout.heightDp.dp
    val density = LocalDensity.current
    val targetWidthPx = with(density) { targetWidth.roundToPx() }
    val targetHeightPx = with(density) { targetHeight.roundToPx() }
    val cacheKey = epubImageCacheKey(epubPath, resourcePath, targetWidthPx, targetHeightPx)
    var loadFailed by remember(cacheKey) { mutableStateOf(false) }
    // produceState's initialValue is not reset when only its effect key changes. Scope the
    // state to the resource as well, otherwise a reused image node can display the old artwork
    // and skip loading the new resource altogether.
    val loaded = key(cacheKey) {
        produceState<EpubImageAsset?>(initialValue = EpubImageCache[cacheKey]) {
            if (value == null && !epubPath.isNullOrBlank() && !resourcePath.isNullOrBlank()) {
                value = EpubImageLoader.load(epubPath, resourcePath, targetWidthPx, targetHeightPx)
                loadFailed = value == null
            }
        }
    }
    val bitmap = loaded.value
    val adaptiveInk = bitmap?.let { imageAdaptiveInk(it.profile, pageColor.toArgb(), inkColor.toArgb()) }?.let(::Color)
    val paper = if (bitmap != null && adaptiveInk == null && (fullPage || !bitmap.themeInk)) {
        imagePaperBackdrop(bitmap.profile.inkLuminance, pageColor.luminance())?.let(::Color)
    } else null
    val imageModifier = modifier
        .size(targetWidth, targetHeight)
        .then(if (fullPage) Modifier else Modifier.clip(MaterialTheme.shapes.medium))
        .drawBehind {
            if (paper != null && bitmap != null) {
                if (cropToFill) drawRect(paper) else {
                    // The Image node is the physical viewport for full-page art. A backing must
                    // follow ContentScale.Fit's actual picture rect, never fill its letterboxing.
                    val scale = minOf(size.width / bitmap.bitmap.width, size.height / bitmap.bitmap.height)
                    val width = bitmap.bitmap.width * scale; val height = bitmap.bitmap.height * scale
                    drawRect(paper, Offset((size.width - width) / 2, (size.height - height) / 2), Size(width, height))
                }
            }
        }
        .pointerInput(Unit) {
            detectTapGestures { onTapFraction(it.x / size.width.coerceAtLeast(1)) }
        }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.bitmap,
            contentDescription = altText.ifBlank { illustrationDescription },
            modifier = imageModifier,
            contentScale = if (cropToFill) ContentScale.Crop else ContentScale.Fit,
            colorFilter = if (adaptiveInk != null) ColorFilter.tint(adaptiveInk)
                else if (!fullPage && bitmap.themeInk) ColorFilter.tint(placeholderColor) else null,
        )
    } else {
        Box(imageModifier, contentAlignment = Alignment.Center) {
            // Publisher alt text (often just "logo") is accessibility/error content, not a
            // loading placeholder. Keep the final image's exact bounds without flashing text.
            if (loadFailed) Text(
                text = altText.ifBlank { illustrationLabel },
                color = placeholderColor,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.background(Color.Transparent),
            )
        }
    }
}

private fun decodeEpubImage(
    epubPath: String,
    resourcePath: String,
    targetWidthPx: Int,
    targetHeightPx: Int,
): EpubImageAsset? = runCatching {
    val file = File(epubPath)
    EpubArchivePool.read(file) { zip ->
        if (INLINE_SVG_FRAGMENT in resourcePath || resourcePath.endsWith(".svg", true)) {
            return@read renderEpubSvg(zip, resourcePath, targetWidthPx, targetHeightPx)?.toImageAsset(1024, 1024)
        }
        val entry = zip.getEntry(resourcePath) ?: zip.entries().asSequence()
            .firstOrNull { it.name.equals(resourcePath, true) }
            ?: return@read null
        if (entry.size !in 1..MAX_ENCODED_IMAGE_BYTES) return@read null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@read null
        val options = BitmapFactory.Options().apply {
            inSampleSize = epubImageSampleSize(bounds.outWidth, bounds.outHeight, targetWidthPx, targetHeightPx)
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        zip.getInputStream(entry).use { input ->
            BitmapFactory.decodeStream(input, null, options)?.toImageAsset(bounds.outWidth, bounds.outHeight)
        }
    }
}.getOrNull()

private fun android.graphics.Bitmap.toImageAsset(nativeWidth: Int, nativeHeight: Int): EpubImageAsset {
    val ink = if (nativeWidth in 1..256 && nativeHeight in 1..256) {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)
        isThemeInkImage(nativeWidth, nativeHeight, pixels)
    } else false
    val columns = minOf(width, 128)
    val rows = minOf(height, 128)
    // One bulk JNI read rather than 16,384 getPixel calls on every cold image.
    val sample = android.graphics.Bitmap.createScaledBitmap(this, columns, rows, false)
    val sampled = IntArray(columns * rows)
    sample.getPixels(sampled, 0, columns, 0, 0, columns, rows)
    if (sample !== this) sample.recycle()
    return EpubImageAsset(asImageBitmap(), ink, readerImageProfile(sampled))
}

private const val MAX_ENCODED_IMAGE_BYTES = 32L * 1024L * 1024L

/** Encoded byte size alone says nothing about decoded RAM, especially for extreme panoramas. */
internal fun epubImageSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
    val safeWidth = max(1, targetWidth)
    val safeHeight = max(1, targetHeight)
    var sample = 1
    while (sample < (1 shl 30)) {
        val next = sample.toLong() * 2
        val exceedsTarget = width / next >= safeWidth && height / next >= safeHeight
        val decodedPixels = (width.toLong() / sample) * (height.toLong() / sample)
        val exceedsBudget = decodedPixels > 4L * 1024 * 1024 || maxOf(width, height) / sample > 8192
        if (!exceedsTarget && !exceedsBudget) break
        sample *= 2
    }
    return sample
}

private fun epubImageCacheKey(
    epubPath: String?,
    resourcePath: String?,
    targetWidthPx: Int,
    targetHeightPx: Int,
): String = "$epubPath::$resourcePath::${targetWidthPx}x${targetHeightPx}"
