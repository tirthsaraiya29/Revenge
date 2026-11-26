package com.revenge.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * Continuation tokens appear under several names depending on the response flavour; the alias set
 * covers all of them.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Continuation(
  @JsonNames("nextContinuationData", "nextRadioContinuationData")
  val nextContinuationData: NextContinuationData?,
) {
  @Serializable
  data class NextContinuationData(
    val continuation: String,
  )
}

fun List<Continuation>.getContinuation(): String? =
  firstOrNull()?.nextContinuationData?.continuation
