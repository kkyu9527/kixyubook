package com.kixyu9527.kixyubook.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun startup() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.text("首页")), UI_TIMEOUT_MILLIS)) {
            "首页未在规定时间内完成渲染"
        }
    }

    @Test
    fun openReaderAndTurnPages() = baselineProfileRule.collect(
        packageName = PACKAGE_NAME,
    ) {
        pressHome()
        startActivityAndWait()

        val book = device.wait(
            Until.findObject(By.descStartsWith(OPEN_BOOK_DESCRIPTION_PREFIX)),
            UI_TIMEOUT_MILLIS,
        ) ?: error("设备中没有可用于生成 Reader Baseline Profile 的已导入书籍")
        book.click()

        check(device.wait(Until.hasObject(By.desc(READER_CONTENT_DESCRIPTION)), READER_TIMEOUT_MILLIS)) {
            "Reader 未在规定时间内完成首章渲染"
        }
        device.waitForIdle()

        repeat(PAGE_TURN_COUNT) {
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(
                width * 4 / 5,
                height / 2,
                width / 5,
                height / 2,
                PAGE_TURN_STEPS,
            )
            device.waitForIdle()
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.kixyu9527.kixyubook"
        const val OPEN_BOOK_DESCRIPTION_PREFIX = "打开书籍："
        const val READER_CONTENT_DESCRIPTION = "阅读正文"
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val READER_TIMEOUT_MILLIS = 30_000L
        const val PAGE_TURN_COUNT = 4
        const val PAGE_TURN_STEPS = 12
    }
}
