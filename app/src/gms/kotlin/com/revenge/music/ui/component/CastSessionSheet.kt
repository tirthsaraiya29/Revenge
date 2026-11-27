package com.revenge.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.cast.MediaMetadata as CastMediaMetadata
import com.google.android.gms.cast.framework.CastContext
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastSessionSheet(
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val castContext = runCatching { CastContext.getSharedInstance(context) }.getOrNull()

  LaunchedEffect(castContext) {
    while (true) {
      delay(1_000)
      val session = castContext?.sessionManager?.currentCastSession
      if (castContext == null || session == null) {
        onDismiss()
        break
      }
    }
  }

  val session = castContext?.sessionManager?.currentCastSession ?: return
  val media = session.remoteMediaClient?.mediaInfo?.metadata
  val title =
    media?.getString(CastMediaMetadata.KEY_TITLE)
      ?: session.remoteMediaClient?.mediaInfo?.contentId?.substringAfterLast('/')
      ?: "Nothing playing"
  val artist = media?.getString(CastMediaMetadata.KEY_ARTIST)

  ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      Text("Casting to", style = MaterialTheme.typography.labelMedium)
      Text(
        session.castDevice?.friendlyName ?: "Cast device",
        style = MaterialTheme.typography.titleLarge,
      )
      Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 12.dp),
      )
      artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
      Button(
        onClick = {
          runCatching { castContext.sessionManager.endCurrentSession(true) }
          onDismiss()
        },
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
      ) {
        Text("Disconnect")
      }
    }
  }
}
