package com.revenge.music.utils

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.revenge.music.BuildConfig
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide uncaught exception handler. It serialises the crash, launches [CrashActivity]
 * in the dedicated `:crash` process and then terminates the current process. Every step is
 * best-effort so a failure inside the handler can never recurse or mask the original crash.
 */
object CrashHandler {

    /** Intent extra read by `CrashActivity` to render the captured log. */
    const val EXTRA_CRASH_LOG = "crash_log"

    private const val TAG = "CrashHandler"
    private const val CRASH_ACTIVITY_CLASS = "com.revenge.music.ui.screens.CrashActivity"

    private val installed = AtomicBoolean(false)
    private val handlingCrash = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // A second thread crashing while we are already reporting must not start a second
            // crash screen; the first reporter terminates the process.
            if (!handlingCrash.compareAndSet(false, true)) return@setDefaultUncaughtExceptionHandler

            try {
                launchCrashScreen(appContext, buildCrashLog(thread, throwable))
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to show the crash screen", t)
                try {
                    previousHandler?.uncaughtException(thread, throwable)
                } catch (ignored: Throwable) {
                    Log.e(TAG, "The previous uncaught exception handler also failed", ignored)
                }
            }

            android.os.Process.killProcess(android.os.Process.myPid())
            kotlin.system.exitProcess(10)
        }
    }

    private fun launchCrashScreen(context: Context, crashLog: String) {
        val intent = Intent().apply {
            setClassName(context.packageName, CRASH_ACTIVITY_CLASS)
            putExtra(EXTRA_CRASH_LOG, crashLog)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
    }

    private fun buildCrashLog(thread: Thread, throwable: Throwable): String {
        val stackTrace = StringWriter().also { writer ->
            PrintWriter(writer).use { throwable.printStackTrace(it) }
        }.toString()

        return buildString {
            appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}, API ${Build.VERSION.SDK_INT}")
            appendLine("Thread: ${thread.name}")
            appendLine()
            append(stackTrace)
        }
    }
}
