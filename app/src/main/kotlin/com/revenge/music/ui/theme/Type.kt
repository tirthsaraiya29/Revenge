package com.revenge.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.revenge.music.R

@OptIn(ExperimentalTextApi::class)
val GoogleSansFontFamily =
  FontFamily(
    Font(
      resId = R.font.sans_flex,
      weight = FontWeight.Normal,
      variationSettings =
        FontVariation.Settings(
          FontVariation.weight(400),
          FontVariation.width(100f),
          FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
      resId = R.font.sans_flex,
      weight = FontWeight.Medium,
      variationSettings =
        FontVariation.Settings(
          FontVariation.weight(500),
          FontVariation.width(100f),
          FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
      resId = R.font.sans_flex,
      weight = FontWeight.Bold,
      variationSettings =
        FontVariation.Settings(
          FontVariation.weight(700),
          FontVariation.width(100f),
          FontVariation.Setting("ROND", 100f),
        ),
    ),
  )

@OptIn(ExperimentalTextApi::class)
val SansFlexFontFamily =
  FontFamily(
    Font(
      resId = R.font.sans_flex,
      weight = FontWeight.Normal,
      variationSettings =
        FontVariation.Settings(
          FontVariation.weight(400),
          FontVariation.width(100f),
          FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
      resId = R.font.sans_flex,
      weight = FontWeight.Medium,
      variationSettings =
        FontVariation.Settings(
          FontVariation.weight(500),
          FontVariation.width(100f),
          FontVariation.Setting("ROND", 100f),
        ),
    ),
    Font(
      resId = R.font.sans_flex,
      weight = FontWeight.Bold,
      variationSettings =
        FontVariation.Settings(
          FontVariation.weight(700),
          FontVariation.width(100f),
          FontVariation.Setting("ROND", 100f),
        ),
    ),
  )

@OptIn(ExperimentalTextApi::class)
val OutfitFontFamily =
  FontFamily(
    Font(
      resId = R.font.outfit,
      weight = FontWeight.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
      resId = R.font.outfit,
      weight = FontWeight.Medium,
      variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
      resId = R.font.outfit,
      weight = FontWeight.Bold,
      variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
  )

@OptIn(ExperimentalTextApi::class)
val PlusJakartaSansFontFamily =
  FontFamily(
    Font(
      resId = R.font.plus_jakarta_sans,
      weight = FontWeight.Normal,
      variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
      resId = R.font.plus_jakarta_sans,
      weight = FontWeight.Medium,
      variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
      resId = R.font.plus_jakarta_sans,
      weight = FontWeight.Bold,
      variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
  )

/**
 * One type ramp for the whole product: a compact display scale for page identity, a restrained
 * title scale for sections and rows, and labels with tabular digits for times and counters.
 * Body and title share the selected brand font so screens do not mix two families.
 */
@OptIn(ExperimentalTextApi::class)
fun getTypography(brandFont: FontFamily, plainFont: FontFamily = brandFont): Typography =
  Typography(
    displayLarge =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = (-0.5).sp,
      ),
    displayMedium =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.4).sp,
      ),
    displaySmall =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.3).sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.15).sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.1).sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = plainFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = plainFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = plainFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = brandFont,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.2.sp,
        fontFeatureSettings = "tnum",
      ),
  )
