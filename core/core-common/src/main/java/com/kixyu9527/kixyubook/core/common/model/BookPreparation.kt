package com.kixyu9527.kixyubook.core.common.model

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

enum class BookPreparationStage {
    WAITING_SYNC, AUTHORIZATION, LOCATING_SOURCE, DOWNLOADING, VERIFYING,
    READING_METADATA, PREPARING_DIRECTORY, RESTORING_PROGRESS, READY,
}

data class BookPreparationProgress(
    val stage: BookPreparationStage,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
)

/** The install and reader share progress without making the database depend on cloud/UI modules. */
class BookPreparationObserver(
    val report: (BookPreparationProgress) -> Unit,
    val retry: (Int, Int?) -> Unit = { _, _ -> },
    val describe: (BookFormat, Long, Boolean, Int) -> Unit = { _, _, _, _ -> },
) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<BookPreparationObserver>
}

suspend fun reportBookPreparation(stage: BookPreparationStage) {
    coroutineContext[BookPreparationObserver]?.report?.invoke(BookPreparationProgress(stage))
}

suspend fun reportBookSource(format: BookFormat, bytes: Long, localFile: Boolean, directoryChapters: Int) {
    coroutineContext[BookPreparationObserver]?.describe?.invoke(format, bytes, localFile, directoryChapters)
}
