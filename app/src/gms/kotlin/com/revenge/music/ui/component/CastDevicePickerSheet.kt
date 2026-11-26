package com.revenge.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.revenge.music.playback.CastDeviceKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePickerSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val router = remember { MediaRouter.getInstance(context) }
  val selector =
    remember {
      MediaRouteSelector.Builder()
        .addControlCategory(
          CastMediaControlIntent.categoryForCast(
            CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
          ),
        )
        .build()
    }
  var routes by remember { mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList()) }
  var loading by remember { mutableStateOf(true) }
  var error by remember { mutableStateOf<String?>(null) }

  fun refresh() {
    routes = router.routes.filter { it.matchesSelector(selector) }
    loading = false
  }

  DisposableEffect(router) {
    val callback =
      object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) {
          refresh()
        }

        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) {
          refresh()
        }

        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) {
          refresh()
        }
      }
    runCatching {
      router.addCallback(
        selector,
        callback,
        MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY,
      )
      refresh()
    }.onFailure { error = it.message }
    onDispose {
      runCatching { router.removeCallback(callback) }
    }
  }

  ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text("Cast to", style = MaterialTheme.typography.titleMedium)
      when {
        error != null ->
          Text(
            "Could not discover devices: $error",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(vertical = 16.dp),
          )
        loading -> Text("Searching for devices...", modifier = Modifier.padding(vertical = 16.dp))
        routes.isEmpty() -> Text("No Cast devices found", modifier = Modifier.padding(vertical = 16.dp))
        else ->
          routes.forEach { route ->
            val kind = CastDeviceKind.fromName(route.name?.toString().orEmpty(), route.description)
            Row(
              modifier =
                Modifier.fillMaxWidth().clickable {
                    val fresh = router.routes.firstOrNull { it.id == route.id }
                    if (fresh != null) {
                      router.selectRoute(fresh)
                      onDismiss()
                    }
                  }
                  .padding(vertical = 12.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(route.name?.toString().orEmpty())
              Text(
                kind.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
      }
    }
  }
}
