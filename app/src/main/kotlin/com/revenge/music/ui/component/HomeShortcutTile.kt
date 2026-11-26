package com.revenge.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.revenge.innertube.models.YTItem
import com.revenge.music.R
import com.revenge.music.ui.theme.RevengeColor
import com.revenge.music.ui.theme.RevengeRadius
import com.revenge.music.ui.theme.RevengeSize
import com.revenge.music.ui.theme.RevengeSpacing
import com.revenge.core.ui.utils.resize

/** Compact tile for the shortcut grid at the top of Home: cover on the left, title on the right. */
@Composable
fun HomeShortcutTile(
  item: YTItem,
  isPinned: Boolean,
  isActive: Boolean,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(RevengeRadius.sm)
  val containerColor =
    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surfaceContainerHigh
  val borderColor =
    if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    else RevengeColor.BorderSubtleDark

  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(RevengeSpacing.sm),
    modifier =
      modifier
        .height(56.dp)
        .clip(shape)
        .border(1.dp, borderColor, shape)
        .background(containerColor),
  ) {
    AsyncImage(
      model = item.thumbnail?.resize(226, 226),
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier =
        Modifier
          .size(56.dp)
          .clip(RoundedCornerShape(topStart = RevengeRadius.sm, bottomStart = RevengeRadius.sm))
          .background(MaterialTheme.colorScheme.surfaceVariant),
    )
    Text(
      text = item.title,
      style = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
      ),
      color =
        if (isActive) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurface,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f).padding(vertical = RevengeSpacing.xs),
    )
    Box(
      modifier = Modifier.fillMaxHeight().padding(end = RevengeSpacing.sm),
      contentAlignment = Alignment.Center,
    ) {
      when {
        isActive && isPlaying ->
          Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
          )
        isPinned ->
          Icon(
            painter = painterResource(R.drawable.ic_push_pin),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(14.dp),
          )
      }
    }
  }
}
