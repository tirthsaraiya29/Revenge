package com.revenge.dpi

import android.content.Context
import android.util.Log

/**
 * Manifest-registered provider entry point. Reads the persisted density ratio and hands it to
 * [DensityConfiguration]; a missing or malformed value leaves the process density untouched.
 */
class DensityScaler : BaseLifecycleContentProvider() {

    override fun onCreate(): Boolean {
        val providerContext = context ?: return false
        try {
            val preferences = providerContext
                .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
            val scale = try {
                preferences.getFloat(DENSITY_SCALE_KEY, DEFAULT_DENSITY_SCALE)
            } catch (t: Throwable) {
                Log.w(TAG, "Invalid density scale preference, using default", t)
                DEFAULT_DENSITY_SCALE
            }
            DensityConfiguration(scale).applyDensityScaling(providerContext)
            return true
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to initialise density scaling", t)
            return true
        }
    }

    internal companion object {
        const val PREFERENCES_NAME = "revenge_settings"
        const val DENSITY_SCALE_KEY = "density_scale_factor"
        private const val DEFAULT_DENSITY_SCALE = 1.0f
        private const val TAG = "DensityScaler"
    }
}
