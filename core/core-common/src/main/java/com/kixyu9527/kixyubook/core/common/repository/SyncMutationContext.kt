package com.kixyu9527.kixyubook.core.common.repository

import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/** Local restore/remote application must not publish user mutations as a side effect. */
suspend fun syncMutationRecordingSuppressed(): Boolean = coroutineContext[SuppressionKey] != null

suspend fun <T> withoutRecordingSyncMutations(block: suspend () -> T): T = withContext(Suppression) { block() }

private object SuppressionKey : CoroutineContext.Key<Suppression>
private object Suppression : AbstractCoroutineContextElement(SuppressionKey)
