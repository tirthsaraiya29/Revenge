package com.revenge.playback

import androidx.media3.common.Player
import java.lang.reflect.Proxy
import java.util.PriorityQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Delay
import kotlinx.coroutines.InternalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

  @Test
  fun startMinusOneArmsSongEndMode() {
    val timer = newTimer()

    timer.start(-1)

    assertTrue(timer.pauseWhenSongEnd)
    assertEquals(-1L, timer.triggerTime)
    assertTrue(timer.isActive)
  }

  @Test
  fun startMinutesSchedulesCountdown() {
    val timer = newTimer()
    val before = System.currentTimeMillis()

    timer.start(1)

    val after = System.currentTimeMillis()
    assertFalse(timer.pauseWhenSongEnd)
    assertTrue(timer.isActive)
    assertTrue(timer.triggerTime in (before + 60_000L)..(after + 61_000L))
  }

  @Test
  fun clearCancelsCountdown() {
    val clock = VirtualClock()
    val player = FakePlayer()
    val timer = SleepTimer(CoroutineScope(clock), player.player)
    timer.start(1)

    timer.clear()
    clock.advanceBy(10 * 60_000L)

    assertFalse(timer.isActive)
    assertEquals(0, player.pauseCount)
    assertTrue(player.volumeHistory.isEmpty())
  }

  @Test
  fun notificationBeforeArmingIsNoOp() {
    val clock = VirtualClock()
    val player = FakePlayer()
    val timer = SleepTimer(CoroutineScope(clock), player.player)

    timer.notifySongTransition()
    clock.advanceBy(10 * 60_000L)

    assertEquals(0, player.pauseCount)
  }

  @Test
  fun songEndModeFadesExactlyOnce() {
    val clock = VirtualClock()
    val player = FakePlayer()
    val timer = SleepTimer(CoroutineScope(clock), player.player)
    timer.start(-1)

    timer.notifySongTransition()
    clock.advanceBy(3_500L)
    assertEquals(1, player.pauseCount)

    timer.notifySongTransition()
    clock.advanceBy(3_500L)
    assertEquals(1, player.pauseCount)
  }

  @Test
  fun endedPlaybackTriggersSongEndFade() {
    val clock = VirtualClock()
    val player = FakePlayer()
    val timer = SleepTimer(CoroutineScope(clock), player.player)
    timer.start(-1)

    timer.onPlaybackStateChanged(Player.STATE_ENDED)
    clock.advanceBy(3_500L)

    assertEquals(1, player.pauseCount)
    assertFalse(timer.pauseWhenSongEnd)
  }

  @Test
  fun fadeStepsPauseAndRestoreVolume() {
    val clock = VirtualClock()
    val player = FakePlayer(initialVolume = 0.9f)
    val timer = SleepTimer(CoroutineScope(clock), player.player)

    timer.start(-2)
    clock.advanceBy(3_100L)

    assertEquals(1, player.pauseCount)
    assertEquals(31, player.volumeHistory.size)
    assertEquals(0.9f / 30f, player.volumeHistory[29], 1e-4f)
    assertEquals(0.9f, player.volume, 1e-4f)
  }

  @Test
  fun restartingReplacesPreviousCountdown() {
    val clock = VirtualClock()
    val player = FakePlayer()
    val timer = SleepTimer(CoroutineScope(clock), player.player)

    timer.start(1)
    timer.start(2)
    clock.advanceBy(10 * 60_000L)

    assertEquals(1, player.pauseCount)
    assertEquals(-1L, timer.triggerTime)
  }

  private fun newTimer(): SleepTimer {
    val clock = VirtualClock()
    return SleepTimer(CoroutineScope(clock), FakePlayer().player)
  }

  private class FakePlayer(initialVolume: Float = 1f) {
    var volume: Float = initialVolume
    val volumeHistory = mutableListOf<Float>()
    var pauseCount: Int = 0

    val player: Player =
      Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
      ) { proxy, method, args ->
        when (method.name) {
          "getVolume" -> volume
          "setVolume" -> {
            volume = args!![0] as Float
            volumeHistory.add(volume)
            null
          }
          "pause" -> {
            pauseCount++
            null
          }
          "toString" -> "FakePlayer"
          "hashCode" -> System.identityHashCode(proxy)
          "equals" -> proxy === args!![0]
          else -> throw UnsupportedOperationException(method.name)
        }
      } as Player
  }

  @OptIn(InternalCoroutinesApi::class)
  private class VirtualClock : CoroutineDispatcher(), Delay {

    private class Task(val time: Long, val order: Long, val action: () -> Unit)

    private val queue =
      PriorityQueue<Task>(
        compareBy<Task> { it.time }.thenBy { it.order },
      )
    private var order: Long = 0L
    private var now: Long = 0L

    override fun dispatch(context: CoroutineContext, block: Runnable) {
      block.run()
    }

    override fun scheduleResumeAfterDelay(
      timeMillis: Long,
      continuation: CancellableContinuation<Unit>,
    ) {
      queue.add(
        Task(now + timeMillis, order++) {
          if (continuation.isActive) {
            continuation.resumeWith(Result.success(Unit))
          }
        },
      )
    }

    fun advanceBy(deltaMs: Long) {
      val target = now + deltaMs
      while (true) {
        val next = queue.peek() ?: break
        if (next.time > target) break
        queue.poll()
        now = next.time
        next.action()
      }
      now = target
    }
  }
}
