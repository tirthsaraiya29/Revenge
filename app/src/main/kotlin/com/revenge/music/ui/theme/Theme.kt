package com.revenge.music.ui.theme

import android.graphics.Bitmap
import android.graphics.Typeface as AndroidTypeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import com.revenge.core.constants.AppFont
import com.revenge.core.constants.CustomFontPathKey
import com.revenge.core.constants.SelectedFontKey
import com.revenge.music.utils.rememberPreference
import java.io.File

/** Sentinel meaning "follow the Android system palette". Never used as a visual seed. */
val DefaultThemeColor = Color(0xFF6C4DF6)

/** Fresh-install seed, identical to [RevengeColor.BrandCrimson]. */
val BrandThemeColor = Color(0xFFD6413B)

private val MonochromeSeed = Color(0xFF808080)
private val ThemeColorFallback = Color(0xFF4285F4)
private val GradientFallbackStart = Color(0xFF595959)
private val GradientFallbackEnd = Color(0xFF0D0D0D)

private val RevengeShapes = Shapes(
  extraSmall = RoundedCornerShape(RevengeRadius.xs),
  small = RoundedCornerShape(RevengeRadius.sm),
  medium = RoundedCornerShape(RevengeRadius.md),
  large = RoundedCornerShape(RevengeRadius.lg),
  extraLarge = RoundedCornerShape(RevengeRadius.xl),
)

/**
 * Applies the Revenge theme once at the activity root. Font preferences are read inside the
 * composable, so a font change recomposes the theme without recreating the activity.
 */
@Composable
fun revengeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  pureBlack: Boolean = false,
  isMonochrome: Boolean = false,
  themeColor: Color = BrandThemeColor,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val selectedFont = rememberPreference(
    key = SelectedFontKey,
    defaultValue = AppFont.PLUS_JAKARTA_SANS.value,
  ).value
  val customFontPath = rememberPreference(
    key = CustomFontPathKey,
    defaultValue = "",
  ).value

  val fontFamily = remember(selectedFont, customFontPath) {
    resolveFontFamily(selectedFont, customFontPath)
  }

  val baseScheme: ColorScheme = when {
    isMonochrome -> rememberDynamicColorScheme(
      seedColor = MonochromeSeed,
      isDark = darkTheme,
      style = PaletteStyle.Monochrome,
      specVersion = ColorSpec.SpecVersion.SPEC_2025,
    ).toGrayscaleColorScheme()

    themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

    else -> rememberDynamicColorScheme(
      // The sentinel is never painted; on API < 31 it falls back to the brand seed.
      seedColor = if (themeColor == DefaultThemeColor) BrandThemeColor else themeColor,
      isDark = darkTheme,
      style = PaletteStyle.TonalSpot,
      specVersion = ColorSpec.SpecVersion.SPEC_2025,
    )
  }

  // Monochrome is already a complete grayscale scheme and must not be tinted by the dark remap.
  val colorScheme = if (darkTheme && !isMonochrome) {
    baseScheme.toProfessionalDarkColorScheme(pureBlack)
  } else {
    baseScheme
  }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = getTypography(fontFamily),
    shapes = RevengeShapes,
    content = content,
  )
}

/** Remaps a dark dynamic scheme onto the neutral Revenge surfaces. */
fun ColorScheme.toProfessionalDarkColorScheme(pureBlack: Boolean): ColorScheme =
  if (pureBlack) {
    copy(
      surface = Color.Black,
      background = Color.Black,
      surfaceContainerLowest = Color.Black,
      surfaceContainerLow = Color(0xFF0C0D10),
      surfaceContainer = Color(0xFF13151A),
      surfaceContainerHigh = Color(0xFF1B1D24),
      surfaceContainerHighest = Color(0xFF242730),
      outline = Color(0xFF383C48),
      outlineVariant = Color(0xFF252830),
    )
  } else {
    copy(
      surface = RevengeColor.DarkCanvas,
      background = RevengeColor.DarkCanvas,
      surfaceContainerLowest = Color(0xFF0E0F14),
      surfaceContainerLow = RevengeColor.DarkSurfaceSubtle,
      surfaceContainer = RevengeColor.DarkSurfaceCard,
      surfaceContainerHigh = RevengeColor.DarkSurfaceElevated,
      surfaceContainerHighest = RevengeColor.DarkSurfaceHighlight,
      outline = Color(0xFF383C4A),
      outlineVariant = Color(0xFF252833),
    )
  }

/** Alias for [toProfessionalDarkColorScheme]; returns exactly the same scheme. */
fun ColorScheme.pureBlack(apply: Boolean): ColorScheme =
  toProfessionalDarkColorScheme(apply)

/** Keeps every role's alpha while removing its HSL saturation. */
fun ColorScheme.toGrayscaleColorScheme(): ColorScheme = copy(
  primary = primary.toGrayscale(),
  onPrimary = onPrimary.toGrayscale(),
  primaryContainer = primaryContainer.toGrayscale(),
  onPrimaryContainer = onPrimaryContainer.toGrayscale(),
  inversePrimary = inversePrimary.toGrayscale(),
  secondary = secondary.toGrayscale(),
  onSecondary = onSecondary.toGrayscale(),
  secondaryContainer = secondaryContainer.toGrayscale(),
  onSecondaryContainer = onSecondaryContainer.toGrayscale(),
  tertiary = tertiary.toGrayscale(),
  onTertiary = onTertiary.toGrayscale(),
  tertiaryContainer = tertiaryContainer.toGrayscale(),
  onTertiaryContainer = onTertiaryContainer.toGrayscale(),
  background = background.toGrayscale(),
  onBackground = onBackground.toGrayscale(),
  surface = surface.toGrayscale(),
  onSurface = onSurface.toGrayscale(),
  surfaceVariant = surfaceVariant.toGrayscale(),
  onSurfaceVariant = onSurfaceVariant.toGrayscale(),
  surfaceTint = surfaceTint.toGrayscale(),
  inverseSurface = inverseSurface.toGrayscale(),
  inverseOnSurface = inverseOnSurface.toGrayscale(),
  error = error.toGrayscale(),
  onError = onError.toGrayscale(),
  errorContainer = errorContainer.toGrayscale(),
  onErrorContainer = onErrorContainer.toGrayscale(),
  outline = outline.toGrayscale(),
  outlineVariant = outlineVariant.toGrayscale(),
  scrim = scrim.toGrayscale(),
  surfaceBright = surfaceBright.toGrayscale(),
  surfaceDim = surfaceDim.toGrayscale(),
  surfaceContainer = surfaceContainer.toGrayscale(),
  surfaceContainerHigh = surfaceContainerHigh.toGrayscale(),
  surfaceContainerHighest = surfaceContainerHighest.toGrayscale(),
  surfaceContainerLow = surfaceContainerLow.toGrayscale(),
  surfaceContainerLowest = surfaceContainerLowest.toGrayscale(),
)

val ColorSaver: Saver<Color, Int> = Saver(
  save = { it.toArgb() },
  restore = { Color(it) },
)

/** Picks the most theme-suitable colour of a small palette. */
fun Bitmap.extractThemeColor(): Color {
  val palette = Palette.from(this).maximumColorCount(8).generate()
  val population = palette.swatches.associate { it.rgb to it.population }
  if (population.isEmpty()) return ThemeColorFallback
  return Score.score(population).firstOrNull()?.let { Color(it) } ?: ThemeColorFallback
}

/** Picks the two most luminous, theme-suitable colours of a larger palette. */
fun Bitmap.extractGradientColors(): List<Color> {
  val palette = Palette.from(this).maximumColorCount(64).generate()
  val population = palette.swatches.associate { it.rgb to it.population }
  val ranked = Score.score(
    colorsToPopulation = population,
    desired = 2,
    fallbackColorArgb = 0xff4285f4.toInt(),
    filter = true,
  ).map { Color(it) }

  val sorted = ranked.sortedByDescending { it.luminance() }
  return if (sorted.size >= 2) sorted.take(2) else listOf(GradientFallbackStart, GradientFallbackEnd)
}

private fun resolveFontFamily(
  selectedFont: String,
  customFontPath: String,
): FontFamily = when (AppFont.fromValue(selectedFont)) {
  AppFont.SYSTEM -> FontFamily.Default
  AppFont.GOOGLE_SANS -> GoogleSansFontFamily
  AppFont.SANS_FLEX -> SansFlexFontFamily
  AppFont.OUTFIT -> OutfitFontFamily
  AppFont.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
  AppFont.CUSTOM -> customTypefaceFamily(customFontPath)
}

private fun customTypefaceFamily(path: String): FontFamily {
  if (path.isBlank() || !File(path).exists()) return FontFamily.Default
  return runCatching {
    FontFamily(AndroidTypeface.createFromFile(File(path)))
  }.getOrDefault(FontFamily.Default)
}

/** HSL saturation = 0 leaves every channel equal to the original lightness. */
private fun Color.toGrayscale(): Color {
  val lightness = (maxOf(red, green, blue) + minOf(red, green, blue)) / 2f
  return Color(lightness, lightness, lightness, alpha)
}
