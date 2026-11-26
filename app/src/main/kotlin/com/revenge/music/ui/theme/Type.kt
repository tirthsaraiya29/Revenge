package com.revenge.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Builds the app type ramp. Brand roles (display, headline, title, label) use [brandFont]; the
 * reading roles (body) use [plainFont], which defaults to the brand face.
 *
 * No role sets a colour: colour is always inherited from the surrounding content colour.
 */
@OptIn(ExperimentalTextApi::class)
fun getTypography(
  brandFont: FontFamily,
  plainFont: FontFamily = brandFont,
): Typography = Typography(
  displayLarge = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Normal,
    fontSize = 44.sp,
    lineHeight = 52.sp,
    letterSpacing = (-0.5).sp,
  ),
  displayMedium = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Normal,
    fontSize = 36.sp,
    lineHeight = 44.sp,
    letterSpacing = (-0.4).sp,
  ),
  displaySmall = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Normal,
    fontSize = 30.sp,
    lineHeight = 38.sp,
    letterSpacing = (-0.3).sp,
  ),
  headlineLarge = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 28.sp,
    lineHeight = 34.sp,
    letterSpacing = (-0.2).sp,
  ),
  headlineMedium = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 24.sp,
    lineHeight = 30.sp,
    letterSpacing = (-0.15).sp,
  ),
  headlineSmall = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    letterSpacing = (-0.1).sp,
  ),
  titleLarge = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Medium,
    fontSize = 20.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.1).sp,
  ),
  titleMedium = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.SemiBold,
    fontSize = 16.sp,
    lineHeight = 22.sp,
    letterSpacing = 0.sp,
  ),
  titleSmall = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
  ),
  bodyLarge = TextStyle(
    fontFamily = plainFont,
    fontWeight = FontWeight.Normal,
    fontSize = 16.sp,
    lineHeight = 24.sp,
    letterSpacing = 0.sp,
  ),
  bodyMedium = TextStyle(
    fontFamily = plainFont,
    fontWeight = FontWeight.Normal,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
  ),
  bodySmall = TextStyle(
    fontFamily = plainFont,
    fontWeight = FontWeight.Normal,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.1.sp,
  ),
  labelLarge = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Medium,
    fontSize = 14.sp,
    lineHeight = 20.sp,
    letterSpacing = 0.sp,
  ),
  labelMedium = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    letterSpacing = 0.1.sp,
  ),
  labelSmall = TextStyle(
    fontFamily = brandFont,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
    letterSpacing = 0.2.sp,
    fontFeatureSettings = "tnum",
  ),
)
