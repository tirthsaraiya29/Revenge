package com.revenge.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.revenge.core.constants.PlayerBackgroundStyle

object PlayerSliderColors {

  /**
   * Slider colours that stay legible over both themed surfaces and artwork-driven backgrounds.
   * Every non-default player background sits on artwork, so its inactive track is a translucent
   * white instead of a theme role.
   */
  @Composable
  fun getSliderColors(
    activeColor: Color,
    playerBackground: PlayerBackgroundStyle,
    useDarkTheme: Boolean,
  ): SliderColors {
    val inactiveColor = when {
      playerBackground != PlayerBackgroundStyle.DEFAULT -> Color.White.copy(alpha = 0.4f)
      useDarkTheme -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
      else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    return SliderDefaults.colors(
      thumbColor = activeColor,
      activeTrackColor = activeColor,
      activeTickColor = activeColor,
      inactiveTrackColor = inactiveColor,
      disabledThumbColor = activeColor,
      disabledActiveTrackColor = activeColor,
      disabledInactiveTrackColor = inactiveColor,
    )
  }
}
