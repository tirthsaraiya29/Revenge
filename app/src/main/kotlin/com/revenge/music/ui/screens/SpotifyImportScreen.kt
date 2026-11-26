package com.revenge.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.revenge.music.spotifyimport.SpotifyImportSourceType
import com.revenge.music.spotifyimport.SpotifyImportSourceUi
import com.revenge.music.spotifyimport.SpotifyImportUiState
import com.revenge.music.spotifyimport.SpotifyImportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyImportScreen(
  navController: NavController,
  spotifyImportViewModel: SpotifyImportViewModel = hiltViewModel(),
) {
  val state by spotifyImportViewModel.uiState.collectAsState()
  var showPicker by remember { mutableStateOf(false) }
  var showLink by remember { mutableStateOf(false) }
  val busy = state.isLoading || state.progress != null

  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text("Spotify Import") },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
          }
        },
      )
    },
  ) { padding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.isAuthenticated) {
        Text("Connect a Spotify account to import your playlists.")
        Button(
          onClick = { navController.navigate("settings/spotify_login") },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Connect") }
        OutlinedButton(
          onClick = { showLink = true },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Import by link") }
      } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
          AsyncImage(
            model = state.accountAvatarUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
          )
          Spacer(Modifier.padding(8.dp))
          Text(state.accountName.ifBlank { "Spotify" }, style = MaterialTheme.typography.titleMedium)
        }
        Button(
          onClick = { showPicker = true },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Select sources") }
        OutlinedButton(
          onClick = { showLink = true },
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Import by link") }
        Button(
          onClick = spotifyImportViewModel::importSelectedSources,
          enabled = state.canImport,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Import selected") }
        OutlinedButton(
          onClick = spotifyImportViewModel::loadSources,
          enabled = !busy,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Refresh") }
        OutlinedButton(
          onClick = spotifyImportViewModel::logout,
          enabled = state.progress == null,
          modifier = Modifier.fillMaxWidth(),
        ) { Text("Logout") }
        if (state.isLoading) Text("Loading...", style = MaterialTheme.typography.bodySmall)
      }
    }
  }

  if (showPicker) {
    SourcePickerDialog(
      sources = state.sources,
      selected = state.selectedSourceIds,
      onToggle = spotifyImportViewModel::toggleSource,
      onSelectAll = spotifyImportViewModel::selectAllSources,
      onClear = spotifyImportViewModel::clearSelection,
      onImport = {
        showPicker = false
        spotifyImportViewModel.importSelectedSources()
      },
      onDismiss = { showPicker = false },
    )
  }

  if (showLink) {
    var link by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showLink = false },
      title = { Text("Import by link") },
      text = {
        OutlinedTextField(
          value = link,
          onValueChange = { link = it },
          singleLine = true,
          label = { Text("Playlist URL or id") },
          modifier = Modifier.fillMaxWidth(),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            spotifyImportViewModel.addPlaylistByUrl(link)
            showLink = false
          },
        ) { Text("Import") }
      },
      dismissButton = { TextButton(onClick = { showLink = false }) { Text("Cancel") } },
    )
  }

  state.progress?.let { progress ->
    AlertDialog(
      onDismissRequest = {},
      title = { Text(progress.sourceTitle) },
      text = {
        Column {
          Text("${progress.percent}%")
          Text("Matched ${progress.matchedTracks}/${progress.totalTracks}")
        }
      },
      confirmButton = {
        TextButton(onClick = spotifyImportViewModel::cancelImport) { Text("Cancel") }
      },
    )
  }

  state.summary?.let { summary ->
    AlertDialog(
      onDismissRequest = spotifyImportViewModel::dismissSummary,
      title = { Text("Import complete") },
      text = {
        Column {
          summary.sources.forEach { source ->
            Text(
              "${source.title}: ${source.importedTracks}/${source.totalTracks} matched, " +
                "${source.failedTracks} failed",
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
          }
          HorizontalDivider()
          Text(
            "Total ${summary.importedTracks}/${summary.totalTracks} matched, " +
              "${summary.failedTracks} failed",
          )
        }
      },
      confirmButton = {
        TextButton(onClick = spotifyImportViewModel::dismissSummary) { Text("Done") }
      },
    )
  }

  state.errorMessage?.let { message ->
    AlertDialog(
      onDismissRequest = spotifyImportViewModel::dismissError,
      title = { Text("Import error") },
      text = { Text(message) },
      confirmButton = {
        TextButton(onClick = spotifyImportViewModel::dismissError) { Text("OK") }
      },
    )
  }
}

@Composable
private fun SourcePickerDialog(
  sources: List<SpotifyImportSourceUi>,
  selected: Set<String>,
  onToggle: (String) -> Unit,
  onSelectAll: () -> Unit,
  onClear: () -> Unit,
  onImport: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Select sources") },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          TextButton(onClick = onSelectAll) { Text("Select all") }
          TextButton(onClick = onClear) { Text("Clear") }
        }
        LazyColumn(modifier = Modifier.height(360.dp)) {
          items(sources, key = { it.id }) { source ->
            Row(
              modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = source.id in selected,
                onCheckedChange = { onToggle(source.id) },
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(source.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle =
                  buildString {
                    if (source.type == SpotifyImportSourceType.PLAYLIST &&
                      source.subtitle.isNotBlank()
                    ) {
                      append(source.subtitle)
                      append(" · ")
                    }
                    source.trackCount?.let { append("$it songs") }
                  }
                if (subtitle.isNotBlank()) {
                  Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onImport) { Text("Import") } },
    dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
  )
}
