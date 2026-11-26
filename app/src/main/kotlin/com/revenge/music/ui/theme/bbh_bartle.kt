package com.revenge.music.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.revenge.music.R

/** Display face used for editorial accents such as wrapped/summary headings. */
val bbhBartle: FontFamily = FontFamily(
  Font(R.font.bbh_bartle_regular, weight = FontWeight.Normal),
)

/** Legacy alias kept because callers import this spelling. Identical to [bbhBartle]. */
val bbh_bartle: FontFamily = bbhBartle
