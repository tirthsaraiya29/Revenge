// ThemeUtils.kt - Centralized theme helper functions
package com.revenge.music.utils

import com.revenge.music.ui.screens.settings.DarkMode

/**
 * Determines whether dark theme should be applied based on the user-selected [darkMode] and the
 * system dark theme state.
 */
fun shouldUseDarkTheme(darkMode: DarkMode, isSystemDark: Boolean): Boolean {
  return when (darkMode) {
    DarkMode.ON -> true
    DarkMode.OFF -> false
    DarkMode.AUTO -> isSystemDark
  }
}
