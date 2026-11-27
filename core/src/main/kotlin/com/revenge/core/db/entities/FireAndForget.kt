package com.revenge.core.db.entities

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal val entityWorkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun launchBestEffort(block: suspend () -> Unit) {
  entityWorkScope.launch { runCatching { block() } }
}
