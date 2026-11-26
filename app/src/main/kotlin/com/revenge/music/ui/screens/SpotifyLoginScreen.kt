package com.revenge.music.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.revenge.music.spotify.SpotifyAuth

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyLoginScreen(
  navController: NavController,
  onCookiesCaptured: (spDc: String, spKey: String) -> Unit,
) {
  val captured = remember { mutableStateOf(false) }
  val holder = remember { mutableStateOf<WebView?>(null) }

  fun maybeCapture(cookieHeader: String?) {
    if (captured.value) return
    val cookie = cookieHeader ?: return
    val values =
      cookie.split(';').mapNotNull { part ->
        val index = part.indexOf('=')
        if (index <= 0) null
        else part.substring(0, index).trim() to part.substring(index + 1).trim()
      }.toMap()
    val spDc = values["sp_dc"]?.takeIf { it.isNotBlank() } ?: return
    val spKey = values["sp_key"].orEmpty()
    captured.value = true
    onCookiesCaptured(spDc, spKey)
    navController.navigateUp()
  }

  DisposableEffect(Unit) {
    onDispose {
      holder.value?.apply {
        stopLoading()
        destroy()
      }
      holder.value = null
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Spotify Login") },
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
      factory = { context ->
        val webView =
          WebView(context).apply {
            // nosemgrep: revenge-webview-javascript -- Spotify OAuth page requires JS; no JS interface is added.
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setSupportMultipleWindows(true)
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            val cookies = CookieManager.getInstance()
            cookies.setAcceptCookie(true)
            cookies.setAcceptThirdPartyCookies(this, true)
            cookies.removeAllCookies(null)
            cookies.flush()
            webChromeClient = WebChromeClient()
            webViewClient =
              object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                  super.onPageStarted(view, url, favicon)
                  maybeCapture(CookieManager.getInstance().getCookie("https://open.spotify.com"))
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                  super.onPageFinished(view, url)
                  maybeCapture(CookieManager.getInstance().getCookie("https://open.spotify.com"))
                }
              }
            loadUrl(SpotifyAuth.LOGIN_URL)
          }
        holder.value = webView
        webView
      },
    )
  }
}
