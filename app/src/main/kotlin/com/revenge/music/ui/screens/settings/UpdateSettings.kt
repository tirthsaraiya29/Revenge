package com.revenge.music.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.revenge.music.BuildConfig
import com.revenge.music.LocalPlayerAwareWindowInsets
import com.revenge.music.R
import com.revenge.music.ui.component.IconButton
import com.revenge.music.ui.component.Material3SettingsGroup
import com.revenge.music.ui.component.Material3SettingsItem
import com.revenge.music.ui.utils.backToMain
import com.revenge.music.ui.utils.parseMarkdownToSections
import com.revenge.music.ui.utils.parseSimpleMarkdown
import com.revenge.music.ui.utils.safeOpenUri
import com.revenge.music.updates.component.UpdateInfoDialog
import com.revenge.music.updates.updater.autoClearOldApks
import com.revenge.music.updates.updater.getAutoUpdateCheckSetting
import com.revenge.music.updates.updater.getBetaUpdatesSetting
import com.revenge.music.updates.updater.getDownloadedApkCount
import com.revenge.music.updates.updater.getUpdateAvailableState
import com.revenge.music.updates.updater.getUpdateNotificationsSetting
import org.json.JSONObject

/**
 * Settings screen for managing app updates, checking for new releases, and displaying the latest
 * release notes ("What's New").
 *
 * @param navController Navigation controller for screen transitions.
 * @param scrollBehavior Top app bar scroll behavior.
 * @param highlightKey Optional key to highlight a specific settings item.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettings(
  navController: NavController,
  scrollBehavior: TopAppBarScrollBehavior,
  highlightKey: String? = null,
) {
  val scrollState = androidx.compose.foundation.rememberScrollState()

  val context = LocalContext.current
  var autoUpdateEnabled by remember { mutableStateOf(getAutoUpdateCheckSetting(context)) }
  var updateNotificationsEnabled by remember {
    mutableStateOf(getUpdateNotificationsSetting(context))
  }
  var betaUpdatesEnabled by remember { mutableStateOf(getBetaUpdatesSetting(context)) }
  val isUpdateAvailable = getUpdateAvailableState(context) && autoUpdateEnabled
  var apkCount by remember { mutableStateOf(getDownloadedApkCount(context)) }
  var showInfoDialog by remember { mutableStateOf(false) }
  var releaseNotes by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    autoClearOldApks(context)
    apkCount = getDownloadedApkCount(context)

    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
      if (BuildConfig.UPDATE_REPO.isBlank()) return@withContext
      try {
        val url =
          java.net.URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
        val json = url.openStream().bufferedReader().use { it.readText() }
        val targetRelease = JSONObject(json)
        releaseNotes = targetRelease.getString("body")
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  if (showInfoDialog) {
    UpdateInfoDialog(onDismiss = { showInfoDialog = false })
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.update_settings_title)) },
        navigationIcon = {
          IconButton(onClick = navController::navigateUp, onLongClick = navController::backToMain) {
            Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
          }
        },
        scrollBehavior = scrollBehavior,
      )
    },
  ) { padding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(padding)
          .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal),
          )
          .verticalScroll(scrollState)
          .padding(horizontal = 16.dp),
    ) {
      Spacer(modifier = Modifier.height(16.dp))

    Material3SettingsGroup(
      scrollState = scrollState,
      title = stringResource(R.string.app_updates_title),
      items =
        listOf(
          Material3SettingsItem(
            isHighlighted = (highlightKey == stringResource(R.string.system_update)),
            icon = painterResource(R.drawable.update),
            title = { Text(stringResource(R.string.system_update)) },
            description = {
              if (isUpdateAvailable) {
                Text(
                  text = "New update is available",
                  color = androidx.compose.ui.graphics.Color.Red,
                )
              } else {
                Text(stringResource(R.string.version, BuildConfig.VERSION_NAME))
              }
            },
            onClick = {
              if (BuildConfig.UPDATE_REPO.isNotBlank()) {
                context.safeOpenUri("https://github.com/${BuildConfig.UPDATE_REPO}/releases/latest")
              }
            },
          ),
        ),
    )

    Text(
      text = "Updates are distributed through the release channel configured for this build.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
    )

    releaseNotes?.let { notes ->
      com.revenge.music.ui.component.PreferenceGroupTitle(title = "What's New")
      androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        colors =
          androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
          ),
        elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 0.dp),
      ) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
          val (effectiveDescription, effectiveSections) =
            remember(notes) { parseMarkdownToSections(notes) }

          if (!effectiveDescription.isNullOrBlank()) {
            Text(
              text = parseSimpleMarkdown(effectiveDescription, MaterialTheme.colorScheme.primary),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(bottom = 8.dp),
            )
          }

          if (effectiveSections.isNotEmpty()) {
            effectiveSections.forEachIndexed { sectionIndex, section ->
              if (section.title.isNotBlank()) {
                Text(
                  text = parseSimpleMarkdown(section.title, MaterialTheme.colorScheme.primary),
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.primary,
                  modifier =
                    Modifier.padding(
                      top =
                        if (sectionIndex == 0 && effectiveDescription.isNullOrBlank()) 0.dp
                        else 10.dp,
                      bottom = 4.dp,
                    ),
                )
              }
              section.items.forEach { item ->
                if (item.isNotBlank()) {
                  Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                  ) {
                    Text(
                      text = "•",
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.primary,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = parseSimpleMarkdown(item.trim(), MaterialTheme.colorScheme.primary),
                      style = MaterialTheme.typography.bodyMedium,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      modifier = Modifier.weight(1f),
                    )
                  }
                }
              }
            }
          } else if (effectiveDescription.isNullOrBlank()) {
            Text(
              text = parseSimpleMarkdown(notes, MaterialTheme.colorScheme.primary),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
      Spacer(modifier = Modifier.height(16.dp))
    }

    Spacer(modifier = Modifier.height(16.dp))

    Spacer(
      Modifier.windowInsetsPadding(
        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom),
      ),
    )
  }
}
}
