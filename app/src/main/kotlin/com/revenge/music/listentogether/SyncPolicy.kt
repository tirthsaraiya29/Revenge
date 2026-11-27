package com.revenge.music.listentogether

/**
 * Suppresses the manager's own outbound player reports while a guest sync is being applied or right
 * after it sent an action, so a remote-originated change is not echoed back.
 */
internal class SuppressionWindow(
  private val sentWindowMs: Long = 300,
  private val appliedWindowMs: Long = 200,
) {
  private var sentAt: Long = Long.MIN_VALUE
  private var appliedAt: Long = Long.MIN_VALUE

  fun markSent(now: Long) {
    sentAt = now
  }

  fun markApplied(now: Long) {
    appliedAt = now
  }

  fun isSuppressed(now: Long): Boolean =
    (sentAt != Long.MIN_VALUE && now - sentAt < sentWindowMs) ||
      (appliedAt != Long.MIN_VALUE && now - appliedAt < appliedWindowMs)
}

/**
 * Emits [T] only once [windowMs] has passed since the last change. A seeded value is never emitted
 * until it actually changes, so attaching to a live player does not send its current queue.
 */
internal class ChangeDebouncer<T>(private val windowMs: Long) {
  private var pending: T? = null
  private var changedAt: Long = Long.MIN_VALUE

  fun seed(value: T) {
    pending = value
    changedAt = Long.MIN_VALUE
  }

  fun offer(value: T, now: Long) {
    if (value != pending) {
      pending = value
      changedAt = now
    }
  }

  fun ready(now: Long): T? {
    if (changedAt == Long.MIN_VALUE) return null
    val value = pending ?: return null
    if (now - changedAt < windowMs) return null
    changedAt = Long.MIN_VALUE
    return value
  }
}
