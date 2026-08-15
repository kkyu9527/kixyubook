package com.kixyu9527.kixyubook.core.ui

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureLevel
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureListener
import com.kixyu9527.kixyubook.core.common.memory.MemoryPressureRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

private object BookCoverMemoryCache : MemoryPressureListener {
    private const val MAX_CACHE_BYTES = 16 * 1024 * 1024
    private const val MAX_DECODE_DIMENSION_PX = 384
    private val bitmaps = object : LruCache<String, ImageBitmap>(MAX_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int =
            (value.width.toLong() * value.height.toLong() * 4L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }
    private val decodeSlots = Semaphore(2)
    private val pathLocks = ConcurrentHashMap<String, Mutex>()

    init {
        MemoryPressureRegistry.register(this)
    }

    operator fun get(path: String): ImageBitmap? = bitmaps.get(path)

    suspend fun load(path: String): ImageBitmap? = withContext(Dispatchers.IO) {
        bitmaps.get(path) ?: pathLocks.getOrPut(path, ::Mutex).withLock {
            bitmaps.get(path) ?: decodeSlots.withPermit {
                runCatching { decodeSampled(path) }
                    .getOrNull()
                    ?.asImageBitmap()
                    ?.also { bitmaps.put(path, it) }
            }
        }
    }

    override fun onMemoryPressure(level: MemoryPressureLevel) {
        when (level) {
            MemoryPressureLevel.BACKGROUND -> bitmaps.trimToSize(MAX_CACHE_BYTES / 4)
            MemoryPressureLevel.MODERATE,
            MemoryPressureLevel.CRITICAL,
            -> bitmaps.evictAll()
        }
    }

    private fun decodeSampled(path: String): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_DECODE_DIMENSION_PX ||
            bounds.outHeight / sampleSize > MAX_DECODE_DIMENSION_PX
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
            },
        )
    }
}

@Composable
fun BookCover(
    title: String,
    coverPath: String?,
    modifier: Modifier = Modifier,
) {
    var bitmap by remember(coverPath) {
        mutableStateOf(coverPath?.let(BookCoverMemoryCache::get))
    }
    LaunchedEffect(coverPath) {
        bitmap = coverPath?.let { BookCoverMemoryCache.load(it) }
    }
    val currentBitmap = bitmap
    BoxWithConstraints(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF35463C), Color(0xFF879487), Color(0xFFD5CDBA)),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (currentBitmap != null) {
            Image(currentBitmap, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val displayTitle = title.trim().ifBlank { "未命名书籍" }
            val visualLength = displayTitle.sumOf { character ->
                when {
                    character.isWhitespace() -> 0.35
                    character.code <= 0x7F -> 0.55
                    else -> 1.0
                }
            }
            val sizeScale = minOf(maxWidth.value * 0.18f, maxHeight.value * 0.11f)
                .coerceIn(14f, 30f)
            val lengthScale = when {
                visualLength <= 4.0 -> 1.12f
                visualLength <= 8.0 -> 1f
                visualLength <= 12.0 -> 0.9f
                visualLength <= 18.0 -> 0.8f
                else -> 0.72f
            }
            val titleFontSize = (sizeScale * lengthScale).coerceIn(12f, 30f)
            val titleMaxLines = when {
                maxHeight >= 200.dp -> 6
                maxHeight >= 140.dp -> 5
                else -> 4
            }
            Text(
                text = displayTitle,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontSize = titleFontSize.sp,
                    lineHeight = (titleFontSize * 1.24f).sp,
                ),
                textAlign = TextAlign.Center,
                maxLines = titleMaxLines,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color(0x22000000), Color.Transparent), endX = 22f)),
        )
    }
}
