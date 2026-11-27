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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.revenge.core.db.MusicDatabase
import com.revenge.innertube.YouTube
import com.revenge.innertube.pages.PodcastPage
import com.revenge.music.MainActivity
import com.revenge.music.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Periodic poll of followed podcast shows. The first observation of a show records its episodes
 * silently; later unseen episodes produce one notification each.
 */
class NewEpisodesWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            NewEpisodesEntryPoint::class.java,
        )
        val database = entryPoint.musicDatabase()
        val shows = runCatching {
            database.playlistsByNameAsc().first()
                .mapNotNull { it.playlist }
                .filter { it.bookmarkedAt != null && PodcastPage.isShowBrowseId(it.browseId) }
                .mapNotNull { it.browseId }
                .distinct()
        }.getOrElse {
            Timber.w(it, "Unable to load followed podcast shows")
            return Result.retry()
        }

        for (browseId in shows) {
            val page = YouTube.podcast(PodcastPage.showId(browseId)).getOrNull() ?: continue
            runCatching { handleShow(browseId, page.songs.map { it.id }, page.songs.map { it.title }) }
                .onFailure { Timber.w(it, "Podcast check failed for $browseId") }
        }
        return Result.success()
    }

    private fun handleShow(browseId: String, ids: List<String>, titles: List<String>) {
        val preferences = applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val key = "seen_$browseId"
        val previouslySeen = preferences.getStringSet(key, null)
        val unseen = if (previouslySeen == null) {
            emptyList()
        } else {
            ids.filterNot { it in previouslySeen }
        }

        if (previouslySeen == null || unseen.isEmpty()) {
            preferences.edit().putStringSet(key, ids.toSet()).apply()
            return
        }

        if (!canNotify()) return

        val index = ids.indexOf(unseen.first())
        val title = titles.getOrNull(index)?.takeIf { it.isNotBlank() }
            ?: applicationContext.getString(R.string.new_episodes)
        postNotification(browseId, title)
        preferences.edit().putStringSet(key, ids.toSet()).apply()
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()
    }

    private fun postNotification(browseId: String, title: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? NotificationManager ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.new_episodes),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )

        val intent = Intent(applicationContext, MainActivity::class.java)
            .setData(Uri.parse("https://music.youtube.com/playlist?list=$browseId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            applicationContext,
            browseId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification)
            .setContentTitle(applicationContext.getString(R.string.new_episodes))
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        runCatching {
            NotificationManagerCompat.from(applicationContext).notify(browseId.hashCode(), notification)
        }.onFailure { Timber.w(it, "Unable to post podcast notification") }
    }

    companion object {
        private const val WORK_NAME = "NewEpisodesWorker"
        private const val CHANNEL_ID = "podcast_episodes"
        private const val PREFERENCES_NAME = "new_episodes"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<NewEpisodesWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NewEpisodesEntryPoint {
    fun musicDatabase(): MusicDatabase
}
