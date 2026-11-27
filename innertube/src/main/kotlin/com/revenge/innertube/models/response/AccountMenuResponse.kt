package com.revenge.innertube.models.response

import com.revenge.innertube.models.AccountInfo
import com.revenge.innertube.models.Runs
import com.revenge.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuResponse(
  val actions: List<Action>,
) {
  @Serializable
  data class Action(
    val openPopupAction: OpenPopupAction?,
  )

  @Serializable
  data class OpenPopupAction(
    val popup: Popup?,
  )

  @Serializable
  data class Popup(
    val multiPageMenuRenderer: MultiPageMenuRenderer?,
  )

  @Serializable
  data class MultiPageMenuRenderer(
    val header: Header?,
  )

  @Serializable
  data class Header(
    val activeAccountHeaderRenderer: ActiveAccountHeaderRenderer?,
  )

  @Serializable
  data class ActiveAccountHeaderRenderer(
    val accountName: Runs,
    val email: Runs?,
    val channelHandle: Runs?,
    val accountPhoto: Thumbnails,
  ) {
    fun toAccountInfo(): AccountInfo =
      AccountInfo(
        name = accountName.runs?.firstOrNull()?.text ?: "",
        email = email?.runs?.firstOrNull()?.text,
        channelHandle = channelHandle?.runs?.firstOrNull()?.text,
        thumbnailUrl = accountPhoto.thumbnails.lastOrNull()?.url,
      )
  }
}
