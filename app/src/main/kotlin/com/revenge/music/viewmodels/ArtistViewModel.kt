package com.revenge.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenge.applecanvas.AppleMusicCanvasProvider
import com.revenge.canvas.YouTubeVideoCanvasProvider
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import com.revenge.innertube.models.filterYoutubeShorts
import com.revenge.innertube.pages.ArtistPage
import com.revenge.core.constants.HideExplicitKey
import com.revenge.core.constants.HideVideoSongsKey
import com.revenge.core.constants.HideYoutubeShortsKey
import com.revenge.core.db.MusicDatabase
import com.revenge.core.extensions.filterExplicit
import com.revenge.core.extensions.filterExplicitAlbums
import com.revenge.core.extensions.filterVideoSongs as filterVideoSongsLocal
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.core.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ArtistViewModel
@Inject
constructor(
  @ApplicationContext private val context: Context,
  database: MusicDatabase,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  val artistId = savedStateHandle.get<String>("artistId")!!
  var artistPage by mutableStateOf<ArtistPage?>(null)

  private val _artistVideoUrl = MutableStateFlow<String?>(null)
  val artistVideoUrl: StateFlow<String?> = _artistVideoUrl

  private val _artistVideoSong = MutableStateFlow<com.revenge.innertube.models.SongItem?>(null)
  val artistVideoSong: StateFlow<com.revenge.innertube.models.SongItem?> = _artistVideoSong

  val libraryArtist = database.artist(artistId).stateIn(viewModelScope, SharingStarted.Lazily, null)
  val librarySongs =
    context.dataStore.data
      .map {
        ((try {
          it[HideExplicitKey]
        } catch (e: Exception) {
          null
        }) ?: false) to
          ((try {
            it[HideVideoSongsKey]
          } catch (e: Exception) {
            null
          }) ?: false)
      }
      .distinctUntilChanged()
      .flatMapLatest { (hideExplicit, hideVideoSongs) ->
        database.artistSongsPreview(artistId).map {
          it.filterExplicit(hideExplicit).filterVideoSongsLocal(hideVideoSongs)
        }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
  val libraryAlbums =
    context.dataStore.data
      .map {
        (try {
          it[HideExplicitKey]
        } catch (e: Exception) {
          null
        }) ?: false
      }
      .distinctUntilChanged()
      .flatMapLatest { hideExplicit ->
        database.artistAlbumsPreview(artistId).map { it.filterExplicitAlbums(hideExplicit) }
      }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  init {

    viewModelScope.launch {
      context.dataStore.data
        .map {
          Triple(
            (try {
              it[HideExplicitKey]
            } catch (e: Exception) {
              null
            }) ?: false,
            (try {
              it[HideVideoSongsKey]
            } catch (e: Exception) {
              null
            }) ?: false,
            (try {
              it[HideYoutubeShortsKey]
            } catch (e: Exception) {
              null
            }) ?: false,
          )
        }
        .distinctUntilChanged()
        .collect { fetchArtistsFromYTM() }
    }
  }

  fun fetchArtistsFromYTM() {
    if (artistId.startsWith("LOCAL_ARTIST_")) return
    viewModelScope.launch {
      val hideExplicit = context.dataStore.get(HideExplicitKey, false)
      val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
      val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, false)
      YouTube.artist(artistId)
        .onSuccess { page ->
          val filteredSections =
            page.sections
              .map { section ->
                section.copy(
                  items =
                    section.items
                      .filterExplicit(hideExplicit)
                      .filterVideoSongs(hideVideoSongs)
                      .filterYoutubeShorts(hideYoutubeShorts),
                )
              }
              .filter { section -> section.items.isNotEmpty() }

          artistPage = page.copy(sections = filteredSections)

          val topSongsSection =
            page.sections.find { it.items.firstOrNull() is com.revenge.innertube.models.SongItem }
          topSongsSection?.items?.forEach { item ->
            if (item is com.revenge.innertube.models.SongItem) {
              val artistTitle = page.artist?.title ?: ""
              val appleCanvas =
                AppleMusicCanvasProvider.getBySongArtist(
                  song = item.title,
                  artist = artistTitle,
                )
              val canvasUrl =
                appleCanvas?.preferredAnimationUrl
                  ?: YouTubeVideoCanvasProvider.getCanvasForVideo(
                      videoId = item.id,
                      title = item.title,
                      artist = artistTitle,
                      durationSec = item.duration?.toLong() ?: 0L,
                    )
                    ?.preferredAnimationUrl

              if (canvasUrl != null) {
                _artistVideoUrl.value = canvasUrl
                _artistVideoSong.value = item
                return@forEach
              }
            }
          }
        }
        .onFailure { reportException(it) }
    }
  }
}
