package com.revenge.dpi

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Provider-based lifecycle observer. Once [onCreate] resolves the process [Application],
 * it registers for activity lifecycle events and drives a periodic timer while at least
 * one activity is resumed.
 */
abstract class ActivityLifecycleManager : BaseLifecycleContentProvider() {

    private val resumedActivities = CopyOnWriteArraySet<Activity>()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var timerScheduled = false
    private var callbacksRegistered = false

    private val timer = object : Runnable {
        override fun run() {
            if (!timerScheduled) return
            for (activity in resumedActivities) {
                try {
                    onActivityTimer(activity)
                } catch (t: Throwable) {
                    Log.w(TAG, "Activity timer callback failed", t)
                }
            }
            if (timerScheduled && resumedActivities.isNotEmpty()) {
                scheduleNextTick()
            } else {
                timerScheduled = false
            }
        }
    }

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            safeHook { this@ActivityLifecycleManager.onActivityCreated(activity) }
        }

        override fun onActivityStarted(activity: Activity) {
            safeHook { this@ActivityLifecycleManager.onActivityStarted(activity) }
        }

        override fun onActivityResumed(activity: Activity) {
            resumedActivities.add(activity)
            startTimer()
            safeHook { this@ActivityLifecycleManager.onActivityResumed(activity) }
        }

        override fun onActivityPaused(activity: Activity) {
            safeHook { this@ActivityLifecycleManager.onActivityPaused(activity) }
            resumedActivities.remove(activity)
            if (resumedActivities.isEmpty()) stopTimer()
        }

        override fun onActivityStopped(activity: Activity) {
            safeHook { this@ActivityLifecycleManager.onActivityStopped(activity) }
            resumedActivities.remove(activity)
            if (resumedActivities.isEmpty()) stopTimer()
        }

        override fun onActivityDestroyed(activity: Activity) {
            safeHook { this@ActivityLifecycleManager.onActivityDestroyed(activity) }
            resumedActivities.remove(activity)
            if (resumedActivities.isEmpty()) stopTimer()
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    }

    override fun onCreate(): Boolean {
        if (callbacksRegistered) return true
        try {
            val application = currentApplication()
            if (application != null && onInit(application)) {
                application.registerActivityLifecycleCallbacks(lifecycleCallbacks)
                callbacksRegistered = true
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to register activity lifecycle callbacks", t)
        }
        return true
    }

    protected open fun onInit(application: Application): Boolean = true

    protected open fun onActivityCreated(activity: Activity) = Unit

    protected open fun onActivityStarted(activity: Activity) = Unit

    protected open fun onActivityResumed(activity: Activity) = Unit

    protected open fun onActivityPaused(activity: Activity) = Unit

    protected open fun onActivityStopped(activity: Activity) = Unit

    protected open fun onActivityDestroyed(activity: Activity) = Unit

    protected open fun onActivityTimer(activity: Activity) = Unit

    protected open val activityTimerDelayMillis: Long = 3_000L

    private fun safeHook(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            Log.w(TAG, "Activity lifecycle callback failed", t)
        }
    }

    private fun startTimer() {
        if (timerScheduled) return
        timerScheduled = true
        scheduleNextTick()
    }

    private fun scheduleNextTick() {
        try {
            mainHandler.postDelayed(timer, activityTimerDelayMillis)
        } catch (t: Throwable) {
            timerScheduled = false
            Log.w(TAG, "Unable to schedule the activity timer", t)
        }
    }

    private fun stopTimer() {
        timerScheduled = false
        try {
            mainHandler.removeCallbacks(timer)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to cancel the activity timer", t)
        }
    }

    /**
     * Resolves the process Application through the hidden ActivityThread API. Returns null
     * (instead of throwing) when reflection is unavailable, e.g. on heavily restricted runtimes.
     */
    private fun currentApplication(): Application? {
        return try {
            val activityThread = Class.forName("android.app.ActivityThread")
            val instance = activityThread.getMethod("currentActivityThread").invoke(null)
                ?: return null
            activityThread.getMethod("getApplication").invoke(instance) as? Application
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to resolve the Application instance", t)
            null
        }
    }

    private companion object {
        const val TAG = "ActivityLifecycleManager"
    }
}
