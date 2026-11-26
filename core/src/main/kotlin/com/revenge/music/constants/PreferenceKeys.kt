package com.revenge.music.constants

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

// Thin aliases keep the long declaration list readable without changing the
// public type of any key.
private fun boolKey(name: String): Preferences.Key<Boolean> = booleanPreferencesKey(name)

private fun textKey(name: String): Preferences.Key<String> = stringPreferencesKey(name)

private fun intKey(name: String): Preferences.Key<Int> = intPreferencesKey(name)

private fun longKey(name: String): Preferences.Key<Long> = longPreferencesKey(name)

private fun floatKey(name: String): Preferences.Key<Float> = floatPreferencesKey(name)

private fun textSetKey(name: String): Preferences.Key<Set<String>> = stringSetPreferencesKey(name)

// ---------------------------------------------------------------------------
// Account / Spotify
// ---------------------------------------------------------------------------
val DataSaverEnabledKey = boolKey("dataSaverEnabled")
val SpotifySpDcKey = textKey("spotify_sp_dc")
val SpotifySpKeyKey = textKey("spotify_sp_key")
val SpotifyAccountNameKey = textKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = textKey("spotify_account_avatar_url")
val SpotifyAccessTokenKey = textKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longKey("spotify_access_token_expires_at")
val EnableLegacyIconKey = boolKey("enableLegacyIcon")
val AppIconTypeKey = textKey("app_icon_type")

// ---------------------------------------------------------------------------
// Haptics and refresh rate
// ---------------------------------------------------------------------------
val EnableHighRefreshRateKey = boolKey("enableHighRefreshRate")
val EnableHapticsKey = boolKey("enableHaptics")
val HapticIntensityKey = textKey("haptic_intensity")
val EnableClickHapticsKey = boolKey("enable_click_haptics")
val EnableLongPressHapticsKey = boolKey("enable_long_press_haptics")
val EnableScrollEdgeHapticsKey = boolKey("enable_scroll_edge_haptics")
val EnableSliderHapticsKey = boolKey("enable_slider_haptics")

enum class HapticIntensity(val label: String, val scaleFactor: Float) {
  LIGHT("Light", 0.4f),
  MEDIUM("Medium", 0.75f),
  STRONG("Strong", 1.0f);

  companion object {
    fun fromName(name: String?): HapticIntensity =
      entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
  }
}

// ---------------------------------------------------------------------------
// Appearance
// ---------------------------------------------------------------------------
val AlbumArtThemeRecoloringKey = boolKey("albumArtThemeRecoloring")
val SelectedThemeColorKey = intKey("selectedThemeColor")
val DarkModeKey = textKey("darkMode")
val PureBlackKey = boolKey("pureBlack")
val PureBlackMiniPlayerKey = boolKey("pureBlackMiniPlayer")
val IsMonochromeThemeKey = boolKey("is_monochrome_theme")
val DensityScaleKey = floatKey("density_scale_factor")
val RevengeWrappedBannerDismissedKey = boolKey("revenge_wrapped_banner_dismissed_2026")

enum class DensityScale(val value: Float, val label: String) {
  NATIVE(1.0f, "Native (100%)"),
  SLIGHTLY_COMPACT(0.85f, "Slightly Compact (85%)"),
  COMPACT(0.75f, "Compact (75%)"),
  VERY_COMPACT(0.65f, "Very Compact (65%)"),
  ULTRA_COMPACT(0.55f, "Ultra Compact (55%)");

  companion object {
    fun fromValue(value: Float): DensityScale = entries.firstOrNull { it.value == value } ?: NATIVE
  }
}

val DefaultOpenTabKey = textKey("defaultOpenTab")
val GridItemsSizeKey = textKey("gridItemSize")
val SliderStyleKey = textKey("sliderStyle")
val SquigglySliderKey = boolKey("squigglySlider")
val WavyPlayPauseKey = boolKey("wavyPlayPause")
val SwipeToSongKey = boolKey("SwipeToSong")
val SwipeToRemoveSongKey = boolKey("SwipeToRemoveSong")
val UseNewPlayerDesignKey = boolKey("useNewPlayerDesign")
val UseNewMiniPlayerDesignKey = boolKey("useNewMiniPlayerDesign")
val ShowCodecOnPlayerKey = boolKey("showCodecOnPlayer")
val HidePlayerSliderKey = boolKey("hidePlayerSlider")
val HidePlayerThumbnailKey = boolKey("hidePlayerThumbnail")
val ThumbnailCornerRadiusKey = floatKey("thumbnailCornerRadius")
val CropAlbumArtKey = boolKey("cropAlbumArt")
val SeekExtraSeconds = boolKey("seekExtraSeconds")
val VarispeedKey = boolKey("varispeed")

// ---------------------------------------------------------------------------
// Playback behaviour
// ---------------------------------------------------------------------------
val PauseOnMute = boolKey("pauseOnMute")
val ResumeOnBluetoothConnectKey = boolKey("resumeOnBluetoothConnect")
val AutoplayOnAutomotiveConnectKey = boolKey("autoplayOnAutomotiveConnect")
val KeepScreenOn = boolKey("keepScreenOn")

enum class SliderStyle {
  DEFAULT,
  WAVY,
  SLIM,
}

// ---------------------------------------------------------------------------
// Language / content region
// ---------------------------------------------------------------------------
const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = textKey("appLanguage")
val ContentLanguageKey = textKey("contentLanguage")
val ContentCountryKey = textKey("contentCountry")
val SuggestionRegionKey = textKey("suggestionRegion")
val EnableKugouKey = boolKey("enableKugou")
val FetchFasterLyricsKey = boolKey("fetchFasterLyrics")

// ---------------------------------------------------------------------------
// Lyrics providers and content filters
// ---------------------------------------------------------------------------
val EnableLrcLibKey = boolKey("enableLrclib")
val EnableBetterLyricsKey = boolKey("enableBetterLyrics")
val EnableSimpMusicKey = boolKey("enableSimpMusic")
val EnableYouLyPlusKey = boolKey("enableYouLyPlus")
val EnablePaxsenixKey = boolKey("enablePaxsenix")
val HideExplicitKey = boolKey("hideExplicit")
val SponsorBlockEnabledKey = boolKey("sponsor_block_enabled")
val HideVideoSongsKey = boolKey("hideVideoSongs")
val HideYoutubeShortsKey = boolKey("hideYoutubeShorts")
val ShowArtistDescriptionKey = boolKey("showArtistDescription")
val ShowArtistSubscriberCountKey = boolKey("showArtistSubscriberCount")
val ShowMonthlyListenersKey = boolKey("showMonthlyListeners")
val ShowArtistVideoKey = boolKey("showArtistVideo")
val ShowArtistBackgroundVideoKey = boolKey("showArtistBackgroundVideo")
val ProxyEnabledKey = boolKey("proxyEnabled")
val ProxyUrlKey = textKey("proxyUrl")
val ProxyTypeKey = textKey("proxyType")
val ProxyUsernameKey = textKey("proxyUsername")
val ProxyPasswordKey = textKey("proxyPassword")
val YtmSyncKey = boolKey("ytmSync")

// ---------------------------------------------------------------------------
// Streaming quality
// ---------------------------------------------------------------------------
val AudioQualityWifiKey = textKey("audioQualityWifi")
val AudioQualityCellularKey = textKey("audioQualityCellular")
val IpVersionKey = textKey("ipVersion")

enum class AudioQuality(
  val maxKbps: Int,
  val label: String,
  val detail: String,
  val hourly: String,
) {
  AUTO(Int.MAX_VALUE, "Auto", "Adaptive based on connection", "Variable"),
  LOW(64, "Low", "~64 kbps · data saver", "29 MB/hr"),
  MEDIUM(160, "Medium", "Standard · ~160 kbps Opus", "77 MB/hr"),
  HIGH(320, "High", "Addon high bitrate, YouTube fallback", "144 MB/hr"),
  LOSSLESS(Int.MAX_VALUE, "Lossless", "Addons bit-exact FLAC/ALAC, YouTube fallback", "300+ MB/hr"),
  OPUS(160, "Opus", "Standard ~160 kbps Opus", "77 MB/hr");

  fun permitsLossless(): Boolean = this == LOSSLESS || this == AUTO

  fun permitsHigh(): Boolean = this == LOSSLESS || this == AUTO || this == HIGH

  fun permitsAddons(): Boolean = this == LOSSLESS || this == AUTO || this == HIGH
}

val DownloadOnWifiOnlyKey = boolKey("downloadOnWifiOnly")
val DownloadWithMetadataKey = boolKey("downloadWithMetadata")

val AudioOffload = boolKey("enableOffload")

val SpatialAudioKey = boolKey("spatialAudio")

val AudioLoudnessPresetKey = textKey("audioLoudnessPreset")

enum class AudioLoudnessPreset(val gainOffsetMb: Int) {
  QUIET(-600),
  NORMAL(0),
  LOUD(300),
  AGGRESSIVE(700),
}

enum class PlaybackEngine {
  POTOKEN,
  BRAVEPIPE,
  AUTO,
}

val PlaybackEngineKey = textKey("playbackEngine")
val ShowSeekButtonsKey = boolKey("showSeekButtons")
val PersistentQueueKey = boolKey("persistentQueue")
val PersistentShuffleAcrossQueuesKey = boolKey("persistentShuffleAcrossQueues")
val RememberShuffleAndRepeatKey = boolKey("rememberShuffleAndRepeat")
val ShuffleModeKey = boolKey("shuffleMode")
val SkipSilenceKey = boolKey("skipSilence")
val SkipSilenceInstantKey = boolKey("skipSilenceInstant")
val AudioNormalizationKey = boolKey("audioNormalization")
val AutoLoadMoreKey = boolKey("autoLoadMore")
val DisableLoadMoreWhenRepeatAllKey = boolKey("disableLoadMoreWhenRepeatAll")
val AutoDownloadOnLikeKey = boolKey("autoDownloadOnLike")
val SimilarContent = boolKey("similarContent")
val AutoSkipNextOnErrorKey = boolKey("autoSkipNextOnError")
val StopMusicOnTaskClearKey = boolKey("stopMusicOnTaskClear")
val ShufflePlaylistFirstKey = boolKey("shufflePlaylistFirst")
val PreventDuplicateTracksInQueueKey = boolKey("preventDuplicateTracksInQueue")
val CrossfadeEnabledKey = boolKey("crossfadeEnabled")
val CrossfadeDurationKey = floatKey("crossfadeDuration")
val CrossfadeGaplessKey = boolKey("crossfadeGapless")
val AutomixCrossfadeKey = boolKey("automixCrossfade")
val AutomixDebugOverlayKey = boolKey("automixDebugOverlay")

// ---------------------------------------------------------------------------
// Cache / export
// ---------------------------------------------------------------------------
val MaxImageCacheSizeKey = intKey("maxImageCacheSize")
val MaxSongCacheSizeKey = intKey("maxSongCacheSize")
val ExportDirectoryUriKey = textKey("exportDirectoryUri")
val ExportingSongIdsKey = textKey("exportingSongIds")
val ExportedSongIdsKey = textKey("exportedSongIds")

// Entries are "songId:percent", comma separated.
val ExportProgressKey = textKey("exportProgress")
val EnableExportAsMp3Key = boolKey("enableExportAsMp3")

// ---------------------------------------------------------------------------
// History and privacy
// ---------------------------------------------------------------------------
val PauseListenHistoryKey = boolKey("pauseListenHistory")
val PodcastHidePlayedKey = boolKey("podcastHidePlayed")
val PauseSearchHistoryKey = boolKey("pauseSearchHistory")
val DisableScreenshotKey = boolKey("disableScreenshot")

val EnableGoogleCastKey = boolKey("enableGoogleCast")

// ---------------------------------------------------------------------------
// Listen Together
// ---------------------------------------------------------------------------
val ListenTogetherServerUrlKey = textKey("listenTogetherServerUrl")
val ListenTogetherUsernameKey = textKey("listenTogetherUsername")
val ListenTogetherAutoApprovalKey = boolKey("listenTogetherAutoApproval")
val ListenTogetherSyncVolumeKey = boolKey("listenTogetherSyncVolume")
val ListenTogetherSmartResyncKey = boolKey("listenTogetherSmartResync")
val ListenTogetherBlockedUsersKey = textKey("listenTogetherBlockedUsers")
val ListenTogetherInTopBarKey = boolKey("listenTogetherInTopBar")

val ListenTogetherSessionTokenKey = textKey("listenTogetherSessionToken")
val ListenTogetherRoomCodeKey = textKey("listenTogetherRoomCode")
val ListenTogetherUserIdKey = textKey("listenTogetherUserId")
val ListenTogetherIsHostKey = boolKey("listenTogetherIsHost")
val ListenTogetherSessionTimestampKey = longKey("listenTogetherSessionTimestamp")

// ---------------------------------------------------------------------------
// Last.fm / scrobbling
// ---------------------------------------------------------------------------
val LastFMSessionKey = textKey("lastfmSession")
val LastFMUsernameKey = textKey("lastfmUsername")
val EnableLastFMScrobblingKey = boolKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = boolKey("lastfmUseNowPlaying")

val LastFMUseSendLikes = boolKey("lastfmUseSendLikes")

val ScrobbleDelayPercentKey = floatKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intKey("scrobbleDelaySeconds")

// ---------------------------------------------------------------------------
// Sort and filter preferences
// ---------------------------------------------------------------------------
val ChipSortTypeKey = textKey("chipSortType")
val SongSortTypeKey = textKey("songSortType")
val SongSortDescendingKey = boolKey("songSortDescending")
val DownloadedSongSortTypeKey = textKey("downloadedSongSortType")
val DownloadedSongSortDescendingKey = boolKey("downloadedSongSortDescending")
val PlaylistSongSortTypeKey = textKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = boolKey("playlistSongSortDescending")
val ArtistSortTypeKey = textKey("artistSortType")
val ArtistSortDescendingKey = boolKey("artistSortDescending")
val AlbumSortTypeKey = textKey("albumSortType")
val AlbumSortDescendingKey = boolKey("albumSortDescending")
val PlaylistSortTypeKey = textKey("playlistSortType")
val PlaylistSortDescendingKey = boolKey("playlistSortDescending")
val AddToPlaylistSortTypeKey = textKey("addToPlaylistSortType")
val AddToPlaylistSortDescendingKey = boolKey("addToPlaylistSortDescending")
val ArtistSongSortTypeKey = textKey("artistSongSortType")
val ArtistSongSortDescendingKey = boolKey("artistSongSortDescending")
val MixSortTypeKey = textKey("mixSortType")

// Deliberately shared with album sorting for backward compatibility.
val MixSortDescendingKey = boolKey("albumSortDescending")

val LocalSongsMinDurationSecondsKey = intKey("local_songs_min_duration_seconds")
val LocalSongsExcludedFoldersKey = textSetKey("local_songs_excluded_folders")
val LocalSongsIncludedFoldersKey = textSetKey("local_songs_included_folders")
val LocalSongsFolderFilterModeKey = textKey("local_songs_folder_filter_mode")
val LocalSongsSortTypeKey = textKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = boolKey("local_songs_sort_descending")

val SongFilterKey = textKey("songFilter")
val ArtistFilterKey = textKey("artistFilter")
val AlbumFilterKey = textKey("albumFilter")

val LastFullSyncKey = longKey("last_full_sync")

const val SYNC_COOLDOWN = 30 * 60L

val ArtistViewTypeKey = textKey("artistViewType")
val AlbumViewTypeKey = textKey("albumViewType")
val PlaylistViewTypeKey = textKey("playlistViewType")

val PlaylistEditLockKey = boolKey("playlistEditLock")
val QuickPicksKey = textKey("discover")
val LyricsProviderOrderKey = textKey("lyricsProviderOrder")
val QueueEditLockKey = boolKey("queueEditLockV2")
val RandomizeHomeOrderKey = boolKey("randomizeHomeOrder")
val AlbumCanvasEnabledKey = boolKey("albumCanvasEnabled")

// ---------------------------------------------------------------------------
// Home screen shelves
// ---------------------------------------------------------------------------
val ShowLikedPlaylistKey = boolKey("show_liked_playlist")
val ShowDownloadedPlaylistKey = boolKey("show_downloaded_playlist")
val ShowExportedPlaylistKey = boolKey("show_exported_playlist")
val ShowTopPlaylistKey = boolKey("show_top_playlist")
val ShowBottomPlaylistKey = boolKey("show_bottom_playlist")
val ShowCachedPlaylistKey = boolKey("show_cached_playlist")
val ShowSpeedDialKey = boolKey("showSpeedDial")
val ShowHomeHeroCarouselKey = boolKey("showHomeHeroCarousel")
val ShowHomeCommunityKey = boolKey("showHomeCommunity")
val ShowHomeMoodAndGenresKey = boolKey("showHomeMoodAndGenres")
val ShowCommentButtonKey = boolKey("show_comment_button")

// ---------------------------------------------------------------------------
// Music flow / addons
// ---------------------------------------------------------------------------
val MusicFlowEnabledKey = boolKey("music_flow_enabled")
val MusicFlowAutoplayKey = boolKey("music_flow_autoplay")
val MusicFlowAutoNextKey = boolKey("music_flow_auto_next")
val MusicFlowAnimatedBackgroundKey = boolKey("music_flow_animated_background")
val MusicFlowLyricsKey = boolKey("music_flow_lyrics")
val MusicFlowCellularPrefetchKey = boolKey("music_flow_cellular_prefetch")
val MusicFlowHideRecentKey = boolKey("music_flow_hide_recent")
val PreferExtensionAudioKey = boolKey("prefer_extension_audio")
val PreferredAudioAddonIdKey = textKey("preferred_audio_addon_id")

// ---------------------------------------------------------------------------
// Enumerated user choices
// ---------------------------------------------------------------------------
enum class LibraryViewType {
  LIST,
  GRID;

  fun toggle() =
    when (this) {
      LIST -> GRID
      GRID -> LIST
    }
}

enum class SongFilter {
  LIBRARY,
  LIKED,
  DOWNLOADED,
  UPLOADED,
  EXPORTED,
}

enum class ArtistFilter {
  LIBRARY,
  LIKED,
}

enum class AlbumFilter {
  LIBRARY,
  LIKED,
  UPLOADED,
}

enum class SongSortType {
  CREATE_DATE,
  NAME,
  ARTIST,
  PLAY_TIME,
}

enum class PlaylistSongSortType {
  CUSTOM,
  CREATE_DATE,
  NAME,
  ARTIST,
  PLAY_TIME,
}

enum class AutoPlaylistSongSortType {
  CREATE_DATE,
  NAME,
  ARTIST,
  PLAY_TIME,
}

enum class ArtistSortType {
  CREATE_DATE,
  NAME,
  SONG_COUNT,
  PLAY_TIME,
}

enum class ArtistSongSortType {
  CREATE_DATE,
  NAME,
  PLAY_TIME,
}

enum class AlbumSortType {
  CREATE_DATE,
  NAME,
  ARTIST,
  YEAR,
  SONG_COUNT,
  LENGTH,
  PLAY_TIME,
}

enum class PlaylistSortType {
  CREATE_DATE,
  NAME,
  SONG_COUNT,
  LAST_UPDATED,
}

enum class MixSortType {
  CREATE_DATE,
  NAME,
  LAST_UPDATED,
}

enum class GridItemSize {
  BIG,
  SMALL,
}

enum class MyTopFilter {
  ALL_TIME,
  DAY,
  WEEK,
  MONTH,
  YEAR;

  fun toTimeMillis(): Long =
    when (this) {
      DAY -> LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()
      WEEK -> LocalDateTime.now().minusWeeks(1).toInstant(ZoneOffset.UTC).toEpochMilli()
      MONTH -> LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC).toEpochMilli()
      YEAR -> LocalDateTime.now().minusMonths(12).toInstant(ZoneOffset.UTC).toEpochMilli()
      ALL_TIME -> 0
    }
}

enum class QuickPicks {
  QUICK_PICKS,
  LAST_LISTEN,
}

enum class PreferredLyricsProvider {
  LRCLIB,
  KUGOU,
  BETTER_LYRICS,
  SIMPMUSIC,
  YOULYPLUS,
  PAXSENIX,
  UNISON,
}

enum class PlayerButtonsStyle {
  DEFAULT,
  PRIMARY,
  TERTIARY,
}

enum class PlayerBackgroundStyle {
  DEFAULT,
  GRADIENT,
  BLUR,
  GLOW_ANIMATED,
  APPLE_MUSIC,
  LIVE_MESH,
  LIQUID_GLASS,
}

val TopSize = textKey("topSize")
val HistoryDuration = floatKey("historyDuration")

// ---------------------------------------------------------------------------
// Player look and lyrics
// ---------------------------------------------------------------------------
val PlayerButtonsStyleKey = textKey("player_buttons_style")
val PlayerBackgroundStyleKey = textKey("playerBackgroundStyle")
val MiniPlayerBackgroundStyleKey = textKey("miniPlayerBackgroundStyle")
val ShowLyricsKey = boolKey("showLyrics")
val ShowLyricsOnPlayerKey = boolKey("showLyricsOnPlayer")
val SwipeLyricsKey = boolKey("swipeLyrics")
val EnableLyricsThumbnailPlayPauseKey = boolKey("enableLyricsThumbnailPlayPause")
val LyricsTextPositionKey = textKey("lyricsTextPosition")
val LyricsClickKey = boolKey("lyricsClick")
val LyricsScrollKey = boolKey("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = boolKey("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = boolKey("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = boolKey("lyricsRomanizeChinese")
val LyricsRomanizeRussianKey = boolKey("lyricsRomanizeRussian")
val LyricsRomanizeUkrainianKey = boolKey("lyricsRomanizeUkrainian")
val LyricsRomanizeSerbianKey = boolKey("lyricsRomanizeSerbian")
val LyricsRomanizeBulgarianKey = boolKey("lyricsRomanizeBulgarian")
val LyricsRomanizeBelarusianKey = boolKey("lyricsRomanizeBelarusian")
val LyricsRomanizeKyrgyzKey = boolKey("lyricsRomanizeKyrgyz")
val LyricsRomanizeMacedonianKey = boolKey("lyricsRomanizeMacedonian")
val LyricsRomanizeHindiKey = boolKey("lyricsRomanizeHindi")
val LyricsRomanizePunjabiKey = boolKey("lyricsRomanizePunjabi")
val LyricsRomanizeAsMainKey = boolKey("lyricsRomanizeAsMain")
val LyricsRomanizeCyrillicByLineKey = boolKey("lyricsRomanizeCyrillicByLine")

// ---------------------------------------------------------------------------
// AI / translation
// ---------------------------------------------------------------------------
val OpenRouterApiKey = textKey("openRouterApiKey")
val AiProviderKey = textKey("aiProvider")
val OpenRouterBaseUrlKey = textKey("openRouterBaseUrl")
val OpenRouterModelKey = textKey("openRouterModel")
val TranslateModeKey = textKey("translateMode")
val TranslateLanguageKey = textKey("translateLanguage")
val AutoTranslateKey = boolKey("autoTranslate")
val AiRecommendationsKey = boolKey("aiRecommendations")
val LastAiRecommendationUpdateDayKey = longKey("lastAiRecommendationUpdateDay")
val CreateFromTasteDailyKey = boolKey("createFromTasteDaily")
val DeeplApiKey = textKey("deeplApiKey")
val DeeplFormalityKey = textKey("deeplFormality")

val LyricsGlowEffectKey = boolKey("lyricsGlowEffect")
val AppleMusicLyricsBlurKey = boolKey("appleMusicLyricsBlur")
val LyricsStandardBlurKey = boolKey("lyricsStandardBlur")
val HideStatusBarOnFullscreenKey = boolKey("hideStatusBarOnFullscreen")

val LyricsAnimationStyleKey = textKey("lyricsAnimationStyle")

enum class LyricsAnimationStyle {
  NONE,
  FADE,
  GLOW,
  SLIDE,
  KARAOKE,
  APPLE,
  APPLE_V2,
  revenge_1,
  LYRICS_V2,
  METRO_LYRICS,
}

val LyricsTextSizeKey = floatKey("lyricsTextSize")
val LyricsLineSpacingKey = floatKey("lyricsLineSpacing")

val PlayerVolumeKey = floatKey("playerVolume")
val RepeatModeKey = intKey("repeatMode")

val SearchSourceKey = textKey("searchSource")
val SwipeThumbnailKey = boolKey("swipeThumbnail")
val RotatingThumbnailKey = boolKey("rotatingThumbnail")
val CanvasThumbnailAnimationKey = boolKey("canvasThumbnailAnimation")
val SwipeSensitivityKey = floatKey("swipeSensitivity")

enum class SearchSource {
  LOCAL,
  ONLINE;

  fun toggle() =
    when (this) {
      LOCAL -> ONLINE
      ONLINE -> LOCAL
    }
}

// ---------------------------------------------------------------------------
// Login / session
// ---------------------------------------------------------------------------
val VisitorDataKey = textKey("visitorData")
val DataSyncIdKey = textKey("dataSyncId")
val InnerTubeCookieKey = textKey("innerTubeCookie")
val AccountNameKey = textKey("accountName")
val AccountEmailKey = textKey("accountEmail")
val AccountChannelHandleKey = textKey("accountChannelHandle")
val UseLoginForBrowse = boolKey("useLoginForBrowse")
val LastOpenedVersionCodeKey = intKey("lastOpenedVersionCode")

// ---------------------------------------------------------------------------
// Localisation tables
// ---------------------------------------------------------------------------
val LanguageCodeToName =
  mapOf(
    "af" to "Afrikaans",
    "az" to "Azərbaycan",
    "id" to "Bahasa Indonesia",
    "ms" to "Bahasa Malaysia",
    "ca" to "Català",
    "cs" to "Čeština",
    "da" to "Dansk",
    "de" to "Deutsch",
    "et" to "Eesti",
    "en-GB" to "English (UK)",
    "en" to "English (US)",
    "es" to "Español (España)",
    "es-419" to "Español (Latinoamérica)",
    "eu" to "Euskara",
    "fil" to "Filipino",
    "fr" to "Français",
    "fr-CA" to "Français (Canada)",
    "gl" to "Galego",
    "hr" to "Hrvatski",
    "zu" to "IsiZulu",
    "is" to "Íslenska",
    "it" to "Italiano",
    "sw" to "Kiswahili",
    "lt" to "Lietuvių",
    "hu" to "Magyar",
    "nl" to "Nederlands",
    "no" to "Norsk",
    "or" to "Odia",
    "uz" to "O‘zbe",
    "pl" to "Polski",
    "pt-PT" to "Português",
    "pt" to "Português (Brasil)",
    "ro" to "Română",
    "sq" to "Shqip",
    "sk" to "Slovenčina",
    "sl" to "Slovenščina",
    "fi" to "Suomi",
    "sv" to "Svenska",
    "bo" to "Tibetan བོད་སྐད།",
    "vi" to "Tiếng Việt",
    "tr" to "Türkçe",
    "bg" to "Български",
    "ky" to "Кыргызча",
    "kk" to "Қазақ Тілі",
    "mk" to "Македонски",
    "mn" to "Монгол",
    "ru" to "Русский",
    "sr" to "Српски",
    "uk" to "Українська",
    "el" to "Ελληνικά",
    "hy" to "Հայերեն",
    "iw" to "עברית",
    "ur" to "اردو",
    "ar" to "العربية",
    "fa" to "فارسی",
    "ne" to "नेपाली",
    "mr" to "मराठी",
    "hi" to "हिन्दी",
    "bn" to "বাংলা",
    "pa" to "ਪੰਜਾਬੀ",
    "gu" to "ગુજરાતી",
    "ta" to "தமிழ்",
    "te" to "తెలుగు",
    "kn" to "ಕನ್ನಡ",
    "ml" to "മലയാളം",
    "si" to "සිංහල",
    "th" to "ภาษาไทย",
    "lo" to "ລາວ",
    "my" to "ဗမာ",
    "ka" to "ქართული",
    "am" to "አማርኛ",
    "km" to "ខ្មែរ",
    "zh-CN" to "中文 (简体)",
    "zh-TW" to "中文 (繁體)",
    "zh-HK" to "中文 (香港)",
    "ja" to "日本語",
    "ko" to "한국어",
  )

val CountryCodeToName =
  mapOf(
    "DZ" to "Algeria",
    "AR" to "Argentina",
    "AU" to "Australia",
    "AT" to "Austria",
    "AZ" to "Azerbaijan",
    "BH" to "Bahrain",
    "BD" to "Bangladesh",
    "BY" to "Belarus",
    "BE" to "Belgium",
    "BO" to "Bolivia",
    "BA" to "Bosnia and Herzegovina",
    "BR" to "Brazil",
    "BG" to "Bulgaria",
    "KH" to "Cambodia",
    "CA" to "Canada",
    "CL" to "Chile",
    "HK" to "Hong Kong",
    "CO" to "Colombia",
    "CR" to "Costa Rica",
    "HR" to "Croatia",
    "CY" to "Cyprus",
    "CZ" to "Czech Republic",
    "DK" to "Denmark",
    "DO" to "Dominican Republic",
    "EC" to "Ecuador",
    "EG" to "Egypt",
    "SV" to "El Salvador",
    "EE" to "Estonia",
    "FI" to "Finland",
    "FR" to "France",
    "GE" to "Georgia",
    "DE" to "Germany",
    "GH" to "Ghana",
    "GR" to "Greece",
    "GT" to "Guatemala",
    "HN" to "Honduras",
    "HU" to "Hungary",
    "IS" to "Iceland",
    "IN" to "India",
    "ID" to "Indonesia",
    "IQ" to "Iraq",
    "IE" to "Ireland",
    "IL" to "Israel",
    "IT" to "Italy",
    "JM" to "Jamaica",
    "JP" to "Japan",
    "JO" to "Jordan",
    "KZ" to "Kazakhstan",
    "KE" to "Kenya",
    "KR" to "South Korea",
    "KW" to "Kuwait",
    "LA" to "Lao",
    "LV" to "Latvia",
    "LB" to "Lebanon",
    "LY" to "Libya",
    "LI" to "Liechtenstein",
    "LT" to "Lithuania",
    "LU" to "Luxembourg",
    "MK" to "Macedonia",
    "MY" to "Malaysia",
    "MT" to "Malta",
    "MX" to "Mexico",
    "ME" to "Montenegro",
    "MA" to "Morocco",
    "NP" to "Nepal",
    "NL" to "Netherlands",
    "NZ" to "New Zealand",
    "NI" to "Nicaragua",
    "NG" to "Nigeria",
    "NO" to "Norway",
    "OM" to "Oman",
    "PK" to "Pakistan",
    "PA" to "Panama",
    "PG" to "Papua New Guinea",
    "PY" to "Paraguay",
    "PE" to "Peru",
    "PH" to "Philippines",
    "PL" to "Poland",
    "PT" to "Portugal",
    "PR" to "Puerto Rico",
    "QA" to "Qatar",
    "RO" to "Romania",
    "RU" to "Russian Federation",
    "SA" to "Saudi Arabia",
    "SN" to "Senegal",
    "RS" to "Serbia",
    "SG" to "Singapore",
    "SK" to "Slovakia",
    "SI" to "Slovenia",
    "ZA" to "South Africa",
    "ES" to "Spain",
    "LK" to "Sri Lanka",
    "SE" to "Sweden",
    "CH" to "Switzerland",
    "TW" to "Taiwan",
    "TZ" to "Tanzania",
    "TH" to "Thailand",
    "TN" to "Tunisia",
    "TR" to "Turkey",
    "UG" to "Uganda",
    "UA" to "Ukraine",
    "AE" to "United Arab Emirates",
    "GB" to "United Kingdom",
    "US" to "United States",
    "UY" to "Uruguay",
    "VE" to "Venezuela (Bolivarian Republic)",
    "VN" to "Vietnam",
    "YE" to "Yemen",
    "ZW" to "Zimbabwe",
  )

val SuggestionRegionSlugToName =
  mapOf(
    "system" to "System Default",
    "us" to "Global (USA)",
    "in" to "India",
    "gb" to "United Kingdom",
    "ca" to "Canada",
    "au" to "Australia",
    "jp" to "Japan",
    "kr" to "South Korea",
    "de" to "Germany",
    "fr" to "France",
    "br" to "Brazil",
    "mx" to "Mexico",
    "ru" to "Russia",
    "it" to "Italy",
    "es" to "Spain",
    "nl" to "Netherlands",
    "se" to "Sweden",
    "no" to "Norway",
    "dk" to "Denmark",
    "fi" to "Finland",
    "pl" to "Poland",
    "tr" to "Turkey",
    "za" to "South Africa",
    "ng" to "Nigeria",
    "id" to "Indonesia",
    "my" to "Malaysia",
    "ph" to "Philippines",
    "th" to "Thailand",
    "vn" to "Vietnam",
    "tw" to "Taiwan",
    "hk" to "Hong Kong",
    "sg" to "Singapore",
    "ar" to "Argentina",
    "co" to "Colombia",
    "cl" to "Chile",
    "pe" to "Peru",
    "eg" to "Egypt",
    "sa" to "Saudi Arabia",
    "ae" to "United Arab Emirates",
    "il" to "Israel",
  )

// ---------------------------------------------------------------------------
// Scrobbling, lyrics and prefetch extras
// ---------------------------------------------------------------------------
val ListenBrainzEnabledKey = boolKey("listenbrainz_enabled")
val ListenBrainzTokenKey = textKey("listenbrainz_token")
val UnisonLyricsEnabledKey = boolKey("unison_lyrics_enabled")
val EnableCronetKey = boolKey("enable_cronet_http3")
val ForceOpusKey = boolKey("force_opus_audio")
val PreloadNextSongEnabledKey = boolKey("preload_next_song_enabled")
val PreloadNextSongLimitKey = intKey("preload_next_song_limit")
val PreloadLyricsEnabledKey = boolKey("preload_lyrics_enabled")

// ---------------------------------------------------------------------------
// Liquid glass
// ---------------------------------------------------------------------------
val LiquidGlassGlobalEnabledKey = boolKey("liquidGlassGlobalEnabled")
val LiquidGlassTextColorKey = intKey("liquidGlassTextColor")
val LiquidGlassSurfaceTintColorKey = intKey("liquidGlassSurfaceTintColor")
val LiquidGlassSurfaceOpacityKey = floatKey("liquidGlassSurfaceOpacity")
val LiquidGlassVibrancyKey = floatKey("liquidGlassVibrancy")
val LiquidGlassBlurRadiusKey = floatKey("liquidGlassBlurRadius")
val LiquidGlassLensHeightKey = floatKey("liquidGlassLensHeight")
val LiquidGlassLensAmountKey = floatKey("liquidGlassLensAmount")
val LiquidGlassChromaticAberrationKey = boolKey("liquidGlassChromaticAberration")
val LiquidGlassDepthEffectKey = boolKey("liquidGlassDepthEffect")
val LiquidGlassPlayerEnabledKey = boolKey("liquidGlassPlayerEnabled")
val LiquidGlassMiniPlayerEnabledKey = boolKey("liquidGlassMiniPlayerEnabled")
val LiquidGlassNavBarEnabledKey = boolKey("liquidGlassNavBarEnabled")

val UseFloatingNavBarKey = boolKey("useFloatingNavBar")
val SavedAccountsKey = textKey("savedAccounts")
val LiveBlurDensityKey = floatKey("live_blur_density")

// ---------------------------------------------------------------------------
// Ambient mode
// ---------------------------------------------------------------------------
val AmbientArtScaleKey = floatKey("ambient_art_scale")
val AmbientShowTitleKey = boolKey("ambient_show_title")
val AmbientShowArtistKey = boolKey("ambient_show_artist")
val AmbientShowLyricsKey = boolKey("ambient_show_lyrics")
val AmbientShowArtKey = boolKey("ambient_show_art")
val AmbientFullScreenArtKey = boolKey("ambient_full_screen_art")

val AmbientSpacingKey = floatKey("ambient_spacing")

enum class AppFont(val value: String) {
  SYSTEM("system"),
  GOOGLE_SANS("google_sans"),
  SANS_FLEX("sans_flex"),
  OUTFIT("outfit"),
  PLUS_JAKARTA_SANS("plus_jakarta_sans"),
  CUSTOM("custom");

  companion object {
    fun fromValue(value: String): AppFont = entries.firstOrNull { it.value == value } ?: SYSTEM
  }
}

val SelectedFontKey = textKey("selected_app_font")
val CustomFontPathKey = textKey("custom_font_path")
val BlockedArtistsKey = textSetKey("blockedArtists")
val DislikedSongsKey = textSetKey("dislikedSongs")

val EnableBitPerfectUsbDacKey = boolKey("enable_bit_perfect_usb_dac")
val UsbDacVolumeModeKey = textKey("usb_dac_volume_mode")

enum class RecommendationSource {
  YOUTUBE,
  SPOTIFY,
}

val RecommendationSourceKey = textKey("recommendation_source")
