package com.revenge.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score
import com.revenge.music.constants.AppFont
import com.revenge.music.constants.SelectedFontKey
import com.revenge.music.utils.rememberPreference

/** Sentinel that asks the theme screen to follow the Android system palette. */
val DefaultThemeColor = Color(0xFF6C4DF6)

/** Revenge's own ember. Fresh installs start here instead of borrowing the device wallpaper. */
val BrandThemeColor = Color(0xFFD6413B)

@Composable
fun revengeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  pureBlack: Boolean = false,
  isMonochrome: Boolean = false,
  themeColor: Color = BrandThemeColor,
  content: @Composable () -> Unit,
) {
  val context = LocalContext.current
  val selectedFontValue by rememberPreference(SelectedFontKey, AppFont.PLUS_JAKARTA_SANS.value)
  val customFontPathValue by rememberPreference(com.revenge.music.constants.CustomFontPathKey, "")

  val brandFont =
    remember(selectedFontValue, customFontPathValue) {
      when (AppFont.fromValue(selectedFontValue)) {
        AppFont.SYSTEM -> FontFamily.Default
        AppFont.GOOGLE_SANS -> GoogleSansFontFamily
        AppFont.SANS_FLEX -> SansFlexFontFamily
        AppFont.OUTFIT -> OutfitFontFamily
        AppFont.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
        AppFont.CUSTOM -> {
          try {
            if (customFontPathValue.isNotEmpty() && java.io.File(customFontPathValue).exists()) {
              val typeface = android.graphics.Typeface.createFromFile(customFontPathValue)
              FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
            } else {
              FontFamily.Default
            }
          } catch (e: Exception) {
            FontFamily.Default
          }
        }
      }
    }

  val useSystemDynamicColor =
    !isMonochrome &&
      (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

  val baseColorScheme =
    if (isMonochrome) {
      val dynamicMono =
        rememberDynamicColorScheme(
          seedColor = Color(0xFF808080),
          isDark = darkTheme,
          specVersion = ColorSpec.SpecVersion.SPEC_2025,
          style = PaletteStyle.Monochrome,
        )
      remember(dynamicMono) { dynamicMono.toGrayscaleColorScheme() }
    } else if (useSystemDynamicColor) {
      if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
      rememberDynamicColorScheme(
        seedColor = themeColor,
        isDark = darkTheme,
        specVersion = ColorSpec.SpecVersion.SPEC_2025,
        style = PaletteStyle.TonalSpot,
      )
    }

  val colorScheme =
    remember(baseColorScheme, pureBlack, darkTheme, isMonochrome) {
      if (darkTheme) {
        baseColorScheme.toProfessionalDarkColorScheme(pureBlack)
      } else {
        baseColorScheme
      }
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = getTypography(brandFont),
    shapes =
      androidx.compose.material3.Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(RevengeRadius.xs),
        small = androidx.compose.foundation.shape.RoundedCornerShape(RevengeRadius.sm),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(RevengeRadius.md),
        large = androidx.compose.foundation.shape.RoundedCornerShape(RevengeRadius.lg),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(RevengeRadius.xl),
      ),
    content = content,
  )
}

fun Bitmap.extractThemeColor(): Color {
  val colorsToPopulation =
    Palette.from(this).maximumColorCount(8).generate().swatches.associate {
      it.rgb to it.population
    }
  val rankedColors = Score.score(colorsToPopulation)
  return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
  val extractedColors =
    Palette.from(this).maximumColorCount(64).generate().swatches.associate {
      it.rgb to it.population
    }

  val orderedColors =
    Score.score(extractedColors, 2, 0xff4285f4.toInt(), true).sortedByDescending {
      Color(it).luminance()
    }

  return if (orderedColors.size >= 2) listOf(Color(orderedColors[0]), Color(orderedColors[1]))
  else listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.toProfessionalDarkColorScheme(pureBlack: Boolean): ColorScheme {
  return if (pureBlack) {
    copy(
      surface = RevengeColor.DarkPureBlackCanvas,
      background = RevengeColor.DarkPureBlackCanvas,
      surfaceContainerLowest = RevengeColor.DarkPureBlackCanvas,
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
}

fun ColorScheme.pureBlack(apply: Boolean) = toProfessionalDarkColorScheme(apply)

fun ColorScheme.toGrayscaleColorScheme(): ColorScheme {
  fun convert(color: Color): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(color.toArgb(), hsl)
    hsl[1] = 0f
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl)).copy(alpha = color.alpha)
  }

  return copy(
    primary = convert(primary),
    onPrimary = convert(onPrimary),
    primaryContainer = convert(primaryContainer),
    onPrimaryContainer = convert(onPrimaryContainer),
    inversePrimary = convert(inversePrimary),
    secondary = convert(secondary),
    onSecondary = convert(onSecondary),
    secondaryContainer = convert(secondaryContainer),
    onSecondaryContainer = convert(onSecondaryContainer),
    tertiary = convert(tertiary),
    onTertiary = convert(onTertiary),
    tertiaryContainer = convert(tertiaryContainer),
    onTertiaryContainer = convert(onTertiaryContainer),
    background = convert(background),
    onBackground = convert(onBackground),
    surface = convert(surface),
    onSurface = convert(onSurface),
    surfaceVariant = convert(surfaceVariant),
    onSurfaceVariant = convert(onSurfaceVariant),
    surfaceTint = convert(surfaceTint),
    inverseSurface = convert(inverseSurface),
    inverseOnSurface = convert(inverseOnSurface),
    error = convert(error),
    onError = convert(onError),
    errorContainer = convert(errorContainer),
    onErrorContainer = convert(onErrorContainer),
    outline = convert(outline),
    outlineVariant = convert(outlineVariant),
    scrim = convert(scrim),
    surfaceBright = convert(surfaceBright),
    surfaceDim = convert(surfaceDim),
    surfaceContainer = convert(surfaceContainer),
    surfaceContainerHigh = convert(surfaceContainerHigh),
    surfaceContainerHighest = convert(surfaceContainerHighest),
    surfaceContainerLow = convert(surfaceContainerLow),
    surfaceContainerLowest = convert(surfaceContainerLowest),
  )
}

val ColorSaver =
  object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)

    override fun SaverScope.save(value: Color): Int = value.toArgb()
  }
