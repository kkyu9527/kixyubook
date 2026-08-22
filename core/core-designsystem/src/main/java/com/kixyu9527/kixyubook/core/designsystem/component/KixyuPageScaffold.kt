package com.kixyu9527.kixyubook.core.designsystem.component

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // A destination underneath the immersive reader is composed before the reader finishes
    // leaving. Keep its top bar on the physical safe-area baseline even while system bars are
    // hidden, otherwise the bar is measured twice and jumps when the reader restores them.
    val stableTopBarInsets = WindowInsets.statusBarsIgnoringVisibility
        .union(WindowInsets.navigationBarsIgnoringVisibility)
        .union(WindowInsets.displayCutout)
        .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
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
                        modifier = Modifier.windowInsetsPadding(stableTopBarInsets),
                        largeTitle = title,
                        navigationIcon = navigationIcon,
                        actions = actions,
                        scrollBehavior = scrollBehavior,
                        // The modifier above supplies and consumes the shared stable insets. The
                        // MIUIX implementation still applies its mandatory top system-bar inset,
                        // which becomes zero after that consumption.
                        defaultWindowInsetsPadding = false,
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
                            windowInsets = stableTopBarInsets,
                        )
                    } else {
                        MediumTopAppBar(
                            title = titleContent,
                            navigationIcon = navigationIcon,
                            actions = actions,
                            scrollBehavior = scrollBehavior,
                            colors = colors,
                            expandedHeight = 88.dp,
                            windowInsets = stableTopBarInsets,
                        )
                    }
                }
            },
            snackbarHost = snackbarHost,
            content = content,
        )
    }
}
