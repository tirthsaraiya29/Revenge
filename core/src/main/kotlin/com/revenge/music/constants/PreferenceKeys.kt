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

private fun boolKey(name: String): Preferences.Key<Boolean> = booleanPreferencesKey(name)
private fun stringKey(name: String): Preferences.Key<String> = stringPreferencesKey(name)
private fun intKey(name: String): Preferences.Key<Int> = intPreferencesKey(name)
private fun longKey(name: String): Preferences.Key<Long> = longPreferencesKey(name)
private fun floatKey(name: String): Preferences.Key<Float> = floatPreferencesKey(name)
private fun stringSetKey(name: String): Preferences.Key<Set<String>> = stringSetPreferencesKey(name)

const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"
const val SYNC_COOLDOWN = 30 * 60L

// Account / Spotify / app icon
val DataSaverEnabledKey = boolKey("dataSaverEnabled")
val SpotifySpDcKey = stringKey("spotify_sp_dc")
val SpotifySpKeyKey = stringKey("spotify_sp_key")
val SpotifyAccountNameKey = stringKey("spotify_account_name")
val SpotifyAccountAvatarUrlKey = stringKey("spotify_account_avatar_url")
val SpotifyAccessTokenKey = stringKey("spotify_access_token")
val SpotifyAccessTokenExpiresAtKey = longKey("spotify_access_token_expires_at")
val EnableLegacyIconKey = boolKey("enableLegacyIcon")
val AppIconTypeKey = stringKey("app_icon_type")

// Haptics / refresh rate
val EnableHighRefreshRateKey = boolKey("enableHighRefreshRate")
val EnableHapticsKey = boolKey("enableHaptics")
val HapticIntensityKey = stringKey("haptic_intensity")
val EnableClickHapticsKey = boolKey("enable_click_haptics")
val EnableLongPressHapticsKey = boolKey("enable_long_press_haptics")
val EnableScrollEdgeHapticsKey = boolKey("enable_scroll_edge_haptics")
val EnableSliderHapticsKey = boolKey("enable_slider_haptics")

// Appearance
val AlbumArtThemeRecoloringKey = boolKey("albumArtThemeRecoloring")
val SelectedThemeColorKey = intKey("selectedThemeColor")
val DarkModeKey = stringKey("darkMode")
val PureBlackKey = boolKey("pureBlack")
val PureBlackMiniPlayerKey = boolKey("pureBlackMiniPlayer")
val IsMonochromeThemeKey = boolKey("is_monochrome_theme")
val DensityScaleKey = floatKey("density_scale_factor")
val RevengeWrappedBannerDismissedKey = boolKey("revenge_wrapped_banner_dismissed_2026")
val DefaultOpenTabKey = stringKey("defaultOpenTab")
val GridItemsSizeKey = stringKey("gridItemSize")
val SliderStyleKey = stringKey("sliderStyle")
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

// Playback behaviour
val PauseOnMute = boolKey("pauseOnMute")
val ResumeOnBluetoothConnectKey = boolKey("resumeOnBluetoothConnect")
val AutoplayOnAutomotiveConnectKey = boolKey("autoplayOnAutomotiveConnect")
val KeepScreenOn = boolKey("keepScreenOn")

// Language / content region / lyrics providers / filters
val AppLanguageKey = stringKey("appLanguage")
val ContentLanguageKey = stringKey("contentLanguage")
val ContentCountryKey = stringKey("contentCountry")
val SuggestionRegionKey = stringKey("suggestionRegion")
val EnableKugouKey = boolKey("enableKugou")
val FetchFasterLyricsKey = boolKey("fetchFasterLyrics")
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
val ProxyUrlKey = stringKey("proxyUrl")
val ProxyTypeKey = stringKey("proxyType")
val ProxyUsernameKey = stringKey("proxyUsername")
val ProxyPasswordKey = stringKey("proxyPassword")
val YtmSyncKey = boolKey("ytmSync")

// Streaming quality / downloads / audio processing
val AudioQualityWifiKey = stringKey("audioQualityWifi")
val AudioQualityCellularKey = stringKey("audioQualityCellular")
val IpVersionKey = stringKey("ipVersion")
val DownloadOnWifiOnlyKey = boolKey("downloadOnWifiOnly")
val DownloadWithMetadataKey = boolKey("downloadWithMetadata")
val AudioOffload = boolKey("enableOffload")
val SpatialAudioKey = boolKey("spatialAudio")
val AudioLoudnessPresetKey = stringKey("audioLoudnessPreset")
val PlaybackEngineKey = stringKey("playbackEngine")
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

// Cache / export / history / privacy
val MaxImageCacheSizeKey = intKey("maxImageCacheSize")
val MaxSongCacheSizeKey = intKey("maxSongCacheSize")
val ExportDirectoryUriKey = stringKey("exportDirectoryUri")
val ExportingSongIdsKey = stringKey("exportingSongIds")
val ExportedSongIdsKey = stringKey("exportedSongIds")
val ExportProgressKey = stringKey("exportProgress")
val EnableExportAsMp3Key = boolKey("enableExportAsMp3")
val PauseListenHistoryKey = boolKey("pauseListenHistory")
val PodcastHidePlayedKey = boolKey("podcastHidePlayed")
val PauseSearchHistoryKey = boolKey("pauseSearchHistory")
val DisableScreenshotKey = boolKey("disableScreenshot")
val EnableGoogleCastKey = boolKey("enableGoogleCast")

// Listen Together
val ListenTogetherServerUrlKey = stringKey("listenTogetherServerUrl")
val ListenTogetherUsernameKey = stringKey("listenTogetherUsername")
val ListenTogetherAutoApprovalKey = boolKey("listenTogetherAutoApproval")
val ListenTogetherSyncVolumeKey = boolKey("listenTogetherSyncVolume")
val ListenTogetherSmartResyncKey = boolKey("listenTogetherSmartResync")
val ListenTogetherBlockedUsersKey = stringKey("listenTogetherBlockedUsers")
val ListenTogetherInTopBarKey = boolKey("listenTogetherInTopBar")
val ListenTogetherSessionTokenKey = stringKey("listenTogetherSessionToken")
val ListenTogetherRoomCodeKey = stringKey("listenTogetherRoomCode")
val ListenTogetherUserIdKey = stringKey("listenTogetherUserId")
val ListenTogetherIsHostKey = boolKey("listenTogetherIsHost")
val ListenTogetherSessionTimestampKey = longKey("listenTogetherSessionTimestamp")

// Last.fm / scrobbling
val LastFMSessionKey = stringKey("lastfmSession")
val LastFMUsernameKey = stringKey("lastfmUsername")
val EnableLastFMScrobblingKey = boolKey("lastfmScrobblingEnable")
val LastFMUseNowPlaying = boolKey("lastfmUseNowPlaying")
val LastFMUseSendLikes = boolKey("lastfmUseSendLikes")
val ScrobbleDelayPercentKey = floatKey("scrobbleDelayPercent")
val ScrobbleMinSongDurationKey = intKey("scrobbleMinSongDuration")
val ScrobbleDelaySecondsKey = intKey("scrobbleDelaySeconds")

// Sort / filter / view / sync
val ChipSortTypeKey = stringKey("chipSortType")
val SongSortTypeKey = stringKey("songSortType")
val SongSortDescendingKey = boolKey("songSortDescending")
val DownloadedSongSortTypeKey = stringKey("downloadedSongSortType")
val DownloadedSongSortDescendingKey = boolKey("downloadedSongSortDescending")
val PlaylistSongSortTypeKey = stringKey("playlistSongSortType")
val PlaylistSongSortDescendingKey = boolKey("playlistSongSortDescending")
val ArtistSortTypeKey = stringKey("artistSortType")
val ArtistSortDescendingKey = boolKey("artistSortDescending")
val AlbumSortTypeKey = stringKey("albumSortType")
val AlbumSortDescendingKey = boolKey("albumSortDescending")
val PlaylistSortTypeKey = stringKey("playlistSortType")
val PlaylistSortDescendingKey = boolKey("playlistSortDescending")
val AddToPlaylistSortTypeKey = stringKey("addToPlaylistSortType")
val AddToPlaylistSortDescendingKey = boolKey("addToPlaylistSortDescending")
val ArtistSongSortTypeKey = stringKey("artistSongSortType")
val ArtistSongSortDescendingKey = boolKey("artistSongSortDescending")
val MixSortTypeKey = stringKey("mixSortType")
val MixSortDescendingKey = boolKey("albumSortDescending")
val LocalSongsMinDurationSecondsKey = intKey("local_songs_min_duration_seconds")
val LocalSongsExcludedFoldersKey = stringSetKey("local_songs_excluded_folders")
val LocalSongsIncludedFoldersKey = stringSetKey("local_songs_included_folders")
val LocalSongsFolderFilterModeKey = stringKey("local_songs_folder_filter_mode")
val LocalSongsSortTypeKey = stringKey("local_songs_sort_type")
val LocalSongsSortDescendingKey = boolKey("local_songs_sort_descending")
val SongFilterKey = stringKey("songFilter")
val ArtistFilterKey = stringKey("artistFilter")
val AlbumFilterKey = stringKey("albumFilter")
val LastFullSyncKey = longKey("last_full_sync")
val ArtistViewTypeKey = stringKey("artistViewType")
val AlbumViewTypeKey = stringKey("albumViewType")
val PlaylistViewTypeKey = stringKey("playlistViewType")
val PlaylistEditLockKey = boolKey("playlistEditLock")
val QuickPicksKey = stringKey("discover")
val LyricsProviderOrderKey = stringKey("lyricsProviderOrder")
val QueueEditLockKey = boolKey("queueEditLockV2")
val RandomizeHomeOrderKey = boolKey("randomizeHomeOrder")
val AlbumCanvasEnabledKey = boolKey("albumCanvasEnabled")

// Home shelves / music flow / addons
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
val MusicFlowEnabledKey = boolKey("music_flow_enabled")
val MusicFlowAutoplayKey = boolKey("music_flow_autoplay")
val MusicFlowAutoNextKey = boolKey("music_flow_auto_next")
val MusicFlowAnimatedBackgroundKey = boolKey("music_flow_animated_background")
val MusicFlowLyricsKey = boolKey("music_flow_lyrics")
val MusicFlowCellularPrefetchKey = boolKey("music_flow_cellular_prefetch")
val MusicFlowHideRecentKey = boolKey("music_flow_hide_recent")
val PreferExtensionAudioKey = boolKey("prefer_extension_audio")
val PreferredAudioAddonIdKey = stringKey("preferred_audio_addon_id")

// Player look / lyrics / AI / translation
val TopSize = stringKey("topSize")
val HistoryDuration = floatKey("historyDuration")
val PlayerButtonsStyleKey = stringKey("player_buttons_style")
val PlayerBackgroundStyleKey = stringKey("playerBackgroundStyle")
val MiniPlayerBackgroundStyleKey = stringKey("miniPlayerBackgroundStyle")
val ShowLyricsKey = boolKey("showLyrics")
val ShowLyricsOnPlayerKey = boolKey("showLyricsOnPlayer")
val SwipeLyricsKey = boolKey("swipeLyrics")
val EnableLyricsThumbnailPlayPauseKey = boolKey("enableLyricsThumbnailPlayPause")
val LyricsTextPositionKey = stringKey("lyricsTextPosition")
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
val OpenRouterApiKey = stringKey("openRouterApiKey")
val AiProviderKey = stringKey("aiProvider")
val OpenRouterBaseUrlKey = stringKey("openRouterBaseUrl")
val OpenRouterModelKey = stringKey("openRouterModel")
val TranslateModeKey = stringKey("translateMode")
val TranslateLanguageKey = stringKey("translateLanguage")
val AutoTranslateKey = boolKey("autoTranslate")
val AiRecommendationsKey = boolKey("aiRecommendations")
val LastAiRecommendationUpdateDayKey = longKey("lastAiRecommendationUpdateDay")
val CreateFromTasteDailyKey = boolKey("createFromTasteDaily")
val DeeplApiKey = stringKey("deeplApiKey")
val DeeplFormalityKey = stringKey("deeplFormality")
val LyricsGlowEffectKey = boolKey("lyricsGlowEffect")
val AppleMusicLyricsBlurKey = boolKey("appleMusicLyricsBlur")
val LyricsStandardBlurKey = boolKey("lyricsStandardBlur")
val HideStatusBarOnFullscreenKey = boolKey("hideStatusBarOnFullscreen")
val LyricsAnimationStyleKey = stringKey("lyricsAnimationStyle")
val LyricsTextSizeKey = floatKey("lyricsTextSize")
val LyricsLineSpacingKey = floatKey("lyricsLineSpacing")
val PlayerVolumeKey = floatKey("playerVolume")
val RepeatModeKey = intKey("repeatMode")
val SearchSourceKey = stringKey("searchSource")
val SwipeThumbnailKey = boolKey("swipeThumbnail")
val RotatingThumbnailKey = boolKey("rotatingThumbnail")
val CanvasThumbnailAnimationKey = boolKey("canvasThumbnailAnimation")
val SwipeSensitivityKey = floatKey("swipeSensitivity")

// Login / session / localisation extras / liquid glass / ambient / fonts / USB
val VisitorDataKey = stringKey("visitorData")
val DataSyncIdKey = stringKey("dataSyncId")
val InnerTubeCookieKey = stringKey("innerTubeCookie")
val AccountNameKey = stringKey("accountName")
val AccountEmailKey = stringKey("accountEmail")
val AccountChannelHandleKey = stringKey("accountChannelHandle")
val UseLoginForBrowse = boolKey("useLoginForBrowse")
val LastOpenedVersionCodeKey = intKey("lastOpenedVersionCode")
val ListenBrainzEnabledKey = boolKey("listenbrainz_enabled")
val ListenBrainzTokenKey = stringKey("listenbrainz_token")
val UnisonLyricsEnabledKey = boolKey("unison_lyrics_enabled")
val ForceOpusKey = boolKey("force_opus_audio")
val PreloadNextSongEnabledKey = boolKey("preload_next_song_enabled")
val PreloadNextSongLimitKey = intKey("preload_next_song_limit")
val PreloadLyricsEnabledKey = boolKey("preload_lyrics_enabled")
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
val SavedAccountsKey = stringKey("savedAccounts")
val LiveBlurDensityKey = floatKey("live_blur_density")
val AmbientArtScaleKey = floatKey("ambient_art_scale")
val AmbientShowTitleKey = boolKey("ambient_show_title")
val AmbientShowArtistKey = boolKey("ambient_show_artist")
val AmbientShowLyricsKey = boolKey("ambient_show_lyrics")
val AmbientShowArtKey = boolKey("ambient_show_art")
val AmbientFullScreenArtKey = boolKey("ambient_full_screen_art")
val AmbientSpacingKey = floatKey("ambient_spacing")
val SelectedFontKey = stringKey("selected_app_font")
val CustomFontPathKey = stringKey("custom_font_path")
val BlockedArtistsKey = stringSetKey("blockedArtists")
val DislikedSongsKey = stringSetKey("dislikedSongs")
val EnableBitPerfectUsbDacKey = boolKey("enable_bit_perfect_usb_dac")
val UsbDacVolumeModeKey = stringKey("usb_dac_volume_mode")
val RecommendationSourceKey = stringKey("recommendation_source")

enum class HapticIntensity(val label: String, val scaleFactor: Float) {
    LIGHT("Light", 0.4f),
    MEDIUM("Medium", 0.75f),
    STRONG("Strong", 1.0f);

    companion object {
        fun fromName(name: String?): HapticIntensity {
            if (name == null || name.isBlank()) return MEDIUM
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
        }
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
    LIST, GRID;

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
    ALL_TIME, DAY, WEEK, MONTH, YEAR;

    fun toTimeMillis(): Long = when (this) {
        ALL_TIME -> 0L
        DAY -> LocalDateTime.now().minusDays(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        WEEK -> LocalDateTime.now().minusWeeks(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        MONTH -> LocalDateTime.now().minusMonths(1).toInstant(ZoneOffset.UTC).toEpochMilli()
        YEAR -> LocalDateTime.now().minusMonths(12).toInstant(ZoneOffset.UTC).toEpochMilli()
    }
}

enum class QuickPicks { QUICK_PICKS, LAST_LISTEN }

enum class PreferredLyricsProvider { LRCLIB, KUGOU, BETTER_LYRICS, SIMPMUSIC, YOULYPLUS, PAXSENIX, UNISON }

enum class PlayerButtonsStyle { DEFAULT, PRIMARY, TERTIARY }

enum class PlayerBackgroundStyle {
    DEFAULT, GRADIENT, BLUR, GLOW_ANIMATED, APPLE_MUSIC, LIVE_MESH, LIQUID_GLASS,
}

enum class LyricsAnimationStyle {
    NONE, FADE, GLOW, SLIDE, KARAOKE, APPLE, APPLE_V2, revenge_1, LYRICS_V2, METRO_LYRICS,
}

enum class SearchSource {
    LOCAL, ONLINE;

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
    "en-US" to "English (United States)",
    "es" to "Español",
    "es-419" to "Español (Latinoamérica)",
    "es-ES" to "Español (España)",
    "pt" to "Português",
    "pt-PT" to "Português (Portugal)",
    "pt-BR" to "Português (Brasil)",
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
    "ja" to "日本語",
    "ko" to "한국어",
    "zh" to "中文",
    "zh-CN" to "中文（简体）",
    "zh-TW" to "中文（繁體）",
    "zh-HK" to "中文（香港）",
    "th" to "ไทย",
    "vi" to "Tiếng Việt",
    "id" to "Bahasa Indonesia",
    "ms" to "Bahasa Melayu",
    "fil" to "Filipino",
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
    "sl" to "Slovenščina",
    "el" to "Ελληνικά",
    "fa" to "فارسی",
    "ur" to "اردو",
)

val CountryCodeToName: Map<String, String> = mapOf(
    "US" to "United States",
    "GB" to "United Kingdom",
    "CA" to "Canada",
    "AU" to "Australia",
    "NZ" to "New Zealand",
    "IE" to "Ireland",
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
    "RU" to "Russia",
    "UA" to "Ukraine",
    "TR" to "Türkiye",
    "IN" to "India",
    "PK" to "Pakistan",
    "BD" to "Bangladesh",
    "LK" to "Sri Lanka",
    "NP" to "Nepal",
    "CN" to "China",
    "TW" to "Taiwan",
    "HK" to "Hong Kong",
    "JP" to "Japan",
    "KR" to "South Korea",
    "SG" to "Singapore",
    "MY" to "Malaysia",
    "ID" to "Indonesia",
    "PH" to "Philippines",
    "TH" to "Thailand",
    "VN" to "Vietnam",
    "BR" to "Brazil",
    "AR" to "Argentina",
    "MX" to "Mexico",
    "CL" to "Chile",
    "CO" to "Colombia",
    "PE" to "Peru",
    "ZA" to "South Africa",
    "NG" to "Nigeria",
    "EG" to "Egypt",
    "SA" to "Saudi Arabia",
    "AE" to "United Arab Emirates",
    "IL" to "Israel",
)

val SuggestionRegionSlugToName: Map<String, String> = mapOf(
    "system" to "System Default",
    "us" to "United States",
    "gb" to "United Kingdom",
    "ca" to "Canada",
    "au" to "Australia",
    "in" to "India",
    "de" to "Germany",
    "fr" to "France",
    "es" to "Spain",
    "it" to "Italy",
    "pt" to "Portugal",
    "nl" to "Netherlands",
    "br" to "Brazil",
    "mx" to "Mexico",
    "jp" to "Japan",
    "kr" to "South Korea",
    "cn" to "China",
    "tw" to "Taiwan",
    "hk" to "Hong Kong",
    "sg" to "Singapore",
    "id" to "Indonesia",
    "ph" to "Philippines",
    "th" to "Thailand",
    "vn" to "Vietnam",
    "ru" to "Russia",
    "ua" to "Ukraine",
    "tr" to "Türkiye",
    "za" to "South Africa",
)
