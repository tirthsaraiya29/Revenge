package com.revenge.paxsenix

import com.revenge.paxsenix.models.LyricText
import com.revenge.paxsenix.models.LyricsContent
import com.revenge.paxsenix.models.LyricsResponse
import com.revenge.paxsenix.models.SearchResponse
import com.revenge.paxsenix.models.SearchResult
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaxsenixCoreTest {
  private val target =
    TrackTarget(
      title = "Song",
      cleanTitle = "Song",
      cleanArtist = "Artist",
      durationSeconds = 200,
    )

  @Test
  fun `rank prefers the exact track over distant matches`() {
    val exact =
      SearchResult(id = "exact", trackName = "Song", artistName = "Artist", duration = 200_000)
    val wrong =
      SearchResult(id = "wrong", trackName = "Other", artistName = "Someone", duration = 200_000)
    val offDuration =
      SearchResult(id = "off", trackName = "Song", artistName = "Artist", duration = 260_000)

    val ranked = PaxsenixCore.rank(listOf(wrong, offDuration, exact), target)

    assertEquals("exact", ranked.first().result.id)
    assertTrue(ranked.first().score > ranked.last().score)
  }

  @Test
  fun `rank drops results with no positive signal`() {
    val unrelated =
      SearchResult(id = "bad", trackName = "Zzz", artistName = "Qqq", duration = 500_000)

    assertTrue(PaxsenixCore.rank(listOf(unrelated), target).isEmpty())
  }

  @Test
  fun `rank ignores results without an id`() {
    val noId =
      SearchResult(id = "", trackName = "Song", artistName = "Artist", duration = 200_000)

    assertTrue(PaxsenixCore.rank(listOf(noId), target).isEmpty())
  }

  @Test
  fun `rank penalises a remix the caller did not ask for`() {
    val original =
      SearchResult(id = "original", trackName = "Song", artistName = "Artist", duration = 200_000)
    val remix =
      SearchResult(
        id = "remix",
        trackName = "Song (Remix)",
        artistName = "Artist",
        duration = 200_000,
      )

    val ranked = PaxsenixCore.rank(listOf(remix, original), target)

    assertEquals("original", ranked.first().result.id)
    assertEquals(2, ranked.size)
  }

  @Test
  fun `rank penalises a mixed version the caller did not ask for`() {
    val mixed =
      SearchResult(id = "mixed", trackName = "Song (Mixed)", artistName = "Artist", duration = 200_000)

    val score = PaxsenixCore.score(mixed, target)
    val plainScore =
      PaxsenixCore.score(
        SearchResult(id = "plain", trackName = "Song", artistName = "Artist", duration = 200_000),
        target,
      )

    assertTrue(score < plainScore)
  }

  @Test
  fun `search queries are most specific first and include the album variant`() {
    assertEquals(
      listOf("Song Artist", "Song", "Song Artist Album"),
      PaxsenixCore.searchQueries("Song", "Artist", "Album"),
    )
    assertEquals(
      listOf("Song Artist", "Song"),
      PaxsenixCore.searchQueries("Song", "Artist", null),
    )
  }

  @Test
  fun `search queries collapse duplicates when the artist is unknown`() {
    assertEquals(listOf("Song"), PaxsenixCore.searchQueries("Song", "", " "))
  }

  @Test
  fun `word timing detection recognises enhanced LRC`() {
    assertTrue(PaxsenixCore.hasWordTimings("[00:01.00]<00:01.00>Hello"))
    assertFalse(PaxsenixCore.hasWordTimings("[00:01.00]Hello"))
  }

  @Test
  fun `plain text content skips blank lines and joins words`() {
    val content =
      listOf(
        line(timestamp = 0, words = listOf("Hello", "world")),
        line(timestamp = 1_000, words = listOf("", "")),
        line(timestamp = 2_000, words = listOf("Again")),
      )

    assertEquals("Hello world\nAgain", PaxsenixCore.plainTextFromContent(content))
  }

  @Test
  fun `syllable content becomes timestamped LRC with agent and word timings`() {
    val content =
      listOf(
        LyricsContent(
          timestamp = 61_500,
          endtime = 63_000,
          duration = 1_500,
          text =
            listOf(
              LyricText(text = "Hello", timestamp = 61_500, endtime = 61_800, duration = 300),
              LyricText(text = "world", timestamp = 61_800, endtime = 62_000, duration = 200),
            ),
          oppositeTurn = true,
        ),
      )

    val lrc = PaxsenixCore.lrcFromContent(content)

    assertEquals(
      "[01:01.50]{agent:v2}Hello world\n<Hello:61.5:61.8|world:61.8:62.0>\n",
      lrc,
    )
  }

  @Test
  fun `lrc formatting selects the right agent tag`() {
    val background = line(timestamp = 0, words = listOf("Bg"), background = true)
    val normal = line(timestamp = 1_000, words = listOf("Main"))

    val lrc = PaxsenixCore.lrcFromContent(listOf(background, normal))

    assertTrue(lrc.contains("{bg}"))
    assertTrue(lrc.contains("{agent:v1}"))
  }

  @Test
  fun `buildLyrics prefers multi-person elrc and flags its word timings`() {
    val response =
      LyricsResponse(
        elrcMultiPerson = "[00:01.00]<00:01.00>Hello",
        elrc = "[00:01.00]Hello",
        plain = "Hello",
      )

    val candidate = PaxsenixCore.buildLyrics(response).getOrThrow()

    assertEquals("[00:01.00]<00:01.00>Hello", candidate.lyrics)
    assertTrue(candidate.hasWordTimings)
  }

  @Test
  fun `buildLyrics prefers TTML over every other field`() {
    val response =
      LyricsResponse(ttmlContent = "<ttml/>", elrc = "[00:01.00]Elrc", plain = "Plain")

    val candidate =
      PaxsenixCore.buildLyrics(response) { "[00:00.00]Converted lyrics" }.getOrThrow()

    assertEquals("[00:00.00]Converted lyrics", candidate.lyrics)
    assertFalse(candidate.hasWordTimings)
  }

  @Test
  fun `buildLyrics falls back to plain when TTML conversion yields nothing`() {
    val response = LyricsResponse(ttmlContent = "<bad/>", plain = "Plain lyrics")

    val candidate = PaxsenixCore.buildLyrics(response) { "" }.getOrThrow()

    assertEquals("Plain lyrics", candidate.lyrics)
  }

  @Test
  fun `buildLyrics uses the content array for non syllable responses`() {
    val response =
      LyricsResponse(
        type = "Line",
        content = listOf(line(timestamp = 0, words = listOf("Hello", "world"))),
      )

    val candidate = PaxsenixCore.buildLyrics(response).getOrThrow()

    assertEquals("Hello world", candidate.lyrics)
    assertFalse(candidate.hasWordTimings)
  }

  @Test
  fun `buildLyrics marks syllable content as word timed`() {
    val response =
      LyricsResponse(
        type = "Syllable",
        content = listOf(line(timestamp = 0, words = listOf("Hello"))),
      )

    val candidate = PaxsenixCore.buildLyrics(response).getOrThrow()

    assertEquals("[00:00.00]{agent:v1}Hello\n<Hello:0.0:0.5>\n", candidate.lyrics)
    assertTrue(candidate.hasWordTimings)
  }

  @Test
  fun `buildLyrics fails when the response carries no lyrics`() {
    assertTrue(PaxsenixCore.buildLyrics(LyricsResponse()).isFailure)
    assertTrue(
      PaxsenixCore.buildLyrics(LyricsResponse(type = "Syllable", content = emptyList())).isFailure,
    )
  }

  @Test
  fun `models decode the provider payloads`() {
    val json = Json { ignoreUnknownKeys = true }

    val results =
      json.decodeFromString<SearchResponse>(
        """[{"id":"1","trackName":"Song","artistName":"Artist","duration":200000,"extra":1}]""",
      )
    assertEquals("Song", results.single().displayName)
    assertEquals("Artist", results.single().displayArtist)

    val lyrics =
      json.decodeFromString<LyricsResponse>(
        """{"type":"Syllable","content":[{"timestamp":0,"endtime":500,"duration":500,"text":[{"text":"Hi","timestamp":0,"endtime":500,"duration":500}]}]}""",
      )
    assertEquals(1, lyrics.content.size)
    assertEquals("Hi", lyrics.content.single().text.single().text)
  }

  @Test
  fun `buildLyrics fails when content only holds blank lines`() {
    val response =
      LyricsResponse(type = "Line", content = listOf(line(timestamp = 0, words = listOf("", ""))))

    assertTrue(PaxsenixCore.buildLyrics(response).isFailure)
  }

  private fun line(
    timestamp: Long,
    words: List<String>,
    background: Boolean = false,
  ): LyricsContent {
    var cursor = timestamp
    val text =
      words.map { word ->
        val start = cursor
        cursor += 500
        LyricText(text = word, timestamp = start, endtime = cursor, duration = 500)
      }
    return LyricsContent(
      timestamp = timestamp,
      endtime = cursor,
      duration = cursor - timestamp,
      text = text,
      background = background,
    )
  }
}
