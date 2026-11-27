package com.revenge.music.ai

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class AiRecommendationWorker
@AssistedInject
constructor(
  @Assisted appContext: Context,
  @Assisted params: WorkerParameters,
  private val helper: AiRecommendationHelper,
) : CoroutineWorker(appContext, params) {
  override suspend fun doWork(): Result =
    try {
      helper.generateRecommendations(applicationContext)
      Result.success()
    } catch (t: Throwable) {
      if (t is kotlinx.coroutines.CancellationException) throw t
      Result.retry()
    }
}
