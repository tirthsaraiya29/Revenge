package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.core.db.entities.LyricsEntity
import com.revenge.lyrics.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LyricsTranslationHelperTest {

  private lateinit var context: Context

  @Before
  fun reset() {
    context = RuntimeEnvironment.getApplication()
    LyricsTranslationHelper.cancelTranslation()
    LyricsTranslationHelper.clearCache()
    LyricsTranslationHelper.triggerClearTranslations()
    LyricsTranslationHelper.resetStatus()
    LyricsTranslationHelper.setCompositionActive(true)
    LyricsTranslationHelper.translationDelegate = null
    LyricsTranslationHelper.logger = { _, _, _ -> }
  }

  private fun TestScope.configure() {
    LyricsTranslationHelper.workDispatcher = StandardTestDispatcher(testScheduler)
    LyricsTranslationHelper.timerScope = this
    LyricsTranslationHelper.persistenceScope = this
    LyricsTranslationHelper.logger = { _, _, _ -> }
  }

  private class FakeDelegate(
    private val lines: List<String> = emptyList(),
    private val failure: Throwable? = null,
    private val delayMs: Long = 0,
    private val logs: List<String> = emptyList(),
    private val onInvoke: (() -> Unit)? = null,
  ) : TranslationDelegate {

    var calls = 0

    override suspend fun translate(
      request: TranslationRequest,
      onLog: (String) -> Unit,
    ): Result<List<String>> {
      calls++
      logs.forEach(onLog)
      onInvoke?.invoke()
      if (delayMs > 0) delay(delayMs)
      return failure?.let { Result.failure(it) } ?: Result.success(lines)
    }
  }

  private fun entries(vararg texts: String): List<LyricsEntry> = texts.mapIndexed { index, text ->
    LyricsEntry(index * 1_000L, text)
  }

  private fun translate(
    scope: TestScope,
    lyrics: List<LyricsEntry>,
    language: String = "es",
    key: String = "key",
    mode: String = "Literal",
  ) {
    LyricsTranslationHelper.translateLyrics(
      lyrics = lyrics,
      targetLanguage = language,
      apiKey = key,
      baseUrl = "",
      model = "",
      mode = mode,
      scope = scope,
      context = context,
    )
  }

  @Test
  fun `successful translation assigns lines and lingers on success`() = runTest {
    configure()
    LyricsTranslationHelper.translationDelegate = FakeDelegate(lines = listOf("uno", "dos"))
    val lyrics = entries("one", "two")

    translate(this, lyrics)
    runCurrent()

    assertEquals(listOf("uno", "dos"), lyrics.map { it.translatedTextFlow.value })
    assertTrue(LyricsTranslationHelper.hasActiveTranslations.value)
    assertEquals(TranslationStatus.Success, LyricsTranslationHelper.status.value)

    advanceTimeBy(3_001)
    runCurrent()
    assertEquals(TranslationStatus.Idle, LyricsTranslationHelper.status.value)
  }

  @Test
  fun `short cache falls through to the model`() = runTest {
    configure()
    val lyrics = entries("one", "two")
    LyricsTranslationHelper.loadTranslationsFromDatabase(
      lyrics,
      LyricsEntity(
        id = "id",
        lyrics = "",
        translatedLyrics = "uno",
        translationLanguage = "es",
        translationMode = "Literal",
      ),
      "es",
      "Literal",
    )
    assertFalse(LyricsTranslationHelper.applyCachedTranslations(lyrics, "Literal", "es"))

    val delegate = FakeDelegate(lines = listOf("uno", "dos"))
    LyricsTranslationHelper.translationDelegate = delegate
    translate(this, lyrics)
    runCurrent()

    assertEquals(1, delegate.calls)
    assertEquals(listOf("uno", "dos"), lyrics.map { it.translatedTextFlow.value })
  }

  @Test
  fun `blank api key is rejected before any model call`() = runTest {
    configure()
    val delegate = FakeDelegate(lines = listOf("x"))
    LyricsTranslationHelper.translationDelegate = delegate

    translate(this, entries("one"), key = " ")
    runCurrent()

    assertEquals(0, delegate.calls)
    assertEquals(
      context.getString(R.string.ai_error_api_key_required),
      (LyricsTranslationHelper.status.value as TranslationStatus.Error).message,
    )
  }

  @Test
  fun `empty lyrics list is rejected`() = runTest {
    configure()
    translate(this, emptyList())
    runCurrent()

    assertEquals(
      context.getString(R.string.ai_error_no_lyrics),
      (LyricsTranslationHelper.status.value as TranslationStatus.Error).message,
    )
  }

  @Test
  fun `all blank entries are rejected`() = runTest {
    configure()
    translate(this, entries(" ", ""))
    runCurrent()

    assertEquals(
      context.getString(R.string.ai_error_lyrics_empty),
      (LyricsTranslationHelper.status.value as TranslationStatus.Error).message,
    )
  }

  @Test
  fun `blank language without cache is rejected`() = runTest {
    configure()
    val delegate = FakeDelegate(lines = listOf("x"))
    LyricsTranslationHelper.translationDelegate = delegate

    translate(this, entries("one"), language = "")
    runCurrent()

    assertEquals(0, delegate.calls)
    assertEquals(
      context.getString(R.string.ai_error_language_required),
      (LyricsTranslationHelper.status.value as TranslationStatus.Error).message,
    )
  }

  @Test
  fun `fewer lines leave missing entries null`() = runTest {
    configure()
    LyricsTranslationHelper.translationDelegate = FakeDelegate(lines = listOf("uno"))
    val lyrics = entries("one", "two")

    translate(this, lyrics)
    runCurrent()

    assertEquals("uno", lyrics[0].translatedTextFlow.value)
    assertNull(lyrics[1].translatedTextFlow.value)
  }

  @Test
  fun `surplus lines are discarded`() = runTest {
    configure()
    LyricsTranslationHelper.translationDelegate = FakeDelegate(lines = listOf("uno", "dos", "tres"))
    val lyrics = entries("one", "two")

    translate(this, lyrics)
    runCurrent()

    assertEquals(listOf("uno", "dos"), lyrics.map { it.translatedTextFlow.value })
  }

  @Test
  fun `empty result is cached but reported as unexpected`() = runTest {
    configure()
    LyricsTranslationHelper.translationDelegate = FakeDelegate(lines = emptyList())
    val lyrics = entries("one")

    translate(this, lyrics)
    runCurrent()

    assertEquals(
      context.getString(R.string.ai_error_unexpected),
      (LyricsTranslationHelper.status.value as TranslationStatus.Error).message,
    )
    assertEquals(
      emptyList<String>(),
      LyricsTranslationHelper.getCachedTranslations(lyrics, "Literal", "es"),
    )
  }

  @Test
  fun `translation exceeding its budget fails`() = runTest {
    configure()
    LyricsTranslationHelper.translationDelegate =
      FakeDelegate(lines = listOf("x"), delayMs = 60_000)
    val lyrics = entries("one")

    translate(this, lyrics)
    advanceTimeBy(45_001)
    runCurrent()

    assertEquals(
      context.getString(R.string.ai_error_translation_failed),
      (LyricsTranslationHelper.status.value as TranslationStatus.Error).message,
    )
  }

  @Test
  fun `error while composition is inactive leaves status untouched`() = runTest {
    configure()
    LyricsTranslationHelper.setCompositionActive(false)

    translate(this, entries("one"), key = "")
    runCurrent()

    assertTrue(LyricsTranslationHelper.status.value is TranslationStatus.Translating)
  }

  @Test
  fun `success while composition is inactive is discarded`() = runTest {
    configure()
    LyricsTranslationHelper.setCompositionActive(false)
    LyricsTranslationHelper.translationDelegate = FakeDelegate(lines = listOf("uno"))
    val lyrics = entries("one")

    translate(this, lyrics)
    runCurrent()

    assertNull(lyrics[0].translatedTextFlow.value)
    assertFalse(LyricsTranslationHelper.hasActiveTranslations.value)
    assertNull(LyricsTranslationHelper.getCachedTranslations(lyrics, "Literal", "es"))
    assertTrue(LyricsTranslationHelper.status.value is TranslationStatus.Translating)
  }

  @Test
  fun `clear translations empties the three persisted fields`() {
    val entity =
      LyricsEntity(
        id = "id",
        lyrics = "line",
        translatedLyrics = "translated",
        translationLanguage = "es",
        translationMode = "Literal",
      )

    val cleared = LyricsTranslationHelper.clearTranslations(entity)

    assertEquals("", cleared.translatedLyrics)
    assertEquals("", cleared.translationLanguage)
    assertEquals("", cleared.translationMode)
    assertEquals("line", cleared.lyrics)
  }

  @Test
  fun `database load assigns lines and handles trailing newline`() = runTest {
    configure()
    val lyrics = entries("one", "two")

    LyricsTranslationHelper.loadTranslationsFromDatabase(
      lyrics,
      LyricsEntity(
        id = "id",
        lyrics = "",
        translatedLyrics = "uno\ndos\n",
        translationLanguage = "es",
        translationMode = "Literal",
      ),
      "es",
      "Literal",
    )

    assertEquals(listOf("uno", "dos"), lyrics.map { it.translatedTextFlow.value })
    assertTrue(LyricsTranslationHelper.hasActiveTranslations.value)
    assertEquals(
      listOf("uno", "dos", ""),
      LyricsTranslationHelper.getCachedTranslations(lyrics, "Literal", "es"),
    )
  }

  @Test
  fun `database load with mismatched language clears flows`() = runTest {
    configure()
    val lyrics = entries("one").onEach { it.translatedTextFlow.value = "stale" }

    LyricsTranslationHelper.loadTranslationsFromDatabase(
      lyrics,
      LyricsEntity(
        id = "id",
        lyrics = "",
        translatedLyrics = "uno",
        translationLanguage = "fr",
        translationMode = "Literal",
      ),
      "es",
      "Literal",
    )

    assertNull(lyrics[0].translatedTextFlow.value)
    assertFalse(LyricsTranslationHelper.hasActiveTranslations.value)
  }

  @Test
  fun `null database entity clears flows`() = runTest {
    configure()
    val lyrics = entries("one").onEach { it.translatedTextFlow.value = "stale" }

    LyricsTranslationHelper.loadTranslationsFromDatabase(lyrics, null, "es", "Literal")

    assertNull(lyrics[0].translatedTextFlow.value)
    assertFalse(LyricsTranslationHelper.hasActiveTranslations.value)
  }

  @Test
  fun `log lines appear only while translating`() = runTest {
    configure()
    var observed: List<String>? = null
    LyricsTranslationHelper.translationDelegate =
      FakeDelegate(
        lines = listOf("uno"),
        logs = listOf("first", "second"),
        onInvoke = {
          observed = (LyricsTranslationHelper.status.value as? TranslationStatus.Translating)?.logs
        },
      )

    translate(this, entries("one"))
    runCurrent()

    assertEquals(listOf("first", "second"), observed)
  }

  @Test
  fun `clear trigger deactivates and emits`() = runTest {
    configure()
    LyricsTranslationHelper.triggerClearTranslations()
    assertFalse(LyricsTranslationHelper.hasActiveTranslations.value)

    val emissions = mutableListOf<Unit>()
    val collector = launch {
      LyricsTranslationHelper.clearTranslationsTrigger.collect { emissions.add(it) }
    }
    runCurrent()
    LyricsTranslationHelper.triggerClearTranslations()
    runCurrent()

    assertEquals(1, emissions.size)
    collector.cancel()
  }

  @Test
  fun `cancelling a translation ends silently`() = runTest {
    configure()
    LyricsTranslationHelper.translationDelegate =
      FakeDelegate(lines = listOf("x"), delayMs = 60_000)

    translate(this, entries("one"))
    advanceTimeBy(1_000)
    LyricsTranslationHelper.cancelTranslation()
    runCurrent()

    assertEquals(TranslationStatus.Idle, LyricsTranslationHelper.status.value)
    advanceUntilIdle()
    assertEquals(TranslationStatus.Idle, LyricsTranslationHelper.status.value)
  }
}
