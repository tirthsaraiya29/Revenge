package com.revenge.music.extensions

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

private class FakeTimeline(
    private val count: Int,
    private val shuffledOrder: List<Int>? = null,
) : Timeline() {

    private fun playbackOrder(shuffle: Boolean): List<Int> =
        if (shuffle && shuffledOrder != null) shuffledOrder else (0 until count).toList()

    override fun getWindowCount(): Int = count

    override fun getWindow(
        windowIndex: Int,
        window: Timeline.Window,
        defaultPositionProjectionUs: Long,
    ): Timeline.Window {
        window.uid = windowIndex
        return window
    }

    override fun getPeriodCount(): Int = count

    override fun getIndexOfPeriod(uid: Any): Int = uid as? Int ?: C.INDEX_UNSET

    override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex

    override fun getPeriod(
        periodIndex: Int,
        period: Timeline.Period,
        setIds: Boolean,
    ): Timeline.Period = period

    override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int =
        playbackOrder(shuffleModeEnabled).firstOrNull() ?: C.INDEX_UNSET

    override fun getNextWindowIndex(
        windowIndex: Int,
        repeatMode: Int,
        shuffleModeEnabled: Boolean,
    ): Int {
        val order = playbackOrder(shuffleModeEnabled)
        val position = order.indexOf(windowIndex)
        return if (position >= 0 && position + 1 < order.size) order[position + 1] else C.INDEX_UNSET
    }
}

private class PlayerStub : InvocationHandler {

    var playWhenReady = false
    var playbackState = Player.STATE_IDLE
    var repeatMode = Player.REPEAT_MODE_OFF
    var shuffle = false
    var currentIndex = C.INDEX_UNSET
    var prepareCount = 0
    var timeline: Timeline = FakeTimeline(0)
    var items: List<MediaItem> = emptyList()

    fun asPlayer(): Player = Proxy.newProxyInstance(
        Player::class.java.classLoader,
        arrayOf(Player::class.java),
        this,
    ) as Player

    override fun invoke(proxy: Any, method: Method, args: Array<out Any?>?): Any? {
        val first = args?.getOrNull(0)
        return when (method.name) {
            "getPlayWhenReady" -> playWhenReady
            "setPlayWhenReady" -> {
                playWhenReady = first as Boolean
                Unit
            }
            "getPlaybackState" -> playbackState
            "prepare" -> {
                prepareCount++
                Unit
            }
            "getRepeatMode" -> repeatMode
            "setRepeatMode" -> {
                repeatMode = first as Int
                Unit
            }
            "getShuffleModeEnabled" -> shuffle
            "getCurrentMediaItemIndex" -> currentIndex
            "getCurrentTimeline" -> timeline
            "getMediaItemCount" -> items.size
            "getMediaItemAt" -> items[first as Int]
            "getCurrentMediaItem" -> items.getOrNull(currentIndex)
            "toString" -> "PlayerStub"
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === first
            else -> defaultValue(method.returnType)
        }
    }

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }
}

@RunWith(RobolectricTestRunner::class)
class PlayerExtTest {

    private fun item(id: String) = MediaItem.Builder().setMediaId(id).build()

    @Test
    fun `repeat mode cycles and rejects unknown values`() {
        val stub = PlayerStub()
        val player = stub.asPlayer()

        player.toggleRepeatMode()
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        player.toggleRepeatMode()
        assertEquals(Player.REPEAT_MODE_ONE, player.repeatMode)
        player.toggleRepeatMode()
        assertEquals(Player.REPEAT_MODE_OFF, player.repeatMode)

        stub.repeatMode = 99
        try {
            player.toggleRepeatMode()
            throw AssertionError("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `toggle play pause prepares once on an idle player`() {
        val stub = PlayerStub()
        val player = stub.asPlayer()

        player.togglePlayPause()
        assertTrue(player.playWhenReady)
        assertEquals(1, stub.prepareCount)

        player.togglePlayPause()
        assertFalse(player.playWhenReady)
        assertEquals(1, stub.prepareCount)
    }

    @Test
    fun `queue windows follow timeline order`() {
        val stub = PlayerStub().apply {
            timeline = FakeTimeline(5)
            currentIndex = 2
        }
        val windows = stub.asPlayer().getQueueWindows()
        assertEquals(listOf(0, 1, 2, 3, 4), windows.map { it.uid })
    }

    @Test
    fun `queue windows with shuffle contain every window once`() {
        val stub = PlayerStub().apply {
            timeline = FakeTimeline(5, shuffledOrder = listOf(3, 0, 4, 1, 2))
            currentIndex = 1
            shuffle = true
        }
        val windows = stub.asPlayer().getQueueWindows()
        assertEquals(listOf(3, 0, 4, 1, 2), windows.map { it.uid })
    }

    @Test
    fun `queue windows are empty without a valid timeline`() {
        assertEquals(emptyList<Timeline.Window>(), PlayerStub().apply { timeline = FakeTimeline(0) }.asPlayer().getQueueWindows())
        assertEquals(
            emptyList<Timeline.Window>(),
            PlayerStub().apply { timeline = FakeTimeline(3); currentIndex = C.INDEX_UNSET }.asPlayer().getQueueWindows(),
        )
    }

    @Test
    fun `current queue index mirrors the media item index`() {
        assertEquals(2, PlayerStub().apply { timeline = FakeTimeline(5); currentIndex = 2 }.asPlayer().getCurrentQueueIndex())
        assertEquals(-1, PlayerStub().apply { timeline = FakeTimeline(0); currentIndex = 0 }.asPlayer().getCurrentQueueIndex())
        assertEquals(-1, PlayerStub().apply { timeline = FakeTimeline(5); currentIndex = 9 }.asPlayer().getCurrentQueueIndex())
    }

    @Test
    fun `find next media item starts at the current index`() {
        val stub = PlayerStub().apply {
            items = listOf(item("a"), item("b"), item("c"))
            currentIndex = 1
            timeline = FakeTimeline(3)
        }
        val player = stub.asPlayer()

        assertNull(player.findNextMediaItemById("a"))
        assertEquals("b", player.findNextMediaItemById("b")?.mediaId)
        assertEquals("c", player.findNextMediaItemById("c")?.mediaId)
        assertNull(player.findNextMediaItemById("missing"))

        stub.currentIndex = C.INDEX_UNSET
        assertEquals("a", player.findNextMediaItemById("a")?.mediaId)
    }

    @Test
    fun `media items view is live and read only`() {
        val stub = PlayerStub().apply {
            items = listOf(item("a"), item("b"))
            timeline = FakeTimeline(2)
        }
        val player = stub.asPlayer()

        assertEquals(2, player.mediaItems.size)
        assertEquals("b", player.mediaItems[1].mediaId)

        stub.items = stub.items + item("c")
        assertEquals(3, player.mediaItems.size)

        try {
            (player.mediaItems as MutableList).add(item("d"))
            throw AssertionError("expected UnsupportedOperationException")
        } catch (_: UnsupportedOperationException) {
            // expected
        }
    }
}
