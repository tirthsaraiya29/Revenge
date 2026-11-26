package com.revenge.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.navigation.NavController
import com.revenge.core.constants.EnableLastFMScrobblingKey
import com.revenge.core.constants.LastFMSessionKey
import com.revenge.core.constants.LastFMUseNowPlaying
import com.revenge.core.constants.LastFMUseSendLikes
import com.revenge.core.constants.LastFMUsernameKey
import com.revenge.core.constants.ScrobbleDelayPercentKey
import com.revenge.core.constants.ScrobbleDelaySecondsKey
import com.revenge.core.constants.ScrobbleMinSongDurationKey
import com.revenge.core.utils.dataStore
import com.revenge.music.utils.lastfm.LastFM
import com.revenge.music.utils.lastfm.LastFmException
import com.revenge.music.utils.rememberPreference
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LastFMSettingsScreen(navController: NavController) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var sessionKey by rememberPreference(LastFMSessionKey, "")
  var username by rememberPreference(LastFMUsernameKey, "")
  var scrobbling by rememberPreference(EnableLastFMScrobblingKey, false)
  var nowPlaying by rememberPreference(LastFMUseNowPlaying, false)
  var sendLikes by rememberPreference(LastFMUseSendLikes, false)
  var minDuration by rememberPreference(ScrobbleMinSongDurationKey, 30)
  var delayPercent by rememberPreference(ScrobbleDelayPercentKey, 0.5f)
  var delaySeconds by rememberPreference(ScrobbleDelaySecondsKey, 180)

  var showLogin by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Last.fm") },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Text(
        if (sessionKey.isNotBlank()) "Connected as ${username.ifBlank { "Last.fm user" }}"
        else "Not connected",
        style = MaterialTheme.typography.titleMedium,
      )
      if (sessionKey.isBlank()) {
        Button(onClick = { showLogin = true }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
          Text("Log in")
        }
      } else {
        OutlinedButton(
          onClick = {
            scope.launch {
              context.dataStore.edit { preferences ->
                preferences.remove(LastFMSessionKey)
                preferences.remove(LastFMUsernameKey)
              }
              LastFM.sessionKey = null
              sessionKey = ""
              username = ""
            }
          },
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Log out") }
      }

      HorizontalDivider()
      ToggleRow("Scrobble plays", scrobbling) { scrobbling = it }
      ToggleRow("Send now playing", nowPlaying) { nowPlaying = it }
      ToggleRow("Send likes", sendLikes) { sendLikes = it }

      HorizontalDivider()
      Text("Scrobble threshold", style = MaterialTheme.typography.titleSmall)
      NumberRow("Minimum song duration (s)", minDuration, 30) { minDuration = it }
      PercentRow("Delay percent", delayPercent, 0.5f) { delayPercent = it }
      NumberRow("Absolute delay (s)", delaySeconds, 180) { delaySeconds = it }
    }
  }

  if (showLogin) {
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showLogin = false },
      title = { Text("Last.fm login") },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = user,
            onValueChange = { user = it },
            label = { Text("Username") },
            singleLine = true,
          )
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
          )
          error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
      },
      confirmButton = {
        TextButton(
          enabled = user.isNotBlank() && password.isNotBlank() && !loading,
          onClick = {
            loading = true
            error = null
            scope.launch {
              val result = LastFM.getMobileSession(user, password)
              result.fold(
                onSuccess = { authentication ->
                  val session = authentication.session
                  if (session == null) {
                    error = "Invalid authentication response"
                  } else {
                    LastFM.sessionKey = session.key
                    context.dataStore.edit { preferences ->
                      preferences[LastFMSessionKey] = session.key
                      preferences[LastFMUsernameKey] = session.name
                    }
                    sessionKey = session.key
                    username = session.name
                    showLogin = false
                  }
                },
                onFailure = { error = describeLastFmError(it) },
              )
              loading = false
            }
          },
        ) { Text(if (loading) "Logging in..." else "Login") }
      },
      dismissButton = { TextButton(onClick = { showLogin = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label)
    Switch(checked = checked, onCheckedChange = onChange)
  }
}

@Composable
private fun NumberRow(label: String, value: Int, default: Int, onChange: (Int) -> Unit) {
  var editing by remember { mutableStateOf(false) }
  var draft by remember { mutableStateOf(value.toString()) }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label)
    TextButton(onClick = { draft = value.toString(); editing = true }) { Text(value.toString()) }
  }
  if (editing) {
    AlertDialog(
      onDismissRequest = { editing = false },
      title = { Text(label) },
      text = {
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          label = { Text("Value") },
        )
      },
      confirmButton = {
        TextButton(onClick = { draft.toIntOrNull()?.let(onChange); editing = false }) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { onChange(default); editing = false }) { Text("Reset") }
      },
    )
  }
}

@Composable
private fun PercentRow(label: String, value: Float, default: Float, onChange: (Float) -> Unit) {
  var editing by remember { mutableStateOf(false) }
  var draft by remember { mutableStateOf(value.toString()) }
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(label)
    TextButton(onClick = { draft = value.toString(); editing = true }) { Text(value.toString()) }
  }
  if (editing) {
    AlertDialog(
      onDismissRequest = { editing = false },
      title = { Text(label) },
      text = {
        OutlinedTextField(
          value = draft,
          onValueChange = { draft = it },
          label = { Text("0.0 - 1.0") },
        )
      },
      confirmButton = {
        TextButton(onClick = { draft.toFloatOrNull()?.let(onChange); editing = false }) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { onChange(default); editing = false }) { Text("Reset") }
      },
    )
  }
}

internal fun describeLastFmError(error: Throwable): String =
  when (error) {
    is LastFmException ->
      when (error.code) {
        4 -> "Invalid credentials"
        6 -> "Invalid parameters"
        9 -> "Invalid session key"
        10 -> "Invalid API key"
        13 -> "Invalid signature"
        14 -> "Unauthorized token"
        15 -> "Service unavailable"
        else -> error.message
      }
    else -> "Network error"
  }
