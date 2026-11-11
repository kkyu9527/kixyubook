package com.kixyu9527.kixyubook.feature.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import com.github.takahirom.roborazzi.captureRoboImage
import com.kixyu9527.kixyubook.core.common.model.AppColorTheme
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.DailyReading
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReaderTheme
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.common.model.ReadingStats
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
class HomeScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun materialLight() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material_light")

    @Test
    fun miuixLight() = capture(AppUiStyle.MIUIX, ReaderTheme.DAY, "miuix_light")

    @Test
    fun materialSage() = capture(
        AppUiStyle.MATERIAL,
        ReaderTheme.DAY,
        "material_sage",
        AppColorTheme.SAGE,
    )

    @Test
    @Config(qualifiers = "zh-rCN-w800dp-h1280dp")
    fun materialTablet() = capture(AppUiStyle.MATERIAL, ReaderTheme.DAY, "material_tablet")

    @Test
    fun materialLargeText() {
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
                    HomeScreen(state = homeState(), onOpenBook = {})
                }
            }
        }

        compose.onNodeWithText("三体").assertExists()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/home_material_large_text.png")
    }

    @Test
    fun materialDark() = capture(AppUiStyle.MATERIAL, ReaderTheme.NIGHT, "material_dark")

    @Test
    fun miuixDark() = capture(AppUiStyle.MIUIX, ReaderTheme.NIGHT, "miuix_dark")

    private fun capture(
        style: AppUiStyle,
        theme: ReaderTheme,
        suffix: String,
        colorTheme: AppColorTheme = AppColorTheme.DEFAULT,
    ) {
        compose.setContent {
            KixyuBookTheme(
                themeMode = theme,
                colorTheme = colorTheme,
                uiStyle = style,
                glassEffectEnabled = false,
            ) {
                HomeScreen(state = homeState(), onOpenBook = {})
            }
        }

        compose.onNodeWithText("三体").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/home_$suffix.png")
    }

    private fun homeState() = HomeUiState(
        current = LibraryBook(book("book-1", "三体", "刘慈欣"), progress("book-1", .42f)),
        recent = listOf(
            LibraryBook(book("book-2", "球状闪电", "刘慈欣"), progress("book-2", .18f)),
            LibraryBook(book("book-3", "百年孤独", "加西亚·马尔克斯"), progress("book-3", .67f)),
        ),
        stats = ReadingStats(
            todayMillis = 25 * 60_000L,
            totalMillis = 12 * 60 * 60_000L,
            streakDays = 3,
            goalMinutes = 30,
            recentDays = listOf(12L, 34L, 8L, 45L, 20L, 0L, 25L).mapIndexed { index, minutes ->
                DailyReading(epochDay = 20_000L + index, durationMillis = minutes * 60_000L)
            },
        ),
    )

    private fun book(uuid: String, title: String, author: String) = Book(
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
    )

    private fun progress(bookUuid: String, fraction: Float) = ReadingProgress(
        bookUuid = bookUuid,
        chapterId = 1L,
        position = 0,
        updatedTime = 0L,
        fraction = fraction,
    )
}
