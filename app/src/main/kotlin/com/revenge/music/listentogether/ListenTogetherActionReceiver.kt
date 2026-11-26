package com.revenge.music.listentogether

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Handles the approve/reject buttons attached to join-request and suggestion notifications. */
class ListenTogetherActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId =
            intent.getIntExtra(ListenTogetherClient.EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (notificationId != Int.MIN_VALUE) {
            context.getSystemService(NotificationManager::class.java)
                ?.cancel(notificationId)
        }

        val client = ListenTogetherClient.getInstance() ?: return
        when (intent.action) {
            ListenTogetherClient.ACTION_APPROVE_JOIN ->
                intent.getStringExtra(ListenTogetherClient.EXTRA_USER_ID)?.let(client::approveJoin)
            ListenTogetherClient.ACTION_REJECT_JOIN ->
                intent.getStringExtra(ListenTogetherClient.EXTRA_USER_ID)?.let { client.rejectJoin(it) }
            ListenTogetherClient.ACTION_APPROVE_SUGGESTION ->
                intent.getStringExtra(ListenTogetherClient.EXTRA_SUGGESTION_ID)
                    ?.let(client::approveSuggestion)
            ListenTogetherClient.ACTION_REJECT_SUGGESTION ->
                intent.getStringExtra(ListenTogetherClient.EXTRA_SUGGESTION_ID)
                    ?.let { client.rejectSuggestion(it) }
        }
    }
}
