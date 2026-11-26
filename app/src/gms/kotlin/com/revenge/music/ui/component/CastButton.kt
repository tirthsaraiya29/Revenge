package com.revenge.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.revenge.music.R
import com.revenge.core.constants.EnableGoogleCastKey
import com.revenge.music.utils.rememberPreference

@Composable
fun CastButton(
  modifier: Modifier = Modifier,
  tintColor: Color = MaterialTheme.colorScheme.onSurface,
  asMenuItem: Boolean = false,
) {
  val context = LocalContext.current
  val (enabled) = rememberPreference(EnableGoogleCastKey, true)
  if (!enabled) return

  val castContext = remember { runCatching { CastContext.getSharedInstance(context) }.getOrNull() }
  if (castContext == null) return

  var showPicker by remember { mutableStateOf(false) }
  var showSession by remember { mutableStateOf(false) }
  val session = castContext.sessionManager.currentCastSession
  val connected = session != null

  Box(modifier = modifier.clickable { if (connected) showSession = true else showPicker = true }) {
    if (connected) {
      Icon(
        painter = painterResource(R.drawable.cast_connected),
        contentDescription = "Cast session",
        tint = tintColor,
        modifier = Modifier.size(24.dp),
      )
    } else {
      Icon(
        imageVector = Icons.Filled.Cast,
        contentDescription = "Cast",
        tint = tintColor,
        modifier = Modifier.size(24.dp),
      )
    }
  }

  if (showPicker) {
    CastDevicePickerSheet(onDismiss = { showPicker = false })
  }
  if (showSession) {
    CastSessionSheet(onDismiss = { showSession = false })
  }
}
