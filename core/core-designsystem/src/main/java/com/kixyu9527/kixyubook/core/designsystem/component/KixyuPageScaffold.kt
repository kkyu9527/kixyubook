package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.designsystem.theme.LocalAppUiStyle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiuixScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiuixTopAppBar

/**
 * Shared edge-to-edge page frame. The top app bar and the list beneath it
 * always receive the same scroll behavior for the active component system.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KixyuPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    largeTitle: Boolean = true,
    showTopBar: Boolean = true,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val horizontalInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
    if (LocalAppUiStyle.current == AppUiStyle.MIUIX) {
        val scrollBehavior = MiuixScrollBehavior()
        MiuixScaffold(
            modifier = if (showTopBar) {
                modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                modifier
            },
            topBar = {
                if (showTopBar) {
                    MiuixTopAppBar(
                        title = title,
                        largeTitle = title,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                    )
                }
            },
            snackbarHost = snackbarHost,
            contentWindowInsets = horizontalInsets,
            content = content,
        )
    } else {
        val topAppBarState = rememberTopAppBarState()
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(topAppBarState)
        Scaffold(
            modifier = if (showTopBar) {
                modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
            } else {
                modifier
            },
            contentWindowInsets = horizontalInsets,
            topBar = {
                if (showTopBar) {
                    val titleContent: @Composable () -> Unit = { Text(title, maxLines = 1) }
                    val colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    )
                    if (largeTitle) {
                        LargeTopAppBar(
                            title = titleContent,
                            navigationIcon = navigationIcon,
                            actions = actions,
                            scrollBehavior = scrollBehavior,
                            colors = colors,
                            expandedHeight = 116.dp,
                        )
                    } else {
                        MediumTopAppBar(
                            title = titleContent,
                            navigationIcon = navigationIcon,
                            actions = actions,
                            scrollBehavior = scrollBehavior,
                            colors = colors,
                            expandedHeight = 88.dp,
                        )
                    }
                }
            },
            snackbarHost = snackbarHost,
            content = content,
        )
    }
}
