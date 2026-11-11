package com.kixyu9527.kixyubook.feature.home

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.kixyu9527.kixyubook.core.common.model.AppUiStyle
import com.kixyu9527.kixyubook.core.common.model.Book
import com.kixyu9527.kixyubook.core.common.model.BookFormat
import com.kixyu9527.kixyubook.core.common.model.DailyReading
import com.kixyu9527.kixyubook.core.common.model.LibraryBook
import com.kixyu9527.kixyubook.core.common.model.ReadingProgress
import com.kixyu9527.kixyubook.core.common.model.ReadingStats
import com.kixyu9527.kixyubook.core.designsystem.theme.KixyuBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w412dp-h915dp")
class HomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun materialThemeShowsTheReadingDeskAndOpensTheCurrentBook() {
        assertHome(AppUiStyle.MATERIAL)
    }

    @Test
    fun miuixThemeShowsTheReadingDeskAndOpensTheCurrentBook() {
        assertHome(AppUiStyle.MIUIX)
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp")
    fun compactWidthKeepsTheHeroAndTodayCardReadable() {
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MATERIAL) {
                HomeScreen(
                    state = HomeUiState(
                        current = LibraryBook(book("book-1", "三体", "刘慈欣"), progress("book-1", .42f)),
                        stats = ReadingStats(todayMillis = 25 * 60_000L, goalMinutes = 30),
                    ),
                    onOpenBook = {},
                )
            }
        }

        compose.onNodeWithText("三体").assertIsDisplayed()
        compose.onNodeWithText(ApplicationProvider.getApplicationContext<Context>()
            .getString(R.string.home_today_label)).assertIsDisplayed()
    }

    @Test
    fun anEmptyReaderIsToldHowToStart() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        compose.setContent {
            KixyuBookTheme(uiStyle = AppUiStyle.MATERIAL) {
                HomeScreen(state = HomeUiState(), onOpenBook = {})
            }
        }

        compose.onNodeWithText(context.getString(R.string.home_choose_book)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_choose_book_hint)).assertIsDisplayed()
    }

    private fun assertHome(style: AppUiStyle) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var opened: String? = null
        val state = HomeUiState(
            current = LibraryBook(book("book-1", "三体", "刘慈欣"), progress("book-1", .42f)),
            recent = listOf(LibraryBook(book("book-2", "球状闪电", "刘慈欣"), progress("book-2", .18f))),
            stats = ReadingStats(
                todayMillis = 25 * 60_000L,
                totalMillis = 12 * 60 * 60_000L,
                streakDays = 3,
                goalMinutes = 30,
                recentDays = (6L downTo 0L).map { DailyReading(0L, it * 60_000L) },
            ),
        )

        compose.setContent {
            KixyuBookTheme(uiStyle = style) {
                HomeScreen(state = state, onOpenBook = { opened = it })
            }
        }

        compose.onNodeWithText("三体").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.home_today_label)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.home_open_book, "三体"))
            .performClick()
        assertEquals("book-1", opened)
    }

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
