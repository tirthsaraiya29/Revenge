package com.revenge.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.revenge.core.constants.AccountEmailKey
import com.revenge.core.constants.InnerTubeCookieKey
import com.revenge.innertube.utils.parseCookieString
import com.revenge.music.BuildConfig
import com.revenge.music.R
import com.revenge.music.ui.component.Material3SettingsGroup
import com.revenge.music.ui.component.Material3SettingsItem
import com.revenge.music.utils.rememberPreference
import com.revenge.music.viewmodels.HomeViewModel

/**
 * The account and navigation hub. It intentionally holds destinations, not a second copy of every
 * preference: each row opens the screen that owns that setting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
  onDismissRequest: () -> Unit,
  onNavigate: (String) -> Unit,
  homeViewModel: HomeViewModel,
) {
  val (innerTubeCookie, _) = rememberPreference(InnerTubeCookieKey, "")
  val isLoggedIn =
    remember(innerTubeCookie) {
      innerTubeCookie.isNotEmpty() && "SAPISID" in parseCookieString(innerTubeCookie)
    }
  val (accountEmail, _) = rememberPreference(AccountEmailKey, "")
  val accountName by homeViewModel.accountName.collectAsState()
  val accountImageUrl by homeViewModel.accountImageUrl.collectAsState()

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val open: (String) -> Unit = { route ->
    onDismissRequest()
    onNavigate(route)
  }

  ModalBottomSheet(
    onDismissRequest = onDismissRequest,
    sheetState = sheetState,
    dragHandle = { BottomSheetDefaults.DragHandle() },
    containerColor = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(
      modifier =
        Modifier.fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp)
          .padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = stringResource(R.string.app_name),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 8.dp, top = 4.dp),
      )

      Material3SettingsGroup(
        title = stringResource(R.string.account),
        compact = true,
        items =
          buildList {
            add(
              Material3SettingsItem(
                title = {
                  Text(if (isLoggedIn) accountName else stringResource(R.string.anonymous))
                },
                description = {
                  Text(
                    if (isLoggedIn) accountEmail.ifEmpty { stringResource(R.string.logged_in) }
                    else stringResource(R.string.not_logged_in),
                  )
                },
                icon = painterResource(R.drawable.account),
                trailingContent =
                  if (isLoggedIn && !accountImageUrl.isNullOrBlank()) {
                    {
                      AsyncImage(
                        model = accountImageUrl,
                        contentDescription = stringResource(R.string.account),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                      )
                    }
                  } else null,
                onClick = { open(if (isLoggedIn) "settings/account" else "login") },
              ),
            )
            add(
              Material3SettingsItem(
                title = { Text(stringResource(R.string.listening_recap)) },
                description = { Text(stringResource(R.string.listening_recap_desc)) },
                icon = painterResource(R.drawable.ic_revenge_wrapped),
                trailingContent = {
                  Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(4.dp),
                  ) {
                    Text(
                      text = "2026",
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                      style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                      color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                  }
                },
                onClick = { open("wrapped") },
              ),
            )
          },
      )

      Material3SettingsGroup(
        title = "Playback",
        compact = true,
        items =
          listOf(
            Material3SettingsItem(
              title = { Text(stringResource(R.string.player_and_audio)) },
              description = { Text(stringResource(R.string.setting_desc_player)) },
              icon = painterResource(R.drawable.equalizer),
              onClick = { open("settings/player") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.equalizer)) },
              icon = painterResource(R.drawable.revenge_equalizer),
              onClick = { open("settings/equalizer") },
            ),
          ),
      )

      Material3SettingsGroup(
        title = "Library",
        compact = true,
        items =
          listOf(
            Material3SettingsItem(
              title = { Text(stringResource(R.string.content)) },
              description = { Text(stringResource(R.string.setting_desc_content)) },
              icon = painterResource(R.drawable.library_music_outlined),
              onClick = { open("settings/content") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.storage)) },
              description = { Text(stringResource(R.string.setting_desc_storage)) },
              icon = painterResource(R.drawable.storage),
              onClick = { open("settings/storage") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.backup_restore)) },
              description = { Text(stringResource(R.string.setting_desc_backup)) },
              icon = painterResource(R.drawable.backup),
              onClick = { open("settings/backup_restore") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.privacy)) },
              description = { Text(stringResource(R.string.setting_desc_privacy)) },
              icon = painterResource(R.drawable.security),
              onClick = { open("settings/privacy") },
            ),
          ),
      )

      Material3SettingsGroup(
        title = "App",
        compact = true,
        items =
          listOf(
            Material3SettingsItem(
              title = { Text(stringResource(R.string.appearance)) },
              description = { Text(stringResource(R.string.setting_desc_appearance)) },
              icon = painterResource(R.drawable.palette),
              onClick = { open("settings/appearance") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.ai_lyrics_translation)) },
              description = { Text(stringResource(R.string.setting_desc_ai)) },
              customIcon = {
                Text(
                  text = "Ai",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              },
              onClick = { open("settings/ai") },
            ),
            Material3SettingsItem(
              title = { Text("Extensions") },
              description = { Text("Manage third-party content plugins") },
              icon = painterResource(R.drawable.extension),
              onClick = { open("settings/extensions") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.settings)) },
              description = { Text(stringResource(R.string.setting_desc_settings_main)) },
              icon = painterResource(R.drawable.settings),
              onClick = { open("settings") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.system_update)) },
              icon = painterResource(R.drawable.update),
              onClick = { open("settings/update") },
            ),
            Material3SettingsItem(
              title = { Text(stringResource(R.string.about)) },
              icon = painterResource(R.drawable.info),
              trailingContent = {
                Text(
                  text = BuildConfig.VERSION_NAME,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              },
              onClick = { open("settings/about") },
            ),
          ),
      )

      Spacer(Modifier.width(1.dp))
      Text(
        text = "Revenge ${BuildConfig.VERSION_NAME}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
  }
}
