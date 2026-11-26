package com.revenge.music.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.revenge.innertube.YouTube
import com.revenge.music.MainActivity
import com.revenge.music.db.MusicDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import timber.log.Timber

@HiltWorker
class NewEpisodesWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val database: MusicDatabase,
) : CoroutineWorker(appContext, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "NewEpisodesWorker"
        private const val CHANNEL_ID = "podcast_episodes"
        private const val SEEN_PREFS = "podcast_seen_episodes"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewEpisodesWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    override suspend fun doWork(): Result {
        createChannel()
        val shows = PodcastShowStore.getInstance(applicationContext).ids.value
        if (shows.isEmpty()) return Result.success()

        val followed = runCatching {
            database.playlistsByNameAsc().first()
                .filter { it.playlist.browseId?.let { id -> id in shows } == true }
        }.getOrDefault(emptyList())
        if (followed.isEmpty()) return Result.success()

        val seenPrefs = applicationContext.getSharedPreferences(SEEN_PREFS, Context.MODE_PRIVATE)
        for (playlist in followed) {
            val showId = playlist.playlist.browseId ?: continue
            val page = runCatching { YouTube.podcast(showId).getOrNull() }.getOrNull() ?: continue
            val ids = page.songs.map { it.id }
            if (ids.isEmpty()) continue
            val stored = seenPrefs.getStringSet(showId, null)
            if (stored == null) {
                seenPrefs.edit().putStringSet(showId, ids.toSet()).apply()
                continue
            }
            val unseen = ids.firstOrNull { it !in stored } ?: continue
            val posted = notifyNewEpisode(
                showId = showId,
                showName = playlist.playlist.name,
                episodeId = unseen,
            )
            if (posted) {
                seenPrefs.edit().putStringSet(showId, ids.toSet()).apply()
            }
        }
        return Result.success()
    }

    private fun notifyNewEpisode(showId: String, showName: String, episodeId: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val url = "https://music.youtube.com/playlist?list=$showId"
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(url)
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            showId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New episode")
            .setContentText(showName)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        return runCatching {
            NotificationManagerCompat.from(applicationContext)
                .notify(showId.hashCode(), notification)
            true
        }.onFailure { Timber.w(it, "Failed to post new-episode notification for $episodeId") }
            .getOrDefault(false)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "New podcast episodes",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }
}
