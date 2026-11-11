package com.kixyu9527.kixyubook.feature.library

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.LibraryLayoutMode
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
class LibraryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun materialLight() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material_light")

    @Test
    fun miuixLight() = capture(AppUiStyle.MIUIX, ReaderTheme.DAY, "miuix_light")

    @Test
    @Config(qualifiers = "zh-rCN-w800dp-h1280dp")
    fun materialTablet() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material_tablet")

    @Test
    fun materialLargeText() {
        val state = libraryState()
        val snackbar = SnackbarHostState()
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.8f),
            ) {
                KixyuBookTheme(
                    themeMode = ReaderTheme.DAY,
                    uiStyle = AppUiStyle.MATERIAL,
                    glassEffectEnabled = false,
                ) {
                    LibraryScreen(
                        state = state,
                        importProgress = null,
                        importHistory = emptyList(),
                        snackbar = snackbar,
                        onSearch = {},
                        onCategory = {},
                        onSortMode = {},
                        onLayoutMode = {},
                        onMoveBook = { _, _ -> },
                        onFinishReorder = {},
                        onSetCategoryHidden = { _, _ -> },
                        onOpenHiddenLibrary = {},
                        onBack = {},
                        onImport = {},
                        onOpenBook = {},
                        onDelete = {},
                        onDeleteMany = {},
                        onExport = {},
                        onExportMany = {},
                        onUpdateMetadata = { _, _, _, _, _ -> },
                        onSetCategories = { _, _ -> },
                        onDropDocuments = { _, _ -> },
                        onClearImportProgress = {},
                        onCancelImport = {},
                        onRetryImport = {},
                        onClearImportHistory = {},
                    )
                }
            }
        }

        compose.onAllNodesWithText(
            androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
                .getString(R.string.library_title),
        ).onFirst().assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/library_material_large_text.png")
    }

    @Test
    fun materialDark() = capture(AppUiStyle.MATERIAL, ReaderTheme.NIGHT, "material_dark")

    @Test
    fun miuixDark() = capture(AppUiStyle.MIUIX, ReaderTheme.NIGHT, "miuix_dark")

    private fun capture(style: AppUiStyle, theme: ReaderTheme, suffix: String) {
        val state = libraryState()
        val snackbar = SnackbarHostState()
        compose.setContent {
            KixyuBookTheme(themeMode = theme, uiStyle = style, glassEffectEnabled = false) {
                LibraryScreen(
                    state = state,
                    importProgress = null,
                    importHistory = emptyList(),
                    snackbar = snackbar,
                    onSearch = {},
                    onCategory = {},
                    onSortMode = {},
                    onLayoutMode = {},
                    onMoveBook = { _, _ -> },
                    onFinishReorder = {},
                    onSetCategoryHidden = { _, _ -> },
                    onOpenHiddenLibrary = {},
                    onBack = {},
                    onImport = {},
                    onOpenBook = {},
                    onDelete = {},
                    onDeleteMany = {},
                    onExport = {},
                    onExportMany = {},
                    onUpdateMetadata = { _, _, _, _, _ -> },
                    onSetCategories = { _, _ -> },
                    onDropDocuments = { _, _ -> },
                    onClearImportProgress = {},
                    onCancelImport = {},
                    onRetryImport = {},
                    onClearImportHistory = {},
                )
            }
        }

        compose.onAllNodesWithText(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>().getString(R.string.library_title)).onFirst().assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/library_$suffix.png")
    }

    private fun libraryState() = LibraryUiState(
        books = books(),
        categories = listOf(ALL_LIBRARY_CATEGORIES, "科幻", "文学"),
        allCategories = listOf("科幻", "文学"),
        layoutMode = LibraryLayoutMode.GRID,
    )

    private fun books(): List<LibraryBook> = listOf(
        book("1", "三体", "刘慈欣", .42f),
        book("2", "球状闪电", "刘慈欣", .18f),
        book("3", "百年孤独", "加西亚·马尔克斯", .67f),
        book("4", "银河系漫游指南", "道格拉斯·亚当斯", 0f),
        book("5", "海伯利安", "丹·西蒙斯", .83f),
        book("6", "基地", "艾萨克·阿西莫夫", .12f),
    )

    private fun book(uuid: String, title: String, author: String, fraction: Float) = LibraryBook(
        book = Book(
            uuid = uuid,
            title = title,
            author = author,
            description = "",
            coverPath = null,
            format = BookFormat.TXT,
            originalPath = "",
            storagePath = "",
            createdTime = 0L,
            contentHash = "hash-$uuid",
        ),
        progress = if (fraction > 0f) ReadingProgress(
            bookUuid = uuid,
            chapterId = 1L,
            position = 0,
            updatedTime = 0L,
            fraction = fraction,
        ) else null,
    )
}
