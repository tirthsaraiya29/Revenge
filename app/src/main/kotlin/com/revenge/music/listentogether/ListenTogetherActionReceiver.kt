package com.revenge.music.listentogether

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Bridges notification approve/reject actions to the live client, when one exists. */
class ListenTogetherActionReceiver : BroadcastReceiver() {

  companion object {
    const val ACTION_APPROVE = "com.revenge.music.listentogether.action.APPROVE"
    const val ACTION_REJECT = "com.revenge.music.listentogether.action.REJECT"
    const val EXTRA_USER_ID = "user_id"

    @Volatile var client: ListenTogetherClient? = null

    fun intent(context: Context, action: String, userId: String): Intent =
      Intent(context, ListenTogetherActionReceiver::class.java)
        .setAction(action)
        .putExtra(EXTRA_USER_ID, userId)
  }

  override fun onReceive(context: Context, intent: Intent) {
    val userId = intent.getStringExtra(EXTRA_USER_ID) ?: return
    val target = client ?: return
    when (intent.action) {
      ACTION_APPROVE -> target.approveJoin(userId)
      ACTION_REJECT -> target.rejectJoin(userId, null)
    }
  }
}
