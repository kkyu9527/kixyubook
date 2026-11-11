// Adapted from the Material 3 expressive LinearWavyProgressIndicator (Apache-2.0).
package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Material 3 expressive wavy progress indicator, implemented locally because the project's
 * Material 3 artifact does not ship the expressive API yet.
 *
 * The wave amplitude fades out near 0% and 100% like the official component. [progress] null
 * renders an indeterminate travelling segment; determinate progress gets a moving wave.
 */
@Composable
fun KixyuWavyProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 10.dp,
    amplitude: Dp = 3.dp,
    wavelength: Dp = 40.dp,
    strokeWidth: Dp = 4.dp,
) {
    val target = progress?.coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = tween(durationMillis = 450),
        label = "wavyProgress",
    )
    val wave = rememberInfiniteTransition(label = "wavyPhase")
    val phase by wave.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavyPhaseValue",
    )
    val indeterminate by wave.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavyIndeterminate",
    )
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val strokePx = with(density) { strokeWidth.toPx() }
    val amplitudePx = with(density) { amplitude.toPx() }
    val wavelengthPx = with(density) { wavelength.toPx() }.coerceAtLeast(1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics(mergeDescendants = true) {
                progressBarRangeInfo = if (progress == null) {
                    ProgressBarRangeInfo.Indeterminate
                } else {
                    ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                }
            },
    ) {
        if (rtl) {
            scale(scaleX = -1f, scaleY = 1f, pivot = center) {
                drawWavyBars(
                    progress, animatedProgress, phase, indeterminate, color,
                    strokePx, amplitudePx, wavelengthPx,
                )
            }
        } else {
            drawWavyBars(
                progress, animatedProgress, phase, indeterminate, color,
                strokePx, amplitudePx, wavelengthPx,
            )
        }
    }
}

private fun DrawScope.drawWavyBars(
    progress: Float?,
    animatedProgress: Float,
    phase: Float,
    indeterminate: Float,
    color: Color,
    strokePx: Float,
    amplitudePx: Float,
    wavelengthPx: Float,
) {
    val centerY = size.height / 2f
    val startX = strokePx / 2f
    val trackEndX = size.width - strokePx / 2f
    if (trackEndX <= startX) return

    // A faint wavy remainder keeps low percentages legible without the old straight grey track.
    drawPath(
        path = wavePath(startX, trackEndX, centerY, amplitudePx, wavelengthPx, phase),
        color = color.copy(alpha = .16f),
        style = Stroke(width = strokePx, cap = StrokeCap.Round),
    )

    if (progress == null) {
        val span = (trackEndX - startX) * .34f
        val travel = (trackEndX - startX) + span
        val segmentStart = startX - span + travel * indeterminate
        val from = max(startX, segmentStart)
        val to = min(trackEndX, segmentStart + span)
        if (to > from) {
            drawPath(
                path = wavePath(from, to, centerY, amplitudePx, wavelengthPx, phase),
                color = color,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
    } else if (animatedProgress > 0f) {
        val endX = startX + (trackEndX - startX) * animatedProgress
        // Match the official amplitude curve: flat at the ends, wavy through the middle.
        val activeAmplitude = if (animatedProgress in .1f..0.95f) amplitudePx else 0f
        drawPath(
            path = wavePath(startX, endX, centerY, activeAmplitude, wavelengthPx, phase),
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round),
        )
    }

}
private fun wavePath(
    startX: Float,
    endX: Float,
    centerY: Float,
    amplitude: Float,
    wavelength: Float,
    phase: Float,
): Path {
    val path = Path()
    if (endX <= startX) return path
    val step = (wavelength / 14f).coerceAtLeast(1f)
    var x = startX
    path.moveTo(x, centerY + sin((x / wavelength) * 2f * PI.toFloat() + phase * 2f * PI.toFloat()) * amplitude)
    while (x < endX) {
        x = min(x + step, endX)
        val y = centerY + sin((x / wavelength) * 2f * PI.toFloat() + phase * 2f * PI.toFloat()) * amplitude
        path.lineTo(x, y)
    }
    return path
}
