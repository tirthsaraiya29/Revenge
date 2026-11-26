package com.revenge.music.lyrics

import android.content.Context
import com.revenge.lyrics.R
import com.revenge.music.ai.AiLogger
import com.revenge.music.api.DeepLService
import com.revenge.music.api.MistralService
import com.revenge.music.api.OpenRouterService
import com.revenge.music.api.OpenRouterStreamingService
import com.revenge.music.constants.LanguageCodeToName
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.LyricsEntity
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

object LyricsTranslationHelper {
  private const val STATUS_LINGER_MS = 3_000L
  private const val TRANSLATION_TIMEOUT_MS = 45_000L

  private val _status = MutableStateFlow<TranslationStatus>(TranslationStatus.Idle)
  val status: StateFlow<TranslationStatus> = _status.asStateFlow()

  private val _hasActiveTranslations = MutableStateFlow(false)
  val hasActiveTranslations: StateFlow<Boolean> = _hasActiveTranslations.asStateFlow()

  private val _manualTrigger =
    MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val manualTrigger: SharedFlow<Unit> = _manualTrigger.asSharedFlow()

  private val _clearTranslationsTrigger =
    MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val clearTranslationsTrigger: SharedFlow<Unit> = _clearTranslationsTrigger.asSharedFlow()

  private val _translationSaved =
    MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  val translationSaved: SharedFlow<Unit> = _translationSaved.asSharedFlow()

  private var translationJob: Job? = null
  private var statusResetJob: Job? = null
  private var isCompositionActive = true

  // In-memory cache keyed by lyrics text hash + mode + language; survives song changes.
  private val translationCache = mutableMapOf<String, List<String>>()

  private fun cacheKey(text: String, mode: String, language: String): String =
    "${text.hashCode()}_${mode}_$language"

  private fun nonEmptyEntries(lyrics: List<LyricsEntry>): List<Pair<Int, LyricsEntry>> =
    lyrics.mapIndexedNotNull { index, entry ->
      if (entry.text.isNotBlank()) index to entry else null
    }

  private fun systemText(lyrics: List<LyricsEntry>): String =
    nonEmptyEntries(lyrics).joinToString("\n") { it.second.text }

  /**
   * Reads complete JSON strings from a partial streaming array so lines can appear as they arrive.
   * Stops at the first unterminated string.
   */
  private fun tryParsePartial(content: String, expectedCount: Int): List<String> {
    val start = content.indexOf('[')
    if (start == -1) return emptyList()

    val result = mutableListOf<String>()
    val current = StringBuilder()
    var inString = false
    var escaping = false
    var pos = start + 1

    while (pos < content.length && result.size < expectedCount) {
      val char = content[pos]
      when {
        escaping -> {
          current.append(char)
          escaping = false
        }
        inString && char == '\\' -> {
          current.append(char)
          escaping = true
        }
        char == '"' ->
          if (inString) {
            result.add(current.toString())
            current.clear()
            inString = false
          } else {
            inString = true
          }
        inString -> current.append(char)
        char == ']' -> break
      }
      pos++
    }
    return result
  }

  fun getCachedTranslations(
    lyrics: List<LyricsEntry>,
    mode: String,
    language: String,
  ): List<String>? = translationCache[cacheKey(systemText(lyrics), mode, language)]

  fun applyCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): Boolean {
    val cached = getCachedTranslations(lyrics, mode, language) ?: return false
    val entries = nonEmptyEntries(lyrics)
    if (cached.size < entries.size) return false
    entries.forEachIndexed { index, (originalIndex, _) ->
      lyrics[originalIndex].translatedTextFlow.value = cached[index]
    }
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

  fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity =
    lyricsEntity.copy(translatedLyrics = "", translationLanguage = "", translationMode = "")

  fun resetStatus() {
    statusResetJob?.cancel()
    statusResetJob = null
    _status.value = TranslationStatus.Idle
  }

  fun clearCache() {
    translationCache.clear()
  }

  fun setCompositionActive(active: Boolean) {
    isCompositionActive = active
  }

  fun cancelTranslation() {
    statusResetJob?.cancel()
    statusResetJob = null
    translationJob?.cancel()
    translationJob = null
    _status.value = TranslationStatus.Idle
  }

  private fun reportError(scope: CoroutineScope, message: String) {
    if (!isCompositionActive) return
    statusResetJob?.cancel()
    val errorStatus = TranslationStatus.Error(message)
    _status.value = errorStatus
    statusResetJob = scope.launch {
      delay(STATUS_LINGER_MS)
      if (_status.value == errorStatus && isCompositionActive) {
        _status.value = TranslationStatus.Idle
      }
    }
  }

  private suspend fun setSuccess() {
    _hasActiveTranslations.value = true
    _status.value = TranslationStatus.Success
    delay(STATUS_LINGER_MS)
    if (_status.value is TranslationStatus.Success && isCompositionActive) {
      _status.value = TranslationStatus.Idle
    }
  }

  fun loadTranslationsFromDatabase(
    lyrics: List<LyricsEntry>,
    lyricsEntity: LyricsEntity?,
    targetLanguage: String,
    mode: String,
  ) {
    lyrics.forEach { it.translatedTextFlow.value = null }

    if (lyricsEntity?.translatedLyrics.isNullOrBlank() ||
      lyricsEntity.translationLanguage != targetLanguage ||
      lyricsEntity.translationMode != mode
    ) {
      _hasActiveTranslations.value = false
      return
    }

    val translatedLines = lyricsEntity.translatedLyrics.lines()
    nonEmptyEntries(lyrics).forEachIndexed { index, (originalIndex, _) ->
      if (index < translatedLines.size) {
        lyrics[originalIndex].translatedTextFlow.value = translatedLines[index]
      }
    }

    translationCache[cacheKey(systemText(lyrics), mode, targetLanguage)] = translatedLines
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
    statusResetJob = null
    translationJob?.cancel()
    _status.value = TranslationStatus.Translating()
    lyrics.forEach { it.translatedTextFlow.value = null }

    translationJob =
      scope.launch(Dispatchers.IO) {
        try {
          val effectiveApiKey = if (provider == "DeepL") deeplApiKey else apiKey
          if (effectiveApiKey.isBlank()) {
            reportError(scope, context.getString(R.string.ai_error_api_key_required))
            return@launch
          }
          if (lyrics.isEmpty()) {
            reportError(scope, context.getString(R.string.ai_error_no_lyrics))
            return@launch
          }

          val entries = nonEmptyEntries(lyrics)
          if (entries.isEmpty()) {
            reportError(scope, context.getString(R.string.ai_error_lyrics_empty))
            return@launch
          }

          val fullText = entries.joinToString("\n") { it.second.text }
          val key = cacheKey(fullText, mode, targetLanguage)

          translationCache[key]?.let { cached ->
            if (cached.size >= entries.size) {
              entries.forEachIndexed { index, (originalIndex, _) ->
                lyrics[originalIndex].translatedTextFlow.value = cached[index]
              }
              AiLogger.log(context, "Lyrics Translation", "Translated lyrics for $songId")
              persistTranslations(
                scope = scope,
                database = database,
                songId = songId,
                targetLanguage = targetLanguage,
                mode = mode,
                lines = cached,
                onlyIfEmpty = true,
              )
              setSuccess()
              return@launch
            }
          }

          if (targetLanguage.isBlank()) {
            reportError(scope, context.getString(R.string.ai_error_language_required))
            return@launch
          }

          val languageName = resolveLanguageName(targetLanguage)
          val result =
            withTimeoutOrNull(TRANSLATION_TIMEOUT_MS) {
              runTranslation(
                provider = provider,
                fullText = fullText,
                targetLanguage = languageName,
                languageCode = targetLanguage,
                apiKey = apiKey,
                deeplApiKey = deeplApiKey,
                deeplFormality = deeplFormality,
                baseUrl = baseUrl,
                model = model,
                mode = mode,
                useStreaming = useStreaming,
                entryCount = entries.size,
                lyrics = lyrics,
                entries = entries,
              )
            } ?: Result.failure(
              Exception(context.getString(R.string.ai_error_translation_failed)),
            )

          result
            .onSuccess { translated ->
              if (!isCompositionActive) return@onSuccess
              translationCache[cacheKey(fullText, mode, targetLanguage)] = translated

              persistTranslations(
                scope = scope,
                database = database,
                songId = songId,
                targetLanguage = targetLanguage,
                mode = mode,
                lines = translated,
              )

              if (translated.isEmpty() && entries.isNotEmpty()) {
                reportError(scope, context.getString(R.string.ai_error_unexpected))
                return@onSuccess
              }
              translated.forEachIndexed { index, line ->
                if (index < entries.size) {
                  lyrics[entries[index].first].translatedTextFlow.value = line
                }
              }
              AiLogger.log(context, "Lyrics Translation", "Translated lyrics for $songId")
              setSuccess()
            }
            .onFailure { error ->
              reportError(
                scope,
                error.message ?: context.getString(R.string.ai_error_unknown),
              )
            }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          reportError(
            scope,
            e.message ?: context.getString(R.string.ai_error_translation_failed),
          )
        }
      }
  }

  private suspend fun runTranslation(
    provider: String,
    fullText: String,
    targetLanguage: String,
    languageCode: String,
    apiKey: String,
    deeplApiKey: String,
    deeplFormality: String,
    baseUrl: String,
    model: String,
    mode: String,
    useStreaming: Boolean,
    entryCount: Int,
    lyrics: List<LyricsEntry>,
    entries: List<Pair<Int, LyricsEntry>>,
  ): Result<List<String>> =
    when {
      provider == "DeepL" ->
        DeepLService.translate(
          text = fullText,
          targetLanguage = languageCode,
          apiKey = deeplApiKey,
          formality = deeplFormality,
          onLog = ::appendLog,
        )
      provider == "Mistral" ->
        MistralService.translate(
          text = fullText,
          targetLanguage = targetLanguage,
          apiKey = apiKey,
          model = model,
          mode = mode,
          onLog = ::appendLog,
        )
      useStreaming && provider != "Custom" ->
        streamTranslation(
          fullText = fullText,
          targetLanguage = targetLanguage,
          apiKey = apiKey,
          baseUrl = baseUrl,
          model = model,
          mode = mode,
          entryCount = entryCount,
          lyrics = lyrics,
          entries = entries,
        )
      else ->
        OpenRouterService.translate(
          text = fullText,
          targetLanguage = targetLanguage,
          apiKey = apiKey,
          baseUrl = baseUrl,
          model = model,
          mode = mode,
          onLog = ::appendLog,
        )
    }

  private suspend fun streamTranslation(
    fullText: String,
    targetLanguage: String,
    apiKey: String,
    baseUrl: String,
    model: String,
    mode: String,
    entryCount: Int,
    lyrics: List<LyricsEntry>,
    entries: List<Pair<Int, LyricsEntry>>,
  ): Result<List<String>> {
    var complete: List<String>? = null
    var failure: String? = null
    val accumulated = StringBuilder()

    OpenRouterStreamingService.streamTranslation(
        text = fullText,
        targetLanguage = targetLanguage,
        apiKey = apiKey,
        baseUrl = baseUrl,
        model = model,
        mode = mode,
      )
      .collect { chunk ->
        when (chunk) {
          is OpenRouterStreamingService.StreamChunk.Content -> {
            accumulated.append(chunk.text)
            tryParsePartial(accumulated.toString(), entryCount).forEachIndexed { index, line ->
              if (index < entries.size && line.isNotBlank()) {
                lyrics[entries[index].first].translatedTextFlow.value = line
              }
            }
            if (_status.value is TranslationStatus.Translating) {
              _status.value = TranslationStatus.Translating()
            }
          }
          is OpenRouterStreamingService.StreamChunk.Complete -> complete = chunk.translatedLines
          is OpenRouterStreamingService.StreamChunk.Error -> failure = chunk.message
        }
      }

    return when {
      failure != null -> Result.failure(Exception(failure))
      complete != null -> Result.success(complete!!)
      else -> Result.failure(Exception("No translation received"))
    }
  }

  private fun appendLog(message: String) {
    val current = _status.value
    if (current is TranslationStatus.Translating) {
      _status.value = current.copy(logs = current.logs + message)
    }
  }

  private fun resolveLanguageName(code: String): String =
    LanguageCodeToName[code]
      ?: runCatching { Locale.forLanguageTag(code).displayLanguage }
        .getOrNull()
        ?.takeIf { it.isNotBlank() && it != code }
      ?: code

  private fun persistTranslations(
    scope: CoroutineScope,
    database: MusicDatabase?,
    songId: String,
    targetLanguage: String,
    mode: String,
    lines: List<String>,
    onlyIfEmpty: Boolean = false,
  ) {
    if (songId.isBlank() || database == null) return
    scope.launch(Dispatchers.IO) {
      try {
        val existing = database.lyrics(songId).first() ?: return@launch
        if (onlyIfEmpty && !existing.translatedLyrics.isNullOrBlank()) return@launch
        database.query {
          upsert(
            existing.copy(
              translatedLyrics = lines.joinToString("\n"),
              translationLanguage = targetLanguage,
              translationMode = mode,
            ),
          )
        }
        _translationSaved.tryEmit(Unit)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Timber.e(e, "Failed to persist translated lyrics")
      }
    }
  }

  sealed class TranslationStatus {
    data object Idle : TranslationStatus()

    data class Translating(val logs: List<String> = emptyList()) : TranslationStatus()

    data object Success : TranslationStatus()

    data class Error(val message: String) : TranslationStatus()
  }
}
