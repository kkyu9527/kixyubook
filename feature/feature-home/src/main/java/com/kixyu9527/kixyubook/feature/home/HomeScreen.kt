package com.kixyu9527.kixyubook.feature.home

import android.os.Looper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.DailyReading
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReadingStats
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuCard
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuEmptyState
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuMetric
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuProgressBar
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuWindowWidthClass
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols
import com.kixyu9527.kixyubook.core.ui.BookCover
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeRoute(onOpenBook: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentBookUuid = state.current?.book?.uuid
    DisposableEffect(currentBookUuid, viewModel) {
        if (currentBookUuid == null) {
            onDispose { }
        } else {
            val queue = Looper.myQueue()
            val idleHandler = android.os.MessageQueue.IdleHandler {
                viewModel.prewarmReader(currentBookUuid)
                false
            }
            queue.addIdleHandler(idleHandler)
            onDispose { queue.removeIdleHandler(idleHandler) }
        }
    }
    HomeScreen(state = state, onOpenBook = onOpenBook)
}

/**
 * State-free home content. Keeping the ViewModel and idle prewarm in [HomeRoute] makes the visual
 * contract testable with fixed state and keeps rendering independent of repositories.
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onOpenBook: (String) -> Unit,
) {
    val windowSizeClass = kixyuWindowSizeClass()
    val expanded = windowSizeClass.supportsTwoPane
    val wideCards = windowSizeClass.widthClass != KixyuWindowWidthClass.COMPACT
    KixyuPageScaffold(
        title = stringResource(R.string.home_title),
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .kixyuPageContentWidth(
                    if (expanded) KixyuSize.expandedPageContentMaxWidth else KixyuSize.pageContentMaxWidth,
                )
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item {
                if (wideCards) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
                    ) {
                        ContinueReadingCard(
                            item = state.current,
                            open = onOpenBook,
                            modifier = Modifier.weight(1.18f).fillMaxHeight(),
                            fillAvailableHeight = true,
                        )
                        TodaySummaryCard(
                            stats = state.stats,
                            modifier = Modifier.weight(.82f).fillMaxHeight(),
                            fillAvailableHeight = true,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap)) {
                        ContinueReadingCard(state.current, onOpenBook)
                        TodaySummaryCard(state.stats)
                    }
                }
            }
            if (state.recent.isNotEmpty()) {
                item { RecentReadingSection(state.recent, onOpenBook, wideCards) }
            }
            item { WeeklyReadingCard(state.stats.recentDays, state.stats.goalMinutes) }
            item { KixyuBottomContentSpacer() }
        }
    }
}

@Composable
private fun SectionOverline(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ContinueReadingCard(
    item: LibraryBook?,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    val openDescription = item?.let { stringResource(R.string.home_open_book, it.book.title) }
    val cardModifier = if (fillAvailableHeight) modifier.fillMaxHeight() else modifier
    KixyuCard(
        modifier = cardModifier.then(
            if (openDescription == null) Modifier else Modifier.semantics {
                contentDescription = openDescription
            },
        ),
        onClick = item?.let { { open(it.book.uuid) } },
    ) {
        if (item == null) {
            KixyuEmptyState(
                icon = KixyuSymbols.LibraryBooks,
                title = stringResource(R.string.home_choose_book),
                description = stringResource(R.string.home_choose_book_hint),
            )
        } else {
            ContinueReadingBody(item, fillAvailableHeight)
        }
    }
}

@Composable
private fun ContinueReadingBody(
    item: LibraryBook,
    fillAvailableHeight: Boolean,
) {
    val progress = (item.progress?.fraction ?: 0f).coerceIn(0f, 1f)
    Column(Modifier.padding(KixyuSpacing.extraLarge)) {
        SectionOverline(stringResource(R.string.home_continue_reading))
        Spacer(Modifier.height(KixyuSpacing.medium))
        Row(
            modifier = if (fillAvailableHeight) Modifier.fillMaxHeight() else Modifier.heightIn(min = 132.dp),
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = item.book.title,
                coverPath = item.book.coverPath,
                modifier = Modifier.size(96.dp, 138.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
            ) {
                Text(
                    item.book.title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(KixyuSpacing.small))
                KixyuProgressBar(progress = progress, wavy = true)
                val largeText = LocalDensity.current.fontScale > 1.25f
                if (largeText) {
                    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall)) {
                        Text(
                            stringResource(R.string.home_read_progress, (progress * 100).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.home_continue_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.home_read_progress, (progress * 100).toInt()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(R.string.home_continue_hint),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodaySummaryCard(
    stats: ReadingStats,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    val goalMillis = TimeUnit.MINUTES.toMillis(stats.goalMinutes.toLong()).coerceAtLeast(1L)
    val goalFraction = (stats.todayMillis.toFloat() / goalMillis).coerceIn(0f, 1f)
    KixyuCard(
        modifier = if (fillAvailableHeight) modifier.fillMaxHeight() else modifier,
    ) {
        Column(
            modifier = Modifier.padding(KixyuSpacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
        ) {
            SectionOverline(stringResource(R.string.home_today_label))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    TimeUnit.MILLISECONDS.toMinutes(stats.todayMillis).toString(),
                    style = MaterialTheme.typography.displayMedium,
                )
                Text(
                    stringResource(R.string.home_minutes_unit),
                    modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.home_goal_progress, (goalFraction * 100).toInt()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            KixyuProgressBar(progress = goalFraction, wavy = true)
            Text(
                stringResource(R.string.home_goal_minutes, stats.goalMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                KixyuMetric(
                    value = stringResource(R.string.home_days_value, stats.streakDays),
                    label = stringResource(R.string.home_reading_streak),
                )
                KixyuMetric(
                    value = stringResource(R.string.home_hours_value, TimeUnit.MILLISECONDS.toHours(stats.totalMillis)),
                    label = stringResource(R.string.home_total_duration),
                )
            }
        }
    }
}

@Composable
private fun RecentReadingSection(
    items: List<LibraryBook>,
    open: (String) -> Unit,
    expanded: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        SectionOverline(stringResource(R.string.home_recent_reading))
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                items.take(3).forEach { item ->
                    RecentReadingCard(
                        item = item,
                        open = open,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                items(items, key = { it.book.uuid }) { item ->
                    RecentReadingCard(
                        item = item,
                        open = open,
                        modifier = Modifier.width(252.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentReadingCard(
    item: LibraryBook,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val openDescription = stringResource(R.string.home_open_recent_book, item.book.title)
    val progress = (item.progress?.fraction ?: 0f).coerceIn(0f, 1f)
    KixyuCard(
        modifier = modifier.heightIn(min = 124.dp).semantics { contentDescription = openDescription },
        onClick = { open(item.book.uuid) },
    ) {
        Row(
            modifier = Modifier.padding(KixyuSpacing.medium),
            horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCover(
                title = item.book.title,
                coverPath = item.book.coverPath,
                modifier = Modifier.size(56.dp, 84.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.extraSmall),
            ) {
                Text(
                    item.book.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                KixyuProgressBar(progress = progress, wavy = true)
                Text(
                    stringResource(R.string.home_read_progress, (progress * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun WeeklyReadingCard(days: List<DailyReading>, goalMinutes: Int) {
    val normalizedDays = if (days.size == 7) days else {
        val today = LocalDate.now().toEpochDay()
        (6L downTo 0L).map { DailyReading(today - it, 0L) }
    }
    val maxDuration = normalizedDays.maxOfOrNull(DailyReading::durationMillis)?.coerceAtLeast(1L) ?: 1L
    val weeklyMillis = normalizedDays.sumOf(DailyReading::durationMillis)
    val weeklyMinutes = TimeUnit.MILLISECONDS.toMinutes(weeklyMillis)
    val activeDays = normalizedDays.count { it.durationMillis > 0L }
    val goalMillis = TimeUnit.MINUTES.toMillis(goalMinutes.toLong())
    val goalDays = normalizedDays.count { it.durationMillis >= goalMillis }
    val averageMinutes = TimeUnit.MILLISECONDS.toMinutes(weeklyMillis / normalizedDays.size)

    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        SectionOverline(stringResource(R.string.home_last_seven_days))
        KixyuCard {
            Column(
                modifier = Modifier.padding(KixyuSpacing.extraLarge),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                Text(
                    stringResource(R.string.home_weekly_total, weeklyMinutes),
                    style = MaterialTheme.typography.titleLarge,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    KixyuMetric(
                        value = stringResource(R.string.home_days_value, activeDays),
                        label = stringResource(R.string.home_days_read),
                    )
                    KixyuMetric(
                        value = stringResource(R.string.home_days_value, goalDays),
                        label = stringResource(R.string.home_days_goal_met),
                    )
                    KixyuMetric(
                        value = stringResource(R.string.home_minutes_value, averageMinutes),
                        label = stringResource(R.string.home_daily_average),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    normalizedDays.forEach { day ->
                        DayBar(day, maxDuration, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayBar(day: DailyReading, maxDuration: Long, modifier: Modifier = Modifier) {
    val ratio = (day.durationMillis.toFloat() / maxDuration).coerceIn(0f, 1f)
    val hasReading = day.durationMillis > 0L
    val isToday = day.epochDay == LocalDate.now().toEpochDay()
    val locale = LocalConfiguration.current.locales[0]
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height(if (hasReading) (6f + 72f * ratio).dp else 2.dp),
        ) {
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxSize(),
                color = when {
                    !hasReading -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                },
                shape = MaterialTheme.shapes.small,
            ) { }
        }
        Text(
            dayLabel(day.epochDay, locale),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dayLabel(epochDay: Long, locale: Locale): String = LocalDate.ofEpochDay(epochDay)
    .format(DateTimeFormatter.ofPattern("EEEEE", locale))
