package com.revenge.music.listentogether

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.WatchEndpoint
import com.revenge.music.constants.ListenTogetherSmartResyncKey
import com.revenge.music.constants.ListenTogetherSyncVolumeKey
import com.revenge.music.models.MediaMetadata
import com.revenge.music.playback.PlayerConnection
import com.revenge.playback.queues.Queue
import com.revenge.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Bridges the socket client to the player. Host-side player changes are reported to the server and
 * guest-side server actions are applied to the player under a re-entrancy guard.
 */
@Singleton
class ListenTogetherManager @Inject constructor(
    private val client: ListenTogetherClient,
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val SYNC_DEBOUNCE_MS = 1000L
        const val POSITION_TOLERANCE_MS = 2000L
        const val PLAYBACK_POSITION_TOLERANCE_MS = 3000L
        const val ACTION_SUPPRESSION_MS = 300L
        const val GUEST_SYNC_SUPPRESSION_MS = 200L
        const val HOST_HEARTBEAT_MS = 10_000L
        const val QUEUE_OBSERVE_DEBOUNCE_MS = 500L
        const val PLAYER_READY_WAIT_MS = 5000L
        const val RADIO_READY_WAIT_MS = 2000L
        const val QUEUE_TITLE = "Listen Together"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _chatMessages = MutableStateFlow<List<ChatMessagePayload>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessagePayload>> = _chatMessages

    // Client aliases.
    val connectionState: StateFlow<ConnectionState> get() = client.connectionState
    val roomState: StateFlow<RoomState?> get() = client.roomState
    val role: StateFlow<RoomRole> get() = client.role
    val userId: StateFlow<String?> get() = client.userId
    val pendingJoinRequests: StateFlow<List<JoinRequestPayload>> get() = client.pendingJoinRequests
    val bufferingUsers: StateFlow<List<String>> get() = client.bufferingUsers
    val pendingSuggestions: StateFlow<List<SuggestionReceivedPayload>> get() = client.pendingSuggestions
    val blockedUsernames: StateFlow<Set<String>> get() = client.blockedUsernames
    val logs get() = client.logs
    val events get() = client.events
    val rtt: StateFlow<Long> get() = client.rtt

    val allowParticipantControl: StateFlow<Boolean> = client.roomState
        .map { it?.allowParticipantControl == true }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val guestPlaybackRestricted: StateFlow<Boolean> =
        combine(client.roomState, client.role, allowParticipantControl) { room, role, control ->
            room != null && role != RoomRole.HOST && !control
        }.stateIn(scope, SharingStarted.Eagerly, false)

    val isInRoom: Boolean get() = client.isInRoom
    val isHost: Boolean get() = client.isHost
    val canControlMusic: Boolean
        get() = !isInRoom || isHost || allowParticipantControl.value
    val isGuestPlaybackRestricted: Boolean get() = isInRoom && !canControlMusic

    private var connection: PlayerConnection? = null
    private var playerListener: Player.Listener? = null
    private var queueObservationJob: Job? = null
    private var volumeObservationJob: Job? = null
    private var heartbeatJob: Job? = null
    private var eventJob: Job? = null

    @Volatile private var syncing = false
    @Volatile private var suppressUntil = 0L
    @Volatile private var lastReportedTrackId: String? = null
    @Volatile private var lastReportedPlaying = false
    @Volatile private var savedMuteState: Boolean? = null

    private var pendingSync: PendingState? = null
    private var pendingTarget: PendingState? = null
    private var pendingBufferTrackId: String? = null
    private var syncGeneration = 0

    private data class PendingState(
        val trackId: String?,
        val positionMs: Long,
        val playWhenReady: Boolean,
    )

    init {
        observeEvents()
    }

    fun initialize() {
        if (eventJob == null || eventJob?.isActive != true) observeEvents()
    }

    // ------------------------------------------------------------------ client passthrough

    fun connect() = client.connect()
    fun disconnect() {
        cleanup()
        client.disconnect()
    }

    fun createRoom(username: String) = client.createRoom(username)
    fun joinRoom(roomCode: String, username: String) = client.joinRoom(roomCode, username)
    fun leaveRoom() {
        restoreMuteState()
        client.leaveRoom()
    }

    fun approveJoin(userId: String) = client.approveJoin(userId)
    fun rejectJoin(userId: String, reason: String? = null) = client.rejectJoin(userId, reason)
    fun kickUser(userId: String, reason: String? = null) = client.kickUser(userId, reason)
    fun blockUser(username: String) = client.blockUser(username)
    fun unblockUser(username: String) = client.unblockUser(username)
    fun getBlockedUsernames(): Set<String> = client.blockedUsernames.value
    fun transferHost(newHostId: String) = client.transferHost(newHostId)
    fun updateRoomSettings(allowParticipantControl: Boolean) =
        client.updateRoomSettings(allowParticipantControl)

    fun clearLogs() = client.clearLogs()
    fun suggestTrack(trackInfo: TrackInfo) = client.suggestTrack(trackInfo)
    fun approveSuggestion(suggestionId: String) = client.approveSuggestion(suggestionId)
    fun rejectSuggestion(suggestionId: String, reason: String? = null) =
        client.rejectSuggestion(suggestionId, reason)
    fun forceReconnect() = client.forceReconnect()
    fun getPersistedRoomCode(): String? = client.getPersistedRoomCode()
    fun getSessionAge(): Long = client.getSessionAge()

    fun sendChatMessage(message: String, replyTo: RepliedMessage? = null) =
        client.sendChatMessage(message, replyTo)

    fun requestSync() {
        if (isGuestPlaybackRestricted) client.requestSync()
    }

    fun sendTrackChange(metadata: MediaMetadata) {
        if (!isInRoom) return
        val info = metadata.toTrackInfo()
        client.sendPlaybackAction(
            action = PlaybackActions.CHANGE_TRACK,
            trackId = info.id,
            trackInfo = info,
            queue = currentQueue(),
            queueTitle = connection?.queueTitle?.value,
        )
        lastReportedTrackId = info.id
    }

    // ------------------------------------------------------------------ player wiring

    fun setPlayerConnection(connection: PlayerConnection?) {
        val previous = this.connection
        if (previous === connection) return
        detachPlayer(previous)
        syncGeneration++
        this.connection = connection
        if (connection == null) return

        connection.shouldBlockPlaybackChanges = { isGuestPlaybackRestricted }
        connection.onSkipPrevious = {
            if (canReportHostActions()) client.sendPlaybackAction(PlaybackActions.SKIP_PREV)
        }
        connection.onSkipNext = {
            if (canReportHostActions()) client.sendPlaybackAction(PlaybackActions.SKIP_NEXT)
        }
        connection.onRestartSong = {
            if (canReportHostActions()) client.sendPlaybackAction(
                PlaybackActions.SEEK, position = 1L,
            )
        }

        if (isGuestPlaybackRestricted && savedMuteState == null) {
            savedMuteState = connection.isMuted.value
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = onPlaybackStateChangedByUser()
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) =
                onTrackTransition(mediaItem)
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) onSeek(newPosition.positionMs)
            }
        }
        connection.player.addListener(listener)
        playerListener = listener

        startObservers()
        lastReportedTrackId = connection.currentMetadata?.id
        lastReportedPlaying = connection.player.isPlaying
    }

    private fun detachPlayer(connection: PlayerConnection?) {
        queueObservationJob?.cancel()
        volumeObservationJob?.cancel()
        heartbeatJob?.cancel()
        queueObservationJob = null
        volumeObservationJob = null
        heartbeatJob = null
        if (connection == null) return
        playerListener?.let { runCatching { connection.player.removeListener(it) } }
        playerListener = null
        connection.shouldBlockPlaybackChanges = null
        connection.onSkipPrevious = null
        connection.onSkipNext = null
        connection.onRestartSong = null
    }

    private fun startObservers() {
        val connection = connection ?: return
        queueObservationJob?.cancel()
        queueObservationJob = scope.launch {
            connection.queueWindows
                .map { windows -> windows.map { it.mediaItem.mediaId } }
                .distinctUntilChanged()
                .debounce(QUEUE_OBSERVE_DEBOUNCE_MS)
                .collect {
                    if (canReportHostActions()) {
                        client.sendPlaybackAction(
                            action = PlaybackActions.SYNC_QUEUE,
                            queue = currentQueue(),
                            queueTitle = connection.queueTitle.value,
                        )
                        suppressUntil = now() + ACTION_SUPPRESSION_MS
                    }
                }
        }

        volumeObservationJob?.cancel()
        volumeObservationJob = scope.launch {
            var previous = connection.service.playerVolume.value
            connection.service.playerVolume.drop(1).collect { volume ->
                if (canReportHostActions() &&
                    abs(volume - previous) >= 0.01f
                ) {
                    client.sendPlaybackAction(
                        action = PlaybackActions.SET_VOLUME,
                        volume = volume.coerceIn(0f, 1f),
                    )
                    suppressUntil = now() + ACTION_SUPPRESSION_MS
                }
                previous = volume
            }
        }

        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                delay(HOST_HEARTBEAT_MS)
                if (client.isHost && client.isInRoom && connection.player.isPlaying &&
                    connection.player.playbackState == Player.STATE_READY
                ) {
                    client.sendPlaybackAction(
                        action = PlaybackActions.PLAY,
                        position = connection.player.currentPosition,
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------ host reporting

    private fun canReportHostActions(): Boolean =
        isInRoom && canControlMusic && !syncing &&
            connection?.allowInternalSync != true &&
            now() >= suppressUntil

    private fun onPlaybackStateChangedByUser() {
        val connection = connection ?: return
        if (!canReportHostActions()) return
        val metadata = connection.currentMetadata
        val trackId = metadata?.id
        val playing = connection.player.isPlaying

        if (playing) {
            if (trackId != null && lastReportedTrackId != null && trackId != lastReportedTrackId) {
                client.sendPlaybackAction(
                    action = PlaybackActions.CHANGE_TRACK,
                    trackId = trackId,
                    trackInfo = metadata.toTrackInfo(),
                    queue = currentQueue(),
                    queueTitle = connection.queueTitle.value,
                )
            }
            client.sendPlaybackAction(
                action = PlaybackActions.PLAY,
                position = connection.player.currentPosition,
            )
            suppressUntil = now() + ACTION_SUPPRESSION_MS
        } else if (lastReportedPlaying) {
            client.sendPlaybackAction(
                action = PlaybackActions.PAUSE,
                position = connection.player.currentPosition,
            )
            suppressUntil = now() + ACTION_SUPPRESSION_MS
        }
        lastReportedTrackId = trackId
        lastReportedPlaying = playing
    }

    private fun onTrackTransition(mediaItem: MediaItem?) {
        val connection = connection ?: return
        if (!canReportHostActions()) return
        val trackId = connection.currentMetadata?.id ?: mediaItem?.mediaId
        if (trackId == lastReportedTrackId) return
        client.sendPlaybackAction(
            action = PlaybackActions.CHANGE_TRACK,
            trackId = trackId,
            trackInfo = connection.currentMetadata?.toTrackInfo(),
            queue = currentQueue(),
            queueTitle = connection.queueTitle.value,
        )
        if (connection.player.isPlaying) {
            client.sendPlaybackAction(
                action = PlaybackActions.PLAY,
                position = connection.player.currentPosition,
            )
        }
        lastReportedTrackId = trackId
        lastReportedPlaying = connection.player.isPlaying
        suppressUntil = now() + ACTION_SUPPRESSION_MS
    }

    private fun onSeek(positionMs: Long) {
        if (!canReportHostActions()) return
        client.sendPlaybackAction(
            action = PlaybackActions.SEEK,
            position = if (positionMs <= 0L) 1L else positionMs,
        )
        suppressUntil = now() + ACTION_SUPPRESSION_MS
    }

    // ------------------------------------------------------------------ guest application

    private fun applyGuestAction(payload: PlaybackActionPayload) {
        if (!isGuestPlaybackRestricted) return
        when (payload.action) {
            PlaybackActions.PLAY -> applyPlay(payload)
            PlaybackActions.PAUSE -> applyPause(payload)
            PlaybackActions.SEEK -> applySeek(payload)
            PlaybackActions.CHANGE_TRACK -> applyChangeTrack(payload)
            PlaybackActions.SKIP_NEXT -> connection?.player?.seekToNext()
            PlaybackActions.SKIP_PREV -> connection?.player?.seekToPrevious()
            PlaybackActions.QUEUE_ADD -> applyQueueAdd(payload)
            PlaybackActions.QUEUE_REMOVE -> applyQueueRemove(payload)
            PlaybackActions.QUEUE_CLEAR -> applyQueueClear()
            PlaybackActions.SET_VOLUME -> applyVolume(payload)
            PlaybackActions.SYNC_QUEUE -> applySyncQueue(payload)
        }
    }

    private fun applyPlay(payload: PlaybackActionPayload) {
        val connection = connection ?: return
        val target = (payload.position ?: 0L) +
            max(0L, now() - (payload.serverTime ?: now()))
        if (pendingBufferTrackId != null) {
            pendingTarget = PendingState(payload.trackId, target, true)
            return
        }
        val player = connection.player
        val drift = abs(player.currentPosition - target)
        if (player.isPlaying && drift <= POSITION_TOLERANCE_MS) return
        if (!player.isPlaying && drift > POSITION_TOLERANCE_MS) {
            guarded { player.seekTo(target) }
        }
        guarded { player.play() }
    }

    private fun applyPause(payload: PlaybackActionPayload) {
        val connection = connection ?: return
        val player = connection.player
        val target = payload.position ?: player.currentPosition
        val drift = abs(player.currentPosition - target)
        if (!player.isPlaying && drift <= POSITION_TOLERANCE_MS) return
        guarded { player.pause() }
        if (drift > POSITION_TOLERANCE_MS) guarded { player.seekTo(target) }
    }

    private fun applySeek(payload: PlaybackActionPayload) {
        val player = connection?.player ?: return
        val target = payload.position ?: return
        if (abs(player.currentPosition - target) <= POSITION_TOLERANCE_MS) return
        guarded { player.seekTo(target) }
    }

    private fun applyChangeTrack(payload: PlaybackActionPayload) {
        val track = payload.trackInfo ?: return
        if (payload.queue.isNotEmpty()) {
            applyState(
                track = track,
                playing = payload.position != null && connection?.player?.isPlaying == true,
                positionMs = payload.position ?: 0L,
                queue = payload.queue,
                queueTitle = payload.queueTitle,
                bufferBypass = true,
            )
        } else {
            loadFromRadioSeed(track)
        }
    }

    private fun applyQueueAdd(payload: PlaybackActionPayload) {
        val track = payload.trackInfo ?: return
        val connection = connection ?: return
        scope.launch {
            val songs = YouTube.queue(listOf(track.id)).getOrNull().orEmpty()
            val item = songs.firstOrNull()?.toMediaMetadata()?.toMediaItem()
                ?: track.toMediaItem()
            if (payload.insertNext) connection.playNext(item) else connection.addToQueue(item)
        }
    }

    private fun applyQueueRemove(payload: PlaybackActionPayload) {
        val player = connection?.player ?: return
        val current = player.currentMediaItemIndex
        val queue = player.currentTimeline
        for (index in current + 1 until queue.windowCount) {
            val window = queue.getWindow(index, Timeline.Window())
            if (window.mediaItem.mediaId == payload.trackId) {
                guarded { player.removeMediaItem(index) }
                return
            }
        }
    }

    private fun applyQueueClear() {
        val player = connection?.player ?: return
        val current = player.currentMediaItemIndex
        while (player.mediaItemCount > current + 1) {
            guarded { player.removeMediaItem(current + 1) }
        }
    }

    private fun applyVolume(payload: PlaybackActionPayload) {
        if (!isGuestPlaybackRestricted) return
        val volume = (payload.volume ?: return).coerceIn(0f, 1f)
        scope.launch {
            val enabled = runCatching {
                context.dataStore.data.first()[ListenTogetherSyncVolumeKey]
            }.getOrNull() ?: true
            if (!enabled) return@launch
            connection?.service?.playerVolume?.let { it.value = volume }
        }
    }

    private fun applySyncQueue(payload: PlaybackActionPayload) {
        val connection = connection ?: return
        val items = payload.queue.map { it.toMediaItem() }
        if (items.isEmpty()) return
        val currentId = connection.currentMetadata?.id
        val startIndex = items.indexOfFirst { it.mediaId == currentId }.coerceAtLeast(0)
        val position = connection.player.currentPosition
        val playing = connection.player.isPlaying
        guarded {
            connection.service.playQueue(
                MediaItemQueue(payload.queueTitle, items, startIndex),
                playing,
            )
            connection.player.seekTo(startIndex, position)
        }
    }

    // ------------------------------------------------------------------ state application

    private fun applyState(
        track: TrackInfo?,
        playing: Boolean,
        positionMs: Long,
        queue: List<TrackInfo>,
        queueTitle: String?,
        bufferBypass: Boolean,
    ) {
        val connection = connection ?: return
        val generation = ++syncGeneration
        syncing = true
        scope.launch {
            try {
                if (generation != syncGeneration) return@launch
                val items = when {
                    queue.isEmpty() -> track?.let { listOf(it.toMediaItem()) }.orEmpty()
                    track != null && queue.any { it.id == track.id } -> queue.map { it.toMediaItem() }
                    track != null -> listOf(track.toMediaItem())
                    else -> queue.map { it.toMediaItem() }
                }
                val startIndex = track?.let { t ->
                    items.indexOfFirst { it.mediaId == t.id }.coerceAtLeast(0)
                } ?: 0

                if (items.isEmpty()) {
                    guarded { connection.player.pause() }
                    return@launch
                }

                if (bufferBypass) {
                    val ready = awaitPlayerReady(PLAYER_READY_WAIT_MS)
                    if (generation != syncGeneration) return@launch
                    guarded {
                        connection.service.playQueue(
                            MediaItemQueue(queueTitle, items, startIndex), false,
                        )
                        connection.player.seekTo(startIndex, positionMs)
                        if (playing) connection.player.play() else connection.player.pause()
                    }
                } else {
                    guarded {
                        connection.service.playQueue(
                            MediaItemQueue(queueTitle, items, startIndex), false,
                        )
                        connection.player.pause()
                        connection.player.seekTo(startIndex, positionMs)
                    }
                    pendingSync = PendingState(track?.id, positionMs, playing)
                    pendingBufferTrackId = track?.id
                    track?.id?.let { client.sendBufferReady(it) }
                }
            } finally {
                syncing = false
                suppressUntil = now() + GUEST_SYNC_SUPPRESSION_MS
            }
        }
    }

    private fun loadFromRadioSeed(track: TrackInfo) {
        val connection = connection ?: return
        val generation = ++syncGeneration
        syncing = true
        scope.launch {
            try {
                val seed = WatchEndpoint(videoId = track.id, playlistId = "RDAMVM${track.id}")
                val result = YouTube.next(seed).getOrNull()
                if (generation != syncGeneration) return@launch
                val songs = result?.items.orEmpty().ifEmpty { listOf(track.toSongItem()) }
                val items = songs.map { it.toMediaMetadata().toMediaItem() }
                val startIndex = items.indexOfFirst { it.mediaId == track.id }.coerceAtLeast(0)
                guarded {
                    connection.service.playQueue(
                        MediaItemQueue(QUEUE_TITLE, items, startIndex), false,
                    )
                    connection.player.pause()
                }
                pendingSync = PendingState(track.id, 0L, false)
                pendingBufferTrackId = track.id
                client.sendBufferReady(track.id)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                // Loading failures are non-fatal: the guest simply stays put.
            } finally {
                syncing = false
            }
        }
    }

    private suspend fun awaitPlayerReady(timeoutMs: Long): Boolean {
        val connection = connection ?: return false
        val deadline = now() + timeoutMs
        while (now() < deadline) {
            if (connection.player.playbackState == Player.STATE_READY) return true
            delay(50)
        }
        return false
    }

    private fun guarded(block: () -> Unit) {
        val connection = connection ?: return
        val previous = connection.allowInternalSync
        connection.allowInternalSync = true
        try {
            block()
        } finally {
            connection.allowInternalSync = previous
        }
    }

    // ------------------------------------------------------------------ events

    private fun observeEvents() {
        eventJob?.cancel()
        eventJob = scope.launch {
            client.events.collect { event -> handleEvent(event) }
        }
    }

    private fun handleEvent(event: ListenTogetherEvent) {
        when (event) {
            is ListenTogetherEvent.RoomCreated -> {
                captureMuteState()
                connection?.currentMetadata?.let { sendTrackChange(it) }
                startObservers()
            }
            is ListenTogetherEvent.JoinApproved -> {
                captureMuteState()
                event.state?.let { state ->
                    if (isGuestPlaybackRestricted) {
                        applyState(
                            track = state.currentTrack,
                            playing = state.isPlaying,
                            positionMs = state.position,
                            queue = state.queue,
                            queueTitle = null,
                            bufferBypass = true,
                        )
                    }
                }
                applyHostVolumeIfGuest()
            }
            is ListenTogetherEvent.PlaybackSync -> applyGuestAction(event.action)
            is ListenTogetherEvent.UserJoined -> {
                if (client.isHost) {
                    connection?.currentMetadata?.let { sendTrackChange(it) }
                    val player = connection?.player ?: return
                    client.sendPlaybackAction(
                        action = if (player.isPlaying) PlaybackActions.PLAY
                        else PlaybackActions.PAUSE,
                        position = player.currentPosition,
                    )
                }
            }
            is ListenTogetherEvent.BufferComplete -> {
                pendingTarget?.let { target ->
                    if (pendingBufferTrackId == null || pendingBufferTrackId == event.trackId) {
                        pendingTarget = null
                        pendingBufferTrackId = null
                        guarded {
                            connection?.player?.seekTo(target.positionMs)
                            if (target.playWhenReady) connection?.player?.play()
                            else connection?.player?.pause()
                        }
                    }
                } ?: run {
                    pendingSync?.let { pending ->
                        if (pending.trackId == event.trackId || event.trackId.isBlank()) {
                            pendingSync = null
                            pendingBufferTrackId = null
                            guarded {
                                connection?.player?.seekTo(pending.positionMs)
                                if (pending.playWhenReady) connection?.player?.play()
                                else connection?.player?.pause()
                            }
                        }
                    }
                }
            }
            is ListenTogetherEvent.SyncStateReceived -> applySyncState(event.state)
            is ListenTogetherEvent.Kicked -> cleanup()
            is ListenTogetherEvent.ConnectionError -> cleanup()
            is ListenTogetherEvent.Reconnected -> {
                if (event.isHost) {
                    connection?.currentMetadata?.let { sendTrackChange(it) }
                } else {
                    event.state?.let { state ->
                        if (isGuestPlaybackRestricted) {
                            applyState(
                                track = state.currentTrack,
                                playing = state.isPlaying,
                                positionMs = state.position,
                                queue = state.queue,
                                queueTitle = null,
                                bufferBypass = true,
                            )
                        }
                    }
                    maybeSmartResync()
                }
            }
            is ListenTogetherEvent.HostChanged -> {
                if (client.isHost) connection?.currentMetadata?.let { sendTrackChange(it) }
            }
            is ListenTogetherEvent.LocalSuggestionApproved ->
                connection?.playNext(event.payload.trackInfo.toMediaItem())
            is ListenTogetherEvent.ChatMessageReceived ->
                _chatMessages.update { it + event.payload }
            is ListenTogetherEvent.RoomSettingsChanged -> {
                // The derived StateFlows reflect the new capability immediately.
            }
            else -> Unit
        }
    }

    private fun applySyncState(state: SyncStatePayload) {
        if (!isGuestPlaybackRestricted) return
        val adjusted = if (state.isPlaying) {
            (state.position ?: 0L) + max(0L, now() - state.lastUpdate)
        } else {
            state.position ?: 0L
        }
        applyState(
            track = state.currentTrack,
            playing = state.isPlaying,
            positionMs = adjusted,
            queue = state.queue.orEmpty(),
            queueTitle = null,
            bufferBypass = true,
        )
    }

    private fun maybeSmartResync() {
        if (!isGuestPlaybackRestricted) return
        scope.launch {
            val enabled = runCatching {
                context.dataStore.data.first()[ListenTogetherSmartResyncKey]
            }.getOrNull() ?: true
            if (!enabled) return@launch
            delay(1000)
            if (isGuestPlaybackRestricted) client.requestSync()
        }
    }

    private fun applyHostVolumeIfGuest() {
        if (!isGuestPlaybackRestricted) return
        scope.launch {
            val enabled = runCatching {
                context.dataStore.data.first()[ListenTogetherSyncVolumeKey]
            }.getOrNull() ?: true
            if (!enabled) return@launch
            val state = roomState.value ?: return@launch
            connection?.service?.playerVolume?.let { it.value = state.volume.coerceIn(0f, 1f) }
        }
    }

    // ------------------------------------------------------------------ mute / cleanup

    private fun captureMuteState() {
        if (savedMuteState != null) return
        val connection = connection ?: return
        savedMuteState = connection.isMuted.value
        connection.setMuted(true)
    }

    private fun restoreMuteState() {
        val connection = connection ?: return
        val saved = savedMuteState
        connection.setMuted(saved ?: false)
        savedMuteState = null
    }

    private fun cleanup() {
        restoreMuteState()
        queueObservationJob?.cancel()
        volumeObservationJob?.cancel()
        heartbeatJob?.cancel()
        pendingSync = null
        pendingTarget = null
        pendingBufferTrackId = null
        syncing = false
        suppressUntil = 0L
    }

    // ------------------------------------------------------------------ mapping helpers

    private fun currentQueue(): List<TrackInfo> {
        val windows = connection?.queueWindows?.value.orEmpty()
        return windows.map { windowToTrackInfo(it) }
    }

    private fun windowToTrackInfo(window: Timeline.Window): TrackInfo {
        val metadata = window.mediaItem.mediaMetadata
        val id = metadata.id.orEmpty()
        val title = metadata.title?.toString().orEmpty()
        if (id.isBlank() && title.isBlank()) {
            return TrackInfo("unknown", "Unknown", "Unknown", album = "", 0L, "")
        }
        val duration = if (window.durationMs != C.TIME_UNSET) window.durationMs else 0L
        return TrackInfo(
            id = id,
            title = title.ifBlank { "Unknown" },
            artist = metadata.artist?.toString().orEmpty().ifBlank { "Unknown" },
            album = metadata.albumTitle?.toString(),
            duration = duration,
            thumbnail = metadata.artworkUri?.toString(),
        )
    }

    private fun MediaMetadata.toTrackInfo(): TrackInfo = TrackInfo(
        id = id,
        title = title,
        artist = artists.joinToString(", ") { it.name },
        album = album?.title,
        duration = if (duration <= 0) 180_000L else duration * 1000L,
        thumbnail = thumbnailUrl,
        suggestedBy = suggestedBy,
    )

    private fun TrackInfo.toMediaItem(): MediaItem = toMediaMetadata().toMediaItem()

    private fun TrackInfo.toMediaMetadata(): MediaMetadata = MediaMetadata(
        id = id,
        title = title,
        artists = listOf(MediaMetadata.Artist(null, artist)),
        duration = (duration / 1000L).toInt(),
        thumbnailUrl = thumbnail,
        album = album?.let { MediaMetadata.Album("", it) },
        suggestedBy = suggestedBy,
    )

    private fun com.revenge.innertube.models.SongItem.toMediaMetadata(): MediaMetadata =
        MediaMetadata(
            id = id,
            title = title,
            artists = artists.map { MediaMetadata.Artist(it.id, it.name) },
            duration = duration ?: -1,
            thumbnailUrl = thumbnail,
            album = album?.let { MediaMetadata.Album(it.id, it.name) },
            musicVideoType = musicVideoType,
            suggestedBy = null,
        )

    private fun TrackInfo.toSongItem(): com.revenge.innertube.models.SongItem =
        com.revenge.innertube.models.SongItem(
            id = id,
            title = title,
            artists = listOf(com.revenge.innertube.models.Artist(artist, null)),
            album = album?.let { com.revenge.innertube.models.Album(it, "") },
            duration = (duration / 1000L).toInt(),
            thumbnail = thumbnail.orEmpty(),
        )

    private fun now(): Long = System.currentTimeMillis()

    private class MediaItemQueue(
        private val title: String?,
        private val items: List<MediaItem>,
        private val startIndex: Int,
    ) : Queue {
        override val preloadItem: MediaMetadata? = null

        override suspend fun getInitialStatus(): Queue.Status = Queue.Status(
            title = title,
            items = items,
            mediaItemIndex = startIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
        )

        override fun hasNextPage(): Boolean = false

        override suspend fun nextPage(): List<MediaItem> = emptyList()
    }
}
