package com.revenge.music.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

enum class AppIconType(val value: Int) {
  DEFAULT(0),
  LEGACY(1),
  STATIC(2),
  CAT(3),
  CRAZY_BLUE(4),
  POOKIE(5),
  SKY(6),
  REVENGE_CAT(7),
  BILLIE_EILISH(13),
  SABRINA_CARPENTER(14),
  SABRINA_CARPENTER_2(15),
  CRAFT(16),
  SADIE(17),
  NEON_GLASS(18),
  MILLIE_BOBBY_BROWN(19),
}

object IconUtils {
  fun setIcon(context: Context, iconType: AppIconType) {
    val pm = context.packageManager

    val dynamic = ComponentName(context, "com.revenge.music.MainActivityAlias")
    val legacy = ComponentName(context, "com.revenge.music.MainActivityLegacy")
    val static = ComponentName(context, "com.revenge.music.MainActivityStatic")
    val cat = ComponentName(context, "com.revenge.music.MainActivityCat")
    val crazyBlue = ComponentName(context, "com.revenge.music.MainActivityCrazyBlue")
    val pookie = ComponentName(context, "com.revenge.music.MainActivityPookie")
    val sky = ComponentName(context, "com.revenge.music.MainActivitySky")
    val catIcon = ComponentName(context, "com.revenge.music.MainActivityRevengeCat")
    val billieEilish = ComponentName(context, "com.revenge.music.MainActivityBillieEilish")
    val sabrina = ComponentName(context, "com.revenge.music.MainActivitySabrina")
    val sabrina2 = ComponentName(context, "com.revenge.music.MainActivitySabrina2")
    val craft = ComponentName(context, "com.revenge.music.MainActivityCraft")
    val sadie = ComponentName(context, "com.revenge.music.MainActivitySadie")
    val neonGlass = ComponentName(context, "com.revenge.music.MainActivityNeonGlass")
    val millie = ComponentName(context, "com.revenge.music.MainActivityMillie")

    pm.setComponentEnabledSetting(
      dynamic,
      if (iconType == AppIconType.DEFAULT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      legacy,
      if (iconType == AppIconType.LEGACY) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      static,
      if (iconType == AppIconType.STATIC) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      cat,
      if (iconType == AppIconType.CAT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      crazyBlue,
      if (iconType == AppIconType.CRAZY_BLUE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      pookie,
      if (iconType == AppIconType.POOKIE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      sky,
      if (iconType == AppIconType.SKY) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      catIcon,
      if (iconType == AppIconType.REVENGE_CAT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      billieEilish,
      if (iconType == AppIconType.BILLIE_EILISH) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      sabrina,
      if (iconType == AppIconType.SABRINA_CARPENTER) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      sabrina2,
      if (iconType == AppIconType.SABRINA_CARPENTER_2)
        PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      craft,
      if (iconType == AppIconType.CRAFT) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      sadie,
      if (iconType == AppIconType.SADIE) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      neonGlass,
      if (iconType == AppIconType.NEON_GLASS) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
    pm.setComponentEnabledSetting(
      millie,
      if (iconType == AppIconType.MILLIE_BOBBY_BROWN) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
      else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
      PackageManager.DONT_KILL_APP,
    )
  }
}
