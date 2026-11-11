package com.kixyu9527.kixyubook.core.sync

import android.content.Context
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DriveParallelDownloadTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test
    fun largeDownloadUsesParallelRangesAndAssemblesTheOriginalBytes() = runBlocking(Dispatchers.IO) {
        val content = ByteArray(4 * 1024 * 1024 + 123) { (it % 251).toByte() }
        RangeServer(content).use { server ->
            val client = DriveAppDataClient(context, null, server.baseUrl)
            val target = File(context.cacheDir, "parallel-download.bin").apply { delete() }
            val progress = AtomicLong(0)
            try {
                client.download(
                    token = "test-token",
                    fileId = "source",
                    destination = target,
                    onProgress = { progress.set(it) },
                    expectedSize = content.size.toLong(),
                    contentVersion = 7,
                )
                assertArrayEquals(content, target.readBytes())
                assertTrue("expected parallel requests, got ${server.requests.get()}", server.requests.get() > 1)
                assertTrue("ranges must overlap, peak was ${server.maxActive.get()}", server.maxActive.get() > 1)
                assertEquals(content.size.toLong(), progress.get())
            } finally {
                target.delete()
            }
        }
    }

    @Test
    fun smallDownloadStaysOnOneRequest() = runBlocking(Dispatchers.IO) {
        val content = ByteArray(256 * 1024) { (it % 97).toByte() }
        RangeServer(content).use { server ->
            val client = DriveAppDataClient(context, null, server.baseUrl)
            val target = File(context.cacheDir, "small-download.bin").apply { delete() }
            try {
                client.download("test-token", "source", target, expectedSize = content.size.toLong())
                assertArrayEquals(content, target.readBytes())
                assertEquals(1, server.requests.get())
            } finally {
                target.delete()
            }
        }
    }

    @Test
    fun anExistingSegmentResumesFromItsOwnOffset() = runBlocking(Dispatchers.IO) {
        val content = ByteArray(4 * 1024 * 1024 + 123) { (it % 251).toByte() }
        RangeServer(content).use { server ->
            val client = DriveAppDataClient(context, null, server.baseUrl)
            val target = File(context.cacheDir, "resumed-download.bin").apply { delete() }
            val first = planDownloadSegments(content.size.toLong()).first()
            val parts = segmentDirectory(target, contentVersion = 9).apply { mkdirs() }
            val resumeBytes = 100
            File(parts, "0.part").writeBytes(content.copyOfRange(0, resumeBytes))
            try {
                client.download(
                    token = "test-token",
                    fileId = "source",
                    destination = target,
                    expectedSize = content.size.toLong(),
                    contentVersion = 9,
                )
                assertArrayEquals(content, target.readBytes())
                assertTrue(
                    "the first segment must request only the missing tail",
                    server.ranges.any { it == "bytes=$resumeBytes-${first.endExclusive - 1}" },
                )
                assertTrue(server.maxActive.get() > 1)
            } finally {
                target.delete()
                parts.deleteRecursively()
            }
        }
    }

    @Test
    fun aServerThatIgnoresRangeFallsBackToTheSingleStreamPath() = runBlocking(Dispatchers.IO) {
        val content = ByteArray(3 * 1024 * 1024 + 17) { (it % 199).toByte() }
        RangeServer(content, ignoreRange = true).use { server ->
            val client = DriveAppDataClient(context, null, server.baseUrl)
            val target = File(context.cacheDir, "fallback-download.bin").apply { delete() }
            try {
                client.download(
                    token = "test-token",
                    fileId = "source",
                    destination = target,
                    expectedSize = content.size.toLong(),
                    contentVersion = 3,
                )
                assertArrayEquals(content, target.readBytes())
                assertTrue(server.requests.get() > 1)
            } finally {
                target.delete()
            }
        }
    }

    @Test
    fun aSourceThatChangesAfterTheProbeFallsBackToAFullSingleStream() = runBlocking(Dispatchers.IO) {
        val content = ByteArray(3 * 1024 * 1024 + 17) { (it % 211).toByte() }
        RangeServer(content, rotateEtag = true).use { server ->
            val client = DriveAppDataClient(context, null, server.baseUrl)
            val target = File(context.cacheDir, "changed-source-download.bin").apply { delete() }
            try {
                client.download(
                    token = "test-token",
                    fileId = "source",
                    destination = target,
                    expectedSize = content.size.toLong(),
                    contentVersion = 4,
                )
                assertArrayEquals(content, target.readBytes())
                assertTrue("expected a probe plus a fallback request", server.requests.get() > 1)
            } finally {
                target.delete()
            }
        }
    }

    private fun segmentDirectory(destination: File, contentVersion: Long): File {
        val downloadKey = "source".hashCode().toUInt().toString(16)
        val destinationKey = destination.name.hashCode().toUInt().toString(16)
        return File(destination.parentFile, ".drive-$downloadKey-$destinationKey-v$contentVersion.parts")
    }

    private class RangeServer(
        private val content: ByteArray,
        private val ignoreRange: Boolean = false,
        private val rotateEtag: Boolean = false,
    ) : AutoCloseable {
        private val executor = Executors.newFixedThreadPool(8)
        private val active = AtomicInteger()
        private val etag = java.util.concurrent.atomic.AtomicReference("\"range-etag\"")
        val requests = AtomicInteger()
        val maxActive = AtomicInteger()
        val ranges = ConcurrentLinkedQueue<String>()
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/drive/v3/files/source", ::handle)
            this.executor = this@RangeServer.executor
            start()
        }
        val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/drive/v3"

        private fun handle(exchange: HttpExchange) {
            val requestNumber = requests.incrementAndGet()
            if (rotateEtag && requestNumber > 1) etag.set("\"changed\"")
            val requestedRange = exchange.requestHeaders.getFirst("Range")
            val ifRange = exchange.requestHeaders.getFirst("If-Range")
            val range = requestedRange.takeUnless {
                ignoreRange || ifRange != null && ifRange != etag.get()
            }
            ranges += requestedRange ?: "none"
            maxActive.accumulateAndGet(active.incrementAndGet(), ::maxOf)
            try {
                val start: Int
                val end: Int
                if (range == null) {
                    start = 0
                    end = content.lastIndex
                } else {
                    val bounds = range.removePrefix("bytes=").split('-')
                    start = bounds[0].toInt()
                    end = bounds[1].toIntOrNull()?.coerceAtMost(content.lastIndex) ?: content.lastIndex
                }
                val length = end - start + 1
                exchange.responseHeaders.add("Content-Range", "bytes $start-$end/${content.size}")
                exchange.responseHeaders.add("ETag", etag.get())
                // Keep responses alive long enough for the parallel lanes to overlap.
                Thread.sleep(50)
                exchange.sendResponseHeaders(if (range == null) 200 else 206, length.toLong())
                exchange.responseBody.use { it.write(content, start, length) }
            } finally {
                exchange.close()
                active.decrementAndGet()
            }
        }

        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }
}
