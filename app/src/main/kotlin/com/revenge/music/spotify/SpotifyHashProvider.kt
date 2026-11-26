package com.revenge.music.spotify

import java.util.concurrent.ConcurrentHashMap

enum class HashSource { HARDCODED, CACHED, REMOTE }

data class GqlHashEntry(
  val hash: String,
  val previousHash: String? = null,
  val source: HashSource,
)

data class RemoteHashEntry(val hash: String, val previousHash: String? = null)

data class UpdateResult(val updated: Int, val unchanged: Int)

/**
 * Registry of persisted GraphQL query hashes. Reads and writes are safe to call concurrently; an
 * unknown operation is a programming error and throws from [getHash].
 */
object SpotifyHashProvider {

  private const val WINDOW = 64

  /** Hardcoded placeholder digests; remote refresh overrides them when Spotify rotates a query. */
  private fun seed(operation: String): String {
    var value = 0x811c9dc5L
    operation.forEach { c ->
      value = (value xor c.code.toLong()) * 0x01000193L
    }
    return (0 until 4).joinToString("") {
      java.lang.String.format("%016x", value + it * 0x9e3779b97f4a7c15UL.toLong())
    }
  }

  private val sharedMutationHash = seed("playlistMutation")

  private val registry: ConcurrentHashMap<String, GqlHashEntry> =
    ConcurrentHashMap(
      buildMap {
        listOf(
            "profileAttributes",
            "libraryV3",
            "fetchPlaylist",
            "fetchLibraryTracks",
            "searchDesktop",
            "queryArtistOverview",
            "getAlbum",
            "queryWhatsNewFeed",
            "home",
            "canvas",
            "addToLibrary",
            "removeFromLibrary",
          )
          .forEach { put(it, GqlHashEntry(seed(it), source = HashSource.HARDCODED)) }
        listOf("addToPlaylist", "removeFromPlaylist", "moveItemsInPlaylist").forEach {
          put(it, GqlHashEntry(sharedMutationHash, source = HashSource.HARDCODED))
        }
        put("editPlaylistAttributes", GqlHashEntry(seed("editPlaylistAttributes"), source = HashSource.HARDCODED))
      },
    )

  fun getHash(operationName: String): String =
    registry[operationName]?.hash
      ?: throw IllegalStateException("Unknown GraphQL operation: $operationName")

  fun getPreviousHash(operationName: String): String? = registry[operationName]?.previousHash

  fun updateHashes(
    remoteHashes: Map<String, RemoteHashEntry>,
    source: HashSource,
  ): UpdateResult {
    var updated = 0
    var unchanged = 0
    for ((name, remote) in remoteHashes) {
      if (name !in registry) continue
      val existing = registry[name]
      if (existing != null && existing.hash == remote.hash) {
        unchanged++
        continue
      }
      registry[name] =
        GqlHashEntry(
          hash = remote.hash,
          previousHash = remote.previousHash ?: existing?.hash,
          source = source,
        )
      updated++
    }
    return UpdateResult(updated = updated, unchanged = unchanged)
  }

  fun getAll(): Map<String, GqlHashEntry> = registry.toMap()

  internal fun resetRegistry() {
    // Kept for deterministic tests: restore the hardcoded baseline.
    registry.clear()
    listOf(
        "profileAttributes",
        "libraryV3",
        "fetchPlaylist",
        "fetchLibraryTracks",
        "searchDesktop",
        "queryArtistOverview",
        "getAlbum",
        "queryWhatsNewFeed",
        "home",
        "canvas",
        "addToLibrary",
        "removeFromLibrary",
        "editPlaylistAttributes",
      )
      .forEach { registry[it] = GqlHashEntry(seed(it), source = HashSource.HARDCODED) }
    listOf("addToPlaylist", "removeFromPlaylist", "moveItemsInPlaylist").forEach {
      registry[it] = GqlHashEntry(sharedMutationHash, source = HashSource.HARDCODED)
    }
  }
}
