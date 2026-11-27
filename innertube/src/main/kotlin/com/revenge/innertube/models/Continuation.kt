package com.revenge.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

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

fun List<Continuation>.getContinuation(): String? = firstNotNullOfOrNull {
  it.nextContinuationData?.continuation
}
