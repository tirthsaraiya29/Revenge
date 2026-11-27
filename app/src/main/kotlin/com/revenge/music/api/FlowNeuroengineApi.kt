package com.revenge.music.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Declared external contract for the experimental recommendation backend. Currently unused; it must
 * remain source-compatible for callers that opt in later.
 */
interface FlowNeuroengineApi {
  @POST("/v1/brain/analyze")
  suspend fun analyzeEvent(@Body request: NeuroengineRequest): NeuroengineResponse
}

@Serializable
data class NeuroengineRequest(
  val trackId: String,
  val durationMs: Long,
  val skipped: Boolean,
)

@Serializable
data class NeuroengineResponse(
  val suggestions: List<SuggestedTrack>,
  val tasteProfileUpdate: TasteProfileDto? = null,
)

@Serializable
data class SuggestedTrack(
  val id: String,
  val title: String,
  val artist: String,
  val album: String? = null,
  val matchReason: String,
)

@Serializable
data class TasteProfileDto(
  val genres: List<String>,
  val confidence: Double,
  val patternsFound: List<String>,
  val modelVersion: String,
)
