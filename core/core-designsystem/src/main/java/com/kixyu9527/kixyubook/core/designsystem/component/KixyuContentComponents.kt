package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Shared quiet-paper content card. It owns the surface, border and corner role; callers own
 * placement, click policy and the content column. Keep using [KixyuSection] for grouped rows and
 * this card for content surfaces such as the home hero.
 */
@Composable
fun KixyuCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionModifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick)
    Surface(
        modifier = interactionModifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(KixyuSize.cardCornerRadius),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
        ),
    ) {
        Column(content = content)
    }
}

/** Shared progress line so every page uses the same height, radius and track treatment. */
@Composable
/**
 * Shared app progress bar. [wavy] opts into the Material 3 expressive wave treatment for
 * prominent, user-visible progress; compact list rows keep the linear form.
 */
fun KixyuProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    wavy: Boolean = false,
) {
    if (wavy) {
        KixyuWavyProgressBar(
            progress = progress,
            modifier = modifier,
            color = color,
        )
        return
    }
    val shape = CircleShape
    if (progress == null) {
        LinearProgressIndicator(
            modifier = modifier
                .fillMaxWidth()
                .height(KixyuSize.progressHeight)
                .clip(shape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    } else {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier
                .fillMaxWidth()
                .height(KixyuSize.progressHeight)
                .clip(shape),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
        )
    }
}

/** One number + one label, used by the home dashboard and weekly summary. */
@Composable
fun KixyuMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, style = MaterialTheme.typography.labelLarge)
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shared empty state: icon, title, optional explanation and one optional action. */
@Composable
fun KixyuEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(KixyuSpacing.extraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(KixyuSize.icon * 2),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        description?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        action?.let {
            Column(Modifier.padding(top = KixyuSpacing.small)) { it() }
        }
    }
}
