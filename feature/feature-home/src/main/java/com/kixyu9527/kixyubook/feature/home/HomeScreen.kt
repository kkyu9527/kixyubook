package com.kixyu9527.kixyubook.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSection
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuPageScaffold
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSize
import com.kixyu9527.kixyubook.core.designsystem.component.KixyuSpacing
import com.kixyu9527.kixyubook.core.ui.BookCover
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeRoute(onOpenBook: (String) -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    KixyuPageScaffold(
        title = "今天读什么？",
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding),
            contentPadding = PaddingValues(
                horizontal = KixyuSpacing.screenHorizontal,
                vertical = KixyuSpacing.screenVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(KixyuSpacing.sectionGap),
        ) {
            item { StatsOverview(state) }
            state.recent.firstOrNull()?.let { current ->
                item { ContinueReading(current, onOpenBook) }
            }
            if (state.recent.size > 1) {
                item {
                    KixyuSection(title = "最近阅读") {
                        state.recent.drop(1).forEachIndexed { index, item ->
                            RecentRow(item, onOpenBook)
                            if (index < state.recent.lastIndex - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = KixyuSpacing.rowHorizontal + KixyuSize.recentCoverWidth + KixyuSpacing.medium),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(KixyuSize.bottomNavigationContentHeight)) }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun StatsOverview(state: HomeUiState) {
    val stats = state.stats
    val goalMillis = TimeUnit.MINUTES.toMillis(stats.goalMinutes.toLong()).coerceAtLeast(1)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(KixyuSpacing.large), verticalArrangement = Arrangement.spacedBy(KixyuSpacing.medium)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(KixyuSize.icon), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(KixyuSpacing.small))
                Text("今日 ${TimeUnit.MILLISECONDS.toMinutes(stats.todayMillis)} 分钟", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text("目标 ${stats.goalMinutes} 分钟", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            LinearProgressIndicator(
                progress = { (stats.todayMillis.toFloat() / goalMillis).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatItem(Icons.Outlined.AutoStories, "${stats.todayCharacters}", "今日字数")
                StatItem(Icons.Outlined.Schedule, "${TimeUnit.MILLISECONDS.toHours(stats.totalMillis)}h", "总时长")
                StatItem(Icons.Outlined.LocalFireDepartment, "${stats.streakDays} 天", "连续阅读")
            }
        }
    }
}

@Composable
private fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        Icon(icon, null, Modifier.size(KixyuSize.iconSmall), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ContinueReading(item: LibraryBook, open: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
        Text("继续阅读", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1)
        Surface(
            modifier = Modifier.fillMaxWidth().clickable { open(item.book.uuid) },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                Modifier.padding(KixyuSpacing.large),
                horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BookCover(item.book.title, item.book.coverPath, Modifier.size(KixyuSize.continueCoverWidth, KixyuSize.continueCoverHeight))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(KixyuSpacing.small)) {
                    Text(item.book.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(item.book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    LinearProgressIndicator(
                        progress = { item.progress?.fraction ?: 0f },
                        modifier = Modifier.fillMaxWidth().height(KixyuSize.progressHeight),
                    )
                    Text("已读 ${((item.progress?.fraction ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RecentRow(item: LibraryBook, open: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { open(item.book.uuid) }
            .padding(horizontal = KixyuSpacing.rowHorizontal, vertical = KixyuSpacing.rowVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(KixyuSpacing.medium),
    ) {
        BookCover(item.book.title, item.book.coverPath, Modifier.size(KixyuSize.recentCoverWidth, KixyuSize.recentCoverHeight))
        Column(Modifier.weight(1f)) {
            Text(item.book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.book.author, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${((item.progress?.fraction ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
