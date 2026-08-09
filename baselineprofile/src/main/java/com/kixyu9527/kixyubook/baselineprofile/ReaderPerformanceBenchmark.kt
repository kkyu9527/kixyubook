package com.kixyu9527.kixyubook.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Performance acceptance benchmarks for the journeys users notice most.
 *
 * FrameTimingMetric writes frameDurationCpuMs percentiles, including P50 and P95, into the
 * Macrobenchmark report. Pass benchmarkBookTitle as an instrumentation argument so the reader
 * cases consistently exercise the same large EPUB on every device.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class ReaderPerformanceBenchmark {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val benchmarkBookTitle = InstrumentationRegistry.getArguments().getString(BOOK_TITLE_ARGUMENT)

    @Test
    fun coldStartup() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = STARTUP_ITERATIONS,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
        check(device.wait(Until.hasObject(By.text("首页")), UI_TIMEOUT_MILLIS)) {
            "首页未在规定时间内完成渲染"
        }
    }

    @Test
    fun openLargeEpub() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = READER_ITERATIONS,
        setupBlock = {
            returnToLibrary()
            findBenchmarkBook()
        },
    ) {
        findBenchmarkBook().click()
        check(device.wait(Until.hasObject(By.desc(READER_CONTENT_DESCRIPTION)), READER_TIMEOUT_MILLIS)) {
            "Reader 未在规定时间内完成首章渲染"
        }
    }

    @Test
    fun turnPagesContinuously() = benchmarkRule.measureRepeated(
        packageName = PACKAGE_NAME,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        iterations = READER_ITERATIONS,
        setupBlock = {
            returnToLibrary()
            findBenchmarkBook().click()
            check(device.wait(Until.hasObject(By.desc(READER_CONTENT_DESCRIPTION)), READER_TIMEOUT_MILLIS)) {
                "Reader 未在规定时间内完成首章渲染"
            }
            device.waitForIdle()
        },
    ) {
        repeat(PAGE_TURN_COUNT) {
            device.swipe(
                device.displayWidth * 4 / 5,
                device.displayHeight / 2,
                device.displayWidth / 5,
                device.displayHeight / 2,
                PAGE_TURN_STEPS,
            )
            device.waitForIdle()
        }
    }

    private fun returnToLibrary() {
        pressHomeAndStart()
        if (device.wait(Until.hasObject(bookSelector()), SHORT_TIMEOUT_MILLIS)) return
        repeat(MAX_BACK_ATTEMPTS) {
            device.pressBack()
            if (device.wait(Until.hasObject(bookSelector()), SHORT_TIMEOUT_MILLIS)) return
        }
        error("无法返回包含基准测试书籍的书库页")
    }

    private fun pressHomeAndStart() {
        device.pressHome()
        device.executeShellCommand("am start -W $PACKAGE_NAME/.MainActivity")
        device.waitForIdle()
    }

    private fun findBenchmarkBook(): UiObject2 = device.wait(
        Until.findObject(bookSelector()),
        UI_TIMEOUT_MILLIS,
    ) ?: error(
        benchmarkBookTitle?.let { "书库中没有名为“$it”的基准测试 EPUB" }
            ?: "设备中没有可用于性能测试的已导入书籍",
    )

    private fun bookSelector() = benchmarkBookTitle
        ?.takeIf(String::isNotBlank)
        ?.let { By.desc("$OPEN_BOOK_DESCRIPTION_PREFIX$it") }
        ?: By.descStartsWith(OPEN_BOOK_DESCRIPTION_PREFIX)

    private companion object {
        const val PACKAGE_NAME = "com.kixyu9527.kixyubook"
        const val BOOK_TITLE_ARGUMENT = "benchmarkBookTitle"
        const val OPEN_BOOK_DESCRIPTION_PREFIX = "打开书籍："
        const val READER_CONTENT_DESCRIPTION = "阅读正文"
        const val UI_TIMEOUT_MILLIS = 10_000L
        const val SHORT_TIMEOUT_MILLIS = 2_000L
        const val READER_TIMEOUT_MILLIS = 30_000L
        const val STARTUP_ITERATIONS = 10
        const val READER_ITERATIONS = 8
        const val PAGE_TURN_COUNT = 20
        const val PAGE_TURN_STEPS = 12
        const val MAX_BACK_ATTEMPTS = 3
    }
}
