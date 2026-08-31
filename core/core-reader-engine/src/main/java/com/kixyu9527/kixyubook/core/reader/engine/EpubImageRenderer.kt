package com.kixyu9527.kixyubook.core.reader.engine

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max

private object EpubImageCache : MemoryPressureListener {
    private var maxBytes = DEFAULT_MAX_BYTES
    private val images = object : LruCache<String, ImageBitmap>(DEFAULT_MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    init {
        MemoryPressureRegistry.register(this)
    }

    operator fun get(key: String): ImageBitmap? = images.get(key)
    fun put(key: String, image: ImageBitmap) = images.put(key, image)

    @Synchronized
    fun configure(context: Context) {
        val memory = context.getSystemService(ActivityManager::class.java)
        val target = if (memory.isLowRamDevice) {
            LOW_RAM_MAX_BYTES
        } else {
            (memory.memoryClass.toLong() * 1024L * 1024L / CACHE_HEAP_DIVISOR)
                .coerceIn(MIN_MAX_BYTES.toLong(), MAX_MAX_BYTES.toLong())
                .toInt()
        }
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

    private const val CACHE_HEAP_DIVISOR = 16L
    private const val LOW_RAM_MAX_BYTES = 8 * 1024 * 1024
    private const val MIN_MAX_BYTES = 12 * 1024 * 1024
    private const val DEFAULT_MAX_BYTES = 32 * 1024 * 1024
    private const val MAX_MAX_BYTES = 48 * 1024 * 1024
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
    ): ImageBitmap? {
        val cacheKey = epubImageCacheKey(epubPath, resourcePath, targetWidthPx, targetHeightPx)
        EpubImageCache[cacheKey]?.let { return it }
        return withContext(Dispatchers.IO) {
            decodeMutex.withLock {
                EpubImageCache[cacheKey] ?: decodeEpubImage(
                    epubPath,
                    resourcePath,
                    targetWidthPx,
                    targetHeightPx,
                )?.also { EpubImageCache.put(cacheKey, it) }
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
) {
    if (epubPath.isNullOrBlank() || maxImages <= 0) return
    val requests = readerEpubImagePrefetchRequests(pages, density, maxImages)
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
): List<EpubImagePrefetchRequest> = pages.asSequence()
        .flatMap(ReaderPage::blocks)
        .filter { it.kind == com.kixyu9527.kixyubook.core.common.model.ParagraphKind.IMAGE }
        .mapNotNull { block ->
            block.resourcePath?.let { resourcePath ->
                EpubImagePrefetchRequest(
                    resourcePath = resourcePath,
                    widthPx = (block.imageWidthDp * density).toInt().coerceAtLeast(1),
                    heightPx = (block.imageHeightDp * density).toInt().coerceAtLeast(1),
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
    val loaded = produceState<ImageBitmap?>(
        initialValue = EpubImageCache[cacheKey],
        key1 = cacheKey,
    ) {
        if (value == null && !epubPath.isNullOrBlank() && !resourcePath.isNullOrBlank()) {
            value = EpubImageLoader.load(epubPath, resourcePath, targetWidthPx, targetHeightPx)
        }
    }
    val imageModifier = modifier
        .size(targetWidth, targetHeight)
        .then(if (fullPage) Modifier else Modifier.clip(MaterialTheme.shapes.medium))
        .background(placeholderColor.copy(alpha = .08f))
        .pointerInput(Unit) {
            detectTapGestures { onTapFraction(it.x / size.width.coerceAtLeast(1)) }
        }
    val bitmap = loaded.value
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = altText.ifBlank { illustrationDescription },
            modifier = imageModifier,
            contentScale = if (cropToFill) ContentScale.Crop else ContentScale.Fit,
        )
    } else {
        Box(imageModifier, contentAlignment = Alignment.Center) {
            Text(
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
): ImageBitmap? = runCatching {
    val file = File(epubPath)
    EpubArchivePool.read(file) { zip ->
        val entry = zip.getEntry(resourcePath) ?: zip.entries().asSequence()
            .firstOrNull { it.name.equals(resourcePath, true) }
            ?: return@read null
        if (entry.size !in 1..MAX_ENCODED_IMAGE_BYTES) return@read null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
        val safeTargetWidthPx = max(1, targetWidthPx)
        val safeTargetHeightPx = max(1, targetHeightPx)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= safeTargetWidthPx &&
            bounds.outHeight / (sample * 2) >= safeTargetHeightPx
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
        }
        zip.getInputStream(entry).use { input ->
            BitmapFactory.decodeStream(input, null, options)?.asImageBitmap()
        }
    }
}.getOrNull()

private const val MAX_ENCODED_IMAGE_BYTES = 32L * 1024L * 1024L

private fun epubImageCacheKey(
    epubPath: String?,
    resourcePath: String?,
    targetWidthPx: Int,
    targetHeightPx: Int,
): String = "$epubPath::$resourcePath::${targetWidthPx}x${targetHeightPx}"
