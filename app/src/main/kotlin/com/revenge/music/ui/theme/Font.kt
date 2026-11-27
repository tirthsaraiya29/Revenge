@file:OptIn(ExperimentalTextApi::class)

package com.revenge.music.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.revenge.music.R

/** Axis of Sans Flex that controls the roundness of the glyph terminals. */
private const val RoundnessAxis = "ROND"

/** Variable-font width used by the Sans Flex family. */
private const val SansFlexWidth = 100f

/** Roundness value used by the Sans Flex family. */
private const val SansFlexRoundness = 100f

/**
 * Sans Flex is a two-axis variable font: weight plus an app-specific "ROND" axis that softens the
 * terminal shapes. Both sans families below intentionally share the same resource and axes.
 */
private fun sansFlexFont(weight: FontWeight, wght: Int): Font =
  Font(
    resId = R.font.sans_flex,
    weight = weight,
    variationSettings =
      FontVariation.Settings(
        FontVariation.weight(wght),
        FontVariation.width(SansFlexWidth),
        FontVariation.Setting(RoundnessAxis, SansFlexRoundness),
      ),
  )

private fun variableFont(resId: Int, weight: FontWeight, wght: Int): Font =
  Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(wght)),
  )

val SansFlexFontFamily: FontFamily =
  FontFamily(
    sansFlexFont(FontWeight.Normal, 400),
    sansFlexFont(FontWeight.Medium, 500),
    sansFlexFont(FontWeight.Bold, 700),
  )

/** Behavioural alias of [SansFlexFontFamily]; the brand "Google Sans" is the bundled Sans Flex. */
val GoogleSansFontFamily: FontFamily = SansFlexFontFamily

val OutfitFontFamily: FontFamily =
  FontFamily(
    variableFont(R.font.outfit, FontWeight.Normal, 400),
    variableFont(R.font.outfit, FontWeight.Medium, 500),
    variableFont(R.font.outfit, FontWeight.Bold, 700),
  )

val PlusJakartaSansFontFamily: FontFamily =
  FontFamily(
    variableFont(R.font.plus_jakarta_sans, FontWeight.Normal, 400),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Medium, 500),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Bold, 700),
  )
