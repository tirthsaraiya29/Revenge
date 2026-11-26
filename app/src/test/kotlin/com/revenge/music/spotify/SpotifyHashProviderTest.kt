package com.revenge.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SpotifyHashProviderTest {

  @Test
  fun everyDocumentedOperationResolves() {
    SpotifyHashProvider.resetRegistry()
    listOf(
        "profileAttributes",
        "libraryV3",
        "fetchPlaylist",
        "fetchLibraryTracks",
        "searchDesktop",
        "queryArtistOverview",
        "getAlbum",
        "queryWhatsNewFeed",
        "addToPlaylist",
        "removeFromPlaylist",
        "moveItemsInPlaylist",
        "editPlaylistAttributes",
        "addToLibrary",
        "removeFromLibrary",
        "home",
        "canvas",
      )
      .forEach { assertNotNull(SpotifyHashProvider.getHash(it)) }
  }

  @Test
  fun unknownOperationThrows() {
    assertThrows(IllegalStateException::class.java) {
      SpotifyHashProvider.getHash("not_an_operation")
    }
  }

  @Test
  fun updateCountsChangesAndPreservesPreviousHash() {
    SpotifyHashProvider.resetRegistry()
    val original = SpotifyHashProvider.getHash("home")
    val first =
      SpotifyHashProvider.updateHashes(
        mapOf("home" to RemoteHashEntry("aaaa")),
        HashSource.REMOTE,
      )
    assertEquals(1, first.updated)
    assertEquals(0, first.unchanged)
    assertEquals("aaaa", SpotifyHashProvider.getHash("home"))
    assertEquals(original, SpotifyHashProvider.getPreviousHash("home"))

    val second =
      SpotifyHashProvider.updateHashes(
        mapOf("home" to RemoteHashEntry("aaaa")),
        HashSource.REMOTE,
      )
    assertEquals(0, second.updated)
    assertEquals(1, second.unchanged)
  }

  @Test
  fun updateIgnoresUnknownOperationsAndSharedMutationHashesStayResolvable() {
    SpotifyHashProvider.resetRegistry()
    val result =
      SpotifyHashProvider.updateHashes(
        mapOf("unrelated" to RemoteHashEntry("bbbb")),
        HashSource.REMOTE,
      )
    assertEquals(0, result.updated)
    assertEquals(0, result.unchanged)
    assertNotNull(SpotifyHashProvider.getHash("addToPlaylist"))
    assertNotNull(SpotifyHashProvider.getHash("removeFromPlaylist"))
    assertNotNull(SpotifyHashProvider.getHash("moveItemsInPlaylist"))
  }
}
