package com.revenge.music.constants

import androidx.datastore.preferences.core.Preferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Field
import java.lang.reflect.ParameterizedType

class PreferenceKeysTest {

    private val golden: Map<String, Pair<String, String>> = """
        DataSaverEnabledKey|B|dataSaverEnabled
        SpotifySpDcKey|S|spotify_sp_dc
        SpotifySpKeyKey|S|spotify_sp_key
        SpotifyAccountNameKey|S|spotify_account_name
        SpotifyAccountAvatarUrlKey|S|spotify_account_avatar_url
        SpotifyAccessTokenKey|S|spotify_access_token
        SpotifyAccessTokenExpiresAtKey|L|spotify_access_token_expires_at
        EnableLegacyIconKey|B|enableLegacyIcon
        AppIconTypeKey|S|app_icon_type
        EnableHighRefreshRateKey|B|enableHighRefreshRate
        EnableHapticsKey|B|enableHaptics
        HapticIntensityKey|S|haptic_intensity
        EnableClickHapticsKey|B|enable_click_haptics
        EnableLongPressHapticsKey|B|enable_long_press_haptics
        EnableScrollEdgeHapticsKey|B|enable_scroll_edge_haptics
        EnableSliderHapticsKey|B|enable_slider_haptics
        AlbumArtThemeRecoloringKey|B|albumArtThemeRecoloring
        SelectedThemeColorKey|I|selectedThemeColor
        DarkModeKey|S|darkMode
        PureBlackKey|B|pureBlack
        PureBlackMiniPlayerKey|B|pureBlackMiniPlayer
        IsMonochromeThemeKey|B|is_monochrome_theme
        DensityScaleKey|F|density_scale_factor
        RevengeWrappedBannerDismissedKey|B|revenge_wrapped_banner_dismissed_2026
        DefaultOpenTabKey|S|defaultOpenTab
        GridItemsSizeKey|S|gridItemSize
        SliderStyleKey|S|sliderStyle
        SquigglySliderKey|B|squigglySlider
        WavyPlayPauseKey|B|wavyPlayPause
        SwipeToSongKey|B|SwipeToSong
        SwipeToRemoveSongKey|B|SwipeToRemoveSong
        UseNewPlayerDesignKey|B|useNewPlayerDesign
        UseNewMiniPlayerDesignKey|B|useNewMiniPlayerDesign
        ShowCodecOnPlayerKey|B|showCodecOnPlayer
        HidePlayerSliderKey|B|hidePlayerSlider
        HidePlayerThumbnailKey|B|hidePlayerThumbnail
        ThumbnailCornerRadiusKey|F|thumbnailCornerRadius
        CropAlbumArtKey|B|cropAlbumArt
        SeekExtraSeconds|B|seekExtraSeconds
        VarispeedKey|B|varispeed
        PauseOnMute|B|pauseOnMute
        ResumeOnBluetoothConnectKey|B|resumeOnBluetoothConnect
        AutoplayOnAutomotiveConnectKey|B|autoplayOnAutomotiveConnect
        KeepScreenOn|B|keepScreenOn
        AppLanguageKey|S|appLanguage
        ContentLanguageKey|S|contentLanguage
        ContentCountryKey|S|contentCountry
        SuggestionRegionKey|S|suggestionRegion
        EnableKugouKey|B|enableKugou
        FetchFasterLyricsKey|B|fetchFasterLyrics
        EnableLrcLibKey|B|enableLrclib
        EnableBetterLyricsKey|B|enableBetterLyrics
        EnableSimpMusicKey|B|enableSimpMusic
        EnableYouLyPlusKey|B|enableYouLyPlus
        EnablePaxsenixKey|B|enablePaxsenix
        HideExplicitKey|B|hideExplicit
        SponsorBlockEnabledKey|B|sponsor_block_enabled
        HideVideoSongsKey|B|hideVideoSongs
        HideYoutubeShortsKey|B|hideYoutubeShorts
        ShowArtistDescriptionKey|B|showArtistDescription
        ShowArtistSubscriberCountKey|B|showArtistSubscriberCount
        ShowMonthlyListenersKey|B|showMonthlyListeners
        ShowArtistVideoKey|B|showArtistVideo
        ShowArtistBackgroundVideoKey|B|showArtistBackgroundVideo
        ProxyEnabledKey|B|proxyEnabled
        ProxyUrlKey|S|proxyUrl
        ProxyTypeKey|S|proxyType
        ProxyUsernameKey|S|proxyUsername
        ProxyPasswordKey|S|proxyPassword
        YtmSyncKey|B|ytmSync
        AudioQualityWifiKey|S|audioQualityWifi
        AudioQualityCellularKey|S|audioQualityCellular
        IpVersionKey|S|ipVersion
        DownloadOnWifiOnlyKey|B|downloadOnWifiOnly
        DownloadWithMetadataKey|B|downloadWithMetadata
        AudioOffload|B|enableOffload
        SpatialAudioKey|B|spatialAudio
        AudioLoudnessPresetKey|S|audioLoudnessPreset
        PlaybackEngineKey|S|playbackEngine
        ShowSeekButtonsKey|B|showSeekButtons
        PersistentQueueKey|B|persistentQueue
        PersistentShuffleAcrossQueuesKey|B|persistentShuffleAcrossQueues
        RememberShuffleAndRepeatKey|B|rememberShuffleAndRepeat
        ShuffleModeKey|B|shuffleMode
        SkipSilenceKey|B|skipSilence
        SkipSilenceInstantKey|B|skipSilenceInstant
        AudioNormalizationKey|B|audioNormalization
        AutoLoadMoreKey|B|autoLoadMore
        DisableLoadMoreWhenRepeatAllKey|B|disableLoadMoreWhenRepeatAll
        AutoDownloadOnLikeKey|B|autoDownloadOnLike
        SimilarContent|B|similarContent
        AutoSkipNextOnErrorKey|B|autoSkipNextOnError
        StopMusicOnTaskClearKey|B|stopMusicOnTaskClear
        ShufflePlaylistFirstKey|B|shufflePlaylistFirst
        PreventDuplicateTracksInQueueKey|B|preventDuplicateTracksInQueue
        CrossfadeEnabledKey|B|crossfadeEnabled
        CrossfadeDurationKey|F|crossfadeDuration
        CrossfadeGaplessKey|B|crossfadeGapless
        AutomixCrossfadeKey|B|automixCrossfade
        AutomixDebugOverlayKey|B|automixDebugOverlay
        MaxImageCacheSizeKey|I|maxImageCacheSize
        MaxSongCacheSizeKey|I|maxSongCacheSize
        ExportDirectoryUriKey|S|exportDirectoryUri
        ExportingSongIdsKey|S|exportingSongIds
        ExportedSongIdsKey|S|exportedSongIds
        ExportProgressKey|S|exportProgress
        EnableExportAsMp3Key|B|enableExportAsMp3
        PauseListenHistoryKey|B|pauseListenHistory
        PodcastHidePlayedKey|B|podcastHidePlayed
        PauseSearchHistoryKey|B|pauseSearchHistory
        DisableScreenshotKey|B|disableScreenshot
        EnableGoogleCastKey|B|enableGoogleCast
        ListenTogetherServerUrlKey|S|listenTogetherServerUrl
        ListenTogetherUsernameKey|S|listenTogetherUsername
        ListenTogetherAutoApprovalKey|B|listenTogetherAutoApproval
        ListenTogetherSyncVolumeKey|B|listenTogetherSyncVolume
        ListenTogetherSmartResyncKey|B|listenTogetherSmartResync
        ListenTogetherBlockedUsersKey|S|listenTogetherBlockedUsers
        ListenTogetherInTopBarKey|B|listenTogetherInTopBar
        ListenTogetherSessionTokenKey|S|listenTogetherSessionToken
        ListenTogetherRoomCodeKey|S|listenTogetherRoomCode
        ListenTogetherUserIdKey|S|listenTogetherUserId
        ListenTogetherIsHostKey|B|listenTogetherIsHost
        ListenTogetherSessionTimestampKey|L|listenTogetherSessionTimestamp
        LastFMSessionKey|S|lastfmSession
        LastFMUsernameKey|S|lastfmUsername
        EnableLastFMScrobblingKey|B|lastfmScrobblingEnable
        LastFMUseNowPlaying|B|lastfmUseNowPlaying
        LastFMUseSendLikes|B|lastfmUseSendLikes
        ScrobbleDelayPercentKey|F|scrobbleDelayPercent
        ScrobbleMinSongDurationKey|I|scrobbleMinSongDuration
        ScrobbleDelaySecondsKey|I|scrobbleDelaySeconds
        ChipSortTypeKey|S|chipSortType
        SongSortTypeKey|S|songSortType
        SongSortDescendingKey|B|songSortDescending
        DownloadedSongSortTypeKey|S|downloadedSongSortType
        DownloadedSongSortDescendingKey|B|downloadedSongSortDescending
        PlaylistSongSortTypeKey|S|playlistSongSortType
        PlaylistSongSortDescendingKey|B|playlistSongSortDescending
        ArtistSortTypeKey|S|artistSortType
        ArtistSortDescendingKey|B|artistSortDescending
        AlbumSortTypeKey|S|albumSortType
        AlbumSortDescendingKey|B|albumSortDescending
        PlaylistSortTypeKey|S|playlistSortType
        PlaylistSortDescendingKey|B|playlistSortDescending
        AddToPlaylistSortTypeKey|S|addToPlaylistSortType
        AddToPlaylistSortDescendingKey|B|addToPlaylistSortDescending
        ArtistSongSortTypeKey|S|artistSongSortType
        ArtistSongSortDescendingKey|B|artistSongSortDescending
        MixSortTypeKey|S|mixSortType
        MixSortDescendingKey|B|albumSortDescending
        LocalSongsMinDurationSecondsKey|I|local_songs_min_duration_seconds
        LocalSongsExcludedFoldersKey|SS|local_songs_excluded_folders
        LocalSongsIncludedFoldersKey|SS|local_songs_included_folders
        LocalSongsFolderFilterModeKey|S|local_songs_folder_filter_mode
        LocalSongsSortTypeKey|S|local_songs_sort_type
        LocalSongsSortDescendingKey|B|local_songs_sort_descending
        SongFilterKey|S|songFilter
        ArtistFilterKey|S|artistFilter
        AlbumFilterKey|S|albumFilter
        LastFullSyncKey|L|last_full_sync
        ArtistViewTypeKey|S|artistViewType
        AlbumViewTypeKey|S|albumViewType
        PlaylistViewTypeKey|S|playlistViewType
        PlaylistEditLockKey|B|playlistEditLock
        QuickPicksKey|S|discover
        LyricsProviderOrderKey|S|lyricsProviderOrder
        QueueEditLockKey|B|queueEditLockV2
        RandomizeHomeOrderKey|B|randomizeHomeOrder
        AlbumCanvasEnabledKey|B|albumCanvasEnabled
        ShowLikedPlaylistKey|B|show_liked_playlist
        ShowDownloadedPlaylistKey|B|show_downloaded_playlist
        ShowExportedPlaylistKey|B|show_exported_playlist
        ShowTopPlaylistKey|B|show_top_playlist
        ShowBottomPlaylistKey|B|show_bottom_playlist
        ShowCachedPlaylistKey|B|show_cached_playlist
        ShowSpeedDialKey|B|showSpeedDial
        ShowHomeHeroCarouselKey|B|showHomeHeroCarousel
        ShowHomeCommunityKey|B|showHomeCommunity
        ShowHomeMoodAndGenresKey|B|showHomeMoodAndGenres
        ShowCommentButtonKey|B|show_comment_button
        MusicFlowEnabledKey|B|music_flow_enabled
        MusicFlowAutoplayKey|B|music_flow_autoplay
        MusicFlowAutoNextKey|B|music_flow_auto_next
        MusicFlowAnimatedBackgroundKey|B|music_flow_animated_background
        MusicFlowLyricsKey|B|music_flow_lyrics
        MusicFlowCellularPrefetchKey|B|music_flow_cellular_prefetch
        MusicFlowHideRecentKey|B|music_flow_hide_recent
        PreferExtensionAudioKey|B|prefer_extension_audio
        PreferredAudioAddonIdKey|S|preferred_audio_addon_id
        TopSize|S|topSize
        HistoryDuration|F|historyDuration
        PlayerButtonsStyleKey|S|player_buttons_style
        PlayerBackgroundStyleKey|S|playerBackgroundStyle
        MiniPlayerBackgroundStyleKey|S|miniPlayerBackgroundStyle
        ShowLyricsKey|B|showLyrics
        ShowLyricsOnPlayerKey|B|showLyricsOnPlayer
        SwipeLyricsKey|B|swipeLyrics
        EnableLyricsThumbnailPlayPauseKey|B|enableLyricsThumbnailPlayPause
        LyricsTextPositionKey|S|lyricsTextPosition
        LyricsClickKey|B|lyricsClick
        LyricsScrollKey|B|lyricsScrollKey
        LyricsRomanizeJapaneseKey|B|lyricsRomanizeJapanese
        LyricsRomanizeKoreanKey|B|lyricsRomanizeKorean
        LyricsRomanizeChineseKey|B|lyricsRomanizeChinese
        LyricsRomanizeRussianKey|B|lyricsRomanizeRussian
        LyricsRomanizeUkrainianKey|B|lyricsRomanizeUkrainian
        LyricsRomanizeSerbianKey|B|lyricsRomanizeSerbian
        LyricsRomanizeBulgarianKey|B|lyricsRomanizeBulgarian
        LyricsRomanizeBelarusianKey|B|lyricsRomanizeBelarusian
        LyricsRomanizeKyrgyzKey|B|lyricsRomanizeKyrgyz
        LyricsRomanizeMacedonianKey|B|lyricsRomanizeMacedonian
        LyricsRomanizeHindiKey|B|lyricsRomanizeHindi
        LyricsRomanizePunjabiKey|B|lyricsRomanizePunjabi
        LyricsRomanizeAsMainKey|B|lyricsRomanizeAsMain
        LyricsRomanizeCyrillicByLineKey|B|lyricsRomanizeCyrillicByLine
        OpenRouterApiKey|S|openRouterApiKey
        AiProviderKey|S|aiProvider
        OpenRouterBaseUrlKey|S|openRouterBaseUrl
        OpenRouterModelKey|S|openRouterModel
        TranslateModeKey|S|translateMode
        TranslateLanguageKey|S|translateLanguage
        AutoTranslateKey|B|autoTranslate
        AiRecommendationsKey|B|aiRecommendations
        LastAiRecommendationUpdateDayKey|L|lastAiRecommendationUpdateDay
        CreateFromTasteDailyKey|B|createFromTasteDaily
        DeeplApiKey|S|deeplApiKey
        DeeplFormalityKey|S|deeplFormality
        LyricsGlowEffectKey|B|lyricsGlowEffect
        AppleMusicLyricsBlurKey|B|appleMusicLyricsBlur
        LyricsStandardBlurKey|B|lyricsStandardBlur
        HideStatusBarOnFullscreenKey|B|hideStatusBarOnFullscreen
        LyricsAnimationStyleKey|S|lyricsAnimationStyle
        LyricsTextSizeKey|F|lyricsTextSize
        LyricsLineSpacingKey|F|lyricsLineSpacing
        PlayerVolumeKey|F|playerVolume
        RepeatModeKey|I|repeatMode
        SearchSourceKey|S|searchSource
        SwipeThumbnailKey|B|swipeThumbnail
        RotatingThumbnailKey|B|rotatingThumbnail
        CanvasThumbnailAnimationKey|B|canvasThumbnailAnimation
        SwipeSensitivityKey|F|swipeSensitivity
        VisitorDataKey|S|visitorData
        DataSyncIdKey|S|dataSyncId
        InnerTubeCookieKey|S|innerTubeCookie
        AccountNameKey|S|accountName
        AccountEmailKey|S|accountEmail
        AccountChannelHandleKey|S|accountChannelHandle
        UseLoginForBrowse|B|useLoginForBrowse
        LastOpenedVersionCodeKey|I|lastOpenedVersionCode
        ListenBrainzEnabledKey|B|listenbrainz_enabled
        ListenBrainzTokenKey|S|listenbrainz_token
        UnisonLyricsEnabledKey|B|unison_lyrics_enabled
        ForceOpusKey|B|force_opus_audio
        PreloadNextSongEnabledKey|B|preload_next_song_enabled
        PreloadNextSongLimitKey|I|preload_next_song_limit
        PreloadLyricsEnabledKey|B|preload_lyrics_enabled
        LiquidGlassGlobalEnabledKey|B|liquidGlassGlobalEnabled
        LiquidGlassTextColorKey|I|liquidGlassTextColor
        LiquidGlassSurfaceTintColorKey|I|liquidGlassSurfaceTintColor
        LiquidGlassSurfaceOpacityKey|F|liquidGlassSurfaceOpacity
        LiquidGlassVibrancyKey|F|liquidGlassVibrancy
        LiquidGlassBlurRadiusKey|F|liquidGlassBlurRadius
        LiquidGlassLensHeightKey|F|liquidGlassLensHeight
        LiquidGlassLensAmountKey|F|liquidGlassLensAmount
        LiquidGlassChromaticAberrationKey|B|liquidGlassChromaticAberration
        LiquidGlassDepthEffectKey|B|liquidGlassDepthEffect
        LiquidGlassPlayerEnabledKey|B|liquidGlassPlayerEnabled
        LiquidGlassMiniPlayerEnabledKey|B|liquidGlassMiniPlayerEnabled
        LiquidGlassNavBarEnabledKey|B|liquidGlassNavBarEnabled
        UseFloatingNavBarKey|B|useFloatingNavBar
        SavedAccountsKey|S|savedAccounts
        LiveBlurDensityKey|F|live_blur_density
        AmbientArtScaleKey|F|ambient_art_scale
        AmbientShowTitleKey|B|ambient_show_title
        AmbientShowArtistKey|B|ambient_show_artist
        AmbientShowLyricsKey|B|ambient_show_lyrics
        AmbientShowArtKey|B|ambient_show_art
        AmbientFullScreenArtKey|B|ambient_full_screen_art
        AmbientSpacingKey|F|ambient_spacing
        SelectedFontKey|S|selected_app_font
        CustomFontPathKey|S|custom_font_path
        BlockedArtistsKey|SS|blockedArtists
        DislikedSongsKey|SS|dislikedSongs
        EnableBitPerfectUsbDacKey|B|enable_bit_perfect_usb_dac
        UsbDacVolumeModeKey|S|usb_dac_volume_mode
        RecommendationSourceKey|S|recommendation_source
    """.trimIndent()
        .lineSequence()
        .filter { it.isNotBlank() }
        .associate { line ->
            val (identifier, type, name) = line.split("|")
            identifier to (type to name)
        }

    private fun facadeFields(): List<Field> {
        val facade = Class.forName("com.revenge.music.constants.PreferenceKeysKt")
        return facade.declaredFields.filter { Preferences.Key::class.java.isAssignableFrom(it.type) }
    }

    private fun declaredType(field: Field): String {
        val generic = field.genericType as ParameterizedType
        val argument = generic.actualTypeArguments.single()
        return when {
            argument === java.lang.Boolean::class.javaObjectType -> "B"
            argument === java.lang.Integer::class.javaObjectType -> "I"
            argument === java.lang.Long::class.javaObjectType -> "L"
            argument === java.lang.Float::class.javaObjectType -> "F"
            argument === String::class.java -> "S"
            argument is ParameterizedType && argument.rawType === Set::class.java -> "SS"
            else -> argument.typeName
        }
    }

    @Test
    fun `every declared key matches the frozen golden map`() {
        val actual = facadeFields().associate { field ->
            val key = field.get(null) as Preferences.Key<*>
            field.name to (declaredType(field) to key.name)
        }
        assertEquals(golden, actual)
    }

    @Test
    fun `stored names stay unique apart from the documented alias`() {
        val byName = facadeFields().groupBy { (it.get(null) as Preferences.Key<*>).name }
        val duplicated = byName.filterValues { it.size > 1 }.keys
        assertEquals(setOf("albumSortDescending"), duplicated)
    }

    @Test
    fun `non obvious stored names are pinned`() {
        assertEquals("discover", QuickPicksKey.name)
        assertEquals("queueEditLockV2", QueueEditLockKey.name)
        assertEquals("albumSortDescending", MixSortDescendingKey.name)
        assertEquals("is_monochrome_theme", IsMonochromeThemeKey.name)
        assertEquals("selected_app_font", SelectedFontKey.name)
        assertEquals("density_scale_factor", DensityScaleKey.name)
    }

    @Test
    fun `constants keep their values`() {
        assertEquals("SYSTEM_DEFAULT", SYSTEM_DEFAULT)
        assertEquals(1800L, SYNC_COOLDOWN)
    }

    @Test
    fun `haptic intensity parses case insensitively without trimming`() {
        assertEquals(HapticIntensity.LIGHT, HapticIntensity.fromName("light"))
        assertEquals(HapticIntensity.STRONG, HapticIntensity.fromName("Strong"))
        assertEquals(HapticIntensity.MEDIUM, HapticIntensity.fromName("  LIGHT "))
        assertEquals(HapticIntensity.MEDIUM, HapticIntensity.fromName(null))
        assertEquals(HapticIntensity.MEDIUM, HapticIntensity.fromName("nope"))
    }

    @Test
    fun `density scale matches values exactly`() {
        assertEquals(DensityScale.COMPACT, DensityScale.fromValue(0.75f))
        assertEquals(DensityScale.NATIVE, DensityScale.fromValue(0.8f))
    }

    @Test
    fun `app font matches the stored value not the constant name`() {
        assertEquals(AppFont.SANS_FLEX, AppFont.fromValue("sans_flex"))
        assertEquals(AppFont.SYSTEM, AppFont.fromValue("SANS_FLEX"))
        assertEquals(AppFont.SYSTEM, AppFont.fromValue("nope"))
    }

    @Test
    fun `view type toggles round trip`() {
        assertEquals(LibraryViewType.GRID, LibraryViewType.LIST.toggle())
        assertEquals(LibraryViewType.LIST, LibraryViewType.GRID.toggle())
        assertEquals(SearchSource.ONLINE, SearchSource.LOCAL.toggle())
        assertEquals(SearchSource.LOCAL, SearchSource.ONLINE.toggle())
    }

    @Test
    fun `my top filter boundaries are ordered in the past`() {
        val now = System.currentTimeMillis()
        assertEquals(0L, MyTopFilter.ALL_TIME.toTimeMillis())
        val day = MyTopFilter.DAY.toTimeMillis()
        val week = MyTopFilter.WEEK.toTimeMillis()
        val month = MyTopFilter.MONTH.toTimeMillis()
        val year = MyTopFilter.YEAR.toTimeMillis()

        assertTrue(day < now)
        assertTrue(week < day)
        assertTrue(month < week)
        assertTrue(year < month)
        assertNotEquals(0L, year)
    }

    @Test
    fun `audio quality capability rules`() {
        assertTrue(AudioQuality.AUTO.permitsLossless())
        assertTrue(AudioQuality.AUTO.permitsHigh())
        assertTrue(AudioQuality.AUTO.permitsAddons())

        assertFalse(AudioQuality.LOW.permitsLossless())
        assertFalse(AudioQuality.LOW.permitsHigh())
        assertFalse(AudioQuality.LOW.permitsAddons())

        assertTrue(AudioQuality.HIGH.permitsHigh())
        assertFalse(AudioQuality.HIGH.permitsLossless())
        assertTrue(AudioQuality.LOSSLESS.permitsLossless())
    }
}
