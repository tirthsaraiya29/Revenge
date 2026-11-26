package com.revenge.music.extensions

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect as collectFlow
import kotlinx.coroutines.flow.collectLatest as collectLatestFlow
import kotlinx.coroutines.launch

fun <T> Flow<T>.collect(scope: CoroutineScope, action: suspend (value: T) -> Unit) {
    scope.launch { collectFlow(action) }
}

fun <T> Flow<T>.collectLatest(scope: CoroutineScope, action: suspend (value: T) -> Unit) {
    scope.launch { collectLatestFlow(action) }
}

val SilentHandler: CoroutineExceptionHandler = CoroutineExceptionHandler { _, _ -> }
