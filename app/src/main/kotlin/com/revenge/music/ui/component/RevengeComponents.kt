package com.revenge.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenge.music.R
import com.revenge.music.ui.theme.RevengeColor
import com.revenge.music.ui.theme.RevengeMotion
import com.revenge.music.ui.theme.RevengeRadius
import com.revenge.music.ui.theme.RevengeSize
import com.revenge.music.ui.theme.RevengeSpacing

/**
 * Revenge Core UI Primitives
 *
 * Highly reusable, accessible, cohesive components adhering to the Revenge design system.
 */
enum class RevengeButtonVariant {
  PRIMARY,
  SECONDARY,
  TONAL,
  OUTLINED,
  GHOST,
}

enum class RevengeButtonSize(val height: Dp, val textStyle: TextStyle, val horizontalPadding: Dp) {
  SMALL(32.dp, TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), 12.dp),
  MEDIUM(40.dp, TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), 16.dp),
  LARGE(48.dp, TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold), 20.dp),
}

@Composable
fun RevengeButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  text: String? = null,
  leadingIcon: Painter? = null,
  trailingIcon: Painter? = null,
  variant: RevengeButtonVariant = RevengeButtonVariant.PRIMARY,
  size: RevengeButtonSize = RevengeButtonSize.MEDIUM,
  enabled: Boolean = true,
  content: (@Composable RowScope.() -> Unit)? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.97f else 1f,
      animationSpec = RevengeMotion.quickTween(),
      label = "button_scale",
    )

  val (containerColor, contentColor, borderStroke) =
    when (variant) {
      RevengeButtonVariant.PRIMARY ->
        Triple(
          MaterialTheme.colorScheme.primary,
          MaterialTheme.colorScheme.onPrimary,
          null,
        )
      RevengeButtonVariant.SECONDARY ->
        Triple(
          MaterialTheme.colorScheme.surfaceContainerHigh,
          MaterialTheme.colorScheme.onSurface,
          BorderStroke(1.dp, RevengeColor.BorderSubtleDark),
        )
      RevengeButtonVariant.TONAL ->
        Triple(
          MaterialTheme.colorScheme.secondaryContainer,
          MaterialTheme.colorScheme.onSecondaryContainer,
          null,
        )
      RevengeButtonVariant.OUTLINED ->
        Triple(
          Color.Transparent,
          MaterialTheme.colorScheme.onSurface,
          BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        )
      RevengeButtonVariant.GHOST ->
        Triple(
          Color.Transparent,
          MaterialTheme.colorScheme.onSurfaceVariant,
          null,
        )
    }

  Surface(
    onClick = onClick,
    modifier = modifier.height(size.height).scale(scale),
    enabled = enabled,
    shape = RoundedCornerShape(RevengeRadius.pill),
    color = if (enabled) containerColor else containerColor.copy(alpha = 0.38f),
    contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
    border = borderStroke,
    interactionSource = interactionSource,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = size.horizontalPadding),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      if (leadingIcon != null) {
        Icon(
          painter = leadingIcon,
          contentDescription = null,
          modifier = Modifier.size(size.height * 0.45f).padding(end = RevengeSpacing.xs),
        )
      }
      if (content != null) {
        content()
      } else if (text != null) {
        Text(
          text = text,
          style = size.textStyle,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      if (trailingIcon != null) {
        Icon(
          painter = trailingIcon,
          contentDescription = null,
          modifier = Modifier.size(size.height * 0.45f).padding(start = RevengeSpacing.xs),
        )
      }
    }
  }
}

/** Standard accessible icon button with subtle press feedback and optional tooltip */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RevengeIconButton(
  onClick: () -> Unit,
  icon: Painter,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tint: Color = LocalContentColor.current,
  containerColor: Color = Color.Transparent,
  size: Dp = RevengeSize.compactTouchTarget,
  iconSize: Dp = RevengeSize.iconMedium,
  tooltip: String? = null,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isPressed by interactionSource.collectIsPressedAsState()
  val scale by
    animateFloatAsState(
      targetValue = if (isPressed) 0.92f else 1f,
      animationSpec = RevengeMotion.quickTween(),
      label = "icon_btn_scale",
    )

  val buttonContent =
    @Composable {
      Box(
        modifier =
          modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
              interactionSource = interactionSource,
              indication = androidx.compose.material3.ripple(bounded = true, radius = size / 2),
              enabled = enabled,
              role = Role.Button,
              onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          painter = icon,
          contentDescription = contentDescription,
          tint = if (enabled) tint else tint.copy(alpha = 0.38f),
          modifier = Modifier.size(iconSize),
        )
      }
    }

  if (tooltip != null) {
    TooltipBox(
      positionProvider = TooltipDefaults.rememberTooltipPositionProvider(),
      tooltip = { PlainTooltip { Text(tooltip) } },
      state = rememberTooltipState(),
    ) {
      buttonContent()
    }
  } else {
    buttonContent()
  }
}

/** Refined surface card with crisp border and standard padding */
@Composable
fun RevengeCard(
  modifier: Modifier = Modifier,
  onClick: (() -> Unit)? = null,
  containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
  borderColor: Color = RevengeColor.BorderSubtleDark,
  shape: RoundedCornerShape = RoundedCornerShape(RevengeRadius.card),
  content: @Composable () -> Unit,
) {
  val cardModifier =
    if (onClick != null) {
      modifier
        .clip(shape)
        .border(1.dp, borderColor, shape)
        .background(containerColor, shape)
        .clickable(role = Role.Button, onClick = onClick)
    } else {
      modifier.clip(shape).border(1.dp, borderColor, shape).background(containerColor, shape)
    }

  Box(modifier = cardModifier) {
    content()
  }
}

/** Standard section header with clean typography and optional action */
@Composable
fun RevengeSectionHeader(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  actionText: String? = null,
  onActionClick: (() -> Unit)? = null,
  actionIcon: Painter? = null,
) {
  Row(
    modifier =
      modifier.fillMaxWidth().padding(horizontal = RevengeSpacing.lg, vertical = RevengeSpacing.sm),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f, fill = false)) {
      Text(
        text = title,
        style =
          MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
          ),
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (subtitle != null) {
        Text(
          text = subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }

    if (actionText != null && onActionClick != null) {
      RevengeButton(
        onClick = onActionClick,
        text = actionText,
        trailingIcon = actionIcon,
        variant = RevengeButtonVariant.GHOST,
        size = RevengeButtonSize.SMALL,
      )
    }
  }
}

/** Modern accessible search input */
@Composable
fun RevengeSearchInput(
  query: String,
  onQueryChange: (String) -> Unit,
  onSearch: (String) -> Unit,
  modifier: Modifier = Modifier,
  placeholder: String = "Search songs, albums, artists...",
  leadingIcon: Painter = painterResource(R.drawable.search),
  onClear: (() -> Unit)? = null,
  trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
  val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
  val outlineColor = RevengeColor.BorderSubtleDark

  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .height(48.dp)
        .clip(RoundedCornerShape(RevengeRadius.pill))
        .border(1.dp, outlineColor, RoundedCornerShape(RevengeRadius.pill))
        .background(containerColor)
        .padding(horizontal = RevengeSpacing.md),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      painter = leadingIcon,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.size(20.dp),
    )

    Spacer(modifier = Modifier.width(RevengeSpacing.sm))

    Box(modifier = Modifier.weight(1f)) {
      if (query.isEmpty()) {
        Text(
          text = placeholder,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
      }
      BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        textStyle =
          MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
          ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
        modifier = Modifier.fillMaxWidth(),
      )
    }

    if (query.isNotEmpty()) {
      RevengeIconButton(
        onClick = {
          onQueryChange("")
          onClear?.invoke()
        },
        icon = painterResource(R.drawable.close),
        contentDescription = "Clear search",
        size = 32.dp,
        iconSize = 16.dp,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    if (trailingContent != null) {
      trailingContent()
    }
  }
}

/** Musical, premium empty state */
@Composable
fun RevengeEmptyState(
  icon: Painter,
  title: String,
  message: String,
  modifier: Modifier = Modifier,
  actionText: String? = null,
  onActionClick: (() -> Unit)? = null,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(RevengeSpacing.xl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Box(
      modifier =
        Modifier.size(72.dp)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        painter = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(36.dp),
      )
    }

    Spacer(modifier = Modifier.height(RevengeSpacing.lg))

    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(RevengeSpacing.xs))

    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(horizontal = RevengeSpacing.lg),
    )

    if (actionText != null && onActionClick != null) {
      Spacer(modifier = Modifier.height(RevengeSpacing.lg))
      RevengeButton(
        onClick = onActionClick,
        text = actionText,
        variant = RevengeButtonVariant.PRIMARY,
        size = RevengeButtonSize.MEDIUM,
      )
    }
  }
}

/** Non-intrusive loading indicator */
@Composable
fun RevengeLoadingState(
  modifier: Modifier = Modifier,
  message: String? = null,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(RevengeSpacing.xxl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    CircularProgressIndicator(
      modifier = Modifier.size(36.dp),
      color = MaterialTheme.colorScheme.primary,
      strokeWidth = 3.dp,
    )
    if (message != null) {
      Spacer(modifier = Modifier.height(RevengeSpacing.md))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

/** Cohesive error state with retry */
@Composable
fun RevengeErrorState(
  message: String,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  title: String = "Something went wrong",
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(RevengeSpacing.xl),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      painter = painterResource(R.drawable.error),
      contentDescription = null,
      tint = RevengeColor.Error,
      modifier = Modifier.size(44.dp),
    )

    Spacer(modifier = Modifier.height(RevengeSpacing.md))

    Text(
      text = title,
      style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(RevengeSpacing.xs))

    Text(
      text = message,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(RevengeSpacing.lg))

    RevengeButton(
      onClick = onRetry,
      text = "Try Again",
      leadingIcon = painterResource(R.drawable.replay),
      variant = RevengeButtonVariant.SECONDARY,
      size = RevengeButtonSize.MEDIUM,
    )
  }
}
