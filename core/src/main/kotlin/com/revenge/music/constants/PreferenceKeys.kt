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

private fun bool(name: String): Preferences.Key<Boolean> = booleanPreferencesKey(name)
private fun text(name: String): Preferences.Key<String> = stringPreferencesKey(name)
private fun number(name: String): Preferences.Key<Int> = intPreferencesKey(name)
private fun bigNumber(name: String): Preferences.Key<Long> = longPreferencesKey(name)
private fun decimal(name: String): Preferences.Key<Float> = floatPreferencesKey(name)
private fun texts(name: String): Preferences.Key<Set<String>> = stringSetPreferencesKey(name)

// Account / Spotify / app icon
val DataSaverEnabledKey = bool("dataSaverEnabled")
val SpotifySpDcKey = text("spotify_sp_dc")
val SpotifySpKeyKey = text("spotify_sp_key")
val SpotifyAccountNameKey = text("spotify_account_name")
val SpotifyAccountAvatarUrlKey = text("spotify_account_avatar_url")
val SpotifyAccessTokenKey = text("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = bigNumber("spotify_access_token_expires_at")
val EnableLegacyIconKey = bool("enableLegacyIcon")
val AppIconTypeKey = text("app_icon_type")

// Haptics / refresh rate
val EnableHighRefreshRateKey = bool("enableHighRefreshRate")
val EnableHapticsKey = bool("enableHaptics")
val HapticIntensityKey = text("haptic_intensity")
val EnableClickHapticsKey = bool("enable_click_haptics")
val EnableLongPressHapticsKey = bool("enable_long_press_haptics")
val EnableScrollEdgeHapticsKey = bool("enable_scroll_edge_haptics")
val EnableSliderHapticsKey = bool("enable_slider_haptics")

// Appearance
val AlbumArtThemeRecoloringKey = bool("albumArtThemeRecoloring")
val SelectedThemeColorKey = number("selectedThemeColor")
val DarkModeKey = text("darkMode")
val PureBlackKey = bool("pureBlack")
val PureBlackMiniPlayerKey = bool("pureBlackMiniPlayer")
val IsMonochromeThemeKey = bool("is_monochrome_theme")
val DensityScaleKey = decimal("density_scale_factor")
val RevengeWrappedBannerDismissedKey = bool("revenge_wrapped_banner_dismissed_2026")
val DefaultOpenTabKey = text("defaultOpenTab")
val GridItemsSizeKey = text("gridItemSize")
val SliderStyleKey = text("sliderStyle")
val SquigglySliderKey = bool("squigglySlider")
val WavyPlayPauseKey = bool("wavyPlayPause")
val SwipeToSongKey = bool("SwipeToSong")
val SwipeToRemoveSongKey = bool("SwipeToRemoveSong")
val UseNewPlayerDesignKey = bool("useNewPlayerDesign")
val UseNewMiniPlayerDesignKey = bool("useNewMiniPlayerDesign")
val ShowCodecOnPlayerKey = bool("showCodecOnPlayer")
val HidePlayerSliderKey = bool("hidePlayerSlider")
val HidePlayerThumbnailKey = bool("hidePlayerThumbnail")
val ThumbnailCornerRadiusKey = decimal("thumbnailCornerRadius")
val CropAlbumArtKey = bool("cropAlbumArt")
val SeekExtraSeconds = bool("seekExtraSeconds")
val VarispeedKey = bool("varispeed")

// Playback behaviour
val PauseOnMute = bool("pauseOnMute")
val ResumeOnBluetoothConnectKey = bool("resumeOnBluetoothConnect")
val AutoplayOnAutomotiveConnectKey = bool("autoplayOnAutomotiveConnect")
val KeepScreenOn = bool("keepScreenOn")

// Language / content region / lyrics providers / filters
const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
val AppLanguageKey = text("appLanguage")
val ContentLanguageKey = text("contentLanguage")
val ContentCountryKey = text("contentCountry")
val SuggestionRegionKey = text("suggestionRegion")
val EnableKugouKey = bool("enableKugou")
val FetchFasterLyricsKey = bool("fetchFasterLyrics")
val EnableLrcLibKey = bool("enableLrclib")
val EnableBetterLyricsKey = bool("enableBetterLyrics")
val EnableSimpMusicKey = bool("enableSimpMusic")
val EnableYouLyPlusKey = bool("enableYouLyPlus")
val EnablePaxsenixKey = bool("enablePaxsenix")
val HideExplicitKey = bool("hideExplicit")
val SponsorBlockEnabledKey = bool("sponsor_block_enabled")
val HideVideoSongsKey = bool("hideVideoSongs")
val HideYoutubeShortsKey = bool("hideYoutubeShorts")
val ShowArtistDescriptionKey = bool("showArtistDescription")
val ShowArtistSubscriberCountKey = bool("showArtistSubscriberCount")
val ShowMonthlyListenersKey = bool("showMonthlyListeners")
val ShowArtistVideoKey = bool("showArtistVideo")
val ShowArtistBackgroundVideoKey = bool("showArtistBackgroundVideo")
val ProxyEnabledKey = bool("proxyEnabled")
val ProxyUrlKey = text("proxyUrl")
val ProxyTypeKey = text("proxyType")
val ProxyUsernameKey = text("proxyUsername")
val ProxyPasswordKey = text("proxyPassword")
val YtmSyncKey = bool("ytmSync")

// Streaming quality / downloads / audio processing
val AudioQualityWifiKey = text("audioQualityWifi")
val AudioQualityCellularKey = text("audioQualityCellular")
val IpVersionKey = text("ipVersion")
val DownloadOnWifiOnlyKey = bool("downloadOnWifiOnly")
val DownloadWithMetadataKey = bool("downloadWithMetadata")
val AudioOffload = bool("enableOffload")
val SpatialAudioKey = bool("spatialAudio")
val AudioLoudnessPresetKey = text("audioLoudnessPreset")
val PlaybackEngineKey = text("playbackEngine")
val ShowSeekButtonsKey = bool("showSeekButtons")
val PersistentQueueKey = bool("persistentQueue")
val PersistentShuffleAcrossQueuesKey = bool("persistentShuffleAcrossQueues")
val RememberShuffleAndRepeatKey = bool("rememberShuffleAndRepeat")
val ShuffleModeKey = bool("shuffleMode")
val SkipSilenceKey = bool("skipSilence")
val SkipSilenceInstantKey = bool("skipSilenceInstant")
val AudioNormalizationKey = bool("audioNormalization")
val AutoLoadMoreKey = bool("autoLoadMore")
val DisableLoadMoreWhenRepeatAllKey = bool("disableLoadMoreWhenRepeatAll")
val AutoDownloadOnLikeKey = bool("autoDownloadOnLike")
val SimilarContent = bool("similarContent")
val AutoSkipNextOnErrorKey = bool("autoSkipNextOnError")
val StopMusicOnTaskClearKey = bool("stopMusicOnTaskClear")
val ShufflePlaylistFirstKey = bool("shufflePlaylistFirst")
val PreventDuplicateTracksInQueueKey = bool("preventDuplicateTracksInQueue")
val CrossfadeEnabledKey = bool("crossfadeEnabled")
val CrossfadeDurationKey = decimal("crossfadeDuration")
val CrossfadeGaplessKey = bool("crossfadeGapless")
val AutomixCrossfadeKey = bool("automixCrossfade")
val AutomixDebugOverlayKey = bool("automixDebugOverlay")

// Cache / export / history / privacy
val MaxImageCacheSizeKey = number("maxImageCacheSize")
val MaxSongCacheSizeKey = number("maxSongCacheSize")
val ExportDirectoryUriKey = text("exportDirectoryUri")
val ExportingSongIdsKey = text("exportingSongIds")
val ExportedSongIdsKey = text("exportedSongIds")
val ExportProgressKey = text("exportProgress")
val EnableExportAsMp3Key = bool("enableExportAsMp3")
val PauseListenHistoryKey = bool("pauseListenHistory")
val PodcastHidePlayedKey = bool("podcastHidePlayed")
val PauseSearchHistoryKey = bool("pauseSearchHistory")
val DisableScreenshotKey = bool("disableScreenshot")
val EnableGoogleCastKey = bool("enableGoogleCast")

// Listen Together
val ListenTogetherServerUrlKey = text("listenTogetherServerUrl")
val ListenTogetherUsernameKey = text("listenTogetherUsername")
val ListenTogetherAutoApprovalKey = bool("listenTogetherAutoApproval")
val ListenTogetherSyncVolumeKey = bool("listenTogetherSyncVolume")
val ListenTogetherSmartResyncKey = bool("listenTogetherSmartResync")
val ListenTogetherBlockedUsersKey = text("listenTogetherBlockedUsers")
val ListenTogetherInTopBarKey = bool("listenTogetherInTopBar")
val ListenTogetherSessionTokenKey = text("listenTogetherSessionToken")
val ListenTogetherRoomCodeKey = text("listenTogetherRoomCode")
val ListenTogetherUserIdKey = text("listenTogetherUserId")
val ListenTogetherIsHostKey = bool("listenTogetherIsHost")
val ListenTogetherSessionTimestampKey = bigNumber("listenTogetherSessionTimestamp")

// Last.fm / scrobbling
val LastFMSessionKey = text("lastfmSession")
val LastFMUsernameKey = text("lastfmUsername")
val EnableLastFMScrobblingKey = bool("lastfmScrobblingEnable")
val LastFMUseNowPlaying = bool("lastfmUseNowPlaying")
val LastFMUseSendLikes = bool("lastfmUseSendLikes")
val ScrobbleDelayPercentKey = decimal("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = number("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = number("scrobbleDelaySeconds")

// Sort / filter / view / sync
val ChipSortTypeKey = text("chipSortType")
val SongSortTypeKey = text("songSortType")
val SongSortDescendingKey = bool("songSortDescending")
val DownloadedSongSortTypeKey = text("downloadedSongSortType")
val DownloadedSongSortDescendingKey = bool("downloadedSongSortDescending")
val PlaylistSongSortTypeKey = text("playlistSongSortType")
val PlaylistSongSortDescendingKey = bool("playlistSongSortDescending")
val ArtistSortTypeKey = text("artistSortType")
val ArtistSortDescendingKey = bool("artistSortDescending")
val AlbumSortTypeKey = text("albumSortType")
val AlbumSortDescendingKey = bool("albumSortDescending")
val PlaylistSortTypeKey = text("playlistSortType")
val PlaylistSortDescendingKey = bool("playlistSortDescending")
val AddToPlaylistSortTypeKey = text("addToPlaylistSortType")
val AddToPlaylistSortDescendingKey = bool("addToPlaylistSortDescending")
val ArtistSongSortTypeKey = text("artistSongSortType")
val ArtistSongSortDescendingKey = bool("artistSongSortDescending")
val MixSortTypeKey = text("mixSortType")
val MixSortDescendingKey = bool("albumSortDescending")
val LocalSongsMinDurationSecondsKey = number("local_songs_min_duration_seconds")
val LocalSongsExcludedFoldersKey = texts("local_songs_excluded_folders")
val LocalSongsIncludedFoldersKey = texts("local_songs_included_folders")
val LocalSongsFolderFilterModeKey = text("local_songs_folder_filter_mode")
val LocalSongsSortTypeKey = text("local_songs_sort_type")
val LocalSongsSortDescendingKey = bool("local_songs_sort_descending")
val SongFilterKey = text("songFilter")
val ArtistFilterKey = text("artistFilter")
val AlbumFilterKey = text("albumFilter")
val LastFullSyncKey = bigNumber("last_full_sync")
const val SYNC_COOLDOWN = 1800L
val ArtistViewTypeKey = text("artistViewType")
val AlbumViewTypeKey = text("albumViewType")
val PlaylistViewTypeKey = text("playlistViewType")
val PlaylistEditLockKey = bool("playlistEditLock")
val QuickPicksKey = text("discover")
val LyricsProviderOrderKey = text("lyricsProviderOrder")
val QueueEditLockKey = bool("queueEditLockV2")
val RandomizeHomeOrderKey = bool("randomizeHomeOrder")
val AlbumCanvasEnabledKey = bool("albumCanvasEnabled")

// Home shelves / music flow / addons
val ShowLikedPlaylistKey = bool("show_liked_playlist")
val ShowDownloadedPlaylistKey = bool("show_downloaded_playlist")
val ShowExportedPlaylistKey = bool("show_exported_playlist")
val ShowTopPlaylistKey = bool("show_top_playlist")
val ShowBottomPlaylistKey = bool("show_bottom_playlist")
val ShowCachedPlaylistKey = bool("show_cached_playlist")
val ShowSpeedDialKey = bool("showSpeedDial")
val ShowHomeHeroCarouselKey = bool("showHomeHeroCarousel")
val ShowHomeCommunityKey = bool("showHomeCommunity")
val ShowHomeMoodAndGenresKey = bool("showHomeMoodAndGenres")
val ShowCommentButtonKey = bool("show_comment_button")
val MusicFlowEnabledKey = bool("music_flow_enabled")
val MusicFlowAutoplayKey = bool("music_flow_autoplay")
val MusicFlowAutoNextKey = bool("music_flow_auto_next")
val MusicFlowAnimatedBackgroundKey = bool("music_flow_animated_background")
val MusicFlowLyricsKey = bool("music_flow_lyrics")
val MusicFlowCellularPrefetchKey = bool("music_flow_cellular_prefetch")
val MusicFlowHideRecentKey = bool("music_flow_hide_recent")
val PreferExtensionAudioKey = bool("prefer_extension_audio")
val PreferredAudioAddonIdKey = text("preferred_audio_addon_id")

// Player look / lyrics / AI / translation
val TopSize = text("topSize")
val HistoryDuration = decimal("historyDuration")
val PlayerButtonsStyleKey = text("player_buttons_style")
val PlayerBackgroundStyleKey = text("playerBackgroundStyle")
val MiniPlayerBackgroundStyleKey = text("miniPlayerBackgroundStyle")
val ShowLyricsKey = bool("showLyrics")
val ShowLyricsOnPlayerKey = bool("showLyricsOnPlayer")
val SwipeLyricsKey = bool("swipeLyrics")
val EnableLyricsThumbnailPlayPauseKey = bool("enableLyricsThumbnailPlayPause")
val LyricsTextPositionKey = text("lyricsTextPosition")
val LyricsClickKey = bool("lyricsClick")
val LyricsScrollKey = bool("lyricsScrollKey")
val LyricsRomanizeJapaneseKey = bool("lyricsRomanizeJapanese")
val LyricsRomanizeKoreanKey = bool("lyricsRomanizeKorean")
val LyricsRomanizeChineseKey = bool("lyricsRomanizeChinese")
val LyricsRomanizeRussianKey = bool("lyricsRomanizeRussian")
val LyricsRomanizeUkrainianKey = bool("lyricsRomanizeUkrainian")
val LyricsRomanizeSerbianKey = bool("lyricsRomanizeSerbian")
val LyricsRomanizeBulgarianKey = bool("lyricsRomanizeBulgarian")
val LyricsRomanizeBelarusianKey = bool("lyricsRomanizeBelarusian")
val LyricsRomanizeKyrgyzKey = bool("lyricsRomanizeKyrgyz")
val LyricsRomanizeMacedonianKey = bool("lyricsRomanizeMacedonian")
val LyricsRomanizeHindiKey = bool("lyricsRomanizeHindi")
val LyricsRomanizePunjabiKey = bool("lyricsRomanizePunjabi")
val LyricsRomanizeAsMainKey = bool("lyricsRomanizeAsMain")
val LyricsRomanizeCyrillicByLineKey = bool("lyricsRomanizeCyrillicByLine")
val OpenRouterApiKey = text("openRouterApiKey")
val AiProviderKey = text("aiProvider")
val OpenRouterBaseUrlKey = text("openRouterBaseUrl")
val OpenRouterModelKey = text("openRouterModel")
val TranslateModeKey = text("translateMode")
val TranslateLanguageKey = text("translateLanguage")
val AutoTranslateKey = bool("autoTranslate")
val AiRecommendationsKey = bool("aiRecommendations")
val LastAiRecommendationUpdateDayKey = bigNumber("lastAiRecommendationUpdateDay")
val CreateFromTasteDailyKey = bool("createFromTasteDaily")
val DeeplApiKey = text("deeplApiKey")
val DeeplFormalityKey = text("deeplFormality")
val LyricsGlowEffectKey = bool("lyricsGlowEffect")
val AppleMusicLyricsBlurKey = bool("appleMusicLyricsBlur")
val LyricsStandardBlurKey = bool("lyricsStandardBlur")
val HideStatusBarOnFullscreenKey = bool("hideStatusBarOnFullscreen")
val LyricsAnimationStyleKey = text("lyricsAnimationStyle")
val LyricsTextSizeKey = decimal("lyricsTextSize")
val LyricsLineSpacingKey = decimal("lyricsLineSpacing")
val PlayerVolumeKey = decimal("playerVolume")
val RepeatModeKey = number("repeatMode")
val SearchSourceKey = text("searchSource")
val SwipeThumbnailKey = bool("swipeThumbnail")
val RotatingThumbnailKey = bool("rotatingThumbnail")
val CanvasThumbnailAnimationKey = bool("canvasThumbnailAnimation")
val SwipeSensitivityKey = decimal("swipeSensitivity")

// Login / session / localisation extras / liquid glass / ambient / fonts / USB
val VisitorDataKey = text("visitorData")
val DataSyncIdKey = text("dataSyncId")
val InnerTubeCookieKey = text("innerTubeCookie")
val AccountNameKey = text("accountName")
val AccountEmailKey = text("accountEmail")
val AccountChannelHandleKey = text("accountChannelHandle")
val UseLoginForBrowse = bool("useLoginForBrowse")
val LastOpenedVersionCodeKey = number("lastOpenedVersionCode")
val ListenBrainzEnabledKey = bool("listenbrainz_enabled")
val ListenBrainzTokenKey = text("listenbrainz_token")
val UnisonLyricsEnabledKey = bool("unison_lyrics_enabled")
val ForceOpusKey = bool("force_opus_audio")
val PreloadNextSongEnabledKey = bool("preload_next_song_enabled")
val PreloadNextSongLimitKey = number("preload_next_song_limit")
val PreloadLyricsEnabledKey = bool("preload_lyrics_enabled")
val LiquidGlassGlobalEnabledKey = bool("liquidGlassGlobalEnabled")
val LiquidGlassTextColorKey = number("liquidGlassTextColor")
val LiquidGlassSurfaceTintColorKey = number("liquidGlassSurfaceTintColor")
val LiquidGlassSurfaceOpacityKey = decimal("liquidGlassSurfaceOpacity")
val LiquidGlassVibrancyKey = decimal("liquidGlassVibrancy")
val LiquidGlassBlurRadiusKey = decimal("liquidGlassBlurRadius")
val LiquidGlassLensHeightKey = decimal("liquidGlassLensHeight")
val LiquidGlassLensAmountKey = decimal("liquidGlassLensAmount")
val LiquidGlassChromaticAberrationKey = bool("liquidGlassChromaticAberration")
val LiquidGlassDepthEffectKey = bool("liquidGlassDepthEffect")
val LiquidGlassPlayerEnabledKey = bool("liquidGlassPlayerEnabled")
val LiquidGlassMiniPlayerEnabledKey = bool("liquidGlassMiniPlayerEnabled")
val LiquidGlassNavBarEnabledKey = bool("liquidGlassNavBarEnabled")
val UseFloatingNavBarKey = bool("useFloatingNavBar")
val SavedAccountsKey = text("savedAccounts")
val LiveBlurDensityKey = decimal("live_blur_density")
val AmbientArtScaleKey = decimal("ambient_art_scale")
val AmbientShowTitleKey = bool("ambient_show_title")
val AmbientShowArtistKey = bool("ambient_show_artist")
val AmbientShowLyricsKey = bool("ambient_show_lyrics")
val AmbientShowArtKey = bool("ambient_show_art")
val AmbientFullScreenArtKey = bool("ambient_full_screen_art")
val AmbientSpacingKey = decimal("ambient_spacing")
val SelectedFontKey = text("selected_app_font")
val CustomFontPathKey = text("custom_font_path")
val BlockedArtistsKey = texts("blockedArtists")
val DislikedSongsKey = texts("dislikedSongs")
val EnableBitPerfectUsbDacKey = bool("enable_bit_perfect_usb_dac")
val UsbDacVolumeModeKey = text("usb_dac_volume_mode")
val RecommendationSourceKey = text("recommendation_source")

enum class HapticIntensity(val label: String, val scaleFactor: Float) {
    LIGHT("Light", 0.4f),
    MEDIUM("Medium", 0.75f),
    STRONG("Strong", 1.0f);

    companion object {
        fun fromName(name: String?): HapticIntensity =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
    }
}

enum class DensityScale(val value: Float, val label: String) {
    NATIVE(1.0f, "Native (100%)"),
    SLIGHTLY_COMPACT(0.85f, "Slightly Compact (85%)"),
    COMPACT(0.75f, "Compact (75%)"),
    VERY_COMPACT(0.65f, "Very Compact (65%)"),
    ULTRA_COMPACT(0.55f, "Ultra Compact (55%)");

    companion object {
        fun fromValue(value: Float): DensityScale =
            entries.firstOrNull { it.value == value } ?: NATIVE
    }
}

enum class SliderStyle { DEFAULT, WAVY, SLIM }

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

    fun permitsAddons(): Boolean = permitsHigh()
}

enum class AudioLoudnessPreset(val gainOffsetMb: Int) {
    QUIET(-600),
    NORMAL(0),
    LOUD(300),
    AGGRESSIVE(700),
}

enum class PlaybackEngine { POTOKEN, BRAVEPIPE, AUTO }

enum class LibraryViewType {
    LIST,
    GRID;

    fun toggle(): LibraryViewType = if (this == LIST) GRID else LIST
}

enum class SongFilter { LIBRARY, LIKED, DOWNLOADED, UPLOADED, EXPORTED }

enum class ArtistFilter { LIBRARY, LIKED }

enum class AlbumFilter { LIBRARY, LIKED, UPLOADED }

enum class SongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }

enum class PlaylistSongSortType { CUSTOM, CREATE_DATE, NAME, ARTIST, PLAY_TIME }

enum class AutoPlaylistSongSortType { CREATE_DATE, NAME, ARTIST, PLAY_TIME }

enum class ArtistSortType { CREATE_DATE, NAME, SONG_COUNT, PLAY_TIME }

enum class ArtistSongSortType { CREATE_DATE, NAME, PLAY_TIME }

enum class AlbumSortType { CREATE_DATE, NAME, ARTIST, YEAR, SONG_COUNT, LENGTH, PLAY_TIME }

enum class PlaylistSortType { CREATE_DATE, NAME, SONG_COUNT, LAST_UPDATED }

enum class MixSortType { CREATE_DATE, NAME, LAST_UPDATED }

enum class GridItemSize { BIG, SMALL }

enum class MyTopFilter {
    ALL_TIME,
    DAY,
    WEEK,
    MONTH,
    YEAR;

    fun toTimeMillis(): Long {
        if (this == ALL_TIME) return 0L
        val now = LocalDateTime.now()
        val boundary = when (this) {
            DAY -> now.minusDays(1)
            WEEK -> now.minusWeeks(1)
            MONTH -> now.minusMonths(1)
            YEAR -> now.minusYears(1)
            ALL_TIME -> return 0L
        }
        return boundary.toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}

enum class QuickPicks { QUICK_PICKS, LAST_LISTEN }

enum class PreferredLyricsProvider {
    LRCLIB,
    KUGOU,
    BETTER_LYRICS,
    SIMPMUSIC,
    YOULYPLUS,
    PAXSENIX,
    UNISON,
}

enum class PlayerButtonsStyle { DEFAULT, PRIMARY, TERTIARY }

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    BLUR,
    GLOW_ANIMATED,
    APPLE_MUSIC,
    LIVE_MESH,
    LIQUID_GLASS,
}

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

enum class SearchSource {
    LOCAL,
    ONLINE;

    fun toggle(): SearchSource = if (this == LOCAL) ONLINE else LOCAL
}

enum class AppFont(val value: String) {
    SYSTEM("system"),
    GOOGLE_SANS("google_sans"),
    SANS_FLEX("sans_flex"),
    OUTFIT("outfit"),
    PLUS_JAKARTA_SANS("plus_jakarta_sans"),
    CUSTOM("custom");

    companion object {
        fun fromValue(value: String): AppFont =
            entries.firstOrNull { it.value == value } ?: SYSTEM
    }
}

enum class RecommendationSource { YOUTUBE, SPOTIFY }

val LanguageCodeToName: Map<String, String> = mapOf(
    "en" to "English",
    "en-GB" to "English (United Kingdom)",
    "es" to "Español",
    "es-419" to "Español (Latinoamérica)",
    "pt" to "Português",
    "pt-PT" to "Português (Portugal)",
    "fr" to "Français",
    "fr-CA" to "Français (Canada)",
    "de" to "Deutsch",
    "it" to "Italiano",
    "nl" to "Nederlands",
    "pl" to "Polski",
    "ru" to "Русский",
    "uk" to "Українська",
    "tr" to "Türkçe",
    "ar" to "العربية",
    "he" to "עברית",
    "hi" to "हिन्दी",
    "bn" to "বাংলা",
    "pa" to "ਪੰਜਾਬੀ",
    "ta" to "தமிழ்",
    "te" to "తెలుగు",
    "mr" to "मराठी",
    "gu" to "ગુજરાતી",
    "kn" to "ಕನ್ನಡ",
    "ml" to "മലയാളം",
    "th" to "ไทย",
    "vi" to "Tiếng Việt",
    "id" to "Bahasa Indonesia",
    "ms" to "Bahasa Melayu",
    "fil" to "Filipino",
    "ja" to "日本語",
    "ko" to "한국어",
    "zh-CN" to "简体中文",
    "zh-TW" to "繁體中文",
    "zh-HK" to "繁體中文（香港）",
    "sv" to "Svenska",
    "no" to "Norsk",
    "da" to "Dansk",
    "fi" to "Suomi",
    "cs" to "Čeština",
    "sk" to "Slovenčina",
    "hu" to "Magyar",
    "ro" to "Română",
    "bg" to "Български",
    "sr" to "Српски",
    "hr" to "Hrvatski",
    "el" to "Ελληνικά",
    "lt" to "Lietuvių",
    "lv" to "Latviešu",
    "et" to "Eesti",
    "fa" to "فارسی",
    "sw" to "Kiswahili",
)

val CountryCodeToName: Map<String, String> = mapOf(
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "AU" to "Australia",
    "IN" to "India",
    "BR" to "Brazil",
    "MX" to "Mexico",
    "AR" to "Argentina",
    "CL" to "Chile",
    "CO" to "Colombia",
    "PE" to "Peru",
    "DE" to "Germany",
    "FR" to "France",
    "ES" to "Spain",
    "IT" to "Italy",
    "PT" to "Portugal",
    "NL" to "Netherlands",
    "BE" to "Belgium",
    "CH" to "Switzerland",
    "AT" to "Austria",
    "SE" to "Sweden",
    "NO" to "Norway",
    "DK" to "Denmark",
    "FI" to "Finland",
    "IS" to "Iceland",
    "IE" to "Ireland",
    "PL" to "Poland",
    "CZ" to "Czechia",
    "SK" to "Slovakia",
    "HU" to "Hungary",
    "RO" to "Romania",
    "BG" to "Bulgaria",
    "GR" to "Greece",
    "HR" to "Croatia",
    "SI" to "Slovenia",
    "RS" to "Serbia",
    "UA" to "Ukraine",
    "RU" to "Russia",
    "TR" to "Türkiye",
    "IL" to "Israel",
    "SA" to "Saudi Arabia",
    "AE" to "United Arab Emirates",
    "EG" to "Egypt",
    "ZA" to "South Africa",
    "NG" to "Nigeria",
    "KE" to "Kenya",
    "CN" to "China",
    "HK" to "Hong Kong",
    "TW" to "Taiwan",
    "JP" to "Japan",
    "KR" to "South Korea",
    "SG" to "Singapore",
    "MY" to "Malaysia",
    "ID" to "Indonesia",
    "TH" to "Thailand",
    "VN" to "Vietnam",
    "PH" to "Philippines",
    "PK" to "Pakistan",
    "BD" to "Bangladesh",
    "LK" to "Sri Lanka",
    "NP" to "Nepal",
    "NZ" to "New Zealand",
)

val SuggestionRegionSlugToName: Map<String, String> = mapOf(
    "system" to "System Default",
    "us" to "United States",
    "gb" to "United Kingdom",
    "ca" to "Canada",
    "au" to "Australia",
    "in" to "India",
    "br" to "Brazil",
    "mx" to "Mexico",
    "de" to "Germany",
    "fr" to "France",
    "es" to "Spain",
    "it" to "Italy",
    "pt" to "Portugal",
    "nl" to "Netherlands",
    "se" to "Sweden",
    "no" to "Norway",
    "dk" to "Denmark",
    "fi" to "Finland",
    "pl" to "Poland",
    "ru" to "Russia",
    "ua" to "Ukraine",
    "tr" to "Türkiye",
    "jp" to "Japan",
    "kr" to "South Korea",
    "cn" to "China",
    "hk" to "Hong Kong",
    "tw" to "Taiwan",
    "sg" to "Singapore",
    "id" to "Indonesia",
    "th" to "Thailand",
    "vn" to "Vietnam",
    "ph" to "Philippines",
    "za" to "South Africa",
)
