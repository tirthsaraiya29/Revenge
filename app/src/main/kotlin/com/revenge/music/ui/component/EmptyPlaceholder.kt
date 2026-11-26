package com.revenge.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revenge.music.ui.theme.RevengeSpacing

@Composable
fun EmptyPlaceholder(
  @DrawableRes icon: Int,
  text: String,
  modifier: Modifier = Modifier,
  description: String? = null,
  action: (@Composable () -> Unit)? = null,
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
    modifier = modifier.fillMaxSize().padding(RevengeSpacing.lg),
  ) {
    Image(
      painter = painterResource(icon),
      contentDescription = null,
      colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
      modifier =
        Modifier.size(64.dp)
          .background(MaterialTheme.colorScheme.surfaceContainer, CircleShape)
          .padding(16.dp),
    )

    Spacer(Modifier.height(RevengeSpacing.md))

    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
    )

    if (description != null) {
      Spacer(Modifier.height(RevengeSpacing.xxs))
      Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }

    if (action != null) {
      Spacer(Modifier.height(RevengeSpacing.md))
      action()
    }
  }
}
