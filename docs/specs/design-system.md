# Clean-Room Specification — Design System (`:app` UI layer)

Status: M2 specification (behavioural contract only).
Module: `:app` (application module). Gradle path `:app`; namespace `com.revenge.music`.
Source roots covered:

- `app/src/main/kotlin/com/revenge/music/ui/theme`
- `app/src/main/kotlin/com/revenge/music/ui/components`
- `app/src/main/kotlin/com/revenge/music/ui/component` (including `backdrop/`, `floatingtabbar/`, `musicflow/`, `shimmer/`)
- `app/src/main/kotlin/com/revenge/music/utils/ComposeToImage.kt`

External dependencies that are part of the contract: Compose 1.10.2 (foundation, animation, ui, runtime), Material 3 `1.5.0-alpha18`, Material Kolor `4.1.1`, androidx palette-ktx `1.0.0`, Coil 3.3.0, compose-shimmer `1.5.0`, Haze `1.0.2`, smooth-corner-rect `v1.0.0`, material-icons-extended `1.7.8`.

This document describes observable behaviour and API shape. It intentionally omits statement-level
implementation. Any implementation that satisfies the contracts and acceptance tests is acceptable.
The clean-room implementation must not read the baseline bodies of the `REIMPLEMENT` files listed in
`docs/provenance/FILE_BY_FILE_INVENTORY.md`.

---

## 1. Provenance dispositions that shape this spec

| Area | Disposition | Consequence |
|---|---|---|
| `ui/theme/Design.kt` | RETAIN | Token object names and values below are fixed compatibility data. |
| `ui/theme/*` (other), `ui/component/*`, `ui/components/*`, `utils/ComposeToImage.kt` | REIMPLEMENT | Reimplement from this contract; no baseline body carried forward. |
| `ui/component/RevengeComponents.kt` | REFACTOR | Revenge-authored primitive set. Its public API is a compatibility contract. |
| `ui/component/backdrop/**` | REPLACE WITH VERIFIED THIRD-PARTY COMPONENT | Implement from pinned, verified `io.github.kyant0:backdrop` v2.0.0 source or an equivalent verified dependency. Apache-2.0 notice must remain. The public API in §9 is the compile contract used by app code. |
| `ui/component/floatingtabbar/FloatingTabBar.kt` | REPLACE WITH VERIFIED THIRD-PARTY COMPONENT | Implement from clean upstream `elyesmansour/compose-floating-tab-bar` v1.0.1 (Apache-2.0) plus the local behavioural additions in §10. Do not carry Echo-matched edits. |
| Bundled font assets | RETAIN | `sans_flex.ttf` v3.007, `outfit.ttf`, `plus_jakarta_sans.ttf`, `bbh_bartle_regular.ttf`; SIL OFL 1.1; notice already in `THIRD_PARTY_NOTICES.md` and `licenses/OFL-1.1.txt`. |

Never add a runtime dependency, permission, service, or manifest component that is not described
here or already present in `AndroidManifest.xml`.

---

## 2. Purpose and consumers

The design system defines the app's visual language (colour, spacing, radius, size, elevation,
motion, typography), the theme composable applied once at the activity root, a catalogue of reusable
Compose components, the vendored backdrop/glass rendering pipeline, the floating navigation bar, and
the bitmap share-card renderer.

Compile-time consumers:

- `MainActivity` wraps its whole content in `revengeTheme { ... }` and provides
  `LocalGlassEffectConfig` and `LocalAppBackdrop`.
- `CrashActivity` wraps its content in `revengeTheme(darkTheme = darkTheme)`.
- Settings screens (`AppearanceSettings`, `FontSelectionScreen`, `AppearancePreviewCard`) consume the
  font families, `PlayerSliderColors`, `Material3SettingsGroup`, `AnimatedRadioButton`.
- Player/mini-player consume `PlayerColorExtractor`, `PlayerSliderColors`, `liquidGlass`.
- Lyrics screen consumes `LyricsImageCard` and `ComposeToImage`.
- `WrappedShareHelper` consumes `ComposeToImage.saveBitmapAsFile`.
- `FloatingNavBar` consumes `FloatingTabBar`, `rememberFloatingTabBarScrollConnection`,
  `LocalGlassEffectConfig`.
- Home/library/search screens consume `EmptyPlaceholder` (13 files), `ListItem`/`GridItem` and their
  typed wrappers (`Items.kt`), shimmer placeholders, `TopSearch`, `Material3SettingsGroup`.

Every public name, parameter name, parameter order, default value, enum entry, and constant value in
Sections 3–15 is a compatibility contract because other code calls these symbols with named
arguments.

---

## 3. Design tokens — package `com.revenge.music.ui.theme`

All token objects are `object` singletons with `val` members. Values are exact; do not substitute
“similar” colours or dimensions.

### 3.1 `RevengeColor`

Dark palette:

| Member | Value |
|---|---|
| `DarkCanvas` | `Color(0xFF0C0D12)` |
| `DarkPureBlackCanvas` | `Color.Black` |
| `DarkSurfaceSubtle` | `Color(0xFF13141B)` |
| `DarkSurfaceCard` | `Color(0xFF1A1B24)` |
| `DarkSurfaceElevated` | `Color(0xFF222430)` |
| `DarkSurfaceHighlight` | `Color(0xFF2C2F3E)` |

Light palette:

| Member | Value |
|---|---|
| `LightCanvas` | `Color(0xFFF7F8FA)` |
| `LightSurfaceSubtle` | `Color(0xFFEFF1F5)` |
| `LightSurfaceCard` | `Color(0xFFFFFFFF)` |
| `LightSurfaceElevated` | `Color(0xFFE4E7EC)` |
| `LightSurfaceHighlight` | `Color(0xFFD8DCE3)` |

Borders, dividers:

| Member | Value |
|---|---|
| `BorderSubtleDark` | `Color.White.copy(alpha = 0.07f)` |
| `BorderSubtleLight` | `Color.Black.copy(alpha = 0.08f)` |
| `DividerDark` | `Color.White.copy(alpha = 0.05f)` |
| `DividerLight` | `Color.Black.copy(alpha = 0.06f)` |

Text tiers:

| Member | Value |
|---|---|
| `TextPrimaryDark` | `Color(0xFFF3F4F6)` |
| `TextSecondaryDark` | `Color(0xFFA0A5B5)` |
| `TextMutedDark` | `Color(0xFF676D7E)` |
| `TextDisabledDark` | `Color(0xFF454955)` |
| `TextPrimaryLight` | `Color(0xFF111318)` |
| `TextSecondaryLight` | `Color(0xFF5A6072)` |
| `TextMutedLight` | `Color(0xFF8C93A4)` |
| `TextDisabledLight` | `Color(0xFFBCC1CC)` |

Surface aliases (must remain):

- `SurfaceCardDark = DarkSurfaceCard`
- `SurfaceElevatedDark = DarkSurfaceElevated`

Functional accents:

| Member | Value |
|---|---|
| `BrandCrimson` | `Color(0xFFD6413B)` |
| `BrandEmber` | `Color(0xFFE65100)` |
| `Success` | `Color(0xFF34C759)` |
| `Warning` | `Color(0xFFFF9F0A)` |
| `Error` | `Color(0xFFFF453A)` |
| `Info` | `Color(0xFF0A84FF)` |
| `LivePlaying` | `Color(0xFF34C759)` |

### 3.2 `RevengeSpacing` (Dp, 4-point scale)

| Member | Value |
|---|---|
| `xxs` | 2.dp |
| `xs` | 4.dp |
| `sm` | 8.dp |
| `md` | 12.dp |
| `lg` | 16.dp |
| `xl` | 24.dp |
| `xxl` | 32.dp |
| `section` | 40.dp |

### 3.3 `RevengeRadius`

| Member | Value |
|---|---|
| `xs` | 4.dp |
| `sm` | 8.dp |
| `md` | 12.dp |
| `lg` | 16.dp |
| `xl` | 22.dp |
| `pill` | 999.dp |
| `artwork` | 8.dp |
| `card` | 12.dp |
| `playerPill` | 16.dp |

### 3.4 `RevengeSize`

| Member | Value |
|---|---|
| `touchTarget` | 48.dp |
| `compactTouchTarget` | 40.dp |
| `iconSmall` | 18.dp |
| `compactIcon` | 20.dp |
| `iconMedium` | 22.dp |
| `icon` | 24.dp |
| `iconLarge` | 28.dp |
| `control` | 40.dp |
| `artworkSmall` | 48.dp |
| `artworkRow` | 48.dp |
| `artworkGridSmall` | 120.dp |
| `artworkGridMedium` | 156.dp |
| `artworkGridLarge` | 192.dp |
| `trackRowHeight` | 56.dp |
| `compactTrackRowHeight` | 48.dp |
| `topBar` | 56.dp |
| `miniPlayer` | 64.dp |
| `navigationBar` | 64.dp |
| `navigationRailWidth` | 80.dp |
| `buttonHeightSmall` | 32.dp |
| `buttonHeightMedium` | 40.dp |
| `buttonHeightLarge` | 48.dp |
| `contentMaxWidth` | 960.dp |
| `tabletBreakpoint` | 600.dp |
| `desktopBreakpoint` | 840.dp |

### 3.5 `RevengeZIndex` (`const val`, type `Float`)

`Content = 0f`, `StickyHeader = 2f`, `Chrome = 5f`, `PersistentPlayer = 10f`,
`NavigationOverlay = 12f`, `BottomSheetPlayer = 20f`, `SecondarySheet = 25f`,
`DialogOverlay = 30f`, `NotificationToast = 40f`.

Ordering invariant: each named layer is strictly greater than the previous one. Tests may assert
the exact values.

### 3.6 `RevengeMotion`

| Member | Value |
|---|---|
| `durationQuick` | `140` (Int ms) |
| `durationStandard` | `220` |
| `durationDeliberate` | `320` |
| `standardEasing` | `CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)` |
| `enterEasing` | `CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)` |
| `exitEasing` | `CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)` |

Functions (generic, return `TweenSpec<T>`):

```kotlin
fun <T> quickTween(): TweenSpec<T>       // durationQuick, standardEasing
fun <T> standardTween(): TweenSpec<T>    // durationStandard, standardEasing
fun <T> deliberateTween(): TweenSpec<T>  // durationDeliberate, standardEasing
```

Invariant: the three functions differ only in duration; all use `standardEasing`.

---

## 4. Typography — `ui/theme`

### 4.1 Font families and assets

Files: `Font.kt`, `Type.kt`, `bbh_bartle.kt`. Resource fonts: `R.font.sans_flex`,
`R.font.outfit`, `R.font.plus_jakarta_sans`, `R.font.bbh_bartle_regular` (plus `res/font/bbh_bartle.xml`
guide).

Public values:

```kotlin
val bbhBartle: FontFamily        // R.font.bbh_bartle_regular, FontWeight.Normal
val bbh_bartle: FontFamily       // identical; both names must exist for callers
val GoogleSansFontFamily: FontFamily
val SansFlexFontFamily: FontFamily
val OutfitFontFamily: FontFamily
val PlusJakartaSansFontFamily: FontFamily
```

Contract:

- `GoogleSansFontFamily` and `SansFlexFontFamily` are behaviourally identical: both are built from
  `R.font.sans_flex` with variable-font settings `weight` 400/500/700, `width = 100f`, and custom
  axis `"ROND" = 100f` for Normal/Medium/Bold.
- `OutfitFontFamily` and `PlusJakartaSansFontFamily` expose Normal, Medium and Bold weights via
  `FontVariation.weight` (400/500/700) over their respective resource.
- A missing or invalid font resource must fail at resource-resolution time, not silently fall back
  inside the family construction.
- The four OFL font resources are the only bundled fonts. Do not add another font asset.

### 4.2 Type ramp

```kotlin
@OptIn(ExperimentalTextApi::class)
fun getTypography(brandFont: FontFamily, plainFont: FontFamily = brandFont): Typography
```

`brandFont` is used for display, headline, title, label and `labelSmall` roles. `plainFont` is used
for `bodyLarge`, `bodyMedium`, `bodySmall`. When `plainFont` is omitted it equals `brandFont` (the
production call passes only `brandFont`).

Exact ramp (font size / line height / letter spacing; weight):

| Role | Family | Weight | Size | Line height | Letter spacing |
|---|---|---|---|---|---|
| `displayLarge` | brand | Normal | 44sp | 52sp | -0.5sp |
| `displayMedium` | brand | Normal | 36sp | 44sp | -0.4sp |
| `displaySmall` | brand | Normal | 30sp | 38sp | -0.3sp |
| `headlineLarge` | brand | SemiBold | 28sp | 34sp | -0.2sp |
| `headlineMedium` | brand | SemiBold | 24sp | 30sp | -0.15sp |
| `headlineSmall` | brand | SemiBold | 20sp | 26sp | -0.1sp |
| `titleLarge` | brand | Medium | 20sp | 28sp | -0.1sp |
| `titleMedium` | brand | SemiBold | 16sp | 22sp | 0sp |
| `titleSmall` | brand | Medium | 14sp | 20sp | 0sp |
| `bodyLarge` | plain | Normal | 16sp | 24sp | 0sp |
| `bodyMedium` | plain | Normal | 14sp | 20sp | 0sp |
| `bodySmall` | plain | Normal | 12sp | 16sp | 0.1sp |
| `labelLarge` | brand | Medium | 14sp | 20sp | 0sp |
| `labelMedium` | brand | Medium | 12sp | 16sp | 0.1sp |
| `labelSmall` | brand | Medium | 11sp | 14sp | 0.2sp, `fontFeatureSettings = "tnum"` |

Invariants:

- `labelSmall` always requests tabular digits (`tnum`) for counters and times.
- No TextStyle sets a colour; colour always comes from the surrounding content colour.
- Display and headline sizes decrease monotonically; title/label sizes match the table exactly.

### 4.3 Font preference and application

The app persists a font choice in `:core` (`SelectedFontKey`, `CustomFontPathKey`) with default
`AppFont.PLUS_JAKARTA_SANS.value`.

`AppFont` enum (in `core/src/main/kotlin/com/revenge/music/constants/PreferenceKeys.kt`):

| Entry | `value` |
|---|---|
| `SYSTEM` | `"system"` |
| `GOOGLE_SANS` | `"google_sans"` |
| `SANS_FLEX` | `"sans_flex"` |
| `OUTFIT` | `"outfit"` |
| `PLUS_JAKARTA_SANS` | `"plus_jakarta_sans"` |
| `CUSTOM` | `"custom"` |

`AppFont.fromValue(value)` returns the matching entry, or `SYSTEM` for any unknown/blank value.

Resolution rules used by both `revengeTheme` and `FontSelectionScreen`:

- `SYSTEM` → `FontFamily.Default`
- `GOOGLE_SANS` → `GoogleSansFontFamily`
- `SANS_FLEX` → `SansFlexFontFamily`
- `OUTFIT` → `OutfitFontFamily`
- `PLUS_JAKARTA_SANS` → `PlusJakartaSansFontFamily`
- `CUSTOM` → if the stored path is non-empty and the file exists, load a `Typeface` from that file
  and wrap it in a `FontFamily`; on any failure or missing path → `FontFamily.Default`. Failure must
  be swallowed (no crash) and must not change the persisted preference.

The custom-font picker copies the chosen `content://` stream to `filesDir/custom_font.ttf`, stores
that absolute path, and selects `CUSTOM`. A read/write failure is non-fatal.

---

## 5. Theme composition — `ui/theme/Theme.kt`

### 5.1 Constants

```kotlin
val DefaultThemeColor = Color(0xFF6C4DF6)  // sentinel: follow Android system palette
val BrandThemeColor   = Color(0xFFD6413B)  // fresh-install seed (equals RevengeColor.BrandCrimson)
```

`DefaultThemeColor` must never be used as a visual seed; it is only a sentinel compared with `==`.
`SelectedThemeColorKey` (Int ARGB, in `:core`) defaults to `BrandThemeColor.toArgb()`.

### 5.2 `revengeTheme`

```kotlin
@Composable
fun revengeTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  pureBlack: Boolean = false,
  isMonochrome: Boolean = false,
  themeColor: Color = BrandThemeColor,
  content: @Composable () -> Unit,
)
```

Observable behaviour:

1. Reads `SelectedFontKey` (default `plus_jakarta_sans`) and `CustomFontPathKey` (default `""`) and
   resolves the brand family per §4.3. The returned `FontFamily` is remembered on both preference
   values.
2. Chooses a base `ColorScheme`:
   - `isMonochrome == true`: a dynamic scheme seeded with `Color(0xFF808080)`, `isDark = darkTheme`,
     Material Kolor `ColorSpec.SpecVersion.SPEC_2025`, `PaletteStyle.Monochrome`, then converted to
     desaturated (HSL saturation = 0) colours; alpha per channel is preserved.
   - else if `themeColor == DefaultThemeColor` and `Build.VERSION.SDK_INT >= S`: system dynamic
     dark/light scheme (`dynamicDarkColorScheme` / `dynamicLightColorScheme`).
   - else: Material Kolor `rememberDynamicColorScheme(seedColor = themeColor, isDark = darkTheme,
     specVersion = SPEC_2025, style = PaletteStyle.TonalSpot)`.
3. If `darkTheme`, the scheme is remapped through the professional dark mapping (§5.3) with
   `pureBlack`; in light theme it is used unchanged.
4. Installs `MaterialTheme(colorScheme, typography = getTypography(brandFont), shapes = ...,
   content = content)`. Shapes map `extraSmall→RevengeRadius.xs`, `small→sm`, `medium→md`,
   `large→lg`, `extraLarge→xl`.

Invariants:

- The default parameter is `BrandThemeColor`, so a caller that supplies no colour gets the Revenge
  ember seed, not the wallpaper.
- Passing `DefaultThemeColor` on API 31+ and with `isMonochrome == false` must yield system dynamic
  colour; below API 31 the same sentinel falls through to the seeded tonal-spot branch (no crash).
- `isMonochrome` wins over both the sentinel and any supplied colour.
- Preference reads happen inside the composable; changing the font preference recomposes the theme
  without recreating the activity.
- The composable provides no `CompositionLocal` of its own beyond what `MaterialTheme` provides.

### 5.3 Professional dark mapping

```kotlin
fun ColorScheme.toProfessionalDarkColorScheme(pureBlack: Boolean): ColorScheme
fun ColorScheme.pureBlack(apply: Boolean): ColorScheme   // delegates to the above
```

Normal dark (`pureBlack = false`) overrides: `surface` and `background = RevengeColor.DarkCanvas`;
`surfaceContainerLowest = 0xFF0E0F14`; `surfaceContainerLow = DarkSurfaceSubtle`;
`surfaceContainer = DarkSurfaceCard`; `surfaceContainerHigh = DarkSurfaceElevated`;
`surfaceContainerHighest = DarkSurfaceHighlight`; `outline = 0xFF383C4A`;
`outlineVariant = 0xFF252833`.

Pure black (`pureBlack = true`) overrides: `surface`, `background`, `surfaceContainerLowest =
Color.Black`; `surfaceContainerLow = 0xFF0C0D10`; `surfaceContainer = 0xFF13151A`;
`surfaceContainerHigh = 0xFF1B1D24`; `surfaceContainerHighest = 0xFF242730`;
`outline = 0xFF383C48`; `outlineVariant = 0xFF252830`.

All other scheme roles (primary/secondary/tertiary/error containers, on-* colours, scrim) are
unchanged. `pureBlack(...)` is a thin alias and must return exactly the same result.

### 5.4 Monochrome conversion

`fun ColorScheme.toGrayscaleColorScheme(): ColorScheme` converts every colour role to HSL with
saturation 0 while preserving each role's original alpha. It must convert at least: primary,
onPrimary, primaryContainer, onPrimaryContainer, inversePrimary, secondary, onSecondary,
secondaryContainer, onSecondaryContainer, tertiary, onTertiary, tertiaryContainer,
onTertiaryContainer, background, onBackground, surface, onSurface, surfaceVariant, onSurfaceVariant,
surfaceTint, inverseSurface, inverseOnSurface, error, onError, errorContainer, onErrorContainer,
outline, outlineVariant, scrim, surfaceBright, surfaceDim, surfaceContainer,
surfaceContainerHigh, surfaceContainerHighest, surfaceContainerLow, surfaceContainerLowest.

### 5.5 `ColorSaver`

```kotlin
val ColorSaver: Saver<Color, Int>
```

`save(value) = value.toArgb()`; `restore(Int) = Color(Int)`. Used by `MainActivity` as
`rememberSaveable(stateSaver = ColorSaver)`. Round trip must be lossless for any ARGB colour
including alpha.

### 5.6 Bitmap theme extraction helpers

```kotlin
fun Bitmap.extractThemeColor(): Color
fun Bitmap.extractGradientColors(): List<Color>
```

- `extractThemeColor`: generate a palette with at most 8 colours, rank with Material Kolor
  `Score.score(mapOf(rgb to population))`, return the first ranked colour. Empty palette behaviour:
  must not throw; return a deterministic fallback colour (implementer may choose a documented
  fallback, but tests only require “no crash and a defined ARGB”).
- `extractGradientColors`: generate a palette with at most 64 colours, `Score.score(..., 2,
  0xff4285f4, true)`, sort descending by luminance; if at least two colours remain return the first
  two; otherwise return `[Color(0xFF595959), Color(0xFF0D0D0D)]`.
- Both are synchronous and may be called from a background coroutine by callers. They must not touch
  UI state.

---

## 6. Player colour extraction — `ui/theme/PlayerColorExtractor.kt`

```kotlin
object PlayerColorExtractor {
  suspend fun extractGradientColors(palette: Palette, fallbackColor: Int): List<Color>

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
}
```

`extractGradientColors` contract:

- Runs on `Dispatchers.Default`; suspends and never blocks the caller's main thread.
- Considers seven swatches: dominant, vibrant, darkVibrant, lightVibrant, muted, darkMuted,
  lightMuted (nulls omitted). Selects the swatch with the maximum weight
  `population * 2 * vibrancyBonus * (saturation + brightness) / 2`, where `vibrancyBonus = 1.5` when
  saturation > 0.3 and brightness > 0.3, otherwise `1`.
- Fallback dominant: the dominant swatch's RGB, else `palette.getDominantColor(fallbackColor)`.
- If the best swatch is vibrant (saturation > 0.25, brightness in (0.2, 0.9)), start from its colour
  and saturate at 1.3×; otherwise start from the fallback dominant and saturate at 1.1×. Vividness
  enhancement saturates (capped at 1.0) and scales value by 0.9, clamped to 0.4..0.85.
- Always returns exactly three colours: `[primary, primary * 0.6 per channel, Color.Black]`. The
  second colour clamps each channel at 0.
- A palette with no usable swatch still returns three colours derived from `fallbackColor` and
  black; it never throws.

Callers (`AlbumGradient`, `MiniPlayer`, `Player`) use the result as a vertical/diagonal gradient.

---

## 7. Player slider colours — `ui/theme/PlayerSliderColors.kt`

```kotlin
object PlayerSliderColors {
  @Composable
  fun getSliderColors(
    activeColor: Color,
    playerBackground: PlayerBackgroundStyle,
    useDarkTheme: Boolean,
  ): SliderColors
}
```

`PlayerBackgroundStyle` is from `:core`: `DEFAULT, GRADIENT, BLUR, GLOW_ANIMATED, APPLE_MUSIC,
LIVE_MESH, LIQUID_GLASS`.

Inactive track colour:

- `DEFAULT` + dark → `MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)`.
- `DEFAULT` + light → `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)`.
- Every non-`DEFAULT` style (any theme) → `Color.White.copy(alpha = 0.4f)`.

Returned `SliderColors` must set active track, active tick, thumb, disabled active track, disabled
inactive track and disabled thumb; active/thumb/disabled-active use `activeColor`; inactive and
disabled-inactive use the computed inactive colour. All other roles keep Material 3 defaults.

---

## 8. Reusable component catalogue

All composables accept `modifier: Modifier = Modifier` unless noted. “No-op defaults” means the
parameter may be omitted without changing layout or semantics.

### 8.1 Core primitives — `ui/component/RevengeComponents.kt` (REFACTOR)

```kotlin
enum class RevengeButtonVariant { PRIMARY, SECONDARY, TONAL, OUTLINED, GHOST }

enum class RevengeButtonSize(val height: Dp, val textStyle: TextStyle, val horizontalPadding: Dp) {
  SMALL(32.dp, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), 12.dp),
  MEDIUM(40.dp, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), 16.dp),
  LARGE(48.dp, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), 20.dp),
}
```

`RevengeButton(onClick, modifier, text = null, leadingIcon: Painter? = null,
trailingIcon: Painter? = null, variant = PRIMARY, size = MEDIUM, enabled = true,
content: (@Composable RowScope.() -> Unit)? = null)`

- Pill shape (`RevengeRadius.pill`), height from `size`, horizontal padding from `size`.
- Colour mapping: PRIMARY `primary/onPrimary`; SECONDARY `surfaceContainerHigh/onSurface` with a
  1dp `BorderSubtleDark` stroke; TONAL `secondaryContainer/onSecondaryContainer`; OUTLINED
  transparent with `onSurface` and a 1dp outline at 40% alpha; GHOST transparent with
  `onSurfaceVariant`.
- Disabled: container and content at 38% alpha, click ignored.
- Press feedback: uniform scale down to 0.97 while pressed, animated with `quickTween`.
- Content: optional `content` wins; otherwise `text` rendered single-line with ellipsis; leading and
  trailing icons are 45% of the button height, with `xs` spacing.
- Accessibility: the surface is a button; the icon painters must have `contentDescription = null`
  because text carries the label.

`RevengeIconButton(onClick, icon: Painter, contentDescription: String?, modifier = Modifier,
enabled = true, tint = LocalContentColor.current, containerColor = Color.Transparent,
size = RevengeSize.compactTouchTarget, iconSize = RevengeSize.iconMedium, tooltip: String? = null)`

- Circular hit area of `size`, icon of `iconSize`, role `Role.Button`.
- Disabled tint at 38% alpha.
- Press scale 0.92 with `quickTween`.
- When `tooltip` is non-null, wraps in a Material 3 `PlainTooltip`.
- `contentDescription` is the sole accessibility label for the icon.

`RevengeCard(modifier, onClick: (() -> Unit)? = null, containerColor =
surfaceContainer, borderColor = BorderSubtleDark, shape = RoundedCornerShape(RevengeRadius.card),
content)` — rounded, 1dp border, background; clickable only when `onClick != null` and then role
`Role.Button`.

`RevengeSectionHeader(title, modifier, subtitle? = null, actionText? = null,
onActionClick? = null, actionIcon: Painter? = null)` — row with title (`titleLarge`, bold),
optional subtitle (`bodySmall`), and an optional GHOST/SMALL `RevengeButton` whose label is
`actionText`; the action is shown only when both `actionText` and `onActionClick` are non-null.

`RevengeSearchInput(query, onQueryChange, onSearch, modifier, placeholder = "Search songs, albums,
artists...", leadingIcon = R.drawable.search, onClear? = null, trailingContent?)`

- Pill surface, 48dp high, surfaceContainerHigh fill, subtle border.
- Placeholder only while `query` is empty.
- IME action Search triggers `onSearch(query)`.
- Non-empty query shows a clear button that calls `onQueryChange("")` then `onClear`.
- `trailingContent` is appended after the clear button.

`RevengeEmptyState(icon: Painter, title: String, message: String, modifier, actionText? = null,
onActionClick? = null)` — centred 72dp circle icon, title (`titleMedium`, bold), message
(`bodyMedium`, `onSurfaceVariant`), optional PRIMARY/MEDIUM action. Decorative icon has
`contentDescription = null`; the text is the accessible content.

`RevengeLoadingState(modifier, message: String? = null)` — centred 36dp circular progress using
`primary`, optional `bodySmall` message.

`RevengeErrorState(message, onRetry, modifier, title = "Something went wrong")` — error icon
(`RevengeColor.Error`, 44dp), title, message, and a SECONDARY/MEDIUM “Try Again” button with a
replay icon. The retry button is present whenever the composable is rendered.

### 8.2 Selection, chips, indicators — `ui/components`, `ui/component`

`AnimatedRadioButton(selected: Boolean, onClick: (() -> Unit)?, modifier = Modifier, enabled = true)`
— 24dp control, 20dp drawn circle (2dp stroke), inner dot (10dp diameter) scaled by a bouncy spring
on selection, ripple unbounded 20dp, press scale 0.92. Semantics role `Role.RadioButton`; enabled
false dims to 38% and disables click. The composable itself does not own selection state; it renders
the `selected` input. `onClick == null` means non-interactive (still rendered).

`ChipsRow<E>(chips: List<Pair<E, String>>, currentValue: E, onValueUpdate: (E) -> Unit, modifier,
containerColor = surfaceContainer, leading: (@Composable () -> Unit)? = null)` — horizontally
scrollable Material 3 `FilterChip` row with 12dp animated corners, a check icon on the selected
chip, 8dp gaps, and horizontal system-bar insets. Each chip exposes Material 3 selected state and
label to accessibility.

`ChoiceChipsRow(chips: List<Pair<Int, String>>, options: List<Pair<OptionStats, String>>,
selectedOption: OptionStats, onSelectionChange: (OptionStats) -> Unit, currentValue: Int,
onValueUpdate: (Int) -> Unit, modifier, containerColor = surfaceContainer)` — same chip row plus a
filter/tune control that opens a dropdown of `OptionStats`; selecting an option calls
`onSelectionChange`; selecting a chip calls `onValueUpdate`.

`PlayingIndicator(color: Color, modifier, bars: Int = 3, barWidth: Dp = 4.dp,
cornerRadius: Dp = ThumbnailCornerRadius)` — animated equaliser bars, each scrolling randomly
between 10% and 100% height at 50ms intervals after a 300ms initial delay; decorative and not
announced.

`PlayingIndicatorBox(modifier, isActive: Boolean, playWhenReady: Boolean, color = Color.White)` —
visible only when `isActive` (500ms fade). Shows animated bars when `playWhenReady`; otherwise a
static play icon. Non-interactive.

`ExpressiveBarChart(days: List<DayUsageData>, goalMs: Long = 2h, todayIndex: Int,
onDayClick: (Long) -> Unit = {}, modifier, chartHeight: Dp = 200.dp)` — weekly listening bar chart
drawn on a Canvas.

- Bars are spring-animated to `day.totalMs / max(totalMs)`; the maximum is at least 1 so an all-zero
  week renders flat.
- Bars above the `goalMs` goal are emphasised with the primary colour; others use
  `secondaryContainer`; labels use `onSurfaceVariant`.
- The “today” bar is 15% wider and marked by the primary colour.
- Long-press on a bar selects it and shows a tooltip (surface background, inverse text) for about one
  second; tapping away dismisses it; `onDayClick(day.totalMs)` fires for the hit day.
- **Compatibility note:** the `todayIndex` parameter is accepted, but the rendered today bar follows
  the device’s `LocalDate` day-of-week (Monday = 0 … Sunday = 6) captured at composition. A caller
  cannot force a different “today” through the parameter in the current contract. Preserve the
  signature; do not let the parameter shadow-crash.
- Accessibility: a Canvas chart is decorative by default. The host screen is responsible for an
  equivalent textual summary.

`AnimatedRadioButton` and `ExpressiveBarChart` live in package `com.revenge.music.ui.components`
(note the plural `components`); all other catalogue items live in `com.revenge.music.ui.component`.

### 8.3 Rows, grids, thumbnails — `ui/component/Items.kt`

Global constant: `const val ActiveBoxAlpha = 0.6f`.

`currentGridThumbnailHeight(): Dp` — reads `GridItemsSizeKey` (`GridItemSize.BIG` default) and
returns `GridThumbnailHeight` for BIG, else `SmallGridThumbnailHeight`.

`ListItem` overloads:

1. `ListItem(modifier, title: String, subtitle: (@Composable RowScope.() -> Unit)? = null,
   thumbnailContent: @Composable () -> Unit, trailingContent: @Composable RowScope.() -> Unit = {},
   isSelected: Boolean? = false, isActive: Boolean = false, isAvailable: Boolean = true,
   shape: Shape = RectangleShape, drawHighlight: Boolean = true, horizontalPadding: Dp = 16.dp,
   color: Color = Color.Transparent, verticalPadding: Dp = 2.dp)`
2. same with `subtitle: AnnotatedString?` and `badges: @Composable RowScope.() -> Unit = {}`.
3. same with `subtitle: String?` and `badges`.

Observable row contract:

- Fixed height `ListItemHeight`; thumbnail box uses `ListThumbnailSize` with
  `ThumbnailCornerRadius`.
- Background precedence: active → `secondaryContainer`; selected and `drawHighlight` →
  `primary` at 40% alpha; otherwise the supplied `color`.
- Title single-line bold `bodyMedium`; subtitle single-line `bodyMedium` in `secondary`.
- Unavailable (`isAvailable = false`) overlays a black 25% scrim plus an offline icon on the
  thumbnail.
- Accessibility: rows are not clickable themselves; the caller supplies click/trailing semantics.
  The title is readable text. Decorative thumbnails/icons use `contentDescription = null`.

`GridItem` overloads (composable-title and string overloads) with `title`, `subtitle`,
`badges`, `thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit`, `thumbnailRatio = 1f`,
`fillMaxWidth = false`, `forceCrop = false`. Width is `currentGridThumbnailHeight() * thumbnailRatio`
unless `fillMaxWidth`; title uses `bodyLarge` bold, subtitle `bodyMedium` secondary.

Typed wrappers (all `@Composable`, all accept `modifier`; most expose `badges` and
`trailingContent`): `SongListItem(Song, albumIndex?, showLikedIcon = true, showInLibraryIcon = false,
showDownloadIcon = true, showSize = false, ...)`, `SongGridItem`, `ArtistListItem`, `ArtistGridItem`,
`AlbumListItem`, `AlbumGridItem`, `PlaylistListItem(Playlist, autoPlaylist = false, ...)`,
`PlaylistGridItem`, `MediaMetadataListItem(MediaMetadata, isSelected = false, isActive = false,
isPlaying = false, shape = RectangleShape, color = Transparent, verticalPadding = 2.dp,
trailingContent)`, `YouTubeListItem(YTItem, ...)`, `YouTubeGridItem(YTItem, thumbnailRatio =
16/9 for SongItem else 1f, ...)`.

`ItemThumbnail(thumbnailUrl: String?, isActive: Boolean, isPlaying: Boolean, shape: Shape,
modifier, albumIndex: Int? = null, isSelected: Boolean = false, thumbnailRatio: Float = 1f,
forceCrop: Boolean = false)`

- Loads artwork with Coil (memory/disk/network cache enabled) at a 544×544 resize hint.
- Crop when the `CropAlbumArtKey` preference is true or `forceCrop` is true; otherwise Fit.
- `albumIndex != null` shows the track number instead of artwork, hidden while active, with
  fade/expand animation.
- Selected state overlays a 50% black scrim and a done icon.
- Active state overlays `Black` at `ActiveBoxAlpha` or transparent for album-index mode.

`LocalThumbnail(...)` mirrors `ItemThumbnail` for local files, adding `showCenterPlay` and
`playButtonVisible`.

`PlaylistThumbnail(thumbnails: List<String>, size: Dp, placeHolder: @Composable () -> Unit, shape:
Shape, cacheKey: String? = null)` — 0 images: coloured placeholder box; 1 image: single cover;
2–4 images: 2×2 quadrant collage, each `size/2`. Missing/error images use the launcher placeholder.

Box extensions: `OverlayPlayButton(visible)`, `OverlayEditButton(visible, onClick, alignment =
Alignment.Center)`, `AlbumPlayButton(visible, onClick)` — circular 36dp black-60% overlays with
white 20dp icons and fade in/out.

`SwipeToSongBox(modifier, mediaItem: MediaItem, content: @Composable BoxScope.() -> Unit)` —
horizontal drag threshold 300px; swipe right (`>= +300`) calls `LocalPlayerConnection.current
?.playNext([mediaItem])` and toasts “play next”; swipe left calls `addToQueue([mediaItem])` and
toasts “add to queue”; otherwise the offset resets. During drag a directional icon/background is
revealed. The feature is disabled when the `SwipeToSongKey` preference is false where used by
`YouTubeListItem`.

`data class Quadruple<A, B, C, D>(first, second, third, fourth)` — plain data holder.

`object Icon` — helper composables for badges: `Favorite`, `Explicit`, `Library`, `Download(state)`
and similar; all decorative (`contentDescription = null`) and sized in the 16–20dp range.

`Library.kt` adds library-specific wrappers: `LibraryArtistListItem`, `LibraryArtistGridItem`,
`LibraryAlbumListItem`, `LibraryAlbumGridItem`, `LibraryPlaylistListItem`,
`LibraryPlaylistGridItem`. They add library affordances (pin state, download badges, selection) on
top of the same `ListItem`/`GridItem` contracts.

### 8.4 Settings, preferences, menus

`Material3SettingsGroup(title: String? = null, compact: Boolean = false, scrollState:
ScrollState? = null, items: List<Material3SettingsItem>)`

`data class Material3SettingsItem(icon: Painter? = null, customIcon: (@Composable () -> Unit)? = null,
title: @Composable () -> Unit, description: (@Composable () -> Unit)? = null, trailingContent:
(@Composable () -> Unit)? = null, showBadge: Boolean = false, isHighlighted: Boolean = false,
tintIcon: Boolean = true, iconShape: Shape? = null, enabled: Boolean = true, onClick:
(() -> Unit)? = null)`

- Items render as a rounded connected group; when `scrollState` is supplied the group participates
  in the shared scroll. Disabled items ignore clicks. Badge/highlight are visual only.

`Preference.kt`:

```kotlin
PreferenceEntry(modifier, title: @Composable () -> Unit, description: String? = null,
  content: (@Composable () -> Unit)? = null, icon: (@Composable () -> Unit)? = null,
  trailingContent: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
  isEnabled: Boolean = true)
fun <T> ListPreference(modifier, title, icon, selectedValue: T, values: List<T>,
  valueText: @Composable (T) -> String, onValueSelected: (T) -> Unit, isEnabled = true)
SwitchPreference(modifier, title, description, icon, checked: Boolean,
  onCheckedChange: (Boolean) -> Unit, isEnabled = true)
EditTextPreference(modifier, title, icon, value: String, onValueChange: (String) -> Unit,
  singleLine = true, isInputValid: (String) -> Boolean = { it.isNotEmpty() }, isEnabled = true)
SliderPreference(modifier, title, icon, value: Float, onValueChange: (Float) -> Unit,
  isEnabled = true)
PreferenceGroupTitle(title: String, modifier = Modifier)
```

`Material3MenuGroup(items: List<Material3MenuItemData>, modifier = Modifier)` with
`data class Material3MenuItemData(icon?, title?, description?, onClick?, cardColors: CardColors?,
trailingContent?, customComposable?)`. Single item uses 24dp corners; first/last use 24dp outer and
6dp inner corners; middle items 6dp; 4dp gaps. Custom composable replaces the default row.

`NewMenuComponents.kt`:

```kotlin
data class NewAction(icon: @Composable () -> Unit, text: String, onClick: @Composable () -> Unit,
  enabled = true, backgroundColor = Color.Unspecified, contentColor = Color.Unspecified)
NewActionButton(icon, text, onClick: @Composable () -> Unit, modifier, enabled = true,
  backgroundColor = surfaceVariant, contentColor = onSurfaceVariant)
NewMenuItem(headlineContent, leadingContent?, trailingContent?, supportingContent?, onClick?,
  enabled = true, modifier)
NewMenuSectionHeader(text, modifier)
NewActionGrid(actions: List<NewAction>, modifier, columns: Int = 3)
NewMenuContent(headerContent?, actionGrid?, menuItems?, modifier)
NewIconButton(icon, onClick, modifier, enabled = true, backgroundColor = surfaceVariant,
  contentColor = onSurfaceVariant)
NewMenuContainer(content, modifier)
```

`NewAction.onClick` is a composable lambda; the action grid invokes it in a composable context. Do
not change the parameter type to `() -> Unit` (source compatibility).

### 8.5 Dialogs and sheets

`Dialog.kt`:

```kotlin
DefaultDialog(onDismiss: () -> Unit, modifier, icon?, title?, buttons: (@Composable RowScope.() -> Unit)?,
  horizontalAlignment = CenterHorizontally, content: @Composable ColumnScope.() -> Unit)
ActionPromptDialog(title? = null, titleBar? = null, onDismiss, onConfirm, onReset? = null,
  onCancel? = null, content: @Composable ColumnScope.() -> Unit = {})
ListDialog(onDismiss, modifier, color = AlertDialogDefaults.containerColor, title?,
  content: LazyListScope.() -> Unit)
InfoLabel(text: String)
TextFieldDialog(modifier, icon?, title?, initialTextFieldValue = TextFieldValue(),
  placeholder?, singleLine = true, autoFocus = true, maxLines = if (singleLine) 1 else 10,
  isInputValid: (String) -> Boolean = { it.isNotEmpty() }, keyboardType = KeyboardType.Text,
  onDone: (String) -> Unit = {}, onReset? = null, resetText? = null,
  textFields: List<Pair<String, TextFieldValue>>? = null,
  onTextFieldsChange: ((Int, TextFieldValue) -> Unit)? = null,
  onDoneMultiple: ((List<String>) -> Unit)? = null, onDismiss, autoDismiss = true, extraContent?)
```

Dialogs dismiss on outside tap/back via `onDismiss`; action prompts confirm only when `isInputValid`
holds for text entry; `TextFieldDialog` supports either a single field or a list of named fields.
Buttons use Material 3 defaults and are announced by text.

`BottomSheet.kt`:

```kotlin
@Stable
class BottomSheetState : DraggableState {          // delegates drag
  val collapsedBound: Dp
  val dismissedBound: Dp
  val expandedBound: Dp
  val value: State<Dp>
  val isDismissed: State<Boolean>
  val isCollapsed: State<Boolean>
  val isExpanded: State<Boolean>
  val progress: State<Float>                        // 0 at collapsed, 1 at expanded
  fun collapse(animationSpec: AnimationSpec<Dp>)
  fun expand(animationSpec: AnimationSpec<Dp>)
  fun collapseSoft(); fun expandSoft()
  fun dismiss()
  suspend fun dismissAndWait()
  fun snapTo(value: Dp)
  fun performFling(velocity: Float, onDismiss: (() -> Unit)?)
  val preUpPostDownNestedScrollConnection: NestedScrollConnection
}

const val expandedAnchor = 2
const val collapsedAnchor = 1
const val dismissedAnchor = 0

@Composable
fun rememberBottomSheetState(dismissedBound: Dp, expandedBound: Dp,
  collapsedBound: Dp = dismissedBound, initialAnchor: Int = dismissedAnchor): BottomSheetState

@Composable
fun BottomSheet(state: BottomSheetState, modifier, background: @Composable (BoxScope.() -> Unit) = {},
  onDismiss: (() -> Unit)? = null, collapsedContent: @Composable BoxScope.() -> Unit,
  isExpandable: Boolean = true, content: @Composable BoxScope.() -> Unit)
```

State machine:

- Bounds order: `dismissed <= collapsed <= expanded` (the remembered state coerces the lower bound
  to at most the expanded bound).
- Anchors: dismissed = 0, collapsed = 1, expanded = 2. The anchor survives configuration changes via
  `rememberSaveable`.
- `performFling`: velocity > 250 → expand; velocity < -250 → dismiss (when a dismiss callback is
  supplied and the sheet is below collapsed) otherwise collapse; near zero uses the midpoint of each
  anchor interval to dismiss/collapse/expand.
- Nested scrolling drives expansion only from the top edge; the connection does not consume scroll
  when the list is not at the top.
- `BottomSheet` renders `collapsedContent` when collapsed and `content` when expanded; back press
  dismisses when `onDismiss` is provided; rounded top corners collapse to square while expanded.

`BottomSheetPage.kt`:

```kotlin
val LocalBottomSheetPageState: LocalBottomSheetPageState (compositionLocalOf { BottomSheetPageState() })

@Stable
class BottomSheetPageState(isVisible: Boolean = false,
  content: @Composable ColumnScope.() -> Unit = {}) {
  var isVisible: Boolean
  var content: (@Composable ColumnScope.() -> Unit)
  fun show(content: @Composable ColumnScope.() -> Unit)
  fun dismiss()
}

@Composable
fun BottomSheetPage(modifier, state: BottomSheetPageState,
  background: Color = MaterialTheme.colorScheme.surfaceContainer)
```

`show` replaces content and makes the page visible; `dismiss` only hides it (content retained).
The page is a modal bottom sheet with a 32×4dp drag handle, focus cleared on dismiss.

### 8.6 Navigation — `ui/component/AppNavigation.kt`, `FloatingNavBar.kt`

`Screens` is a sealed class in `com.revenge.music.ui.screens` with `titleId`, `iconIdInactive`,
`iconIdActive`, `route`; `Screens.MainScreens = [Home, Podcast, Search, ListenTogether, Library]`.
Routes include `home`, `podcasts`, `search_input`, `listen_together`, `library`, `generate`.

```kotlin
@Stable
fun isRouteSelected(currentRoute: String?, screenRoute: String,
  navigationItems: List<Screens>): Boolean
```

Returns false for null current route; true when equal; true when the screen is a known destination
and the current route starts with `"$screenRoute/"` (detail screens keep their tab selected).

```kotlin
AppNavigationBar(navigationItems: List<Screens>, currentRoute: String?, onItemClick: (Screens, Boolean) -> Unit,
  modifier, pureBlack = false, slimNav = false, glassEnabled = false, onSearchLongClick: (() -> Unit)? = null)

AppNavigationRail(navigationItems, currentRoute, onItemClick, modifier, pureBlack = false,
  onSearchLongClick? = null, onMusicRecognitionClick? = null, musicRecognitionContentDescription = "",
  onMusicFlowClick? = null, musicFlowIconRes: Int? = R.drawable.swipe_vertical,
  musicFlowContentDescription = "", onShuffleClick? = null, shuffleEnabled = false,
  shuffleIconRes: Int? = null, shuffleContentDescription = "", onAiHubClick? = null,
  aiHubIconRes: Int? = null, aiHubContentDescription = "")
```

- `AppNavigationBar` is a Material 3 `NavigationBar`, 64dp-class, hairline top border, transparent
  tonal elevation, primary-coloured selected icon/label, `onSurfaceVariant` at 75% unselected,
  primary indicator at 12% alpha. `slimNav` hides labels while keeping icon labels as content
  descriptions. Each item reports its screen title to accessibility.
- When a screen is `Screens.Search` and `onSearchLongClick != null`, the item uses a long-press
  interaction: short release calls `onItemClick`; a long press (system timeout) performs a long-press
  haptic and calls `onSearchLongClick` instead.
- `AppNavigationRail` is the wide-screen rail (80dp). It renders the same items plus an overflow
  “more” item opening a modal sheet for the extra callbacks (recognition, music flow, shuffle, AI
  hub). Each extra action appears only when its callback is non-null; content descriptions come from
  the supplied strings and must be non-blank for meaningful actions.
- `onItemClick(screen, isAlreadySelected)` — the second argument is the selection state computed by
  `isRouteSelected`.

`AppFloatingNavBar(navigationItems, currentRoute, onItemClick, scrollConnection:
FloatingTabBarScrollConnection, modifier, pureBlack = false, showPlayerAccessory = false,
onAccessoryClick: () -> Unit = {}, onMusicRecognitionClick? = null,
musicRecognitionContentDescription = "", onSearchLongClick? = null)` — wraps `FloatingTabBar`
(§10). Selected tab key is the screen route. Tab icons use active/inactive resources and screen
titles as content descriptions. `contentKey` captures selection, items, colours, recognition label
and compact mode so the content lambda never goes stale. When `showPlayerAccessory` is true, the
inline and expanded accessories are the mini-player dock; clicking the accessory calls
`onAccessoryClick`.

### 8.7 Search — `ui/component/SearchBar.kt`

```kotlin
@ExperimentalMaterial3Api
@Composable
fun TopSearch(query: TextFieldValue, onQueryChange: (TextFieldValue) -> Unit,
  onSearch: (String) -> Unit, active: Boolean, onActiveChange: (Boolean) -> Unit,
  modifier, enabled = true, placeholder?, leadingIcon?, trailingIcon?, shape: Shape? = null,
  colors: TopAppBarColors = topAppBarColors(containerColor = surfaceContainerLow),
  scrollBehavior: TopAppBarScrollBehavior? = null, windowInsets: WindowInsets = systemBars,
  interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
  focusRequester: FocusRequester = remember { FocusRequester() },
  content: @Composable ColumnScope.() -> Unit = {})
```

- A 48dp input inside a `TopAppBar`. Any pointer-up inside the field activates search
  (`onActiveChange(true)`) even before text editing.
- Enter key or IME Search calls `onSearch(query.text)`.
- When `active`, a full-width suggestions/content panel is shown below the 56dp app bar and the
  system back gesture calls `onActiveChange(false)`.
- Accessibility: the field sets `contentDescription = "Search"` and, while active,
  `stateDescription = "Suggestions available"`.
- Constants exposed for layout compatibility: `val InputFieldHeight = 48.dp`,
  `internal val TopAppBarVerticalPadding: Dp = 8.dp`, `internal val TopAppBarHorizontalPadding =
  12.dp`, `val SearchBarIconOffsetX: Dp = 4.dp`.

### 8.8 Text utilities

`AutoResizeText(text: String, fontSizeRange: FontSizeRange, modifier, color = Color.Unspecified,
fontStyle?, fontWeight?, fontFamily?, letterSpacing = TextUnit.Unspecified, textDecoration?,
textAlign?, lineHeight = TextUnit.Unspecified, overflow = TextOverflow.Clip, softWrap = true,
maxLines = Int.MAX_VALUE, style: TextStyle = LocalTextStyle.current)` — measures and decrements the
font size by `fontSizeRange.step` until the text fits the incoming constraints or reaches `min`.

```kotlin
data class FontSizeRange(val min: TextUnit, val max: TextUnit, val step: TextUnit = 1.sp) {
  // requires min < max and step.value > 0, throwing IllegalArgumentException otherwise
}
```

`ExpandableText(text = "", runs: List<LinkSegment>? = null, modifier, collapsedMaxLines = 3)` —
shows up to `collapsedMaxLines`, then an expand/collapse affordance; expanded state is internal and
survives recomposition (not process death). Link runs remain clickable.

`LyricsV2.kt` exports `LyricsLineV2(...)` and `AnimatedWordV2(...)`; their exact signatures may
evolve only with the lyrics spec, but the names must remain importable.

### 8.9 Sliders and sliders-adjacent controls

```kotlin
BigSeekBar(progressProvider: () -> Float, onProgressChange: (Float) -> Unit, modifier,
  background = surfaceTint alpha 0.13, color = primary)
PlayerSliderTrack(sliderState: SliderState, modifier, colors = SliderDefaults.colors(),
  trackHeight: Dp = 10.dp)
VolumeSlider(value: Float, onValueChange: (Float) -> Unit, modifier, enabled = true,
  onValueChangeFinished: (() -> Unit)? = null, accentColor = primary)
WavySlider(value, onValueChange, modifier, valueRange = 0f..1f, onValueChangeFinished?,
  colors = SliderDefaults.colors(), isPlaying = true, enabled = true, strokeWidth = 4.dp,
  thumbRadius = 8.dp, wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
  waveSpeed = wavelength)
SquigglySlider(value, onValueChange, modifier, enabled = true, valueRange = 0f..1f,
  onValueChangeFinished?, colors = SliderDefaults.colors(), isPlaying = true)
```

`BigSeekBar` reads progress from a provider each frame (not a value) so it can track player progress
without recomposing the whole tree; it reports drag changes through `onProgressChange`. The wave
sliders animate only while `isPlaying`; their value semantics remain standard slider semantics.

### 8.10 Shimmer and loading placeholders — `ui/component/shimmer/`

```kotlin
ShimmerHost(modifier, horizontalAlignment = Alignment.Start,
  verticalArrangement = Arrangement.Top, content: @Composable ColumnScope.() -> Unit)
getShimmerTheme(): ShimmerTheme
TextPlaceholder(modifier, height: Dp = 16.dp, shape: CornerBasedShape = RoundedCornerShape(0.dp))
ButtonPlaceholder(modifier)
ListItemPlaceHolder(modifier, thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius))
GridItemPlaceHolder(modifier, thumbnailShape: Shape = RoundedCornerShape(ThumbnailCornerRadius),
  fillMaxWidth: Boolean = false)
```

- `ShimmerHost` applies the shimmer modifier to a column. `getShimmerTheme` uses
  `surfaceVariant` at 25/50/25% alpha, an 800ms linear tween, 250ms delay, restart repeat.
  `MainActivity` provides it as `LocalShimmerTheme`.
- `TextPlaceholder` width is a remembered random 25%–75% of available width, so a list of
  placeholders looks organic but does not change between recompositions.
- `ListItemPlaceHolder` matches the real row height and thumbnail size; `GridItemPlaceHolder`
  matches big/small grid thumbnail heights from preferences.
- Placeholders are decorative (`contentDescription = null`) and carry no text.

### 8.11 Other reusable pieces present in the package

The following files are part of the package and must compile with their listed public entry points;
detailed behaviour follows the same conventions (theme colours, `RevengeSpacing`, 48dp touch targets,
decorative icons, text as labels):

`AlbumGradient`, `AppearancePreviewCard` (`FullPlayerPreview`, `MiniPlayerPreview`,
`playerBackgroundStyleLabel`, `playerButtonsStyleLabel`, `PlayerBackgroundStyleModal`,
`PlayerButtonsStyleModal`, `MiniPlayerBackgroundStyleModal`, `Modifier.fadingEdges`,
`Modifier.drawGridScrollbar`, `Modifier.drawListScrollbar`), `AutoPlaylistButton`,
`BitPerfectBadge`, `BottomSheetMenu` (`LocalMenuState`, `MenuState`), `ContinueListening`,
`CreateAiPlaylistDialog`, `CreateFromTasteDialog`, `CreatePlaylistDialog`, `DraggableLyricsProviderList`,
`DraggableScrollBarOverlay`, `EnumDialog`, `EpisodeDetails`, `EpisodeProgress`, `GridMenu`,
`HeartBurstIcon`, `HideOnScrollFAB`, `HomeShortcutTile`, `IconButton` (`IconButton(onClick,
onLongClick, ...)`, `ResizableIconButton(@DrawableRes icon, ...)`), `IntegrationCard`, `Library`,
`LocalSongPickerDialog`, `Lyrics`, `LyricsImageCard` (§12), `LyricsV2`, `MetroLyrics`, `NavigationTile`,
`NavigationTitle` (`title`, `label?`, `thumbnail?`, `onClick?`, `onPlayAllClick?`), `OnlineBlur`,
`PlaybackLogsDialog`, `PodcastHome`, `RandomizeGridItem`, `RefreshAiRecommendationDialog`,
`RevengeLyrics`, `RingtoneProgressDialog`, `RingtoneTrimmerDialog`, `SongDnaBottomSheet`,
`SortHeader`, `SpeedDialGridItem`, `ThumbnailCornerRadiusSelector`, `UpdaterComponents`, and
`musicflow/{MusicFlowEntryCard,MusicFlowPage,MusicFlowTopMenu}`.

Shared rules for all of them:

- Colours come from `MaterialTheme.colorScheme` or the tokens; never hardcode a one-off colour except
  where this spec names one (e.g. white track at 40% on artwork backgrounds).
- All interactive controls must expose role + label; decorative imagery must not be announced.
- Touch targets that are visually smaller than 48dp must still provide at least a 48dp hit target
  (or 40dp where the compact control is deliberate and not the sole target).

---

## 9. Backdrop subsystem contract — `ui/component/backdrop/`

Vendored Apache-2.0 code (Kyant0/backdrop v2.0.0). Implement from verified upstream source; keep the
Apache-2.0 attribution header and the `THIRD_PARTY_NOTICES.md` entry. App code imports these exact
symbols.

### 9.1 Core types

```kotlin
interface Backdrop {
  val isCoordinatesDependent: Boolean
  fun DrawScope.drawBackdrop(density: Density, coordinates: LayoutCoordinates?,
    layerBlock: (GraphicsLayerScope.() -> Unit)? = null)
}

sealed interface BackdropEffectScope : Density, RuntimeShaderCache {
  val size: Size
  val layoutDirection: LayoutDirection
  val shape: Shape
  var padding: Float
  var renderEffect: RenderEffect?
  fun update(scope: DrawScope, contentScale: Float = 1f): Boolean
  fun apply(effects: BackdropEffectScope.() -> Unit)
  fun reset()
}

sealed interface RuntimeShaderCache {
  fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
  fun clear()
}

interface RuntimeShader { /* setFloatUniform ×5 overloads, setIntUniform ×5, setColorUniform */ }
```

`isCoordinatesDependent` is true only for layer-backed backdrops, because their content is positioned
relative to the drawing coordinates.

### 9.2 Backdrop factories

```kotlin
@Composable fun rememberBackdrop(backdrop: Backdrop,
  onDraw: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit): Backdrop
@Composable fun rememberCanvasBackdrop(onDraw: DrawScope.() -> Unit): Backdrop
@Composable fun rememberCombinedBackdrop(backdrop1: Backdrop, backdrop2: Backdrop): Backdrop
@Composable fun rememberCombinedBackdrop(backdrop1: Backdrop, backdrop2: Backdrop,
  backdrop3: Backdrop): Backdrop
@Composable fun rememberCombinedBackdrop(vararg backdrops: Backdrop): Backdrop
@Composable fun rememberLayerBackdrop(graphicsLayer: GraphicsLayer = rememberGraphicsLayer(),
  onDraw: ContentDrawScope.() -> Unit = { drawContent() }): LayerBackdrop

@Stable
class LayerBackdrop internal constructor(val graphicsLayer: GraphicsLayer, ...) : Backdrop
fun Modifier.layerBackdrop(backdrop: LayerBackdrop): Modifier
```

- `rememberBackdrop` wraps a backdrop and lets the caller intercept its draw.
- A canvas backdrop is coordinate-independent and simply invokes the supplied draw block.
- Combined backdrops draw their children in order; `isCoordinatesDependent` is the logical OR.
- A layer backdrop records the app’s own UI into a graphics layer, then samples it. It requires
  `layerBackdrop` to be attached to the source content and does nothing when coordinates are absent.
- Factories are `@Composable` and remember on their inputs; creating a new lambda with a new identity
  invalidates the remembered value.

### 9.3 Modifiers

```kotlin
fun Modifier.drawPlainBackdrop(backdrop: Backdrop, shape: () -> Shape,
  effects: BackdropEffectScope.() -> Unit, layerBlock: (GraphicsLayerScope.() -> Unit)? = null,
  exportedBackdrop: LayerBackdrop? = null, onDrawBehind: (DrawScope.() -> Unit)? = null,
  onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = { it() },
  onDrawSurface: (DrawScope.() -> Unit)? = null, onDrawFront: (DrawScope.() -> Unit)? = null): Modifier

fun Modifier.drawBackdrop(backdrop: Backdrop, shape: () -> Shape,
  effects: BackdropEffectScope.() -> Unit, highlight: (() -> Highlight?)? = { Highlight.Default },
  shadow: (() -> Shadow?)? = { Shadow.Default }, innerShadow: (() -> InnerShadow)? = null,
  layerBlock: (GraphicsLayerScope.() -> Unit)? = null, exportedBackdrop: LayerBackdrop? = null,
  onDrawBehind: (DrawScope.() -> Unit)? = null,
  onDrawBackdrop: DrawScope.(drawBackdrop: DrawScope.() -> Unit) -> Unit = { it() },
  onDrawSurface: (DrawScope.() -> Unit)? = null, onDrawFront: (DrawScope.() -> Unit)? = null,
  backdropScale: Float = 1f): Modifier
```

- `drawPlainBackdrop` samples without highlight/shadow and is used where edge decoration would be
  wrong.
- `drawBackdrop` layers, back to front: inner shadow, shadow, backdrop content (clipped to `shape`),
  optional surface draw, highlight, front draw.
- `backdropScale` records the backdrop at that fraction of resolution and upscales; callers that use
  pixel-sized effect parameters must pre-multiply them by the same scale (the glass code does).
  `1f` means full resolution.
- A nulled highlight/shadow/innerShadow lambda suppresses that layer. `onDrawBehind` draws behind,
  `onDrawFront` in front, and `onDrawSurface` after the backdrop but before highlight/front.
- The modifier caches its draw work keyed by backdrop, shape, effects and callbacks; recomposition
  with equivalent state must not re-record the backdrop every frame.

### 9.4 Effects (extension functions on `BackdropEffectScope`)

```kotlin
fun BackdropEffectScope.blur(radius: Float, edgeTreatment: TileMode = TileMode.Clamp)
fun BackdropEffectScope.effect(effect: RenderEffect)
fun BackdropEffectScope.runtimeShaderEffect(key: String, @Language("AGSL") shaderString: String,
  uniformShaderName: String, block: RuntimeShader.() -> Unit)
fun BackdropEffectScope.colorFilter(colorFilter: ColorFilter)
fun BackdropEffectScope.opacity(alpha: Float)
fun BackdropEffectScope.colorControls(brightness: Float = 0f, contrast: Float = 1f, saturation: Float = 1f)
fun BackdropEffectScope.vibrancy()   // saturation 1.5
fun BackdropEffectScope.lens(refractionHeight: Float, refractionAmount: Float,
  depthEffect: Boolean = true, chromaticAberration: Boolean = true, ...)
```

Platform gates: effects are silently skipped on unsupported SDKs rather than crashing.

- `isRenderEffectSupported()` true on API >= 31 (S).
- `isRuntimeShaderSupported()` true on API >= 33 (Tiramisu).
- `blur(radius <= 0)` and `colorControls(0,1,1)` are no-ops.
- Effects chain in call order onto `renderEffect`.
- `lens` throws `UnsupportedOperationException` for non-corner-based shapes; callers must pass a
  `CornerBasedShape` at the modifier.

### 9.5 Edge decorations

```kotlin
@Immutable data class Highlight(width: Dp = 0.5.dp, blurRadius: Dp = width / 2f,
  alpha: Float = 1f, style: HighlightStyle = HighlightStyle.Default) {
  companion object { val Default; val Ambient; val Plain }
}

interface HighlightStyle { val color: Color; val blendMode: BlendMode
  fun DrawScope.createShader(shape: Shape, runtimeShaderCache: RuntimeShaderCache): RuntimeShader? }
  // data classes Plain, Default(angle = 45f), Ambient(intensity = 0.38f)

@Immutable data class Shadow(radius: Dp = 24.dp, offset: DpOffset = DpOffset(0.dp, radius / 6),
  color: Color = Black 10%, alpha: Float = 1f, blendMode = DrawScope.DefaultBlendMode) {
  companion object { val Default }
}
@Immutable data class InnerShadow(radius: Dp = 24.dp, offset: DpOffset = DpOffset(0.dp, radius),
  color: Color = Black 15%, alpha: Float = 1f, blendMode = DrawScope.DefaultBlendMode) {
  companion object { val Default }
}
fun lerp(start: InnerShadow, stop: InnerShadow, fraction: Float): InnerShadow
```

`lerp` interpolates radius, offset, colour and alpha; blendMode snaps to the nearer endpoint at 0.5.

### 9.6 Platform helpers

`isRenderEffectSupported(): Boolean` and `isRuntimeShaderSupported(): Boolean` (top-level, package
root). Runtime shader helpers: `RuntimeShader(@Language("AGSL") String)`,
`RuntimeShader.asComposeShader()`, `RuntimeShader.asAndroidRuntimeShader()`.

Internal implementation files (`internal/InverseLayerScope.kt`, `LayerRecorder.kt`, `Outline.kt`,
`Paint.kt`, `RenderEffect.kt`, `Shaders.kt`, `ShapeProvider.kt`) and the `highlight`/`shadow`
modifier files are part of the subsystem but not imported by app code; keep their API private or
internal.

---

## 10. FloatingTabBar contract — `ui/component/floatingtabbar/FloatingTabBar.kt`

Clean upstream v1.0.1 plus these behavioural additions: tab pill and standalone tab cluster are
centred (iOS-26 style), inline/expanded content is centre-aligned, and the local package name is
`com.revenge.music.ui.component.floatingtabbar`.

### 10.1 Composables

```kotlin
@Composable
fun FloatingTabBar(isInline: Boolean, selectedTabKey: Any?, modifier = Modifier,
  tabBarContentModifier = Modifier,
  inlineAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
  expandedAccessory: (@Composable SharedTransitionScope.(Modifier, AnimatedVisibilityScope) -> Unit)? = null,
  colors: FloatingTabBarColors = FloatingTabBarDefaults.colors(),
  shapes: FloatingTabBarShapes = FloatingTabBarDefaults.shapes(),
  sizes: FloatingTabBarSizes = FloatingTabBarDefaults.sizes(),
  elevations: FloatingTabBarElevations = FloatingTabBarDefaults.elevations(),
  contentKey: Any? = null, content: FloatingTabBarScope.() -> Unit)

@Composable
fun FloatingTabBar(selectedTabKey: Any?, scrollConnection: FloatingTabBarScrollConnection,
  modifier = Modifier, tabBarContentModifier = Modifier,
  inlineAccessory = null, expandedAccessory = null,
  colors = ..., shapes = ..., sizes = ..., elevations = ..., contentKey: Any? = null,
  content: FloatingTabBarScope.() -> Unit)
```

- The first overload is externally driven: `isInline` changes animate; its internal scroll connection
  uses `FloatingTabBarInlineBehavior.Never`.
- The second overload is scroll driven by the supplied `scrollConnection`.
- The tab bar cross-fades (`fadeIn togetherWith fadeOut`) between inline and expanded bars inside a
  `SharedTransitionLayout`; both accessories are optional.
- `contentKey` invalidates the captured scope; callers must include everything their tab lambdas
  close over (selection, colours, callbacks, labels), otherwise the UI goes stale.

### 10.2 Scope and tabs

```kotlin
interface FloatingTabBarScope {
  fun tab(key: Any, title: @Composable () -> Unit, icon: @Composable () -> Unit,
    onClick: () -> Unit, onLongClick: (() -> Unit)? = null,
    indication: (@Composable () -> Indication)? = { LocalIndication.current })
  fun standaloneTab(key: Any, icon: @Composable () -> Unit, onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    indication: (@Composable () -> Indication)? = { LocalIndication.current })
}
```

- Tabs are ordered as registered. A second `standaloneTab` replaces the first; only one standalone
  tab exists.
- Inline mode shows exactly one tab — the selected tab when it is a registered tab and not the
  standalone key; otherwise the last inline tab; otherwise the first tab. With no tabs, the inline
  tab area is empty.
- Selecting an inline tab invokes its `onClick` and also expands the bar.
- Icon/title content is supplied by the caller; the caller is responsible for content descriptions.

### 10.3 State machine and defaults

```kotlin
class FloatingTabBarScrollConnection(initialIsInline: Boolean = false,
  scrollThresholdPx: Float,
  inlineBehavior: FloatingTabBarInlineBehavior = OnScrollDown) : NestedScrollConnection {
  var isInline: Boolean   // observable, private set
  fun expand(); fun inline()
}

enum class FloatingTabBarInlineBehavior { Never, OnScrollDown, OnScrollUp }

@Composable
fun rememberFloatingTabBarScrollConnection(initialIsInline = false, scrollThreshold: Dp = 50.dp,
  inlineBehavior = OnScrollDown): FloatingTabBarScrollConnection
```

Behaviour:

- Accumulates vertical scroll; a direction reversal resets the accumulator.
- `OnScrollDown`: scrolling down (negative delta) past the threshold collapses to inline; scrolling
  up past the threshold expands.
- `OnScrollUp`: mirrored.
- `Never`: never changes state from scroll.
- `expand()`/`inline()` set the state and reset the accumulator; they are idempotent.
- The connection never consumes scroll (`Offset.Zero`), so content scrolls normally.

Data/defaults:

```kotlin
@Immutable data class FloatingTabBarColors(backgroundColor, accessoryBackgroundColor)
@Immutable data class FloatingTabBarShapes(tabBarShape, tabShape, standaloneTabShape, accessoryShape)
@Immutable data class FloatingTabBarElevations(inlineElevation: Dp, expandedElevation: Dp)
@Immutable data class FloatingTabBarSizes(tabBarContentPadding: PaddingValues,
  tabInlineContentPadding, tabExpandedContentPadding, componentSpacing: Dp, tabSpacing: Dp)

object FloatingTabBarDefaults {
  fun colors(backgroundColor = surfaceContainerHigh,
    accessoryBackgroundColor = surfaceContainerHigh): FloatingTabBarColors
  fun shapes(tabBarShape = RoundedCornerShape(100), tabShape = RoundedCornerShape(100),
    standaloneTabShape = CircleShape, accessoryShape = RoundedCornerShape(100)): FloatingTabBarShapes
  fun sizes(tabBarContentPadding = PaddingValues(vertical = 2.dp, horizontal = 4.dp),
    tabInlineContentPadding = PaddingValues(10.dp),
    tabExpandedContentPadding = PaddingValues(vertical = 2.dp, horizontal = 20.dp),
    componentSpacing = 8.dp, tabSpacing = 0.dp): FloatingTabBarSizes
  fun elevations(inlineElevation = 6.dp, expandedElevation = 12.dp): FloatingTabBarElevations
}
```

Selection styling is the caller’s job (`FloatingNavBar` passes selected/unselected content colours
and active/inactive icon resources).

---

## 11. Cross-cutting state model: loading, empty, error, offline

The design system exposes the states; screens decide when to show them. The mapping below is the
required observable semantics.

| State | Component | Required observable behaviour |
|---|---|---|
| Loading | `RevengeLoadingState`, `ShimmerHost` + placeholders | Deterministic progress or shimmer; no content-shaped holes that shift layout when content arrives (placeholders must match final row/grid dimensions). |
| Empty | `EmptyPlaceholder` (`icon: @DrawableRes Int`, `text: String`, `description: String? = null`, `action: (@Composable () -> Unit)? = null`) and `RevengeEmptyState` | Centred icon + title, optional description, optional action. Full-size by default (`fillMaxSize()`), padding `RevengeSpacing.lg`. Decorative icon not announced. |
| Error | `RevengeErrorState(message, onRetry, title = "Something went wrong")` | Message plus a retry action always present; retry invokes exactly the supplied callback. |
| Offline item | `ListItem(isAvailable = false)` / `ItemThumbnail` availability | Thumbnail dimmed by a black 25% scrim with an offline icon; row remains listed and does not crash a click handler. |
| Unavailable action | any `enabled = false` control | Visually 38% alpha and ignores clicks; semantics mark it disabled. |

`EmptyPlaceholder` signature is a hard contract used by 13 screens:

```kotlin
@Composable
fun EmptyPlaceholder(@DrawableRes icon: Int, text: String, modifier: Modifier = Modifier,
  description: String? = null, action: (@Composable () -> Unit)? = null)
```

Invariants:

- Empty and error states must not request focus.
- Switching loading → empty/error/content must not retain the previous state’s size where it would
  cause a scroll jump; use the same outer padding/height.
- An error state must never be shown as a full-screen overlay that blocks navigation chrome; it is
  ordinary content unless the screen explicitly wraps it.

---

## 12. Share-card rendering

### 12.1 `utils/ComposeToImage.kt`

```kotlin
object ComposeToImage {
  suspend fun createLyricsImage(
    context: Context,
    coverArtUrl: String?,
    songTitle: String,
    artistName: String,
    lyrics: String,
    width: Int,
    height: Int,
    backgroundColor: Int? = null,
    backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
    textColor: Int? = null,
    secondaryTextColor: Int? = null,
    lyricsAlignment: Layout.Alignment = Layout.Alignment.ALIGN_CENTER,
  ): Bitmap

  fun saveBitmapAsFile(context: Context, bitmap: Bitmap, fileName: String): Uri
}
```

`createLyricsImage` contract:

- Runs on `Dispatchers.Default`; callers await a `Bitmap` and must not receive a UI-thread block.
- **Output size is always 2160×2160 regardless of `width` and `height`.** The parameters are accepted
  for source compatibility only. Do not divide by zero or reject non-positive values.
- Background styles (`com.revenge.music.ui.component.LyricsBackgroundStyle`):
  - `SOLID`: fill with `backgroundColor`, or `0xFF121212` when null.
  - `BLUR`: black fill, then the cover image scaled down, fast-blurred (a box blur) and drawn with a
    30% black overlay. If the cover is missing or blur fails, fall back to the solid background.
  - `GRADIENT`: if the cover exists, generate a palette and draw a linear gradient from vibrant to
    dark-vibrant (both falling back to `backgroundColor`); otherwise solid.
- Cover art: load with Coil at `size(1024)` and `allowHardware(false)` off the main thread. Any load
  failure is swallowed and the card renders without cover art.
- Text colours: `textColor` or `0xFFFFFFFF`; secondary `secondaryTextColor` or `0xB3FFFFFF`.
- Layout invariants (proportions, not pixel-exact copy): 1:1 card with a 20/340 rounded border at 9%
  of text colour; 28/340 padding; 64/340 cover art with 3/340 corners and a 16% border; title bold
  at 20/340 and artist at 16/340, each single-line ellipsised beside the cover; a footer with a
  circular logo badge, the app name and a divider reserved above the lyrics; lyrics occupy the
  remaining vertical space.
- Lyrics sizing: start at 50/340 text units, 1.2 line spacing, shrink by 1/340 until the block fits
  the available height or reaches a 13/340 floor. Vertically centre when it fits; otherwise start at
  the top.
- Required strings/resources: `R.string.app_name`, `R.mipmap.ic_launcher`, and the alignments passed
  by the caller (`ALIGN_NORMAL`, `ALIGN_CENTER`, `ALIGN_OPPOSITE`).
- Deterministic for the same inputs except for palette rounding; no timestamps are drawn into the
  bitmap.

`saveBitmapAsFile` contract:

- API >= 29 (Q): insert a `MediaStore.Images` record with `DISPLAY_NAME = "$fileName.png"`,
  `MIME_TYPE = "image/png"`, `RELATIVE_PATH = Pictures/revenge`; write PNG at quality 100; return the
  inserted `content://` URI. If the insert returns null, throw `IllegalStateException`.
- API < 29: write `"$fileName.png"` under `cacheDir/images` and return a
  `FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)` URI. Create the
  directory if missing.
- Never mutates the bitmap. The URI is readable by another app only with an explicit
  `FLAG_GRANT_READ_URI_PERMISSION` from the caller.

Share flow used by `Lyrics.kt` (contract for testability):

1. Build the bitmap.
2. Save it with a unique `lyrics_<epochMillis>` name.
3. Start a chooser for `Intent.ACTION_SEND`, type `image/png`, `EXTRA_STREAM = uri`,
   `FLAG_GRANT_READ_URI_PERMISSION`.
4. On any failure, show a “failed to create image” toast and end the progress dialog.

`WrappedShareHelper` also uses `saveBitmapAsFile`; if saving throws it retries by writing a PNG to
`cacheDir/images` and returning a `FileProvider` URI with the same authority.

### 12.2 `ui/component/LyricsImageCard.kt`

```kotlin
enum class LyricsBackgroundStyle { SOLID, BLUR, GRADIENT }

@Composable
fun rememberAdjustedFontSize(text: String, maxWidth: Dp, maxHeight: Dp, density: Density,
  initialFontSize: TextUnit = 20.sp, minFontSize: TextUnit = 14.sp,
  style: TextStyle = TextStyle.Default, textMeasurer: TextMeasurer? = null): TextUnit

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun LyricsImageCard(lyricText: String, mediaMetadata: MediaMetadata, darkBackground: Boolean = true,
  backgroundColor: Color? = null,
  backgroundStyle: LyricsBackgroundStyle = LyricsBackgroundStyle.SOLID,
  textColor: Color? = null, secondaryTextColor: Color? = null,
  textAlign: TextAlign = TextAlign.Center)
```

`rememberAdjustedFontSize` behaviour:

- Short text (< 50 chars) starts at `initialFontSize`; 50–99 chars at 0.8×; 100–199 at 0.6×; 200+ at
  0.5×.
- Blank text returns `minFontSize`.
- The result is recomputed when `text`, `maxWidth`, `maxHeight` or `style` changes.
- Uses a binary search over the 0.5sp grid, capped at 20 iterations, targeting 92% of width and
  height, never returning below `minFontSize`.
- The initial value is available synchronously on first composition; the refined value arrives after
  measurement without a crash or visible layout explosion.

`LyricsImageCard` is the 1:1 preview used in the lyrics share sheet:

- Defaults: dark background `0xFF121212`, light `0xFFF5F5F5`; primary text white/black; secondary
  text at 70% alpha.
- Renders cover, title (`20.sp`, bold), artist list, lyrics and a footer with the launcher icon and
  app name, inside a 20dp rounded card with a 9% border.
- `BLUR` crops and blurs the cover at 50dp with a 30% black overlay; `GRADIENT` computes a linear
  vibrant→dark-vibrant brush from the cover palette, falling back to the solid background. The
  palette work is asynchronous and must not block composition; before it resolves, the card shows
  the solid background.
- Lyrics use bold text at the adjusted size with 1.2 line height, ellipsised.
- `textAlign` controls both text alignment and the block’s horizontal placement (start/centre/end).
- The card fills its parent; the host controls aspect ratio (`Lyrics.kt` uses `aspectRatio(1f)`).

---

## 13. Accessibility semantics requirements

The implementation must satisfy these observable semantics; a test may read them via the semantics
tree.

1. `AnimatedRadioButton` exposes `Role.RadioButton` and is disabled when `enabled = false` or
   `onClick = null`.
2. `RevengeIconButton`, `RevengeCard(onClick)`, overlay buttons and `IconButton` expose
   `Role.Button`.
3. `TopSearch` field exposes content description `"Search"`; while active it exposes state
   description `"Suggestions available"`.
4. Navigation bar/rail items expose their screen title as content description or label; the
   long-press search item keeps the same label and additionally reports the long-press action.
5. Decorative imagery (icons that duplicate adjacent text, thumbnails, empty-state icons, shimmer
   blocks, playing bars) must have `contentDescription = null`.
6. Interactive 24dp/36dp controls must still meet the project’s 48dp touch-target expectation via
   padding or minimum interactive size (40dp compact where the control is secondary).
7. Text remains readable in monochrome and pure-black themes; no token pair below WCAG AA for
   normal text (`TextPrimary*` on `*Canvas`, `TextSecondary*` on surfaces). `BrandCrimson` on white
   is used for accents/icons, not long body copy.
8. Focus order follows visual order inside rows/grids; dialogs trap focus while shown.
9. Content that changes state (chips, switches) exposes selected/checked state through Material 3
   components rather than colour alone.

---

## 14. Navigation destinations and arguments

The design system does not own a nav graph, but navigation chrome must preserve these route
arguments and selection rules.

| Destination | Route | Chrome behaviour |
|---|---|---|
| Home | `home` | Main tab |
| Podcasts | `podcasts` | Main tab |
| Search | `search_input` | Main tab; long-press shortcut when a callback is supplied |
| Listen Together | `listen_together` | Main tab |
| Library | `library` | Main tab |
| Generate / playlists | `generate` | Reachable from rail/overflow, not in `MainScreens` |

Argument contract: `Screens` carries only `titleId`, `iconIdInactive`, `iconIdActive`, `route`; no
screen-specific arguments are carried by the design system. Detail routes are recognised for tab
selection by the prefix rule `"$screenRoute/"`. The design system must not hardcode a route string
outside `AppNavigation`/`FloatingNavBar`; it must consume `Screens` instances.

---

## 15. Cross-module contracts required to compile

From `:core` (must not be redefined in `:app`):

- `com.revenge.music.constants.AppFont` (entries and `fromValue` per §4.3).
- `val SelectedFontKey`, `val CustomFontPathKey`, `val SelectedThemeColorKey`,
  `val PlayerBackgroundStyleKey`, `val MiniPlayerBackgroundStyleKey`, `val PlayerButtonsStyleKey`,
  `val GridItemsSizeKey`, `val ThumbnailCornerRadius`, `val ListItemHeight`, `val ListThumbnailSize`,
  `val GridThumbnailHeight`, `val SmallGridThumbnailHeight`, `val AppBarHeight`,
  `val NavigationBarAnimationSpec`, `val CropAlbumArtKey`, `val SwipeToSongKey`,
  `val UseFloatingNavBarKey`, and the liquid-glass preference keys used by `MainActivity`.
- Enums: `PlayerBackgroundStyle` (7 entries per §7), `PlayerButtonsStyle`
  (`DEFAULT, PRIMARY, TERTIARY`), `GridItemSize`, `LyricsPosition`.

From `:core` models / `:app` viewmodels (used by typed item wrappers):

- `com.revenge.music.models.MediaMetadata` (at least `title`, `artists: List<...>` with `name`,
  `thumbnailUrl: String?`, `suggestedBy: String?`, `duration: Long`, `explicit: Boolean`).
- `Song`, `Album`, `Artist`, `Playlist` wrappers with `.song`, `.album`, `.artist`, `.playlist`
  inner records, plus `thumbnails`.
- `YTItem` and its `SongItem`/`AlbumItem`/`ArtistItem` subtypes with `id`, `title`, `artists`,
  `duration`, `year`, `explicit`, `isPodcastEpisode`.
- `com.revenge.music.viewmodels.DayUsageData` with `totalMs: Long` (used by `ExpressiveBarChart`).
- `OptionStats` (used by `ChoiceChipsRow`).

CompositionLocals provided by `MainActivity` and consumed by the design system:

- `LocalGlassEffectConfig: ProvidableCompositionLocal<GlassEffectConfig>` default
  `GlassEffectConfig()`.
- `LocalAppBackdrop: ProvidableCompositionLocal<Backdrop>` with an error default when no backdrop is
  provided.
- `LocalShimmerTheme` (provided as `getShimmerTheme()`), `LocalHapticFeedback`,
  `LocalContentColor`, `LocalDownloadUtil`, `LocalDatabase`, `LocalPlayerConnection`,
  `LocalPlayerAwareWindowInsets`.

Third-party APIs that are part of the contract: Material Kolor `rememberDynamicColorScheme`,
`ColorSpec.SpecVersion.SPEC_2025`, `PaletteStyle.{Monochrome,TonalSpot}`, `Score.score`; androidx
`Palette`; Coil 3 `imageLoader`/`ImageRequest`/`toBitmap`; `com.valentinilk.shimmer` `shimmer()`,
`defaultShimmerTheme`, `ShimmerTheme`.

---

## 16. Platform components, permissions, and storage

- No new permission is introduced by this cluster. The renderer writes through MediaStore on API 29+
  (no storage permission required for app-created images) and to the app cache on older APIs.
- `AndroidManifest.xml` already declares:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.FileProvider"
    android:exported="false"
    android:grantUriPermissions="true">
  <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
             android:resource="@xml/provider_paths" />
</provider>
```

- The authority string used by `saveBitmapAsFile` must be exactly
  `"${context.packageName}.FileProvider"`, matching the manifest `${applicationId}.FileProvider`.
  The debug build has application id `com.revenge.music.debug`, so the runtime authority is
  `com.revenge.music.debug.FileProvider`; do not hardcode the production id.
- The provider is not exported; sharing works only through the returned URI plus
  `FLAG_GRANT_READ_URI_PERMISSION`.
- `@xml/provider_paths` must continue to cover the `cacheDir/images` location used by the pre-Q path.
- There are no services, receivers, or exported activities in this cluster. Do not add any.
- Image output must work when the app runs in the background (no activity context assumptions beyond
  `Context`), and must not require `WRITE_EXTERNAL_STORAGE`.

---

## 17. Target files the implementer must write

Clean-room source files (exact paths; package names as in the file headers above):

```text
app/src/main/kotlin/com/revenge/music/ui/theme/Design.kt
app/src/main/kotlin/com/revenge/music/ui/theme/Font.kt
app/src/main/kotlin/com/revenge/music/ui/theme/PlayerColorExtractor.kt
app/src/main/kotlin/com/revenge/music/ui/theme/PlayerSliderColors.kt
app/src/main/kotlin/com/revenge/music/ui/theme/Theme.kt
app/src/main/kotlin/com/revenge/music/ui/theme/Type.kt
app/src/main/kotlin/com/revenge/music/ui/theme/bbh_bartle.kt

app/src/main/kotlin/com/revenge/music/ui/components/AnimatedRadioButton.kt
app/src/main/kotlin/com/revenge/music/ui/components/ExpressiveBarChart.kt

app/src/main/kotlin/com/revenge/music/ui/component/AlbumGradient.kt
app/src/main/kotlin/com/revenge/music/ui/component/AppNavigation.kt
app/src/main/kotlin/com/revenge/music/ui/component/AppearancePreviewCard.kt
app/src/main/kotlin/com/revenge/music/ui/component/AutoPlaylistButton.kt
app/src/main/kotlin/com/revenge/music/ui/component/AutoResizeText.kt
app/src/main/kotlin/com/revenge/music/ui/component/BigSeekBar.kt
app/src/main/kotlin/com/revenge/music/ui/component/BitPerfectBadge.kt
app/src/main/kotlin/com/revenge/music/ui/component/BottomSheet.kt
app/src/main/kotlin/com/revenge/music/ui/component/BottomSheetMenu.kt
app/src/main/kotlin/com/revenge/music/ui/component/BottomSheetPage.kt
app/src/main/kotlin/com/revenge/music/ui/component/ChipsRow.kt
app/src/main/kotlin/com/revenge/music/ui/component/ColorPicker.kt
app/src/main/kotlin/com/revenge/music/ui/component/ContinueListening.kt
app/src/main/kotlin/com/revenge/music/ui/component/CreateAiPlaylistDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/CreateFromTasteDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/CreatePlaylistDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/Dialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/DraggableLyricsProviderList.kt
app/src/main/kotlin/com/revenge/music/ui/component/DraggableScrollBarOverlay.kt
app/src/main/kotlin/com/revenge/music/ui/component/EmptyPlaceholder.kt
app/src/main/kotlin/com/revenge/music/ui/component/EnumDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/EpisodeDetails.kt
app/src/main/kotlin/com/revenge/music/ui/component/EpisodeProgress.kt
app/src/main/kotlin/com/revenge/music/ui/component/ExpandableText.kt
app/src/main/kotlin/com/revenge/music/ui/component/FloatingNavBar.kt
app/src/main/kotlin/com/revenge/music/ui/component/FloatingNavigationToolbar.kt
app/src/main/kotlin/com/revenge/music/ui/component/GlassEffect.kt
app/src/main/kotlin/com/revenge/music/ui/component/GridMenu.kt
app/src/main/kotlin/com/revenge/music/ui/component/HeartBurstIcon.kt
app/src/main/kotlin/com/revenge/music/ui/component/HideOnScrollFAB.kt
app/src/main/kotlin/com/revenge/music/ui/component/HomeShortcutTile.kt
app/src/main/kotlin/com/revenge/music/ui/component/IconButton.kt
app/src/main/kotlin/com/revenge/music/ui/component/IntegrationCard.kt
app/src/main/kotlin/com/revenge/music/ui/component/Items.kt
app/src/main/kotlin/com/revenge/music/ui/component/Library.kt
app/src/main/kotlin/com/revenge/music/ui/component/LocalSongPickerDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/Lyrics.kt
app/src/main/kotlin/com/revenge/music/ui/component/LyricsImageCard.kt
app/src/main/kotlin/com/revenge/music/ui/component/LyricsV2.kt
app/src/main/kotlin/com/revenge/music/ui/component/Material3SettingsGroup.kt
app/src/main/kotlin/com/revenge/music/ui/component/Menu.kt
app/src/main/kotlin/com/revenge/music/ui/component/MetroLyrics.kt
app/src/main/kotlin/com/revenge/music/ui/component/NavigationTile.kt
app/src/main/kotlin/com/revenge/music/ui/component/NavigationTitle.kt
app/src/main/kotlin/com/revenge/music/ui/component/NewMenuComponents.kt
app/src/main/kotlin/com/revenge/music/ui/component/OnlineBlur.kt
app/src/main/kotlin/com/revenge/music/ui/component/PlaybackLogsDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/PlayerSlider.kt
app/src/main/kotlin/com/revenge/music/ui/component/PlayingIndicator.kt
app/src/main/kotlin/com/revenge/music/ui/component/PodcastHome.kt
app/src/main/kotlin/com/revenge/music/ui/component/Preference.kt
app/src/main/kotlin/com/revenge/music/ui/component/RandomizeGridItem.kt
app/src/main/kotlin/com/revenge/music/ui/component/RefreshAiRecommendationDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/RevengeComponents.kt
app/src/main/kotlin/com/revenge/music/ui/component/RevengeLyrics.kt
app/src/main/kotlin/com/revenge/music/ui/component/RingtoneProgressDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/RingtoneTrimmerDialog.kt
app/src/main/kotlin/com/revenge/music/ui/component/SearchBar.kt
app/src/main/kotlin/com/revenge/music/ui/component/SongDnaBottomSheet.kt
app/src/main/kotlin/com/revenge/music/ui/component/SortHeader.kt
app/src/main/kotlin/com/revenge/music/ui/component/SpeedDialGridItem.kt
app/src/main/kotlin/com/revenge/music/ui/component/SquigglySlider.kt
app/src/main/kotlin/com/revenge/music/ui/component/ThumbnailCornerRadiusSelector.kt
app/src/main/kotlin/com/revenge/music/ui/component/UpdaterComponents.kt
app/src/main/kotlin/com/revenge/music/ui/component/VolumeSlider.kt
app/src/main/kotlin/com/revenge/music/ui/component/WavySlider.kt

app/src/main/kotlin/com/revenge/music/ui/component/floatingtabbar/FloatingTabBar.kt

app/src/main/kotlin/com/revenge/music/ui/component/musicflow/MusicFlowEntryCard.kt
app/src/main/kotlin/com/revenge/music/ui/component/musicflow/MusicFlowPage.kt
app/src/main/kotlin/com/revenge/music/ui/component/musicflow/MusicFlowTopMenu.kt

app/src/main/kotlin/com/revenge/music/ui/component/shimmer/ButtonPlaceholder.kt
app/src/main/kotlin/com/revenge/music/ui/component/shimmer/GridItemPlaceholder.kt
app/src/main/kotlin/com/revenge/music/ui/component/shimmer/ListItemPlaceholder.kt
app/src/main/kotlin/com/revenge/music/ui/component/shimmer/ShimmerHost.kt
app/src/main/kotlin/com/revenge/music/ui/component/shimmer/TextPlaceholder.kt

app/src/main/kotlin/com/revenge/music/ui/component/backdrop/Backdrop.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/BackdropEffectScope.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/DrawBackdropModifier.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/Platform.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/RuntimeShader.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/RuntimeShaderCache.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/backdrops/Backdrop.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/backdrops/CanvasBackdrop.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/backdrops/CombinedBackdrop.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/backdrops/EmptyBackdrop.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/backdrops/LayerBackdrop.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/backdrops/LayerBackdropModifier.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/effects/Blur.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/effects/ColorFilter.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/effects/Lens.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/effects/RenderEffect.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/highlight/Highlight.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/highlight/HighlightModifier.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/highlight/HighlightStyle.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/internal/InverseLayerScope.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/internal/LayerRecorder.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/internal/Outline.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/internal/Paint.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/internal/RenderEffect.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/internal/Shaders.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/internal/ShapeProvider.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/shadow/InnerShadow.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/shadow/InnerShadowModifier.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/shadow/Shadow.kt
app/src/main/kotlin/com/revenge/music/ui/component/backdrop/shadow/ShadowModifier.kt

app/src/main/kotlin/com/revenge/music/utils/ComposeToImage.kt
```

Test files the implementer must write (deterministic, CI-runnable):

```text
app/src/test/kotlin/com/revenge/music/ui/theme/DesignTokensTest.kt
app/src/test/kotlin/com/revenge/music/ui/theme/TypographyContractTest.kt
app/src/test/kotlin/com/revenge/music/ui/theme/ThemeColorSchemeTest.kt          (Robolectric)
app/src/test/kotlin/com/revenge/music/ui/theme/PlayerColorExtractorTest.kt      (Robolectric)
app/src/test/kotlin/com/revenge/music/ui/theme/PlayerSliderColorsTest.kt        (Robolectric + Compose test rule)
app/src/test/kotlin/com/revenge/music/ui/component/GlassEffectContractTest.kt
app/src/test/kotlin/com/revenge/music/ui/component/floatingtabbar/FloatingTabBarScrollConnectionTest.kt
app/src/test/kotlin/com/revenge/music/ui/component/AutoResizeTextContractTest.kt
app/src/test/kotlin/com/revenge/music/utils/ComposeToImageContractTest.kt       (Robolectric)
app/src/androidTest/kotlin/com/revenge/music/ui/DesignSystemSemanticsTest.kt
app/src/androidTest/kotlin/com/revenge/music/ui/ShareCardScreenshotTest.kt
```

Preview/screenshot targets: `revengeTheme` previews for light/dark/pure-black/monochrome; a
`LyricsImageCard` 1:1 preview per `LyricsBackgroundStyle`; a `FloatingTabBar` inline/expanded pair; a
`RevengeComponents` gallery (button variants × sizes, empty/loading/error, list/grid item,
shimmer); a `BottomSheet` collapsed/expanded pair.

---

## 18. Acceptance tests

All tests are deterministic: no wall-clock waits longer than a single frame idle, no network, no
real device storage dependence (Robolectric or in-memory fakes). Where a visual is asserted, assert
layout invariants and semantics, not pixel snapshots, unless the test is explicitly a preview test.

### 18.1 Tokens (`DesignTokensTest`)

T1. Every `RevengeColor` value equals the ARGB in §3.1, including alpha on border/divider tokens.
T2. `RevengeSpacing` and `RevengeRadius` values equal §3.2/§3.3 exactly (Dp equality).
T3. Every `RevengeSize` value equals §3.4 exactly.
T4. `RevengeZIndex` values are strictly increasing in the documented order and equal the literals.
T5. `RevengeMotion.durationQuick/Standard/Deliberate == 140/220/320`;
`quickTween<Float>().durationMillis == 140`, etc.
T6. `RevengeColor.SurfaceCardDark === RevengeColor.DarkSurfaceCard` and
`SurfaceElevatedDark === DarkSurfaceElevated` (same value).

### 18.2 Typography (`TypographyContractTest`)

U1. `getTypography(someFamily)` produces each role’s size, line height, letter spacing and weight
per §4.2; all display/headline/title/label roles use `someFamily`; all body roles use the same
family when `plainFont` is omitted.
U2. `getTypography(brand, plain)` uses `brand` for non-body roles and `plain` for the three body
roles only.
U3. `labelSmall.fontFeatureSettings == "tnum"`.
U4. `AppFont.fromValue` maps every declared value and maps `""`, `"nonsense"` to `SYSTEM`.
U5. All four font families resolve their expected `R.font` resource and expose Normal/Medium/Bold
weights (assert number of fonts and weight set via `FontFamily` inspection where available, or via a
Robolectric resource assertion).

### 18.3 Theme composition (`ThemeColorSchemeTest`, Robolectric)

V1. `revengeTheme(darkTheme = true)` yields `colorScheme.background == RevengeColor.DarkCanvas`.
V2. `revengeTheme(darkTheme = true, pureBlack = true)` yields `background == Color.Black`,
`surfaceContainerLowest == Color.Black` and the four other `surfaceContainer*` values from §5.3.
V3. `revengeTheme(darkTheme = false)` leaves the light scheme’s background equal to the base
dynamic-light/tonal-spot `background` (i.e. no professional dark remap).
V4. On an API 31+ Robolectric config, `revengeTheme(themeColor = DefaultThemeColor)` does not use the
tonal-spot branch (observable via a different scheme from the same call with `BrandThemeColor`).
V5. `revengeTheme(isMonochrome = true)` yields a scheme where every converted role has HSL
saturation 0 but unchanged alpha.
V6. `ColorScheme.toProfessionalDarkColorScheme(false).pureBlack(false)` equals
`toProfessionalDarkColorScheme(false)` (alias identity).
V7. `ColorSaver` round-trips `Color(0x80123456)`, preserving alpha.
V8. Changing `SelectedFontKey` while composed recomposes the provided `MaterialTheme.typography`
without throwing; unknown font values fall back to the default family.
V9. `Bitmap.extractGradientColors` on a solid-blue bitmap returns two colours where the first is at
least as luminous as the second; on a black bitmap it returns two non-crashing colours.

### 18.4 Colour extraction (`PlayerColorExtractorTest`, Robolectric)

W1. A vibrant red bitmap with a null-heavy palette returns three colours; alpha is 255; the third is
black; the second equals the first scaled by 0.6 per channel within 1/255.
W2. An empty `Palette` (all swatches null) returns three colours derived from `fallbackColor`, and
never throws.
W3. Calling `extractGradientColors` from a coroutine on `Dispatchers.Main` in a Robolectric test
completes without `NetworkOnMainThreadException`/blocking assertion.
W4. A dominant muted colour (low saturation) uses the fallback 1.1× saturation path (observable as
less saturated output than the vibrant path for the same base colour).

### 18.5 Slider colours (`PlayerSliderColorsTest`)

X1. `DEFAULT` + dark → inactive track is `outline` at 0.4 alpha; `DEFAULT` + light →
`onSurface` at 0.3 alpha.
X2. For every non-`DEFAULT` `PlayerBackgroundStyle` entry, inactive track is white at 0.4 alpha and
is identical across entries.
X3. `activeTrackColor`, `thumbColor`, and active/disabled tick colours equal the supplied
`activeColor`; `disabledActiveTrackColor == activeColor`.

### 18.6 Glass contract (`GlassEffectContractTest`)

Y1. `isGlassSupported(30) == false`, `isGlassSupported(31) == true`, `isGlassSupported(34) == true`.
Y2. `glassSaturation(0f) == 1f`, `glassSaturation(1f) == 1.5f`, `glassSaturation(2f) == 2f`, and
values outside 0..2 clamp.
Y3. `glassResolutionScale(0f) == 1f`; `glassResolutionScale(8f) == 0.33f`;
`glassResolutionScale(16f) == 0.33f`; monotonic non-increasing between 0 and 8.
Y4. `GlassEffectConfig().isEnabledFor(PLAYER/MINI_PLAYER/NAV_BAR)` is false when `globalEnabled` is
false; with `globalEnabled = true` each per-component flag independently disables its component.
Y5. `Modifier.liquidGlass` returns the receiver unchanged when `isGlassSupported()` is false (assert
on a Robolectric API 30 configuration: no exception and no composition-local lookup of
`LocalAppBackdrop`).
Y6. Macrobenchmark/preview smoke: `liquidGlass` with default config on API 34 renders without
throwing; `blur(0f)` and `colorControls(0f, 1f, 1f)` are no-ops; `opacity` is applied.

### 18.7 FloatingTabBar (`FloatingTabBarScrollConnectionTest`)

Z1. With `OnScrollDown` and threshold 50, `onPreScroll(Offset(0, -49))` leaves `isInline` false;
another `-1` flips it to true; another `-50` keeps it true.
Z2. With `OnScrollUp`, the directions are mirrored.
Z3. `Never` never changes `isInline` regardless of accumulated scroll.
Z4. A direction reversal resets the accumulator: `-40`, then `+30` must not flip.
Z5. `expand()` and `inline()` set the state and reset the accumulator; calling them twice is
idempotent.
Z6. The connection returns `Offset.Zero` from `onPreScroll` for every input.
Z7. With no tabs registered, `FloatingTabBar` renders without crash and the inline tab area is
empty (Compose test).
Z8. Registering two standalone tabs keeps only the last; the inline bar shows the selected
registered tab, else the last inline tab, else the first tab.

### 18.8 Auto-resize and text (`AutoResizeTextContractTest`)

AA1. `FontSizeRange(min = 8.sp, max = 4.sp)` throws `IllegalArgumentException`;
`step = 0.sp` throws.
AA2. `rememberAdjustedFontSize("", ...)` returns `minFontSize`.
AA3. Longer text never produces a larger computed size than shorter text for the same bounds.
AA4. The computed size is always within `[minFontSize, initialFontSize]` (or the documented larger
buckets), and a 20-iteration binary search terminates.

### 18.9 Share rendering (`ComposeToImageContractTest`, Robolectric)

AB1. `createLyricsImage(..., width = 100, height = 100)` returns a bitmap with `width == 2160` and
`height == 2160` (proves the documented size invariant).
AB2. `SOLID` style with `backgroundColor = 0xFF00FF00` produces a bitmap whose centre pixel is
green; with `backgroundColor = null`, the centre is `0xFF121212`.
AB3. A null/blank `coverArtUrl` and an unreachable URL both complete and return a bitmap (no throw).
AB4. `lyricsAlignment` affects the horizontal placement of non-empty lyrics (compare bounding column
ranges for `ALIGN_NORMAL` vs `ALIGN_OPPOSITE`), and no exception is thrown for CENTER.
AB5. On Robolectric API 29+, `saveBitmapAsFile` returns a `content://media/...` URI and writes PNG
bytes whose header is the PNG signature. On API 28, it returns a `content://` URI with the
`.FileProvider` authority and the file exists under `cacheDir/images`.
AB6. The returned bitmap is not recycled by the save call.

### 18.10 Screenshare preview (`ShareCardScreenshotTest`, instrumented/preview)

AC1. `LyricsImageCard` renders each `LyricsBackgroundStyle` without throwing, at a 1:1 parent.
AC2. The card shows the metadata title, joined artist names and the supplied lyric text in the
semantics tree (text nodes), and the footer app name.
AC3. Switching `backgroundStyle` from SOLID to GRADIENT does not crash while the gradient palette
loads; the card shows the solid fallback first.
AC4. The card’s lyrics text is single-block and does not overflow the card bounds (assert text node
bounds within the card bounds).

### 18.11 State and semantics (`DesignSystemSemanticsTest`)

AD1. `EmptyPlaceholder` renders icon, text and optional description/action; with no action the
button count is zero; the decorative image contributes no content description.
AD2. `RevengeErrorState` always contains a clickable “Try Again” node; clicking calls the retry
callback exactly once.
AD3. `RevengeLoadingState` with a message exposes the message text and a progress indicator; without
a message exposes only progress.
AD4. `AnimatedRadioButton` exposes `Role.RadioButton`; with `enabled = false` or `onClick = null`
the click action is absent.
AD5. `TopSearch` field exposes content description “Search”; setting `active = true` adds state
description “Suggestions available”; pressing back calls `onActiveChange(false)`.
AD6. `isRouteSelected(null, "home", MainScreens) == false`;
`isRouteSelected("home/album/1", "home", MainScreens) == true`;
`isRouteSelected("library", "home", MainScreens) == false`.
AD7. `AppNavigationBar` with `slimNav = true` shows no label text nodes but each item still has its
screen title as content description.
AD8. Pure-black theme: `AppNavigationBar(pureBlack = true)` uses `Color.Black` as the container and a
white 8% top border (assert via captured colour state in a Robolectric Compose test).

### 18.12 Lists, grids, shimmer

AE1. `ListItem` with `isActive = true` uses `secondaryContainer`; with `isSelected = true,
drawHighlight = true` uses primary at 40% alpha; with both false uses the caller colour.
AE2. `ListItem(isAvailable = false)` still reports its title text and adds an offline overlay
(assert one additional image node with no content description).
AE3. `GridItem` width is `currentGridThumbnailHeight() * thumbnailRatio` when `fillMaxWidth = false`
and fills width when true.
AE4. `PlaylistThumbnail` with 0, 1, and 4 thumbnails renders 1, 1, and 4 image nodes respectively.
AE5. `ShimmerHost` + `TextPlaceholder` keeps the same placeholder widths across recomposition
(randomness is remembered).
AE6. `getShimmerTheme` returns 800ms linear tween with 250ms delay and three surfaceVariant stops at
25/50/25% alpha.

### 18.13 Process recreation

AF1. `rememberBottomSheetState(..., initialAnchor = expandedAnchor)` restores the expanded anchor
after a configuration change (`rememberSaveable`), reopening expanded.
AF2. `ColorSaver`-saved `themeColor` survives process recreation in `MainActivity`; the recreated
activity applies the same seed.
AF3. `AppFont.CUSTOM` with a path that no longer exists falls back to `FontFamily.Default` after
process recreation without clearing the stored path.

### 18.14 Build gates

AG1. `./gradlew :app:compileUniversalFossDebugKotlin` succeeds with the listed public APIs.
AG2. No new dependency, permission, provider, service, or exported component is introduced.
AG3. The Apache-2.0 notices for backdrop and floating tab bar remain in
`THIRD_PARTY_NOTICES.md`, and no RETAIN file (font assets, `Design.kt`) changes value.
AG4. A preview build renders every preview target in §17 without exceptions.

---

## 19. Non-goals

- No new design token values, colour roles, type roles, or component APIs beyond those listed.
- No screen-level layouts, business logic, navigation graph ownership, playback state, or data
  loading; this cluster only supplies theme, reusable UI, backdrop rendering, navigation chrome,
  and bitmap share rendering.
- No new permissions, exported components, background services, or storage locations.
- No change to the retained token values or bundled OFL font assets.
- Backdrop and floating-tab-bar internals may be sourced from the verified third-party projects;
  their public surface must match §9 and §10, but internal file layout is not a behavioural
  contract beyond compiling the listed files.
