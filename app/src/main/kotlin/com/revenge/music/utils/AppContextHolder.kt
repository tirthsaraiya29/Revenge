package com.revenge.music.utils

import android.content.Context

/**
 * Process-wide application context handed to code that cannot receive one through DI
 * (workers, receivers and non-injected utilities).
 */
object AppContextHolder {
    lateinit var context: Context
        private set

    fun initialize(context: Context) {
        this.context = context.applicationContext
    }
}
