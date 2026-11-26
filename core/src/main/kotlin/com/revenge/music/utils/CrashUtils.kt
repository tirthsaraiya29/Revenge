package com.revenge.music.utils

import timber.log.Timber

/** Set by the application to forward caught exceptions to the crash reporter. */
var exceptionReporter: ((Throwable) -> Unit)? = null

fun reportException(throwable: Throwable) {
  Timber.e(throwable)
  exceptionReporter?.invoke(throwable)
}
