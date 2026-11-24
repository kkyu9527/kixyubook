package com.kixyu9527.kixyubook.core.reader.engine

import kotlin.math.pow
import kotlin.math.roundToInt

/** Shared policy values are also supplied to the browser backend, not chosen by feature UI. */
object ReaderImageThemePolicy {
    const val MIN_TRANSPARENCY = .05f
    const val NEUTRAL_TOLERANCE = 16
    const val SINGLE_INK_FRACTION = .995f
    const val MAX_INK_SPREAD = .06f
    const val SIGNIFICANT_INK_FRACTION = .2f
    const val MIN_CONTRAST = 3f
    const val TARGET_INK_CONTRAST = 4.5f
    const val SOFT_INK_LUMINANCE = .55f
}

internal data class ReaderImageProfile(val inkLuminance: Float?, val singleInk: Boolean)

/** Only alpha-backed, nearly uniform neutral ink is recolorable; photos/colored art are not. */
internal fun readerImageProfile(pixels: IntArray): ReaderImageProfile {
    if (pixels.isEmpty() || pixels.count { it ushr 24 < 16 } < pixels.size * ReaderImageThemePolicy.MIN_TRANSPARENCY) {
        return ReaderImageProfile(null, false)
    }
    val visible = pixels.filter { it ushr 24 >= 32 }
    if (visible.isEmpty()) return ReaderImageProfile(null, false)
    val luminances = visible.map(::imagePixelLuminance)
    val dark = luminances.filter { it < .18f }
    val light = luminances.filter { it > .75f }
    val dominant = if (dark.size >= light.size) dark else light
    // Mixed-color transparent logos can contain substantial black text without a 65% majority.
    // They need a subdued backing, not recoloring; opaque photos were excluded above.
    if (dominant.size < visible.size * ReaderImageThemePolicy.SIGNIFICANT_INK_FRACTION) return ReaderImageProfile(null, false)
    val neutral = visible.count {
        val r = it ushr 16 and 255; val g = it ushr 8 and 255; val b = it and 255
        maxOf(r, g, b) - minOf(r, g, b) <= ReaderImageThemePolicy.NEUTRAL_TOLERANCE
    }
    val sorted = luminances.sorted()
    val spread = sorted[(sorted.lastIndex * .95f).roundToInt()] - sorted[(sorted.lastIndex * .05f).roundToInt()]
    return ReaderImageProfile(dominant.average().toFloat(),
        neutral >= visible.size * ReaderImageThemePolicy.SINGLE_INK_FRACTION &&
            dominant.size >= visible.size * ReaderImageThemePolicy.SINGLE_INK_FRACTION &&
            spread <= ReaderImageThemePolicy.MAX_INK_SPREAD)
}

internal fun imagePixelLuminance(pixel: Int): Float =
    (.2126f * imageLinearChannel(pixel ushr 16 and 255) + .7152f * imageLinearChannel(pixel ushr 8 and 255) +
        .0722f * imageLinearChannel(pixel and 255))

private fun imageLinearChannel(value: Int): Float = (value / 255f).let {
    if (it <= .04045f) it / 12.92f else ((it + .055f) / 1.055f).pow(2.4f)
}
private fun imageSrgbChannel(value: Float): Int = ((if (value <= .0031308f) value * 12.92f
    else 1.055f * value.pow(1f / 2.4f) - .055f) * 255f).roundToInt().coerceIn(0, 255)
private fun imageContrast(a: Float, b: Float) = (maxOf(a, b) + .05f) / (minOf(a, b) + .05f)

/** Change only the ink; keep every transparent pixel transparent, including full-page drawings. */
internal fun imageAdaptiveInk(profile: ReaderImageProfile, pageArgb: Int, readerInkArgb: Int): Int? {
    val ink = profile.inkLuminance ?: return null
    val page = imagePixelLuminance(pageArgb)
    if (!profile.singleInk || imageContrast(ink, page) >= ReaderImageThemePolicy.MIN_CONTRAST) return null
    val original = imagePixelLuminance(readerInkArgb)
    val target = if (page < .18f) maxOf(ReaderImageThemePolicy.SOFT_INK_LUMINANCE,
        (page + .05f) * ReaderImageThemePolicy.TARGET_INK_CONTRAST - .05f).coerceAtMost(1f)
    else minOf(original, ((page + .05f) / ReaderImageThemePolicy.TARGET_INK_CONTRAST - .05f).coerceAtLeast(0f))
    val amount = if (target > original) (target - original) / (1f - original).coerceAtLeast(.0001f)
        else target / original.coerceAtLeast(.0001f)
    fun channel(shift: Int): Int {
        val value = imageLinearChannel(readerInkArgb ushr shift and 255)
        return imageSrgbChannel(if (target > original) value + (1f - value) * amount else value * amount)
    }
    return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** Ambiguous colored drawings keep their pixels; use only the minimum contrasting neutral backing. */
internal fun imagePaperBackdrop(ink: Float?, page: Float): Int? {
    if (ink == null || imageContrast(ink, page) >= ReaderImageThemePolicy.MIN_CONTRAST) return null
    val candidates = listOf((ink + .05f) * ReaderImageThemePolicy.MIN_CONTRAST - .05f,
        (ink + .05f) / ReaderImageThemePolicy.MIN_CONTRAST - .05f).filter { it in 0f..1f }
    val luminance = candidates.minByOrNull { kotlin.math.abs(it - page) } ?: return null
    val gray = imageSrgbChannel(luminance)
    return (0xff shl 24) or (gray shl 16) or (gray shl 8) or gray
}
