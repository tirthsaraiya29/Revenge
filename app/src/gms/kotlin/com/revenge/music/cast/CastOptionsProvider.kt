package com.revenge.music.cast

import android.content.Context
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider
import com.google.android.gms.cast.framework.media.NotificationOptions
import com.google.android.gms.cast.framework.media.MediaIntentReceiver

class CastOptionsProvider : OptionsProvider {

  override fun getCastOptions(context: Context): CastOptions {
    val notificationOptions =
      NotificationOptions.Builder()
        .setActions(
          listOf(
            MediaIntentReceiver.ACTION_SKIP_PREVIOUS,
            MediaIntentReceiver.ACTION_TOGGLE_PLAYBACK,
            MediaIntentReceiver.ACTION_SKIP_NEXT,
            MediaIntentReceiver.ACTION_STOP_CASTING,
          ),
        )
        .setCompactViewActionIndices(listOf(1, 2))
        .build()
    return CastOptions.Builder()
      .setReceiverApplicationId(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
      .setNotificationOptions(notificationOptions)
      .setStopReceiverApplicationWhenEndingSession(true)
      .setResumeSavedSession(true)
      .setEnableReconnectionService(true)
      .build()
  }

  override fun getAdditionalSessionProviders(context: Context): List<SessionProvider> = emptyList()
}
