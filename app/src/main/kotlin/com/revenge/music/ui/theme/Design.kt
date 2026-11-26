package com.revenge.music.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Revenge Unified Design Tokens
 *
 * Professional, music-centric, restrained, and intentional design language.
 * Clean surfaces, balanced spacing, cohesive typography, and purposeful micro-interactions.
 */

object RevengeColor {
  // Pure Black Dark Palette (Default Premium Dark Mode)
  val DarkCanvas = Color(0xFF0C0D12)
  val DarkPureBlackCanvas = Color.Black
  val DarkSurfaceSubtle = Color(0xFF13141B)
  val DarkSurfaceCard = Color(0xFF1A1B24)
  val DarkSurfaceElevated = Color(0xFF222430)
  val DarkSurfaceHighlight = Color(0xFF2C2F3E)

  // Light Palette (Refined Neutral Studio)
  val LightCanvas = Color(0xFFF7F8FA)
  val LightSurfaceSubtle = Color(0xFFEFF1F5)
  val LightSurfaceCard = Color(0xFFFFFFFF)
  val LightSurfaceElevated = Color(0xFFE4E7EC)
  val LightSurfaceHighlight = Color(0xFFD8DCE3)

  // Hairline Borders and Separators
  val BorderSubtleDark = Color.White.copy(alpha = 0.07f)
  val BorderSubtleLight = Color.Black.copy(alpha = 0.08f)
  val DividerDark = Color.White.copy(alpha = 0.05f)
  val DividerLight = Color.Black.copy(alpha = 0.06f)

  // Semantic Text Tiers
  val TextPrimaryDark = Color(0xFFF3F4F6)
  val TextSecondaryDark = Color(0xFFA0A5B5)
  val TextMutedDark = Color(0xFF676D7E)
  val TextDisabledDark = Color(0xFF454955)

  // Surface aliases for compatibility
  val SurfaceCardDark = DarkSurfaceCard
  val SurfaceElevatedDark = DarkSurfaceElevated

  val TextPrimaryLight = Color(0xFF111318)
  val TextSecondaryLight = Color(0xFF5A6072)
  val TextMutedLight = Color(0xFF8C93A4)
  val TextDisabledLight = Color(0xFFBCC1CC)

  // Functional Accents
  val BrandCrimson = Color(0xFFD6413B)
  val BrandEmber = Color(0xFFE65100)
  val Success = Color(0xFF34C759)
  val Warning = Color(0xFFFF9F0A)
  val Error = Color(0xFFFF453A)
  val Info = Color(0xFF0A84FF)

  // Live Playback Indicator
  val LivePlaying = Color(0xFF34C759)
}

/** Standard 4-point spacing scale */
object RevengeSpacing {
  val xxs: Dp = 2.dp
  val xs: Dp = 4.dp
  val sm: Dp = 8.dp
  val md: Dp = 12.dp
  val lg: Dp = 16.dp
  val xl: Dp = 24.dp
  val xxl: Dp = 32.dp
  val section: Dp = 40.dp
}

/** Intentional corner radius scale */
object RevengeRadius {
  val xs: Dp = 4.dp
  val sm: Dp = 8.dp
  val md: Dp = 12.dp
  val lg: Dp = 16.dp
  val xl: Dp = 22.dp
  val pill: Dp = 999.dp
  val artwork: Dp = 8.dp
  val card: Dp = 12.dp
  val playerPill: Dp = 16.dp
}

/** Standardized component dimensions */
object RevengeSize {
  val touchTarget: Dp = 48.dp
  val compactTouchTarget: Dp = 40.dp

  val iconSmall: Dp = 18.dp
  val compactIcon: Dp = 20.dp
  val iconMedium: Dp = 22.dp
  val icon: Dp = 24.dp
  val iconLarge: Dp = 28.dp

  val control: Dp = 40.dp
  val artworkSmall: Dp = 48.dp
  val artworkRow: Dp = 48.dp
  val artworkGridSmall: Dp = 120.dp
  val artworkGridMedium: Dp = 156.dp
  val artworkGridLarge: Dp = 192.dp

  val trackRowHeight: Dp = 56.dp
  val compactTrackRowHeight: Dp = 48.dp

  val topBar: Dp = 56.dp
  val miniPlayer: Dp = 64.dp
  val navigationBar: Dp = 64.dp
  val navigationRailWidth: Dp = 80.dp

  val buttonHeightSmall: Dp = 32.dp
  val buttonHeightMedium: Dp = 40.dp
  val buttonHeightLarge: Dp = 48.dp

  val contentMaxWidth: Dp = 960.dp
  val tabletBreakpoint: Dp = 600.dp
  val desktopBreakpoint: Dp = 840.dp
}

/** Application-level z-index layers */
object RevengeZIndex {
  const val Content: Float = 0f
  const val StickyHeader: Float = 2f
  const val Chrome: Float = 5f
  const val PersistentPlayer: Float = 10f
  const val NavigationOverlay: Float = 12f
  const val BottomSheetPlayer: Float = 20f
  const val SecondarySheet: Float = 25f
  const val DialogOverlay: Float = 30f
  const val NotificationToast: Float = 40f
}

/** Intentional, restrained motion system */
object RevengeMotion {
  const val durationQuick: Int = 140
  const val durationStandard: Int = 220
  const val durationDeliberate: Int = 320

  val standardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
  val enterEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
  val exitEasing = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)

  fun <T> quickTween() = tween<T>(durationMillis = durationQuick, easing = standardEasing)
  fun <T> standardTween() = tween<T>(durationMillis = durationStandard, easing = standardEasing)
  fun <T> deliberateTween() = tween<T>(durationMillis = durationDeliberate, easing = standardEasing)
}
