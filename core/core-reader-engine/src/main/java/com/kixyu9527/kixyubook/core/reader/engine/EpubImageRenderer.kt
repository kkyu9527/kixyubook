package com.kixyu9527.kixyubook.core.reader.engine

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile
import kotlin.math.max

private object EpubImageCache {
    private const val MAX_BYTES = 32 * 1024 * 1024
    private val images = object : LruCache<String, ImageBitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: ImageBitmap): Int = value.width * value.height * 4
    }

    operator fun get(key: String): ImageBitmap? = images.get(key)
    fun put(key: String, image: ImageBitmap) = images.put(key, image)
}

@Composable
internal fun ReaderEpubImage(
    epubPath: String?,
    resourcePath: String?,
    altText: String,
    layout: ReaderImageLayout,
    placeholderColor: Color,
    onTapFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val targetWidth = layout.widthDp.dp
    val targetHeight = layout.heightDp.dp
    val density = LocalDensity.current
    val targetWidthPx = with(density) { targetWidth.roundToPx() }
    val targetHeightPx = with(density) { targetHeight.roundToPx() }
    val cacheKey = "$epubPath::$resourcePath::${targetWidthPx}x${targetHeightPx}"
    val loaded = produceState<ImageBitmap?>(
        initialValue = EpubImageCache[cacheKey],
        key1 = cacheKey,
    ) {
        if (value == null && !epubPath.isNullOrBlank() && !resourcePath.isNullOrBlank()) {
            value = withContext(Dispatchers.IO) {
                decodeEpubImage(epubPath, resourcePath, targetWidthPx, targetHeightPx)
                    ?.also { EpubImageCache.put(cacheKey, it) }
            }
        }
    }
    Crossfade(loaded.value, animationSpec = tween(160), label = "epub-image") { bitmap ->
        val imageModifier = modifier
            .size(targetWidth, targetHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(placeholderColor.copy(alpha = .08f))
            .pointerInput(Unit) {
                detectTapGestures { onTapFraction(it.x / size.width.coerceAtLeast(1)) }
            }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = altText.ifBlank { "书内插图" },
                modifier = imageModifier,
                contentScale = ContentScale.Fit,
            )
        } else {
            Box(imageModifier, contentAlignment = Alignment.Center) {
                Text(
                    text = altText.ifBlank { "插图" },
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
}

private fun decodeEpubImage(
    epubPath: String,
    resourcePath: String,
    targetWidthPx: Int,
    targetHeightPx: Int,
): ImageBitmap? = runCatching {
    val file = File(epubPath)
    if (!file.isFile) return null
    ZipFile(file).use { zip ->
        val entry = zip.getEntry(resourcePath) ?: zip.entries().asSequence()
            .firstOrNull { it.name.equals(resourcePath, true) }
            ?: return null
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
