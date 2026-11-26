package com.revenge.music

import android.app.ActivityManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.allowHardware
import coil3.request.crossfade
import com.revenge.core.constants.AppLanguageKey
import com.revenge.core.constants.ContentCountryKey
import com.revenge.core.constants.ContentLanguageKey
import com.revenge.core.constants.CountryCodeToName
import com.revenge.core.constants.DataSyncIdKey
import com.revenge.core.constants.IpVersionKey
import com.revenge.core.constants.InnerTubeCookieKey
import com.revenge.core.constants.LanguageCodeToName
import com.revenge.core.constants.MaxImageCacheSizeKey
import com.revenge.core.constants.PlaybackEngineKey
import com.revenge.core.constants.ProxyEnabledKey
import com.revenge.core.constants.ProxyPasswordKey
import com.revenge.core.constants.ProxyTypeKey
import com.revenge.core.constants.ProxyUrlKey
import com.revenge.core.constants.ProxyUsernameKey
import com.revenge.core.constants.SYSTEM_DEFAULT
import com.revenge.core.constants.UseLoginForBrowse
import com.revenge.core.constants.VisitorDataKey
import com.revenge.core.di.ApplicationScope
import com.revenge.core.extensions.toEnum
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.core.utils.reportException
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.IpVersion
import com.revenge.innertube.models.YouTubeLocale
import com.revenge.kugou.KuGou
import com.revenge.music.utils.AppContextHolder
import com.revenge.music.utils.CrashHandler
import com.revenge.music.utils.InnerTubeXResolver
import com.revenge.music.utils.LocalAlbumArtFetcher
import com.revenge.music.utils.NewEpisodesWorker
import com.revenge.music.utils.YTPlayerUtils
import com.revenge.music.utils.YouTubeThumbnailFallbackInterceptor
import com.revenge.music.utils.cipher.CipherDeobfuscator
import com.revenge.music.utils.lastfm.LastFM
import dagger.hilt.android.HiltAndroidApp
import java.net.Authenticator
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Application entry point. Process routing is significant: the crash process and the secondary
 * Cast SDK process must never touch application state, workers or the settings DataStore.
 */
@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory, Configuration.Provider {

  @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

  @Inject lateinit var workerFactory: HiltWorkerFactory

  @Volatile private var imageCacheSizeMiB: Int? = null

  override fun onCreate() {
    super.onCreate()

    when (processKind()) {
      ProcessKind.CRASH -> {
        CrashHandler.install(this)
        return
      }
      ProcessKind.SECONDARY -> {
        CrashHandler.install(this)
        return
      }
      ProcessKind.MAIN -> CrashHandler.install(this)
    }

    AppContextHolder.initialize(this)
    NewEpisodesWorker.enqueue(this)
    CipherDeobfuscator.initialize(this)
    YTPlayerUtils.initialize()

    if (BuildConfig.DEBUG) {
      Timber.plant(Timber.DebugTree())
    }

    applicationScope.launch(Dispatchers.IO) {
      imageCacheSizeMiB =
        runCatching { dataStore[MaxImageCacheSizeKey] ?: DEFAULT_IMAGE_CACHE_SIZE_MIB }
          .getOrDefault(DEFAULT_IMAGE_CACHE_SIZE_MIB)
    }

    applicationScope.launch(Dispatchers.IO) {
      WARM_UP_HOSTS.forEach { host -> runCatching { InetAddress.getByName(host) } }
    }

    applicationScope.launch {
      initializeSettings()
      InnerTubeXResolver.init(this@App)
      observeSettingsChanges()
    }
  }

  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.ERROR)
        .setWorkerFactory(workerFactory)
        .build()

  override fun startForegroundService(service: Intent): ComponentName? {
    return try {
      super.startForegroundService(service)
    } catch (exception: Exception) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        exception is android.app.ForegroundServiceStartNotAllowedException
      ) {
        Timber.w(exception, "Foreground service start was not allowed")
        null
      } else {
        throw exception
      }
    }
  }

  override fun newImageLoader(context: Context): ImageLoader {
    val cacheSizeMiB =
      imageCacheSizeMiB
        ?: runCatching { context.dataStore[MaxImageCacheSizeKey] ?: DEFAULT_IMAGE_CACHE_SIZE_MIB }
          .getOrDefault(DEFAULT_IMAGE_CACHE_SIZE_MIB)

    val builder =
      ImageLoader.Builder(this)
        .crossfade(true)
        .components {
          add(YouTubeThumbnailFallbackInterceptor())
          add(LocalAlbumArtFetcher.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(this@App, 0.25).build() }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      builder.allowHardware(true)
    }

    if (cacheSizeMiB <= 0) {
      builder.diskCache(null)
    } else {
      builder.diskCache(
        DiskCache.Builder()
          .directory(cacheDir.resolve("coil"))
          .maxSizeBytes(cacheSizeMiB.toLong() * 1024L * 1024L)
          .build(),
      )
    }

    return builder.build()
  }

  private fun processKind(): ProcessKind {
    val name = currentProcessName()
    return when {
      name == "$packageName:crash" -> ProcessKind.CRASH
      name == null || name == packageName -> ProcessKind.MAIN
      else -> ProcessKind.SECONDARY
    }
  }

  @Suppress("DEPRECATION")
  private fun currentProcessName(): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      return Application.getProcessName()
    }
    val pid = Process.myPid()
    return (getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
      ?.runningAppProcesses
      ?.firstOrNull { it.pid == pid }
      ?.processName
  }

  private suspend fun initializeSettings() {
    val preferences = runCatching { dataStore.data.first() }.getOrNull()

    val contentCountry = preferences?.get(ContentCountryKey)
    val contentLanguage = preferences?.get(ContentLanguageKey)
    val appLanguage = preferences?.get(AppLanguageKey)
    YouTube.locale = resolveLocale(contentCountry, contentLanguage, appLanguage)

    if (isTraditionalChineseDevice()) {
      KuGou.useTraditionalChinese = true
    }

    if (preferences != null && preferences[ProxyEnabledKey] == true) {
      applyProxy(
        type = parseProxyType(preferences[ProxyTypeKey]),
        url = preferences[ProxyUrlKey],
        username = preferences[ProxyUsernameKey],
        password = preferences[ProxyPasswordKey],
      )
    } else {
      YouTube.proxy = null
      YouTube.proxyAuth = null
    }

    YouTube.useLoginForBrowse = preferences?.get(UseLoginForBrowse) ?: true
    YouTube.ipVersion = (preferences?.get(IpVersionKey)).toEnum(IpVersion.AUTO)
    YTPlayerUtils.playbackEngine =
      (preferences?.get(PlaybackEngineKey)).toEnum(com.revenge.core.constants.PlaybackEngine.AUTO)

    createUpdateNotificationChannel()
  }

  private fun observeSettingsChanges() {
    val settings =
      dataStore.data.catch { throwable ->
        Timber.w(throwable, "Settings read failed; treating values as absent")
        emit(emptyPreferences())
      }

    applicationScope.launch(Dispatchers.IO) {
      settings
        .map { it[VisitorDataKey] }
        .distinctUntilChanged()
        .collect { raw ->
          runCatching {
            val value = raw?.takeIf { it.isNotBlank() && it != "null" }
            if (value != null) {
              YouTube.visitorData = value
            } else {
              YouTube.visitorData().getOrNull()?.let { refreshed ->
                YouTube.visitorData = refreshed
                dataStore.edit { it[VisitorDataKey] = refreshed }
              }
            }
          }.onFailure { Timber.w(it, "visitorData observer failed") }
        }
    }

    applicationScope.launch(Dispatchers.IO) {
      settings
        .map { it[DataSyncIdKey] }
        .distinctUntilChanged()
        .collect { raw ->
          runCatching { YouTube.dataSyncId = normalizeDataSyncId(raw) }
            .onFailure { Timber.w(it, "dataSyncId observer failed") }
        }
    }

    applicationScope.launch(Dispatchers.IO) {
      settings
        .map { it[InnerTubeCookieKey] }
        .distinctUntilChanged()
        .collect { raw ->
          try {
            YouTube.cookie = raw
          } catch (throwable: Throwable) {
            Timber.e(throwable, "Stored cookie could not be parsed")
            forgetAccount(this@App)
          }
        }
    }

    applicationScope.launch(Dispatchers.IO) {
      combine(
          settings.map { it[ContentCountryKey] },
          settings.map { it[ContentLanguageKey] },
          settings.map { it[AppLanguageKey] },
        ) { country, language, appLanguage ->
          Triple(country, language, appLanguage)
        }
        .distinctUntilChanged()
        .collect { (country, language, appLanguage) ->
          runCatching { YouTube.locale = resolveLocale(country, language, appLanguage) }
            .onFailure { Timber.w(it, "locale observer failed") }
        }
    }

    applicationScope.launch(Dispatchers.IO) {
      settings
        .map { it[IpVersionKey] }
        .distinctUntilChanged()
        .collect { raw ->
          runCatching { YouTube.ipVersion = raw.toEnum(IpVersion.AUTO) }
            .onFailure { Timber.w(it, "ipVersion observer failed") }
        }
    }

    LastFM.initialize(
      apiKey = BuildConfig.LASTFM_API_KEY,
      secret = BuildConfig.LASTFM_SECRET,
    )
  }

  private fun createUpdateNotificationChannel() {
    runCatching {
      val manager = getSystemService(NotificationManager::class.java) ?: return
      val channel =
        NotificationChannel(
            UPDATE_CHANNEL_ID,
            getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
          )
          .apply { description = getString(R.string.update_channel_desc) }
      manager.createNotificationChannel(channel)
    }.onFailure { Timber.w(it, "Could not create update notification channel") }
  }

  private fun resolveLocale(
    storedCountry: String?,
    storedLanguage: String?,
    appLanguage: String?,
  ): YouTubeLocale {
    val systemLocale =
      appLanguage
        ?.takeIf { it.isNotBlank() && it != SYSTEM_DEFAULT }
        ?.let { Locale.forLanguageTag(it) }
        ?: Locale.getDefault()

    val country =
      storedCountry?.takeIf { it.isNotBlank() && it != SYSTEM_DEFAULT }
        ?: systemLocale.country.takeIf { it in CountryCodeToName }
        ?: FALLBACK_COUNTRY

    val language =
      storedLanguage?.takeIf { it.isNotBlank() && it != SYSTEM_DEFAULT }
        ?: systemLocale.language.takeIf { it in LanguageCodeToName }
        ?: systemLocale.toLanguageTag().takeIf { it in LanguageCodeToName }
        ?: FALLBACK_LANGUAGE

    return YouTubeLocale(gl = country, hl = language)
  }

  private fun isTraditionalChineseDevice(): Boolean {
    val locale = Locale.getDefault()
    return locale.language == "zh" &&
      (locale.country == "TW" || locale.toLanguageTag().equals("zh-TW", ignoreCase = true))
  }

  private suspend fun applyProxy(
    type: Proxy.Type,
    url: String?,
    username: String?,
    password: String?,
  ) {
    if (url.isNullOrBlank()) return
    try {
      YouTube.proxy = buildProxy(type, url)
      when {
        type == Proxy.Type.HTTP && !username.isNullOrBlank() -> {
          YouTube.proxyAuth =
            "Basic " +
              Base64.encodeToString("$username:${password.orEmpty()}".toByteArray(), Base64.NO_WRAP)
        }
        type != Proxy.Type.HTTP && !username.isNullOrBlank() && password != null -> {
          Authenticator.setDefault(
            object : Authenticator() {
              override fun getPasswordAuthentication(): PasswordAuthentication =
                PasswordAuthentication(username, password.toCharArray())
            },
          )
        }
      }
    } catch (throwable: Throwable) {
      withContext(Dispatchers.Main) {
        Toast.makeText(this@App, R.string.failed_to_parse_proxy, Toast.LENGTH_SHORT).show()
      }
      reportException(throwable)
    }
  }

  private fun parseProxyType(raw: String?): Proxy.Type {
    return raw?.trim()?.takeIf { it.isNotEmpty() }?.let {
      runCatching { Proxy.Type.valueOf(it.uppercase(Locale.US)) }.getOrNull()
    } ?: Proxy.Type.HTTP
  }

  private fun buildProxy(type: Proxy.Type, rawUrl: String): Proxy {
    val normalized = if (rawUrl.contains("://")) rawUrl else "http://$rawUrl"
    val uri = URI(normalized)
    val host = uri.host ?: throw IllegalArgumentException("Proxy URL has no host")
    val port = if (uri.port > 0) uri.port else DEFAULT_PROXY_PORT
    return Proxy(type, InetSocketAddress(host, port))
  }

  private fun normalizeDataSyncId(raw: String?): String? {
    if (raw == null || !raw.contains(DATA_SYNC_SEPARATOR)) return raw
    return if (raw.endsWith(DATA_SYNC_SEPARATOR)) {
      raw.substringBefore(DATA_SYNC_SEPARATOR)
    } else {
      raw.substringAfter(DATA_SYNC_SEPARATOR)
    }
  }

  private enum class ProcessKind {
    MAIN,
    CRASH,
    SECONDARY,
  }

  companion object {
    private const val DEFAULT_IMAGE_CACHE_SIZE_MIB = 512
    private const val DEFAULT_PROXY_PORT = 8080
    private const val UPDATE_CHANNEL_ID = "updates"
    private const val DATA_SYNC_SEPARATOR = "||"
    private const val FALLBACK_COUNTRY = "US"
    private const val FALLBACK_LANGUAGE = "en"

    private val WARM_UP_HOSTS =
      listOf("music.youtube.com", "youtubei.googleapis.com", "googlevideo.com")

    /**
     * Removes all persisted account identity and clears the in-memory session. Safe to call from
     * any dispatcher; never throws.
     */
    suspend fun forgetAccount(context: Context) {
      runCatching {
        context.dataStore.edit { preferences ->
          preferences.remove(InnerTubeCookieKey)
          preferences.remove(VisitorDataKey)
          preferences.remove(DataSyncIdKey)
          preferences.remove(com.revenge.core.constants.AccountNameKey)
          preferences.remove(com.revenge.core.constants.AccountEmailKey)
          preferences.remove(com.revenge.core.constants.AccountChannelHandleKey)
        }

        YouTube.cookie = null
        YouTube.visitorData = null
        YouTube.dataSyncId = null

        InnerTubeXResolver.onSessionChanged()

        withContext(Dispatchers.Main) {
          android.webkit.CookieManager.getInstance().removeAllCookies(null)
          android.webkit.CookieManager.getInstance().flush()
        }
      }.onFailure { Timber.w(it, "forgetAccount failed") }
    }
  }
}
