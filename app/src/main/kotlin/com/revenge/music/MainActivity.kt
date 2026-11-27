package com.revenge.music

import android.Manifest
import android.app.Activity
import android.app.ForegroundServiceStartNotAllowedException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.datastore.preferences.core.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.revenge.core.constants.AiRecommendationsKey
import com.revenge.core.constants.AlbumArtThemeRecoloringKey
import com.revenge.core.constants.AppLanguageKey
import com.revenge.core.constants.AppBarHeight
import com.revenge.core.constants.DarkModeKey
import com.revenge.core.constants.DefaultOpenTabKey
import com.revenge.core.constants.DisableScreenshotKey
import com.revenge.core.constants.EnableHighRefreshRateKey
import com.revenge.core.constants.ExportProgressKey
import com.revenge.core.constants.ExportedSongIdsKey
import com.revenge.core.constants.ExportingSongIdsKey
import com.revenge.core.constants.ForceOpusKey
import com.revenge.core.constants.IsMonochromeThemeKey
import com.revenge.core.constants.LastAiRecommendationUpdateDayKey
import com.revenge.core.constants.LastOpenedVersionCodeKey
import com.revenge.core.constants.ListenTogetherInTopBarKey
import com.revenge.core.constants.ListenTogetherUsernameKey
import com.revenge.core.constants.LiquidGlassNavBarEnabledKey
import com.revenge.core.constants.MiniPlayerBottomSpacing
import com.revenge.core.constants.MiniPlayerHeight
import com.revenge.core.constants.PauseListenHistoryKey
import com.revenge.core.constants.PauseSearchHistoryKey
import com.revenge.core.constants.PureBlackKey
import com.revenge.core.constants.SYSTEM_DEFAULT
import com.revenge.core.constants.SelectedThemeColorKey
import com.revenge.core.constants.SlimNavBarHeight
import com.revenge.core.constants.StopMusicOnTaskClearKey
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.SearchHistory
import com.revenge.core.models.MediaMetadata
import com.revenge.core.models.toMediaMetadata
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.core.utils.reportException
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.music.ai.AiRecommendationHelper
import com.revenge.music.ai.AiRecommendationWorker
import com.revenge.music.generate.LocalTasteEngine
import com.revenge.music.listentogether.ListenTogetherManager
import com.revenge.music.playback.DownloadUtil
import com.revenge.music.playback.MusicService
import com.revenge.music.playback.PlayerConnection
import com.revenge.music.ui.component.AppNavigationBar
import com.revenge.music.ui.component.AppNavigationRail
import com.revenge.music.ui.component.GlassEffectConfig
import com.revenge.music.ui.component.LocalAppBackdrop
import com.revenge.music.ui.component.LocalGlassEffectConfig
import com.revenge.music.ui.component.RingtoneProgressDialog
import com.revenge.music.ui.component.RingtoneTrimmerDialog
import com.revenge.music.ui.component.rememberBottomSheetState
import com.revenge.music.ui.component.backdrop.backdrops.emptyBackdrop
import com.revenge.music.ui.component.shimmer.getShimmerTheme
import com.revenge.music.ui.player.BottomSheetPlayer
import com.revenge.music.ui.screens.Screens
import com.revenge.music.ui.screens.WelcomeDialog
import com.revenge.music.ui.screens.navigationBuilder
import com.revenge.music.ui.screens.settings.DarkMode
import com.revenge.music.ui.screens.settings.RingtoneViewModel
import com.revenge.music.ui.theme.BrandThemeColor
import com.revenge.music.ui.theme.ColorSaver
import com.revenge.music.ui.theme.extractThemeColor
import com.revenge.music.ui.theme.revengeTheme
import com.revenge.music.updates.UpdateNotificationHelper
import com.revenge.music.updates.component.UpdateAvailableDialog
import com.revenge.music.updates.updater.ChangelogSection
import com.revenge.music.updates.updater.WhatsNewDialog
import com.revenge.music.updates.updater.WhatsNewInfo
import com.revenge.music.updates.updater.checkForUpdate
import com.revenge.music.updates.updater.fetchChangelogForVersion
import com.revenge.music.updates.updater.getAutoUpdateCheckSetting
import com.revenge.music.updates.updater.getLastSeenChangelogVersion
import com.revenge.music.updates.updater.getUpdateNotificationsSetting
import com.revenge.music.updates.updater.saveLastSeenChangelogVersion
import com.revenge.music.updates.updater.saveUpdateAvailableState
import com.revenge.music.utils.HapticHelper
import com.revenge.music.utils.HapticType
import com.revenge.music.utils.SyncUtils
import com.revenge.music.utils.onlinePlaylistRoute
import com.revenge.music.utils.rememberEnumPreference
import com.revenge.music.utils.rememberHapticHelper
import com.revenge.music.utils.rememberPreference
import com.revenge.music.utils.setAppLocale
import com.revenge.music.utils.validOnlinePlaylistId
import com.revenge.music.viewmodels.HomeViewModel
import com.revenge.playback.queues.ListQueue
import com.revenge.playback.queues.YouTubeQueue
import com.valentinilk.shimmer.LocalShimmerTheme
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Launcher/shell activity. The intent contract (actions, extras, deep-link routing, voice search)
 * is consumed by widgets, the quick-settings tile and external launchers, so the string values in
 * [Companion] must never change.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

  @Inject internal lateinit var database: MusicDatabase
  @Inject internal lateinit var localTasteEngine: LocalTasteEngine
  @Inject internal lateinit var downloadUtil: DownloadUtil
  @Inject internal lateinit var syncUtils: SyncUtils
  @Inject internal lateinit var listenTogetherManager: ListenTogetherManager
  @Inject internal lateinit var aiRecommendationHelper: AiRecommendationHelper

  internal var playerConnection by mutableStateOf<PlayerConnection?>(null)
  internal var navController: NavHostController? = null

  internal var initialRoute: String = Screens.Home.route

  private var pendingIntent: Intent? = null
  private var isServiceBound = false
  private val voiceRequestSequence = AtomicLong(0)

  private val serviceConnection =
    object : ServiceConnection {
      override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
        val musicBinder = binder as? MusicService.MusicBinder ?: return
        connectPlayer(musicBinder, allowRetry = true)
      }

      override fun onServiceDisconnected(name: ComponentName?) {
        detachPlayer()
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)

    runCatching { startService(Intent(this, MusicService::class.java)) }
      .onFailure { Timber.w(it, "Could not pre-start MusicService") }

    enableEdgeToEdge()
    WindowCompat.setDecorFitsSystemWindows(window, false)
    window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR

    listenTogetherManager.initialize()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
      applyStoredLocale()
    }

    clearExportStateIfRequested()
    startScreenshotGuard()
    startAiRecommendationObservers()

    initialRoute = resolveInitialRoute(intent)

    setContent {
      revengeApp(activity = this, initialRoute = initialRoute)
    }
  }

  override fun onStart() {
    super.onStart()
    requestNotificationPermissionIfNeeded()
    bindMusicService()
  }

  override fun onStop() {
    super.onStop()
    unbindMusicService()
  }

  override fun onDestroy() {
    val wasPlaying = playerConnection?.isPlaying?.value == true
    detachPlayer()

    if (wasPlaying && isFinishing && dataStore[StopMusicOnTaskClearKey] == true) {
      runCatching { stopService(Intent(this, MusicService::class.java)) }
        .onFailure { Timber.w(it, "Could not stop MusicService") }
    }

    super.onDestroy()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    val controller = navController
    if (controller != null) {
      handleDeepLinkIntent(intent)
      handleRecognitionIntent(intent)
      handleAssistantSearchIntent(intent)
    } else {
      pendingIntent = intent
    }
  }

  override fun startForegroundService(service: Intent): ComponentName? {
    return try {
      super.startForegroundService(service)
    } catch (exception: Exception) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        exception is ForegroundServiceStartNotAllowedException
      ) {
        Timber.w(exception, "Foreground service start was not allowed")
        null
      } else {
        throw exception
      }
    }
  }

  internal fun widgetIntentAction(): String? = (pendingIntent ?: intent)?.action

  internal fun dispatchInitialIntents(controller: NavHostController) {
    navController = controller
    val pending = pendingIntent

    if (pending != null) {
      handleDeepLinkIntent(pending)
      handleRecognitionIntent(pending)
      handleAssistantSearchIntent(pending)
      pendingIntent = null
      return
    }

    val current = intent
    when (current.action) {
      Intent.ACTION_VIEW, Intent.ACTION_SEND -> handleDeepLinkIntent(current)
      ACTION_RECOGNITION -> handleRecognitionIntent(current)
      MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> handleAssistantSearchIntent(current)
    }
  }

  internal suspend fun awaitPlayerConnection(): PlayerConnection? {
    repeat(PLAYER_CONNECTION_RETRIES) {
      playerConnection?.let { return it }
      delay(PLAYER_CONNECTION_RETRY_DELAY_MS)
    }
    return playerConnection
  }

  private fun connectPlayer(binder: MusicService.MusicBinder, allowRetry: Boolean) {
    try {
      val connection = PlayerConnection(this, binder, database, lifecycleScope)
      playerConnection = connection
      listenTogetherManager.setPlayerConnection(connection)
    } catch (throwable: Throwable) {
      Timber.e(throwable, "Could not construct PlayerConnection")
      playerConnection = null
      if (allowRetry) {
        lifecycleScope.launch {
          delay(SERVICE_CONNECTION_RETRY_DELAY_MS)
          connectPlayer(binder, allowRetry = false)
        }
      }
    }
  }

  private fun detachPlayer() {
    listenTogetherManager.setPlayerConnection(null)
    playerConnection?.dispose()
    playerConnection = null
  }

  private fun bindMusicService() {
    if (isServiceBound) return
    isServiceBound =
      runCatching {
        bindService(
          Intent(this, MusicService::class.java),
          serviceConnection,
          Context.BIND_AUTO_CREATE,
        )
      }
        .getOrDefault(false)
  }

  private fun unbindMusicService() {
    if (!isServiceBound) return
    runCatching { unbindService(serviceConnection) }
      .onFailure { Timber.w(it, "Could not unbind MusicService") }
    isServiceBound = false
  }

  private fun requestNotificationPermissionIfNeeded() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (
      ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
        NOTIFICATION_PERMISSION_REQUEST_CODE,
      )
    }
  }

  private fun applyStoredLocale() {
    val language = dataStore[AppLanguageKey]
    if (!language.isNullOrBlank() && language != SYSTEM_DEFAULT) {
      runCatching { setAppLocale(this, Locale.forLanguageTag(language)) }
    }
  }

  private fun clearExportStateIfRequested() {
    val marker = File(filesDir, CLEAR_EXPORT_STATE_MARKER)
    if (!marker.exists()) return
    lifecycleScope.launch(Dispatchers.IO) {
      runCatching {
        dataStore.edit { preferences ->
          preferences.remove(ExportingSongIdsKey)
          preferences.remove(ExportedSongIdsKey)
          preferences.remove(ExportProgressKey)
        }
        marker.delete()
      }.onFailure { Timber.w(it, "Could not clear export state") }
    }
  }

  private fun startScreenshotGuard() {
    lifecycleScope.launch {
      dataStore.data
        .map { it[DisableScreenshotKey] == true }
        .distinctUntilChanged()
        .collect { secure ->
          if (secure) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
          } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
          }
        }
    }
  }

  private fun startAiRecommendationObservers() {
    lifecycleScope.launch(Dispatchers.IO) {
      val today = System.currentTimeMillis() / MILLIS_PER_DAY
      val enabled = dataStore[AiRecommendationsKey] == true
      val lastDay = dataStore[LastAiRecommendationUpdateDayKey] ?: -1L
      if (enabled && lastDay != today) {
        runCatching {
          dataStore.edit { it[LastAiRecommendationUpdateDayKey] = today }
          aiRecommendationHelper.generateRecommendations(this@MainActivity)
        }.onFailure { Timber.w(it, "Daily AI recommendation generation failed") }
      }
    }

    lifecycleScope.launch {
      dataStore.data
        .map { it[AiRecommendationsKey] == true }
        .distinctUntilChanged()
        .collect { enabled -> updateAiRecommendationWorker(enabled) }
    }
  }

  private fun updateAiRecommendationWorker(enabled: Boolean) {
    runCatching {
      val workManager = WorkManager.getInstance(this)
      if (enabled) {
        val request =
          PeriodicWorkRequestBuilder<AiRecommendationWorker>(1, java.util.concurrent.TimeUnit.DAYS)
            .setConstraints(
              Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .build()
        workManager.enqueueUniquePeriodicWork(
          AI_RECOMMENDATION_WORK_NAME,
          ExistingPeriodicWorkPolicy.KEEP,
          request,
        )
      } else {
        workManager.cancelUniqueWork(AI_RECOMMENDATION_WORK_NAME)
      }
    }.onFailure { Timber.w(it, "Could not update AiRecommendationWorker schedule") }
  }

  private fun resolveInitialRoute(intent: Intent?): String {
    return when (intent?.action) {
      ACTION_SEARCH -> Screens.Library.route
      ACTION_LIBRARY -> Screens.Search.route
      else ->
        when (dataStore[DefaultOpenTabKey]) {
          "HOME" -> Screens.Home.route
          "LIBRARY" -> Screens.Library.route
          else -> Screens.Home.route
        }
    }
  }

  private fun handleDeepLinkIntent(intent: Intent) {
    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
    val uri =
      intent.data
        ?: sharedText
          ?.let { SHARE_URL_REGEX.find(it)?.value }
          ?.let { runCatching { Uri.parse(it) }.getOrNull() }
        ?: return

    intent.data = null
    intent.removeExtra(Intent.EXTRA_TEXT)

    val pathSegments = uri.pathSegments
    val firstSegment = pathSegments.firstOrNull()

    val listenPaths = firstSegment == "listen"
    val listenHost = uri.host?.equals("listen", ignoreCase = true) == true
    if (listenPaths || listenHost) {
      val code =
        uri.getQueryParameter("code")
          ?: uri.getQueryParameter("room")
          ?: pathSegments.getOrNull(1)
          ?: uri.lastPathSegment
      if (!code.isNullOrBlank()) {
        val username =
          dataStore[ListenTogetherUsernameKey]?.takeIf { it.isNotBlank() } ?: GUEST_USERNAME
        runCatching { listenTogetherManager.joinRoom(code, username) }
          .onFailure { reportException(it) }
        return
      }
    }

    when (firstSegment) {
      "playlist" -> handlePlaylistLink(uri)
      "browse" -> {
        val browseId = uri.lastPathSegment
        if (!browseId.isNullOrBlank()) navigate("album/${encode(browseId)}")
      }
      "channel", "c" -> {
        val artistId = uri.lastPathSegment
        if (!artistId.isNullOrBlank()) navigate("artist/${encode(artistId)}")
      }
      "search" -> {
        val query = uri.getQueryParameter("q")
        if (!query.isNullOrBlank()) navigate("search/${encode(query)}")
      }
      else -> handleWatchLink(uri)
    }
  }

  private fun handlePlaylistLink(uri: Uri) {
    val playlistId = validOnlinePlaylistId(uri.getQueryParameter("list"))
    if (playlistId == null) {
      Toast.makeText(this, R.string.invalid_playlist_url, Toast.LENGTH_SHORT).show()
      return
    }

    if (playlistId.startsWith(ALBUM_PLAYLIST_PREFIX)) {
      lifecycleScope.launch {
        val songs = YouTube.albumSongs(playlistId).getOrNull()
        val browseId = songs?.firstOrNull()?.album?.id
        if (browseId != null) {
          navigate("album/${encode(browseId)}")
        } else {
          reportException(IllegalStateException("Album playlist has no browse id"))
        }
      }
    } else {
      onlinePlaylistRoute(playlistId)?.let { navigate(it) }
    }
  }

  private fun handleWatchLink(uri: Uri) {
    val pathSegments = uri.pathSegments
    val videoId =
      when {
        pathSegments.firstOrNull() == "watch" -> uri.getQueryParameter("v")
        uri.host == "youtu.be" || uri.host == "music.youtube.com" -> pathSegments.firstOrNull()
        else -> null
      }
    val playlistId = uri.getQueryParameter("list")

    when {
      !videoId.isNullOrBlank() -> {
        lifecycleScope.launch {
          val firstItem =
            YouTube.queue(videoIds = listOf(videoId), playlistId = playlistId).fold(
              onSuccess = { it.firstOrNull() },
              onFailure = {
                reportException(it)
                null
              },
            )
          if (firstItem == null) return@launch
          val connection = awaitPlayerConnection() ?: return@launch
          connection.playQueue(
            YouTubeQueue(
              WatchEndpoint(videoId, playlistId),
              firstItem.toMediaMetadata(),
            ),
          )
        }
      }
      !playlistId.isNullOrBlank() -> {
        lifecycleScope.launch {
          val firstItem =
            YouTube.queue(videoIds = null, playlistId = playlistId).fold(
              onSuccess = { it.firstOrNull() },
              onFailure = {
                reportException(it)
                null
              },
            )
          if (firstItem == null) return@launch
          val connection = awaitPlayerConnection() ?: return@launch
          connection.playQueue(
            YouTubeQueue(
              WatchEndpoint(firstItem.id, playlistId),
              firstItem.toMediaMetadata(),
            ),
          )
        }
      }
    }
  }

  private fun handleRecognitionIntent(intent: Intent) {
    if (intent.action != ACTION_RECOGNITION) return
    val autoStart = intent.getBooleanExtra(EXTRA_AUTO_START_RECOGNITION, false)
    intent.removeExtra(EXTRA_AUTO_START_RECOGNITION)
    navigate(if (autoStart) "recognition?autoStart=true" else "recognition") {
      launchSingleTop = true
    }
  }

  private fun handleAssistantSearchIntent(intent: Intent) {
    if (intent.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
    if (intent.getBooleanExtra(EXTRA_VOICE_REQUEST_HANDLED, false)) return
    intent.putExtra(EXTRA_VOICE_REQUEST_HANDLED, true)

    val query = intent.getStringExtra(android.app.SearchManager.QUERY)
    val extras = intent.extras
    val requestCount = voiceRequestSequence.incrementAndGet()

    lifecycleScope.launch {
      val connection = awaitPlayerConnection() ?: return@launch

      val result =
        withContext(Dispatchers.IO) {
          runCatching { connection.service.mediaLibrarySessionCallback.voiceSearchQueue(query, extras) }
            .onFailure { reportException(it) }
            .getOrNull()
        }

      if (requestCount != voiceRequestSequence.get()) return@launch

      if (result != null) {
        val artworkUri =
          result.mediaItems.getOrNull(result.startIndex)?.mediaMetadata?.artworkUri
        connection.service.warmArtwork(artworkUri)
        if (requestCount != voiceRequestSequence.get()) return@launch
        connection.playQueue(
          ListQueue(
            items = result.mediaItems,
            startIndex = result.startIndex,
            position = max(0L, result.startPositionMs),
          ),
        )
      } else if (!query.isNullOrBlank()) {
        navigate("search/${encode(query)}")
      }
    }
  }

  private fun navigate(route: String, options: androidx.navigation.NavOptionsBuilder.() -> Unit = {}) {
    navController?.navigate(route, options)
  }

  private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

  companion object {
    private const val EXTRA_VOICE_REQUEST_HANDLED = "com.revenge.music.voice_request_handled"

    const val ACTION_SEARCH = "com.revenge.music.action.SEARCH"
    const val ACTION_LIBRARY = "com.revenge.music.action.LIBRARY"
    const val ACTION_RECOGNITION = "com.revenge.music.action.RECOGNITION"
    const val ACTION_NOW_PLAYING = "com.revenge.music.action.NOW_PLAYING"
    const val ACTION_QUEUE = "com.revenge.music.action.QUEUE"
    const val ACTION_OUTPUT_SWITCHER = "com.revenge.music.action.OUTPUT_SWITCHER"
    const val ACTION_SONG_OPTIONS = "com.revenge.music.action.SONG_OPTIONS"
    const val ACTION_LYRICS = "com.revenge.music.action.LYRICS"
    const val ACTION_MEDIA_OUTPUT = "com.android.settings.panel.action.MEDIA_OUTPUT"
    const val EXTRA_MEDIA_OUTPUT_PACKAGE_NAME = "com.android.settings.panel.extra.PACKAGE_NAME"
    const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"

    private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1000
    private const val PLAYER_CONNECTION_RETRIES = 20
    private const val PLAYER_CONNECTION_RETRY_DELAY_MS = 100L
    private const val SERVICE_CONNECTION_RETRY_DELAY_MS = 500L
    private const val GUEST_USERNAME = "Guest"
    private const val AI_RECOMMENDATION_WORK_NAME = "AiRecommendationWorker"
    private const val CLEAR_EXPORT_STATE_MARKER = "clear_export_state"
    private const val ALBUM_PLAYLIST_PREFIX = "OLAK5uy_"
    private const val MILLIS_PER_DAY = 86_400_000L

    private val SHARE_URL_REGEX = Regex("https?://\\S+")
  }
}

// ---------------------------------------------------------------------------------------------
// Composition locals consumed across the UI modules.
// ---------------------------------------------------------------------------------------------

val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }
val LocalRingtoneViewModel =
  compositionLocalOf<RingtoneViewModel> { error("No RingtoneViewModel provided") }
val LocalPlayerConnection = staticCompositionLocalOf<PlayerConnection?> {
  error("No PlayerConnection provided")
}
val LocalPlayerAwareWindowInsets = compositionLocalOf<WindowInsets> {
  error("No WindowInsets provided")
}
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> {
  error("No DownloadUtil provided")
}
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
val LocalListenTogetherManager = staticCompositionLocalOf<ListenTogetherManager?> { null }
val LocalIsPlayerExpanded = compositionLocalOf { false }
val LocalExpandPlayer = staticCompositionLocalOf<() -> Unit> { {} }

private val EmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

/** Pure gate used by [WelcomeDialog] and its unit test; version codes are strictly increasing. */
internal fun shouldShowWelcome(lastOpenedVersionCode: Int, currentVersionCode: Int): Boolean =
  lastOpenedVersionCode < currentVersionCode

private data class UpdatePrompt(
  val version: String,
  val changelog: List<ChangelogSection>,
  val description: String?,
)

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun revengeApp(activity: MainActivity, initialRoute: String) {
  val context = LocalContext.current
  val view = LocalView.current
  val coroutineScope = rememberCoroutineScope()

  val navController = rememberNavController()
  val snackbarHostState = remember { SnackbarHostState() }
  val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

  val (darkMode) = rememberEnumPreference(DarkModeKey, DarkMode.AUTO)
  val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
  val darkTheme =
    when (darkMode) {
      DarkMode.ON -> true
      DarkMode.OFF -> false
      DarkMode.AUTO -> systemDark
    }

  val (pureBlackPreference) = rememberPreference(PureBlackKey, false)
  val pureBlack = pureBlackPreference && darkTheme
  val (monochrome) = rememberPreference(IsMonochromeThemeKey, false)
  val (selectedThemeColor) = rememberPreference(SelectedThemeColorKey, BrandThemeColor.toArgb())
  var themeColor by rememberSaveable(stateSaver = ColorSaver) {
    mutableStateOf(Color(selectedThemeColor))
  }
  LaunchedEffect(selectedThemeColor) { themeColor = Color(selectedThemeColor) }

  val (recolorArtwork) = rememberPreference(AlbumArtThemeRecoloringKey, false)
  val playerConnection = activity.playerConnection
  val metadata by produceState<MediaMetadata?>(initialValue = null, playerConnection) {
    playerConnection?.mediaMetadata?.collect { value = it }
  }
  val effectivelyPlaying by produceState(initialValue = false, playerConnection) {
    playerConnection?.isEffectivelyPlaying?.collect { value = it }
  }
  var artworkColor by remember { mutableStateOf<Color?>(null) }
  LaunchedEffect(recolorArtwork, effectivelyPlaying, metadata?.thumbnailUrl) {
    val thumbnail = metadata?.thumbnailUrl
    artworkColor =
      if (recolorArtwork && effectivelyPlaying && !thumbnail.isNullOrBlank()) {
        loadArtworkThemeColor(context, thumbnail)
      } else {
        null
      }
  }
  val effectiveThemeColor = artworkColor ?: themeColor

  SideEffect {
    val window = (context as? Activity)?.window ?: return@SideEffect
    WindowCompat.getInsetsController(window, view).apply {
      isAppearanceLightStatusBars = !darkTheme
      isAppearanceLightNavigationBars = !darkTheme
    }
  }

  val (highRefreshRate) = rememberPreference(EnableHighRefreshRateKey, false)
  LaunchedEffect(highRefreshRate) { applyRefreshRate(activity, highRefreshRate) }

  revengeTheme(
    darkTheme = darkTheme,
    pureBlack = pureBlack,
    isMonochrome = monochrome,
    themeColor = effectiveThemeColor,
  ) {
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    val useRail =
      widthDp >= DESKTOP_BREAKPOINT_DP ||
        (widthDp >= TABLET_BREAKPOINT_DP &&
          configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)

    val (listenTogetherInTopBar) = rememberPreference(ListenTogetherInTopBarKey, false)
    val navigationItems =
      remember(listenTogetherInTopBar) {
        // filterNotNull: release/R8 has produced a null-containing MainScreens list.
        (if (listenTogetherInTopBar) {
          Screens.MainScreens.filter { it != Screens.ListenTogether }
        } else {
          Screens.MainScreens
        }).filterNotNull()
      }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val searchVisible =
      currentRoute == Screens.Search.route || currentRoute?.startsWith("search/") == true
    val topBarVisible = isTopBarRoute(currentRoute, listenTogetherInTopBar)
    val navBarVisible = isNavigationBarRoute(currentRoute, useRail)
    val playerVisible = isPlayerRoute(currentRoute) && playerConnection != null

    val playerSheetState =
      rememberBottomSheetState(
        dismissedBound = 0.dp,
        expandedBound = heightDp.dp,
        collapsedBound = MiniPlayerHeight,
      )

    var expandQueueRequested by remember { mutableStateOf(false) }
    var showPlayerMenuRequested by remember { mutableStateOf(false) }
    var showLyricsRequested by remember { mutableStateOf(false) }
    var showAudioDevices by remember { mutableStateOf(false) }
    var updatePrompt by remember { mutableStateOf<UpdatePrompt?>(null) }
    var whatsNew by remember { mutableStateOf<WhatsNewInfo?>(null) }
    var celebrationVisible by rememberSaveable { mutableStateOf(isOctoberFourteenth()) }

    var searchQuery by rememberSaveable(stateSaver = TextFieldValue.Saver) {
      mutableStateOf(TextFieldValue(""))
    }
    val (pauseSearchHistory) = rememberPreference(PauseSearchHistoryKey, false)
    val (pauseListenHistory) = rememberPreference(PauseListenHistoryKey, false)
    val eventCount by remember { activity.database.eventCount() }.collectAsState(initial = 0)
    val showHistory = !pauseListenHistory || eventCount > 0
    val (lastOpenedVersionCode) = rememberPreference(LastOpenedVersionCodeKey, -1)

    fun submitSearch(rawQuery: String) {
      val query = rawQuery.trim()
      if (query.isEmpty()) return
      navController.navigate("search/${URLEncoder.encode(query, "UTF-8")}")
      if (!pauseSearchHistory) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
          runCatching { activity.database.query { insert(SearchHistory(query = query)) } }
        }
      }
    }

    fun onTabClick(screen: Screens, alreadySelected: Boolean) {
      searchQuery = TextFieldValue("")
      if (alreadySelected) {
        navController.currentBackStackEntry?.savedStateHandle?.set(SCROLL_TO_TOP_KEY, true)
        scrollBehavior.state.heightOffset = 0f
        return
      }
      val popped = navController.popBackStack(screen.route, inclusive = false)
      if (!popped) {
        navController.navigate(screen.route) {
          popUpTo(navController.graph.findStartDestination().id) { saveState = true }
          launchSingleTop = true
          restoreState = true
        }
      }
    }

    fun handleWidgetAction(action: String?) {
      when (action) {
        MainActivity.ACTION_NOW_PLAYING -> playerSheetState.expandSoft()
        MainActivity.ACTION_QUEUE -> {
          playerSheetState.expandSoft()
          expandQueueRequested = true
        }
        MainActivity.ACTION_SONG_OPTIONS -> {
          playerSheetState.expandSoft()
          showPlayerMenuRequested = true
        }
        MainActivity.ACTION_LYRICS -> {
          playerSheetState.expandSoft()
          showLyricsRequested = true
        }
        MainActivity.ACTION_OUTPUT_SWITCHER -> {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mediaOutput =
              Intent(MainActivity.ACTION_MEDIA_OUTPUT).apply {
                putExtra(MainActivity.EXTRA_MEDIA_OUTPUT_PACKAGE_NAME, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
            val resolved = mediaOutput.resolveActivity(context.packageManager)
            if (resolved != null) {
              runCatching { context.startActivity(mediaOutput) }
                .onFailure { showAudioDevices = true }
            } else {
              showAudioDevices = true
            }
          } else {
            showAudioDevices = true
          }
        }
        else -> Unit
      }
    }

    DisposableEffect(activity) {
      val listener = androidx.core.util.Consumer<Intent> { newIntent ->
        handleWidgetAction(newIntent.action)
      }
      activity.addOnNewIntentListener(listener)
      handleWidgetAction(activity.widgetIntentAction())
      onDispose { activity.removeOnNewIntentListener(listener) }
    }

    LaunchedEffect(navController) { activity.dispatchInitialIntents(navController) }

    LaunchedEffect(currentRoute) {
      if (currentRoute?.startsWith("search/") == true) {
        val decoded = Uri.decode(currentRoute.removePrefix("search/"))
        searchQuery = TextFieldValue(decoded, selection = TextRange(decoded.length))
      }
    }

    LaunchedEffect(playerConnection, metadata) {
      if (playerConnection == null) return@LaunchedEffect
      if (metadata == null) playerSheetState.dismiss() else playerSheetState.collapseSoft()
    }

    LaunchedEffect(Unit) {
      if (getAutoUpdateCheckSetting(context)) {
        delay(UPDATE_CHECK_DELAY_MS)
        checkForUpdate(
          context = context,
          onSuccess = { tag, available, changelog, _, _, description, _, _ ->
            if (available) {
              saveUpdateAvailableState(context, true)
              updatePrompt = UpdatePrompt(tag, changelog, description)
              if (getUpdateNotificationsSetting(context)) {
                UpdateNotificationHelper.showUpdateNotification(context, tag)
              }
            }
          },
          onError = {},
        )
      }
    }

    LaunchedEffect(Unit) {
      val currentVersion = BuildConfig.VERSION_NAME
      val lastSeenVersion = getLastSeenChangelogVersion(context)
      if (lastSeenVersion != currentVersion) {
        runCatching { context.dataStore.edit { it[ForceOpusKey] = true } }
        if (lastSeenVersion.isBlank()) {
          saveLastSeenChangelogVersion(context, currentVersion)
        } else {
          whatsNew = fetchChangelogForVersion(currentVersion)
        }
      }
    }

    val baseInsets =
      WindowInsets.systemBars.union(WindowInsets.displayCutout)
        .add(
          WindowInsets(
            top = if (topBarVisible || searchVisible) AppBarHeight else 0.dp,
            bottom =
              (if (navBarVisible && !useRail) SlimNavBarHeight else 0.dp) +
                (if (playerVisible) MiniPlayerHeight + MiniPlayerBottomSpacing else 0.dp),
          ),
        )

    val hapticHelper = rememberHapticHelper()
    val hapticFeedback = hapticFeedbackFor(hapticHelper)

    val ringtoneViewModel: RingtoneViewModel = viewModel()
    val ringtoneState by ringtoneViewModel.uiState.collectAsState()
    val homeViewModel: HomeViewModel = hiltViewModel()
    val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

    CompositionLocalProvider(
      LocalDatabase provides activity.database,
      LocalRingtoneViewModel provides ringtoneViewModel,
      LocalPlayerConnection provides playerConnection,
      LocalPlayerAwareWindowInsets provides baseInsets,
      LocalDownloadUtil provides activity.downloadUtil,
      LocalSyncUtils provides activity.syncUtils,
      LocalListenTogetherManager provides activity.listenTogetherManager,
      LocalExpandPlayer provides { playerSheetState.expandSoft() },
      LocalGlassEffectConfig provides GlassEffectConfig(),
      LocalAppBackdrop provides emptyBackdrop(),
      LocalShimmerTheme provides getShimmerTheme(),
      LocalHapticFeedback provides hapticFeedback,
      LocalContentColor provides MaterialTheme.colorScheme.onBackground,
    ) {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .pointerInput(hapticHelper) {
              awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                hapticHelper.performHaptic(HapticType.CLICK)
              }
            },
      ) {
        Row(Modifier.fillMaxSize()) {
          if (useRail) {
            AppNavigationRail(
              navigationItems = navigationItems,
              currentRoute = currentRoute,
              onItemClick = { screen, selected -> onTabClick(screen, selected) },
              pureBlack = pureBlack,
              onMusicRecognitionClick = { navController.navigate("recognition") },
              musicRecognitionContentDescription = stringResource(R.string.recognition),
            )
          }

          Scaffold(
            modifier = if (useRail) Modifier.weight(1f) else Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
              when {
                searchVisible -> {
                  SearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = { submitSearch(it) },
                    scrollBehavior = scrollBehavior,
                    onAccountClick = { navController.navigate("account") },
                    onSettingsClick = { navController.navigate("settings") },
                    accountImageUrl = accountImageUrl,
                    onOpenMenu = { menuAction -> handleMenuAction(menuAction, navController, listenTogetherInTopBar) },
                    showHistory = showHistory,
                  )
                }
                topBarVisible -> {
                  ShellTopBar(
                    title =
                      if (currentRoute == Screens.Home.route) {
                        HOME_TITLE
                      } else {
                        topBarTitle(currentRoute)?.let { stringResource(it) } ?: ""
                      },
                    scrollBehavior = scrollBehavior,
                    onAccountClick = { navController.navigate("account") },
                    onSettingsClick = { navController.navigate("settings") },
                    accountImageUrl = accountImageUrl,
                    onOpenMenu = { menuAction -> handleMenuAction(menuAction, navController, listenTogetherInTopBar) },
                    showHistory = showHistory,
                  )
                }
              }
            },
            bottomBar = {
              if (navBarVisible && !useRail) {
                val (glassNav) = rememberPreference(LiquidGlassNavBarEnabledKey, false)
                AppNavigationBar(
                  navigationItems = navigationItems,
                  currentRoute = currentRoute,
                  onItemClick = { screen, selected -> onTabClick(screen, selected) },
                  pureBlack = pureBlack,
                  glassEnabled = glassNav,
                )
              }
            },
          ) { _ ->
            NavHost(
              navController = navController,
              startDestination = initialRoute,
              modifier = Modifier.fillMaxSize(),
              enterTransition = {
                tabEnterTransition(
                  initialState.destination.route,
                  targetState.destination.route,
                  navigationItems,
                )
              },
              exitTransition = {
                tabExitTransition(
                  initialState.destination.route,
                  targetState.destination.route,
                  navigationItems,
                )
              },
              popEnterTransition = {
                tabPopEnterTransition(
                  initialState.destination.route,
                  targetState.destination.route,
                  navigationItems,
                )
              },
              popExitTransition = {
                tabPopExitTransition(
                  initialState.destination.route,
                  targetState.destination.route,
                  navigationItems,
                )
              },
            ) {
              navigationBuilder(navController, scrollBehavior, activity, snackbarHostState)
            }
          }
        }

        if (playerVisible) {
          BottomSheetPlayer(
            state = playerSheetState,
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
            pureBlack = pureBlack,
            expandQueueRequested = expandQueueRequested,
            onQueueExpanded = { expandQueueRequested = false },
            showPlayerMenuRequested = showPlayerMenuRequested,
            onPlayerMenuShown = { showPlayerMenuRequested = false },
            showLyricsRequested = showLyricsRequested,
            onLyricsShown = { showLyricsRequested = false },
          )
        }

        if (showAudioDevices) {
          com.revenge.music.updates.AudioDeviceBottomSheet(onDismiss = { showAudioDevices = false })
        }

        if (shouldShowWelcome(lastOpenedVersionCode, BuildConfig.VERSION_CODE)) {
          WelcomeDialog(
            onDismissRequest = {
              coroutineScope.launch {
                context.dataStore.edit { it[LastOpenedVersionCodeKey] = BuildConfig.VERSION_CODE }
              }
            },
          )
        }

        updatePrompt?.let { prompt ->
          UpdateAvailableDialog(
            version = prompt.version,
            changelog = prompt.changelog,
            description = prompt.description,
            onDismiss = { updatePrompt = null },
          )
        }

        whatsNew?.let { info ->
          WhatsNewDialog(
            version = BuildConfig.VERSION_NAME,
            info = info,
            onDismiss = {
              saveLastSeenChangelogVersion(context, BuildConfig.VERSION_NAME)
              whatsNew = null
            },
          )
        }

        if (ringtoneState.showTrimmer) {
          RingtoneTrimmerDialog(
            isVisible = true,
            songId = ringtoneState.targetSongId,
            songTitle = ringtoneState.targetSongTitle,
            duration = ringtoneState.targetSongDuration,
            onDismiss = ringtoneViewModel::hideTrimmer,
            onResolveStreamUrl = { songId -> ringtoneViewModel.getStreamUrl(context, songId) },
            onConfirm = { startMs, endMs ->
              ringtoneViewModel.setAsRingtone(context, startMs, endMs)
            },
          )
        }

        RingtoneProgressDialog(
          isVisible = ringtoneState.showProgress,
          progress = ringtoneState.progress,
          statusMessage = ringtoneState.statusMessage,
          isComplete = ringtoneState.isComplete,
          isSuccess = ringtoneState.isSuccess,
          onDismiss = ringtoneViewModel::dismissProgress,
          onOpenSettings = { ringtoneViewModel.openRingtoneSettings(context) },
        )

        if (celebrationVisible) {
          OctoberCelebration(onFinished = { celebrationVisible = false })
        }
      }
    }
  }
}

// ---------------------------------------------------------------------------------------------
// Shell helpers.
// ---------------------------------------------------------------------------------------------

private const val DESKTOP_BREAKPOINT_DP = 840
private const val TABLET_BREAKPOINT_DP = 600
private const val UPDATE_CHECK_DELAY_MS = 2000L
private const val SCROLL_TO_TOP_KEY = "scrollToTop"
private const val HOME_TITLE = "Revenge"

private enum class ShellMenuAction {
  HISTORY,
  STATS,
  TOGETHER,
  RECOGNITION,
}

private fun handleMenuAction(
  action: ShellMenuAction,
  navController: NavHostController,
  listenTogetherInTopBar: Boolean,
) {
  when (action) {
    ShellMenuAction.HISTORY -> navController.navigate("history")
    ShellMenuAction.STATS -> navController.navigate("stats")
    ShellMenuAction.TOGETHER ->
      navController.navigate(
        if (listenTogetherInTopBar) "listen_together_from_topbar" else Screens.ListenTogether.route,
      )
    ShellMenuAction.RECOGNITION -> navController.navigate("recognition")
  }
}

private fun isTopBarRoute(route: String?, listenTogetherInTopBar: Boolean): Boolean {
  return when (route) {
    Screens.Home.route, Screens.Podcast.route, Screens.Library.route -> true
    Screens.ListenTogether.route -> !listenTogetherInTopBar
    else -> false
  }
}

private fun isNavigationBarRoute(route: String?, useRail: Boolean): Boolean {
  if (route == null) return true
  if (useRail) return route != "update"
  if (route == "update") return false
  if (route.startsWith("settings")) return false
  if (route in HIDDEN_BOTTOM_BAR_ROUTES) return false
  if (route in Screens.MainScreens.filterNotNull().map { it.route }) return true
  return route.substringBefore('/') in BOTTOM_BAR_DETAIL_SEGMENTS
}

private fun isPlayerRoute(route: String?): Boolean {
  if (route == null) return true
  if (route.startsWith("settings")) return false
  return route !in HIDDEN_PLAYER_ROUTES
}

private fun topBarTitle(route: String?): Int? =
  when (route) {
    Screens.Home.route -> null
    Screens.Search.route -> R.string.search
    Screens.Library.route -> R.string.filter_library
    Screens.ListenTogether.route -> R.string.together
    Screens.Podcast.route -> R.string.podcast
    else -> null
  }

private val HIDDEN_BOTTOM_BAR_ROUTES =
  setOf(
    "update",
    "listen_together/chat",
    "ambient_mode",
    "music_flow",
    "podcast_video",
    "uptime",
    "wrapped",
  )

private val HIDDEN_PLAYER_ROUTES =
  setOf(
    "update",
    "listen_together/chat",
    "ambient_mode",
    "music_flow",
    "podcast_video",
    "uptime",
    "wrapped",
  )

private val BOTTOM_BAR_DETAIL_SEGMENTS =
  setOf(
    "search",
    "album",
    "online_playlist",
    "local_playlist",
    "spotify_playlist",
    "auto_playlist",
    "cache_playlist",
    "top_playlist",
    "bottom_playlist",
    "artist",
  )

private fun tabIndex(
  route: String?,
  items: List<Screens>,
): Pair<Int, Boolean> {
  val index = items.indexOfFirst { it.route == route?.substringBefore('/') }
  return if (index >= 0) index to true else items.size to false
}

private fun tabEnterTransition(
  fromRoute: String?,
  toRoute: String?,
  items: List<Screens>,
): EnterTransition {
  val (from, fromKnown) = tabIndex(fromRoute, items)
  val (to, toKnown) = tabIndex(toRoute, items)
  val forward = if (fromKnown && toKnown) to >= from else true
  return slideInHorizontally(tween(400, easing = EmphasizedEasing)) { width ->
    if (forward) width / 8 else -width / 8
  } + fadeIn(tween(400, easing = EmphasizedEasing))
}

private fun tabExitTransition(
  fromRoute: String?,
  toRoute: String?,
  items: List<Screens>,
): ExitTransition {
  val (from, fromKnown) = tabIndex(fromRoute, items)
  val (to, toKnown) = tabIndex(toRoute, items)
  val forward = if (fromKnown && toKnown) to >= from else true
  return slideOutHorizontally(tween(400, easing = EmphasizedEasing)) { width ->
    if (forward) -width / 8 else width / 8
  } + fadeOut(tween(400, easing = EmphasizedEasing))
}

private fun tabPopEnterTransition(
  fromRoute: String?,
  toRoute: String?,
  items: List<Screens>,
): EnterTransition {
  val (from, fromKnown) = tabIndex(fromRoute, items)
  val (to, toKnown) = tabIndex(toRoute, items)
  val forward = if (fromKnown && toKnown) to >= from else false
  return slideInHorizontally(tween(400, easing = EmphasizedEasing)) { width ->
    if (forward) width / 8 else -width / 8
  } + fadeIn(tween(400, easing = EmphasizedEasing))
}

private fun tabPopExitTransition(
  fromRoute: String?,
  toRoute: String?,
  items: List<Screens>,
): ExitTransition {
  val (from, fromKnown) = tabIndex(fromRoute, items)
  val (to, toKnown) = tabIndex(toRoute, items)
  val forward = if (fromKnown && toKnown) to >= from else false
  return slideOutHorizontally(tween(400, easing = EmphasizedEasing)) { width ->
    if (forward) -width / 8 else width / 8
  } + fadeOut(tween(400, easing = EmphasizedEasing))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellTopBar(
  title: String,
  scrollBehavior: TopAppBarScrollBehavior,
  onAccountClick: () -> Unit,
  onSettingsClick: () -> Unit,
  accountImageUrl: String?,
  onOpenMenu: (ShellMenuAction) -> Unit,
  showHistory: Boolean,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  TopAppBar(
    title = { Text(title) },
    actions = {
      IconButton(onClick = { menuExpanded = true }) {
        Icon(
          painter = painterResource(R.drawable.more_vert),
          contentDescription = stringResource(R.string.more_options),
        )
      }
      ShellMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        onOpenMenu = onOpenMenu,
        showHistory = showHistory,
      )
      AccountAction(
        accountImageUrl = accountImageUrl,
        onAccountClick = onAccountClick,
        onSettingsClick = onSettingsClick,
      )
    },
    scrollBehavior = scrollBehavior,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopBar(
  query: TextFieldValue,
  onQueryChange: (TextFieldValue) -> Unit,
  onSearch: (String) -> Unit,
  scrollBehavior: TopAppBarScrollBehavior,
  onAccountClick: () -> Unit,
  onSettingsClick: () -> Unit,
  accountImageUrl: String?,
  onOpenMenu: (ShellMenuAction) -> Unit,
  showHistory: Boolean,
) {
  var menuExpanded by remember { mutableStateOf(false) }

  TopAppBar(
    title = {
      TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query.text) }),
        colors =
          TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
          ),
      )
    },
    actions = {
      IconButton(onClick = { menuExpanded = true }) {
        Icon(
          painter = painterResource(R.drawable.more_vert),
          contentDescription = stringResource(R.string.more_options),
        )
      }
      ShellMenu(
        expanded = menuExpanded,
        onDismiss = { menuExpanded = false },
        onOpenMenu = onOpenMenu,
        showHistory = showHistory,
      )
      AccountAction(
        accountImageUrl = accountImageUrl,
        onAccountClick = onAccountClick,
        onSettingsClick = onSettingsClick,
      )
    },
    scrollBehavior = scrollBehavior,
  )
}

@Composable
private fun ShellMenu(
  expanded: Boolean,
  onDismiss: () -> Unit,
  onOpenMenu: (ShellMenuAction) -> Unit,
  showHistory: Boolean,
) {
  DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
    if (showHistory) {
      DropdownMenuItem(
        text = { Text(stringResource(R.string.history)) },
        leadingIcon = {
          Icon(painterResource(R.drawable.music_history), contentDescription = null)
        },
        onClick = {
          onDismiss()
          onOpenMenu(ShellMenuAction.HISTORY)
        },
      )
    }
    DropdownMenuItem(
      text = { Text(stringResource(R.string.stats)) },
      leadingIcon = { Icon(painterResource(R.drawable.stats), contentDescription = null) },
      onClick = {
        onDismiss()
        onOpenMenu(ShellMenuAction.STATS)
      },
    )
    DropdownMenuItem(
      text = { Text(stringResource(R.string.together)) },
      leadingIcon = { Icon(painterResource(R.drawable.group_outlined), contentDescription = null) },
      onClick = {
        onDismiss()
        onOpenMenu(ShellMenuAction.TOGETHER)
      },
    )
    DropdownMenuItem(
      text = { Text(stringResource(R.string.recognition)) },
      leadingIcon = { Icon(painterResource(R.drawable.mic), contentDescription = null) },
      onClick = {
        onDismiss()
        onOpenMenu(ShellMenuAction.RECOGNITION)
      },
    )
  }
}

@Composable
private fun AccountAction(
  accountImageUrl: String?,
  onAccountClick: () -> Unit,
  onSettingsClick: () -> Unit,
) {
  if (!accountImageUrl.isNullOrBlank()) {
    AsyncImage(
      model = accountImageUrl,
      contentDescription = stringResource(R.string.account),
      modifier =
        Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
          .size(32.dp)
          .clip(CircleShape)
          .clickable(onClick = onAccountClick),
      contentScale = ContentScale.Crop,
    )
  } else {
    IconButton(onClick = onSettingsClick) {
      Icon(
        painter = painterResource(R.drawable.settings),
        contentDescription = stringResource(R.string.settings),
      )
    }
  }
}

@Composable
private fun hapticFeedbackFor(hapticHelper: HapticHelper): HapticFeedback =
  remember(hapticHelper) {
    object : HapticFeedback {
      override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        when (hapticFeedbackType) {
          HapticFeedbackType.LongPress -> hapticHelper.performHaptic(HapticType.LONG_PRESS)
          HapticFeedbackType.TextHandleMove -> Unit
          else -> hapticHelper.performHaptic(HapticType.CLICK)
        }
      }
    }
  }

@Composable
private fun OctoberCelebration(onFinished: () -> Unit) {
  val progress = remember { Animatable(0f) }
  LaunchedEffect(Unit) {
    progress.animateTo(1f, tween(durationMillis = 4000))
    onFinished()
  }

  val celebrationColors =
    remember {
      listOf(
        Color(0xFFF44336),
        Color(0xFFFF9800),
        Color(0xFFFFEB3B),
        Color(0xFF4CAF50),
        Color(0xFF2196F3),
        Color(0xFF9C27B0),
      )
    }

  Canvas(Modifier.fillMaxSize().background(Color.Transparent)) {
    val count = 80
    val alpha = (1f - progress.value).coerceIn(0f, 1f)
    repeat(count) { index ->
      val xSeed = ((index * 37) % count) / count.toFloat()
      val speed = 0.4f + (index % 7) * 0.12f
      val y = ((progress.value * speed) % 1f) * size.height
      val x = xSeed * size.width + sin(index.toDouble()).toFloat() * 24f
      drawCircle(
        color = celebrationColors[index % celebrationColors.size],
        radius = 5f + (index % 3),
        center = Offset(x, y),
        alpha = alpha,
      )
    }
  }
}

private fun isOctoberFourteenth(): Boolean {
  val today = LocalDate.now()
  return today.monthValue == 10 && today.dayOfMonth == 14
}

private suspend fun loadArtworkThemeColor(context: Context, url: String): Color? {
  return withContext(Dispatchers.IO) {
    runCatching {
        val request =
          ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val result = coil3.SingletonImageLoader.get(context).execute(request)
        result.image?.toBitmap()?.extractThemeColor()
      }
      .getOrNull()
  }
}

@Suppress("DEPRECATION")
private fun applyRefreshRate(activity: Activity, highRefreshRate: Boolean) {
  val window = activity.window ?: return
  val display: Display? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display
    else activity.windowManager.defaultDisplay
  display ?: return
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
    val modeId =
      if (highRefreshRate) {
        0
      } else {
        display.supportedModes.minByOrNull { abs(it.refreshRate - 60f) }?.modeId ?: 0
      }
    window.attributes = window.attributes.apply { preferredDisplayModeId = modeId }
  } else {
    @Suppress("DEPRECATION")
    val rate =
      if (highRefreshRate) {
        0f
      } else {
        display.supportedRefreshRates.minByOrNull { abs(it - 60f) } ?: 0f
      }
    window.attributes = window.attributes.apply { preferredRefreshRate = rate }
  }
}
