package com.revenge.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenge.music.R
import com.revenge.music.ui.screens.Screens
import com.revenge.music.ui.theme.RevengeColor
import com.revenge.music.ui.theme.RevengeRadius
import com.revenge.music.ui.theme.RevengeSize
import com.revenge.music.ui.theme.RevengeSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@Stable
fun isRouteSelected(
  currentRoute: String?,
  screenRoute: String,
  navigationItems: List<Screens>,
): Boolean {
  if (currentRoute == null) return false
  if (currentRoute == screenRoute) return true
  return navigationItems.any { it.route == screenRoute } && currentRoute.startsWith("$screenRoute/")
}

/**
 * Reconstructed Revenge Bottom Navigation Bar
 *
 * Professional, flush, accessible navigation chrome with subtle hairline border,
 * clear indicator pills, and readable typography.
 */
@Composable
fun AppNavigationBar(
  navigationItems: List<Screens>,
  currentRoute: String?,
  onItemClick: (Screens, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  pureBlack: Boolean = false,
  slimNav: Boolean = false,
  glassEnabled: Boolean = false,
  onSearchLongClick: (() -> Unit)? = null,
) {
  val containerColor =
    if (pureBlack) RevengeColor.DarkPureBlackCanvas
    else MaterialTheme.colorScheme.surfaceContainerHigh
  val topBorderColor =
    if (pureBlack) Color.White.copy(alpha = 0.08f)
    else RevengeColor.BorderSubtleDark

  val haptics = LocalHapticFeedback.current
  val viewConfiguration = LocalViewConfiguration.current

  NavigationBar(
    modifier =
      modifier
        .fillMaxWidth()
        .border(1.dp, topBorderColor)
        .background(containerColor),
    containerColor = containerColor,
    contentColor = MaterialTheme.colorScheme.onSurface,
    tonalElevation = 0.dp,
  ) {
    navigationItems.forEach { screen ->
      val isSelected =
        remember(currentRoute, screen.route) {
          isRouteSelected(currentRoute, screen.route, navigationItems)
        }
      val iconRes =
        remember(isSelected, screen) {
          if (isSelected) screen.iconIdActive else screen.iconIdInactive
        }

      val isSearchItem = screen == Screens.Search && onSearchLongClick != null
      val interactionSource = remember { MutableInteractionSource() }

      if (isSearchItem) {
        LaunchedEffect(interactionSource) {
          var isLongClick = false
          interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
              is PressInteraction.Press -> {
                isLongClick = false
                delay(viewConfiguration.longPressTimeoutMillis)
                isLongClick = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSearchLongClick.invoke()
              }
              is PressInteraction.Release -> {
                if (!isLongClick) {
                  onItemClick(screen, isSelected)
                }
              }
              is PressInteraction.Cancel -> {
                isLongClick = false
              }
            }
          }
        }
      }

      NavigationBarItem(
        selected = isSelected,
        onClick = {
          if (!isSearchItem) {
            onItemClick(screen, isSelected)
          }
        },
        interactionSource = interactionSource,
        icon = {
          Icon(
            painter = painterResource(id = iconRes),
            contentDescription = stringResource(screen.titleId),
            modifier = Modifier.size(RevengeSize.iconMedium),
          )
        },
        label =
          if (!slimNav) {
            {
              Text(
                text = stringResource(screen.titleId),
                style = MaterialTheme.typography.labelSmall.copy(
                  fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                  fontSize = 11.sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          } else null,
        alwaysShowLabel = !slimNav,
        colors =
          NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          ),
      )
    }
  }
}

/**
 * Reconstructed Revenge Navigation Rail
 *
 * Professional side navigation for wide screens, tablets, foldables, and landscape desktop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationRail(
  navigationItems: List<Screens>,
  currentRoute: String?,
  onItemClick: (Screens, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  pureBlack: Boolean = false,
  onSearchLongClick: (() -> Unit)? = null,
  onMusicRecognitionClick: (() -> Unit)? = null,
  musicRecognitionContentDescription: String = "",
  onMusicFlowClick: (() -> Unit)? = null,
  musicFlowIconRes: Int? = R.drawable.swipe_vertical,
  musicFlowContentDescription: String = "",
  onShuffleClick: (() -> Unit)? = null,
  shuffleEnabled: Boolean = false,
  shuffleIconRes: Int? = null,
  shuffleContentDescription: String = "",
  onAiHubClick: (() -> Unit)? = null,
  aiHubIconRes: Int? = null,
  aiHubContentDescription: String = "",
) {
  val containerColor =
    if (pureBlack) RevengeColor.DarkPureBlackCanvas
    else MaterialTheme.colorScheme.surfaceContainerHigh
  val rightBorderColor =
    if (pureBlack) Color.White.copy(alpha = 0.08f)
    else RevengeColor.BorderSubtleDark

  val haptics = LocalHapticFeedback.current
  val viewConfiguration = LocalViewConfiguration.current
  var showMoreOptionsSheet by rememberSaveable { mutableStateOf(false) }
  val hasMoreOptions =
    onMusicFlowClick != null ||
      (onShuffleClick != null && shuffleIconRes != null) ||
      onAiHubClick != null ||
      onMusicRecognitionClick != null

  NavigationRail(
    modifier =
      modifier
        .width(RevengeSize.navigationRailWidth)
        .border(1.dp, rightBorderColor)
        .background(containerColor),
    containerColor = containerColor,
    header = {
      Box(
        modifier =
          Modifier
            .padding(vertical = RevengeSpacing.lg)
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = painterResource(R.drawable.ic_launcher_nobg),
          contentDescription = "Revenge",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(22.dp),
        )
      }
    },
  ) {
    Spacer(modifier = Modifier.weight(1f))

    navigationItems.forEach { screen ->
      val isSelected =
        remember(currentRoute, screen.route) {
          isRouteSelected(currentRoute, screen.route, navigationItems)
        }
      val iconRes =
        remember(isSelected, screen) {
          if (isSelected) screen.iconIdActive else screen.iconIdInactive
        }

      val isSearchItem = screen == Screens.Search && onSearchLongClick != null
      val interactionSource = remember { MutableInteractionSource() }

      if (isSearchItem) {
        LaunchedEffect(interactionSource) {
          var isLongClick = false
          interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
              is PressInteraction.Press -> {
                isLongClick = false
                delay(viewConfiguration.longPressTimeoutMillis)
                isLongClick = true
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onSearchLongClick.invoke()
              }
              is PressInteraction.Release -> {
                if (!isLongClick) {
                  onItemClick(screen, isSelected)
                }
              }
              is PressInteraction.Cancel -> {
                isLongClick = false
              }
            }
          }
        }
      }

      NavigationRailItem(
        selected = isSelected,
        onClick = {
          if (!isSearchItem) {
            onItemClick(screen, isSelected)
          }
        },
        interactionSource = interactionSource,
        icon = {
          Icon(
            painter = painterResource(id = iconRes),
            contentDescription = stringResource(screen.titleId),
            modifier = Modifier.size(RevengeSize.iconMedium),
          )
        },
        label = {
          Text(
            text = stringResource(screen.titleId),
            style = MaterialTheme.typography.labelSmall.copy(
              fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
              fontSize = 11.sp,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        },
        colors =
          NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
          ),
      )
    }

    if (hasMoreOptions) {
      NavigationRailItem(
        selected = false,
        onClick = { showMoreOptionsSheet = true },
        icon = {
          Icon(
            painter = painterResource(id = R.drawable.more_horiz),
            contentDescription = stringResource(R.string.more_label),
            modifier = Modifier.size(RevengeSize.iconMedium),
          )
        },
        label = {
          Text(
            text = stringResource(R.string.more_label),
            style = MaterialTheme.typography.labelSmall,
          )
        },
      )
    }

    Spacer(modifier = Modifier.weight(1f))
  }

  if (hasMoreOptions && showMoreOptionsSheet) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
      onDismissRequest = { showMoreOptionsSheet = false },
      sheetState = sheetState,
      dragHandle = { BottomSheetDefaults.DragHandle() },
      containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = stringResource(R.string.more_options),
          style =
            MaterialTheme.typography.headlineSmall.copy(
              fontWeight = FontWeight.Bold,
              letterSpacing = 0.5.sp,
            ),
          color = MaterialTheme.colorScheme.onSurface,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        Material3SettingsGroup(
          items =
            buildList {
              if (onMusicFlowClick != null && musicFlowIconRes != null) {
                add(
                  Material3SettingsItem(
                    title = {
                      Text(
                        if (musicFlowContentDescription.isNotEmpty()) musicFlowContentDescription
                        else stringResource(R.string.music_flow),
                      )
                    },
                    icon = painterResource(musicFlowIconRes),
                    onClick = {
                      showMoreOptionsSheet = false
                      onMusicFlowClick()
                    },
                  ),
                )
              }

              if (onAiHubClick != null && aiHubIconRes != null) {
                add(
                  Material3SettingsItem(
                    title = { Text(aiHubContentDescription) },
                    icon = painterResource(aiHubIconRes),
                    onClick = {
                      showMoreOptionsSheet = false
                      onAiHubClick()
                    },
                  ),
                )
              }

              if (onMusicRecognitionClick != null) {
                add(
                  Material3SettingsItem(
                    title = {
                      Text(
                        musicRecognitionContentDescription.ifEmpty {
                          stringResource(R.string.recognition)
                        },
                      )
                    },
                    icon = painterResource(R.drawable.mic),
                    onClick = {
                      showMoreOptionsSheet = false
                      onMusicRecognitionClick()
                    },
                  ),
                )
              }

              if (onShuffleClick != null && shuffleIconRes != null) {
                add(
                  Material3SettingsItem(
                    title = { Text(shuffleContentDescription) },
                    icon = painterResource(shuffleIconRes),
                    trailingContent = {
                      Switch(checked = shuffleEnabled, onCheckedChange = { onShuffleClick() })
                    },
                    onClick = onShuffleClick,
                  ),
                )
              }
            },
        )
      }
    }
  }
}
