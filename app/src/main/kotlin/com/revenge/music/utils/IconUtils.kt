package com.revenge.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconType(val value: Int) {
    DEFAULT(0),
    STATIC(2),
}

/** Switches the enabled launcher alias; exactly one alias stays enabled after every call. */
object IconUtils {
    private const val ALIAS = "com.revenge.music.MainActivityAlias"
    private const val STATIC = "com.revenge.music.MainActivityStatic"

    fun setIcon(context: Context, iconType: AppIconType) {
        val useStatic = iconType == AppIconType.STATIC
        val pm = context.packageManager
        setEnabled(pm, context, STATIC, useStatic)
        setEnabled(pm, context, ALIAS, !useStatic)
    }

    private fun setEnabled(
        pm: PackageManager,
        context: Context,
        className: String,
        enabled: Boolean,
    ) {
        pm.setComponentEnabledSetting(
            ComponentName(context, className),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            PackageManager.DONT_KILL_APP,
        )
    }
}
