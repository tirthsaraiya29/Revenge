package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.lyrics.R
import com.revenge.core.ai.AiLogger
import com.revenge.lyrics.api.DeepLService
import com.revenge.lyrics.api.MistralService
import com.revenge.lyrics.api.OpenRouterService
import com.revenge.lyrics.api.OpenRouterStreamingService
import com.revenge.lyrics.api.OpenRouterStreamingService.StreamChunk
import com.revenge.core.constants.LanguageCodeToName
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.LyricsEntity
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

sealed class TranslationStatus {
    data object Idle : TranslationStatus()
    data class Translating(val logs: List<String> = emptyList()) : TranslationStatus()
    data object Success : TranslationStatus()
    data class Error(val message: String) : TranslationStatus()
}

internal data class TranslationRequest(
    val fullText: String,
    val targetLanguageCode: String,
    val targetLanguageName: String,
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val mode: String,
    val provider: String,
    val deeplApiKey: String,
    val deeplFormality: String,
    val useStreaming: Boolean,
    val lineCount: Int,
)

internal interface TranslationDelegate {
    suspend fun translate(request: TranslationRequest, onLog: (String) -> Unit): Result<List<String>>
}

object LyricsTranslationHelper {

    private const val STATUS_LINGER_MS = 3_000L
    private const val TRANSLATION_BUDGET_MS = 45_000L

    private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
    val status: StateFlow<TranslationStatus> = _status.asStateFlow()

    private val _hasActiveTranslations = MutableStateFlow(false)
    val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

    private val _manualTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val manualTrigger: SharedFlow<Unit> = _manualTrigger

    private val _clearTranslationsTrigger = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger

    private val _translationSaved = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val translationSaved: SharedFlow<Unit> = _translationSaved

    // Internal seams for deterministic tests.
    internal var timerScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal var persistenceScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var workDispatcher: CoroutineDispatcher = Dispatchers.IO
    internal var translationDelegate: TranslationDelegate? = null
    internal var logger: (suspend (Context, String, String) -> Unit)? = null

    private val cache = HashMap<String, List<String>>()

    private var translationJob: Job? = null
    private var statusResetJob: Job? = null

    @Volatile
    private var compositionActive: Boolean = false

    fun getCachedTranslations(
        lyrics: List<LyricsEntry>,
        mode: String,
        language: String,
    ): List<String>? = synchronized(cache) { cache[cacheKey(lyrics, mode, language)] }

    fun applyCachedTranslations(
        lyrics: List<LyricsEntry>,
        mode: String,
        language: String,
    ): Boolean {
        val cached = getCachedTranslations(lyrics, mode, language) ?: return false
        val nonEmpty = lyrics.filter { it.text.isNotBlank() }
        if (cached.size < nonEmpty.size) return false
        nonEmpty.forEachIndexed { index, entry -> entry.translatedTextFlow.value = cached[index] }
        return true
    }

    fun triggerManualTranslation() {
        _manualTrigger.tryEmit(Unit)
    }

    fun triggerClearTranslations() {
        _hasActiveTranslations.value = false
        _clearTranslationsTrigger.tryEmit(Unit)
    }

    fun hasTranslations(lyricsEntity: LyricsEntity?): Boolean =
        !lyricsEntity?.translatedLyrics.isNullOrBlank()

    fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity = lyricsEntity.copy(
        translatedLyrics = "",
        translationLanguage = "",
        translationMode = "",
    )

    fun resetStatus() {
        statusResetJob?.cancel()
        _status.value = TranslationStatus.Idle
    }

    fun clearCache() {
        synchronized(cache) { cache.clear() }
    }

    fun setCompositionActive(active: Boolean) {
        compositionActive = active
    }

    fun cancelTranslation() {
        statusResetJob?.cancel()
        translationJob?.cancel()
        translationJob = null
        _status.value = TranslationStatus.Idle
    }

    fun loadTranslationsFromDatabase(
        lyrics: List<LyricsEntry>,
        lyricsEntity: LyricsEntity?,
        targetLanguage: String,
        mode: String,
    ) {
        lyrics.forEach { it.translatedTextFlow.value = null }

        if (lyricsEntity == null ||
            lyricsEntity.translatedLyrics.isNullOrBlank() ||
            lyricsEntity.translationLanguage != targetLanguage ||
            lyricsEntity.translationMode != mode
        ) {
            _hasActiveTranslations.value = false
            return
        }

        val lines = lyricsEntity.translatedLyrics.split("\n")
        val nonEmpty = lyrics.filter { it.text.isNotBlank() }
        nonEmpty.forEachIndexed { index, entry ->
            if (index < lines.size) entry.translatedTextFlow.value = lines[index]
        }
        storeCache(cacheKey(lyrics, mode, targetLanguage), lines)
        _hasActiveTranslations.value = true
    }

    fun translateLyrics(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        scope: CoroutineScope,
        context: Context,
        provider: String = "OpenRouter",
        deeplApiKey: String = "",
        deeplFormality: String = "default",
        useStreaming: Boolean = true,
        songId: String = "",
        database: MusicDatabase? = null,
    ) {
        statusResetJob?.cancel()
        translationJob?.cancel()
        _status.value = TranslationStatus.Translating()
        lyrics.forEach { it.translatedTextFlow.value = null }

        translationJob = scope.launch(workDispatcher) {
            try {
                runPipeline(
                    lyrics = lyrics,
                    targetLanguage = targetLanguage,
                    apiKey = apiKey,
                    baseUrl = baseUrl,
                    model = model,
                    mode = mode,
                    context = context,
                    provider = provider,
                    deeplApiKey = deeplApiKey,
                    deeplFormality = deeplFormality,
                    useStreaming = useStreaming,
                    songId = songId,
                    database = database,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                reportError(
                    error.message ?: context.getString(R.string.ai_error_translation_failed)
                )
            }
        }
    }

    private suspend fun runPipeline(
        lyrics: List<LyricsEntry>,
        targetLanguage: String,
        apiKey: String,
        baseUrl: String,
        model: String,
        mode: String,
        context: Context,
        provider: String,
        deeplApiKey: String,
        deeplFormality: String,
        useStreaming: Boolean,
        songId: String,
        database: MusicDatabase?,
    ) {
        val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
        if (effectiveApiKey.isBlank()) {
            reportError(context.getString(R.string.ai_error_api_key_required))
            return
        }
        if (lyrics.isEmpty()) {
            reportError(context.getString(R.string.ai_error_no_lyrics))
            return
        }

        val nonEmpty = lyrics.filter { it.text.isNotBlank() }
        if (nonEmpty.isEmpty()) {
            reportError(context.getString(R.string.ai_error_lyrics_empty))
            return
        }

        val fullText = nonEmpty.joinToString("\n") { it.text }
        val cached = getCachedTranslations(lyrics, mode, targetLanguage)
        if (cached != null && cached.size >= nonEmpty.size) {
            assignTranslations(nonEmpty, cached)
            logTranslation(context, songId)
            persist(songId, database, targetLanguage, mode, cached)
            reportSuccess()
            return
        }

        if (targetLanguage.isBlank()) {
            reportError(context.getString(R.string.ai_error_language_required))
            return
        }

        val request = TranslationRequest(
            fullText = fullText,
            targetLanguageCode = targetLanguage,
            targetLanguageName = resolveLanguageName(targetLanguage),
            apiKey = apiKey,
            baseUrl = baseUrl,
            model = model,
            mode = mode,
            provider = provider,
            deeplApiKey = deeplApiKey,
            deeplFormality = deeplFormality,
            useStreaming = useStreaming,
            lineCount = nonEmpty.size,
        )

        val delegate = translationDelegate ?: defaultDelegate
        val result = try {
            withTimeout(TRANSLATION_BUDGET_MS) {
                delegate.translate(request) { message -> appendLog(message) }
            }
        } catch (timeout: TimeoutCancellationException) {
            Result.failure(Exception(context.getString(R.string.ai_error_translation_failed)))
        }

        val translated = result.getOrNull()
        if (translated == null) {
            reportError(
                result.exceptionOrNull()?.message ?: context.getString(R.string.ai_error_unknown)
            )
            return
        }

        if (!compositionActive) return

        storeCache(cacheKey(lyrics, mode, targetLanguage), translated)
        persist(songId, database, targetLanguage, mode, translated)

        if (translated.isEmpty() && nonEmpty.isNotEmpty()) {
            reportError(context.getString(R.string.ai_error_unexpected))
            return
        }

        assignTranslations(nonEmpty, translated)
        logTranslation(context, songId)
        reportSuccess()
    }

    private suspend fun logTranslation(context: Context, songId: String) {
        val seam = logger
        if (seam != null) {
            seam(context, "Lyrics Translation", "Translated lyrics for $songId")
        } else {
            AiLogger.log(context, "Lyrics Translation", "Translated lyrics for $songId")
        }
    }

    private fun assignTranslations(entries: List<LyricsEntry>, translated: List<String>) {
        entries.forEachIndexed { index, entry ->
            entry.translatedTextFlow.value = translated.getOrNull(index)
        }
    }

    private fun appendLog(message: String) {
        _status.update { current ->
            if (current is TranslationStatus.Translating) {
                TranslationStatus.Translating(current.logs + message)
            } else {
                current
            }
        }
    }

    private fun reportError(message: String) {
        if (!compositionActive) return
        statusResetJob?.cancel()
        val error = TranslationStatus.Error(message)
        _status.value = error
        statusResetJob = timerScope.launch {
            delay(STATUS_LINGER_MS)
            if (_status.value == error && compositionActive) {
                _status.value = TranslationStatus.Idle
            }
        }
    }

    private fun reportSuccess() {
        _hasActiveTranslations.value = true
        statusResetJob?.cancel()
        _status.value = TranslationStatus.Success
        statusResetJob = timerScope.launch {
            delay(STATUS_LINGER_MS)
            if (_status.value == TranslationStatus.Success && compositionActive) {
                _status.value = TranslationStatus.Idle
            }
        }
    }

    private fun resolveLanguageName(code: String): String {
        LanguageCodeToName[code]?.let { return it }
        val display = try {
            Locale.forLanguageTag(code).displayLanguage
        } catch (_: Exception) {
            ""
        }
        return if (display.isNotBlank() && display != code) display else code
    }

    private fun cacheKey(lyrics: List<LyricsEntry>, mode: String, language: String): String {
        val text = lyrics.filter { it.text.isNotBlank() }.joinToString("\n") { it.text }
        return "$text\u0000$mode\u0000$language"
    }

    private fun storeCache(key: String, lines: List<String>) {
        synchronized(cache) { cache[key] = lines }
    }

    private fun persist(
        songId: String,
        database: MusicDatabase?,
        targetLanguage: String,
        mode: String,
        lines: List<String>,
    ) {
        if (songId.isBlank() || database == null) return
        persistenceScope.launch {
            try {
                val existing = database.lyrics(songId).first() ?: return@launch
                if (!existing.translatedLyrics.isBlank()) return@launch
                database.query {
                    upsert(
                        existing.copy(
                            translatedLyrics = lines.joinToString("\n"),
                            translationLanguage = targetLanguage,
                            translationMode = mode,
                        )
                    )
                }
                _translationSaved.tryEmit(Unit)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
            }
        }
    }

    private val defaultDelegate = object : TranslationDelegate {
        override suspend fun translate(
            request: TranslationRequest,
            onLog: (String) -> Unit,
        ): Result<List<String>> = when {
            request.provider == "DeepL" -> DeepLService.translate(
                text = request.fullText,
                targetLanguage = request.targetLanguageCode,
                apiKey = request.deeplApiKey,
                formality = request.deeplFormality,
                onLog = onLog,
            )

            request.provider == "Mistral" -> MistralService.translate(
                text = request.fullText,
                targetLanguage = request.targetLanguageName,
                apiKey = request.apiKey,
                model = request.model,
                mode = request.mode,
                onLog = onLog,
            )

            request.useStreaming && request.provider != "Custom" -> streamTranslate(request)

            else -> OpenRouterService.translate(
                text = request.fullText,
                targetLanguage = request.targetLanguageName,
                apiKey = request.apiKey,
                baseUrl = request.baseUrl,
                model = request.model,
                mode = request.mode,
                onLog = onLog,
            )
        }
    }

    private suspend fun streamTranslate(request: TranslationRequest): Result<List<String>> {
        var failure: String? = null
        var completed: List<String>? = null
        OpenRouterStreamingService.streamTranslation(
            text = request.fullText,
            targetLanguage = request.targetLanguageName,
            apiKey = request.apiKey,
            baseUrl = request.baseUrl,
            model = request.model,
            mode = request.mode,
        ).collect { chunk ->
            when (chunk) {
                is StreamChunk.Content -> Unit
                is StreamChunk.Complete -> completed = chunk.translatedLines
                is StreamChunk.Error -> failure = chunk.message
            }
        }
        val recordedFailure = failure
        val recordedComplete = completed
        return when {
            recordedFailure != null -> Result.failure(Exception(recordedFailure))
            recordedComplete != null -> Result.success(recordedComplete)
            else -> Result.failure(Exception("No translation received"))
        }
    }
}
