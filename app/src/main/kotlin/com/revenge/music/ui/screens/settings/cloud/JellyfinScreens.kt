package com.revenge.music.ui.screens.settings.cloud

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.revenge.music.cloud.JellyfinPlaylist
import com.revenge.music.cloud.JellyfinRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinLoginScreen(navController: NavController, repository: JellyfinRepository) {
  val context = LocalContext.current
  var serverUrl by remember { mutableStateOf("") }
  var username by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf<String?>(null) }
  var loading by remember { mutableStateOf(false) }
  val scope = rememberCoroutineScope()

  fun submit() {
    // nosemgrep: revenge-cleartext-endpoint -- user-supplied LAN Jellyfin server; HTTP is a
    // supported local setup.
    val url =
      serverUrl.trim().let {
        if (it.startsWith("http://") || it.startsWith("https://")) it else "https://$it"
      }
    if (url.isBlank() || username.isBlank() || password.isBlank()) {
      error = "Server URL, username and password are required"
      return
    }
    error = null
    loading = true
    scope.launch {
      repository
        .login(url, username, password)
        .fold(
          onSuccess = {
            Toast.makeText(context, "Connected to ${url.trimEnd('/')}", Toast.LENGTH_SHORT).show()
            navController.navigate("settings/jellyfin_dashboard") {
              popUpTo("settings/jellyfin_login") { inclusive = true }
            }
          },
          onFailure = {
            error = it.localizedMessage ?: "Could not connect to the server"
            loading = false
          },
        )
    }
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Jellyfin") },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      OutlinedTextField(
        value = serverUrl,
        onValueChange = { serverUrl = it },
        label = { Text("Server URL") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
          onClick = {
            serverUrl =
              "http://${serverUrl.trim().removePrefix("http://").removePrefix("https://")}" // nosemgrep: revenge-cleartext-endpoint -- user explicitly selects HTTP for a self-hosted server.
          },
        ) {
          Text("Add http://")
        }
        OutlinedButton(
          onClick = {
            serverUrl =
              "https://${serverUrl.trim().removePrefix("http://").removePrefix("https://")}"
          },
        ) {
          Text("Add https://")
        }
      }
      if (serverUrl.trim().startsWith("http://", ignoreCase = true)) {
        Text(
          "HTTP is unencrypted. Use HTTPS unless this server is on a trusted local network.",
          color = MaterialTheme.colorScheme.error,
          style = MaterialTheme.typography.bodySmall,
        )
      }
      OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text("Username") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
      )
      OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password") },
        singleLine = true,
        visualTransformation =
          if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
          IconButton(onClick = { passwordVisible = !passwordVisible }) {
            Text(if (passwordVisible) "Hide" else "Show")
          }
        },
        modifier = Modifier.fillMaxWidth(),
      )
      error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
      Button(onClick = ::submit, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
        Text(if (loading) "Connecting..." else "Login")
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JellyfinDashboardScreen(navController: NavController, repository: JellyfinRepository) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val serverUrl by repository.serverUrlFlow.collectAsState()
  val username by repository.usernameFlow.collectAsState()
  var playlists by remember { mutableStateOf<List<JellyfinPlaylist>>(emptyList()) }
  var busy by remember { mutableStateOf(false) }

  fun refresh() {
    busy = true
    scope.launch {
      playlists = repository.getPlaylists().getOrDefault(emptyList())
      busy = false
    }
  }

  LaunchedEffect(Unit) { refresh() }

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Jellyfin") },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
      Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(serverUrl ?: "No server", style = MaterialTheme.typography.titleMedium)
        Text(username ?: "No user", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedButton(
            enabled = !busy,
            onClick = {
              scope.launch {
                repository
                  .ping()
                  .fold(
                    onSuccess = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() },
                    onFailure = {
                      Toast.makeText(
                          context,
                          it.localizedMessage ?: "Ping failed",
                          Toast.LENGTH_SHORT,
                        )
                        .show()
                    },
                  )
              }
            },
          ) {
            Text("Test Ping")
          }
          Button(
            enabled = !busy,
            onClick = {
              busy = true
              scope.launch {
                repository
                  .syncPlaylistsAndSongs()
                  .fold(
                    onSuccess = { count ->
                      Toast.makeText(context, "Synced $count songs", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = {
                      Toast.makeText(
                          context,
                          it.localizedMessage ?: "Sync failed",
                          Toast.LENGTH_SHORT,
                        )
                        .show()
                    },
                  )
                refresh()
              }
            },
          ) {
            Text(if (busy) "Working..." else "Sync to Library")
          }
        }
      }
      HorizontalDivider()
      if (playlists.isEmpty()) {
        Text("No playlists", modifier = Modifier.padding(16.dp))
      } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
          items(playlists, key = { it.id }) { playlist ->
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
              Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
              Text("${playlist.songCount} songs", style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
          }
        }
      }
      OutlinedButton(
        onClick = {
          repository.logout()
          navController.navigate("settings/jellyfin_login") {
            popUpTo("settings/jellyfin_dashboard") { inclusive = true }
          }
        },
        modifier = Modifier.fillMaxWidth().padding(16.dp),
      ) {
        Text("Log out")
      }
    }
  }
}
