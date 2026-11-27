package com.revenge.music.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.revenge.core.constants.DataSyncIdKey
import com.revenge.core.constants.InnerTubeCookieKey
import com.revenge.core.constants.SavedAccountsKey
import com.revenge.core.constants.VisitorDataKey
import com.revenge.core.models.AccountData
import com.revenge.core.utils.dataStore
import com.revenge.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val JSON = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(navController: NavController) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val done = remember { mutableStateOf(false) }
  val holder = remember { mutableStateOf<WebView?>(null) }

  fun cookieValue(cookie: String?, name: String): String? =
    cookie
      ?.split(';')
      ?.mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) null
        else part.substring(0, index).trim() to part.substring(index + 1).trim()
      }
      ?.firstOrNull { it.first == name }
      ?.second

  fun complete(cookie: String, visitorData: String?, dataSyncId: String?) {
    if (done.value) return
    done.value = true
    scope.launch {
      try {
        withContext(Dispatchers.IO) {
          YouTube.cookie = cookie
          YouTube.visitorData = visitorData
          YouTube.dataSyncId = dataSyncId
          val account =
            AccountData(
              name = "YouTube",
              email = "",
              channelHandle = "",
              cookie = cookie,
              visitorData = visitorData.orEmpty(),
              dataSyncId = dataSyncId.orEmpty(),
            )
          val existing = runCatching {
            JSON.decodeFromString<List<AccountData>>(
              context.dataStore.data.first()[SavedAccountsKey] ?: "[]",
            )
          }
            .getOrDefault(emptyList())
          val accounts = listOf(account) + existing.filterNot { it.cookie == cookie }
          context.dataStore.edit { preferences ->
            preferences[SavedAccountsKey] = JSON.encodeToString(accounts)
            preferences[InnerTubeCookieKey] = cookie
            visitorData?.let { preferences[VisitorDataKey] = it }
            dataSyncId?.let { preferences[DataSyncIdKey] = it }
          }
        }
      } catch (_: Throwable) {
        // The login flow must never surface an exception to the UI thread.
      } finally {
        navController.navigateUp()
      }
    }
  }

  fun inspect(url: String?) {
    val cookie =
      CookieManager.getInstance().getCookie("https://music.youtube.com")
        ?: CookieManager.getInstance().getCookie("https://www.youtube.com")
        ?: return
    val authenticated =
      cookie.contains("SAPISID") ||
        cookie.contains("__Secure-3PSID") ||
        cookie.contains("LOGIN_INFO")
    val onYouTube =
      url?.contains("music.youtube.com") == true || url?.contains("youtube.com") == true
    if (authenticated && onYouTube) {
      complete(
        cookie = cookie,
        visitorData = cookieValue(cookie, "VISITOR_DATA"),
        dataSyncId = cookieValue(cookie, "DATASYNC_ID"),
      )
    }
  }

  fun isAllowed(uri: Uri): Boolean {
    val host = uri.host?.lowercase() ?: return false
    return ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
  }

  DisposableEffect(Unit) {
    onDispose {
      holder.value?.apply {
        stopLoading()
        clearHistory()
        destroy()
      }
      holder.value = null
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Sign in") },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    AndroidView(
      modifier = Modifier.fillMaxSize().padding(padding),
      factory = {
        val webView =
          WebView(it).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient =
              object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                  view: WebView?,
                  request: android.webkit.WebResourceRequest?,
                ): Boolean {
                  val uri = request?.url ?: return false
                  if (isAllowed(uri)) {
                    inspect(uri.toString())
                    return false
                  }
                  runCatching {
                    view?.context?.startActivity(Intent(Intent.ACTION_VIEW, uri))
                  }
                  return true
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  inspect(url)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  inspect(url)
                }
              }
            loadUrl(
              "https://accounts.google.com/ServiceLogin?continue=https://music.youtube.com",
            )
          }
        holder.value = webView
        webView
      },
    )
  }
}

private val ALLOWED_HOSTS =
  listOf(
    "google.com",
    "googleapis.com",
    "googleusercontent.com",
    "googlevideo.com",
    "gstatic.com",
    "youtube.com",
    "g.co",
  )
