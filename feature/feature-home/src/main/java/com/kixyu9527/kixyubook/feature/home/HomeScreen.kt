package com.kixyu9527.kixyubook.feature.home

import com.kixyu9527.kixyubook.core.designsystem.icon.KixyuSymbols

import android.os.Looper
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.DailyReading
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuBottomContentSpacer
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuPageContentWidth
import com.kixyu9527.kixyubook.core.designsystem.component.kixyuWindowSizeClass
import com.kixyu9527.kixyubook.core.ui.BookCover
import java.time.DayOfWeek
import java.time.LocalDate
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

    val expanded = kixyuWindowSizeClass().supportsTwoPane
    KixyuPageScaffold(title = "阅读", modifier = Modifier.fillMaxSize()) { innerPadding ->
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
                if (expanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
                    ) {
                        ReadingFocusCard(
                            state.current,
                            onOpenBook,
                            Modifier.weight(1.18f).fillMaxHeight(),
                            fillAvailableHeight = true,
                        )
                        TodayOverview(
                            state,
                            Modifier.weight(.82f).fillMaxHeight(),
                            fillAvailableHeight = true,
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap)) {
                        ReadingFocusCard(state.current, onOpenBook)
                        TodayOverview(state)
                    }
                }
            }
            if (state.recent.isNotEmpty()) {
                item { RecentReadingSection(state.recent, onOpenBook, expanded) }
            }
            item { WeeklyReadingCard(state.stats.recentDays, state.stats.goalMinutes) }
            item { KixyuBottomContentSpacer() }
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
        Text("最近阅读", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                items.forEach { item ->
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
    Surface(
        modifier = modifier
            .height(116.dp)
            .semantics { contentDescription = "打开最近阅读：${item.book.title}" }
            .clickable { open(item.book.uuid) },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
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
                val progress = item.progress?.fraction?.coerceIn(0f, 1f) ?: 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                )
                Text(
                    "已读 ${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReadingFocusCard(
    item: LibraryBook?,
    open: (String) -> Unit,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        Text("继续阅读", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(
            modifier = (if (fillAvailableHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth())
                .then(
                    if (item == null) Modifier else Modifier
                        .semantics { contentDescription = "打开书籍：${item.book.title}" }
                        .clickable { open(item.book.uuid) },
                ),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            if (item == null) {
                Column(
                    modifier = Modifier.padding(KixyuSpacing.large),
                    verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small),
                ) {
                    Text("从书架选一本书开始", style = MaterialTheme.typography.titleLarge)
                    Text("开始阅读后，这里会直接带你回到上次的位置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Row(
                    modifier = Modifier.padding(KixyuSpacing.large),
                    horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BookCover(
                        item.book.title,
                        item.book.coverPath,
                        Modifier.size(KixyuSize.continueCoverWidth, KixyuSize.continueCoverHeight),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                        Text(item.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.book.author, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LinearProgressIndicator(
                            progress = { item.progress?.fraction?.coerceIn(0f, 1f) ?: 0f },
                            modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                        )
                        Text(
                            "已读 ${((item.progress?.fraction ?: 0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TodayOverview(
    state: HomeUiState,
    modifier: Modifier = Modifier,
    fillAvailableHeight: Boolean = false,
) {
    val stats = state.stats
    val goalMillis = TimeUnit.MINUTES.toMillis(stats.goalMinutes.toLong()).coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        Text("今日目标", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(
            modifier = if (fillAvailableHeight) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(KixyuSpacing.large),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${TimeUnit.MILLISECONDS.toMinutes(stats.todayMillis)}",
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(" 分钟", modifier = Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text("目标 ${stats.goalMinutes} 分钟", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { (stats.todayMillis.toFloat() / goalMillis).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    StatItem(KixyuSymbols.LocalFireDepartment, "${stats.streakDays} 天", "连续阅读")
                    StatItem(KixyuSymbols.Schedule, "${TimeUnit.MILLISECONDS.toHours(stats.totalMillis)} 小时", "累计时长")
                }
            }
        }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        Icon(icon, null, Modifier.size(KixyuSize.iconSmall), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(value, style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        Text("近 7 天", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(KixyuSpacing.large),
                verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
            ) {
                val weeklyMillis = normalizedDays.sumOf(DailyReading::durationMillis)
                val weeklyMinutes = TimeUnit.MILLISECONDS.toMinutes(weeklyMillis)
                val activeDays = normalizedDays.count { it.durationMillis > 0L }
                val goalMillis = TimeUnit.MINUTES.toMillis(goalMinutes.toLong())
                val goalDays = normalizedDays.count { it.durationMillis >= goalMillis }
                val averageMinutes = TimeUnit.MILLISECONDS.toMinutes(weeklyMillis / normalizedDays.size)
                Text("7 天共阅读 $weeklyMinutes 分钟", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    WeeklyMetric("$activeDays 天", "有阅读")
                    WeeklyMetric("$goalDays 天", "完成目标")
                    WeeklyMetric("$averageMinutes 分钟", "日均")
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
private fun WeeklyMetric(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelLarge)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayBar(day: DailyReading, maxDuration: Long, modifier: Modifier = Modifier) {
    val ratio = (day.durationMillis.toFloat() / maxDuration).coerceIn(0f, 1f)
    val isToday = day.epochDay == LocalDate.now().toEpochDay()
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .width(16.dp)
                .height((6f + 72f * ratio).dp),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                shape = MaterialTheme.shapes.small,
            ) { }
        }
        Text(
            dayLabel(day.epochDay),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun dayLabel(epochDay: Long): String = when (LocalDate.ofEpochDay(epochDay).dayOfWeek) {
    DayOfWeek.MONDAY -> "一"
    DayOfWeek.TUESDAY -> "二"
    DayOfWeek.WEDNESDAY -> "三"
    DayOfWeek.THURSDAY -> "四"
    DayOfWeek.FRIDAY -> "五"
    DayOfWeek.SATURDAY -> "六"
    DayOfWeek.SUNDAY -> "日"
}
