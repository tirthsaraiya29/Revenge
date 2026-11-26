package com.revenge.music.models

import kotlinx.serialization.Serializable

/** One remembered logged-in account, persisted as JSON in [com.revenge.music.constants.SavedAccountsKey]. */
@Serializable
data class AccountData(
  val name: String,
  val email: String,
  val channelHandle: String,
  val cookie: String,
  val visitorData: String,
  val dataSyncId: String,
  val avatarUrl: String = "",
)
