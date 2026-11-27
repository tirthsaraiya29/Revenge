package com.revenge.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Derives the three-stop artwork gradient used behind the player, mini-player and album headers.
 * Extraction is CPU-bound, so it always hops to [Dispatchers.Default] instead of running on the
 * caller's thread.
 */
object PlayerColorExtractor {

  object Config {
    const val MAX_COLOR_COUNT = 32
    const val BITMAP_AREA = 8000
    const val IMAGE_SIZE = 200

    const val VIBRANT_SATURATION_THRESHOLD = 0.25f
    const val VIBRANT_BRIGHTNESS_MIN = 0.2f
    const val VIBRANT_BRIGHTNESS_MAX = 0.9f

    const val POPULATION_WEIGHT_MULTIPLIER = 2f
    const val VIBRANCY_THRESHOLD_SATURATION = 0.3f
    const val VIBRANCY_THRESHOLD_BRIGHTNESS = 0.3f
    const val VIBRANCY_BONUS = 1.5f

    const val DEFAULT_SATURATION_FACTOR = 1.4f
    const val VIBRANT_SATURATION_FACTOR = 1.3f
    const val FALLBACK_SATURATION_FACTOR = 1.1f

    const val BRIGHTNESS_MULTIPLIER = 0.9f
    const val BRIGHTNESS_MIN = 0.4f
    const val BRIGHTNESS_MAX = 0.85f

    const val DARKER_VARIANT_FACTOR = 0.6f
  }

  /**
   * Returns exactly three colours: the enhanced primary, a 60%-darker variant, and black. The
   * result is never empty and never throws, even for a palette with no usable swatch.
   */
  suspend fun extractGradientColors(
    palette: Palette,
    fallbackColor: Int,
  ): List<Color> =
    withContext(Dispatchers.Default) {
      val candidates =
        listOfNotNull(
          palette.dominantSwatch,
          palette.vibrantSwatch,
          palette.darkVibrantSwatch,
          palette.lightVibrantSwatch,
          palette.mutedSwatch,
          palette.darkMutedSwatch,
          palette.lightMutedSwatch,
        )

      val best = candidates.maxByOrNull { weight(it) }
      val fallbackDominant = palette.dominantSwatch?.rgb ?: palette.getDominantColor(fallbackColor)

      val vibrantBest = best?.takeIf {
        val hsl = it.hsl
        hsl[1] > Config.VIBRANT_SATURATION_THRESHOLD &&
          hsl[2] > Config.VIBRANT_BRIGHTNESS_MIN &&
          hsl[2] < Config.VIBRANT_BRIGHTNESS_MAX
      }

      val baseColor = vibrantBest?.rgb ?: fallbackDominant
      val saturationFactor =
        if (vibrantBest != null) {
          Config.VIBRANT_SATURATION_FACTOR
        } else {
          Config.FALLBACK_SATURATION_FACTOR
        }

      val primary = enhance(baseColor, saturationFactor)
      val darker =
        Color(
          red = primary.red * Config.DARKER_VARIANT_FACTOR,
          green = primary.green * Config.DARKER_VARIANT_FACTOR,
          blue = primary.blue * Config.DARKER_VARIANT_FACTOR,
          alpha = 1f,
        )

      listOf(primary, darker, Color.Black)
    }

  private fun weight(swatch: Palette.Swatch): Float {
    val hsl = swatch.hsl
    val saturation = hsl[1]
    val brightness = hsl[2]
    val vibrancyBonus =
      if (
        saturation > Config.VIBRANCY_THRESHOLD_SATURATION &&
          brightness > Config.VIBRANCY_THRESHOLD_BRIGHTNESS
      ) {
        Config.VIBRANCY_BONUS
      } else {
        1f
      }

    return swatch.population *
      Config.POPULATION_WEIGHT_MULTIPLIER *
      vibrancyBonus *
      (saturation + brightness) / 2f
  }

  private fun enhance(baseColor: Int, saturationFactor: Float): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(baseColor, hsl)
    hsl[1] = min(1f, hsl[1] * saturationFactor)
    hsl[2] =
      (hsl[2] * Config.BRIGHTNESS_MULTIPLIER).coerceIn(Config.BRIGHTNESS_MIN, Config.BRIGHTNESS_MAX)
    return Color(ColorUtils.HSLToColor(hsl))
  }
}
