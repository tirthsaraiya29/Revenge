package com.revenge.dpi

import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.util.Log
import kotlin.math.roundToInt

/**
 * Applies a fixed density ratio to the process resources and re-applies it to every new
 * activity. All recalculation derives from the captured original density, so repeated
 * invocations are idempotent.
 */
internal class DensityConfiguration(private val densityScale: Float) : ActivityLifecycleManager() {

    private var originalDensityDpi: Int = 0

    fun applyDensityScaling(context: Context) {
        if (densityScale == 1.0f) return
        try {
            onCreate()
            val resources = context.resources
            originalDensityDpi = resources.configuration.densityDpi
            applyTo(resources)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to apply density scaling", t)
        }
    }

    override fun onActivityCreated(activity: Activity) = reapply(activity)

    override fun onActivityStarted(activity: Activity) = reapply(activity)

    override fun onActivityResumed(activity: Activity) = reapply(activity)

    private fun reapply(activity: Activity) {
        if (originalDensityDpi <= 0) return
        try {
            applyTo(activity.resources)
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to re-apply density scaling", t)
        }
    }

    @Suppress("DEPRECATION")
    private fun applyTo(resources: Resources) {
        val configuration = resources.configuration
        configuration.densityDpi = (originalDensityDpi * densityScale).roundToInt()
        resources.updateConfiguration(configuration, resources.displayMetrics)
    }

    private companion object {
        const val TAG = "DensityConfiguration"
    }
}
