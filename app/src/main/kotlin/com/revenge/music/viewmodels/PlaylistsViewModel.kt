@file:OptIn(ExperimentalCoroutinesApi::class)

package com.revenge.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenge.core.constants.AddToPlaylistSortDescendingKey
import com.revenge.core.constants.AddToPlaylistSortTypeKey
import com.revenge.core.constants.PlaylistSortType
import com.revenge.core.db.MusicDatabase
import com.revenge.core.extensions.toEnum
import com.revenge.core.utils.dataStore
import com.revenge.music.utils.SyncUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class PlaylistsViewModel
@Inject
constructor(
  @ApplicationContext context: Context,
  database: MusicDatabase,
  private val syncUtils: SyncUtils,
) : ViewModel() {
  val allPlaylists =
    context.dataStore.data
      .map {
        (try {
            it[AddToPlaylistSortTypeKey]
          } catch (e: Exception) {
            null
          })
          .toEnum(PlaylistSortType.CREATE_DATE) to
          ((try {
            it[AddToPlaylistSortDescendingKey]
          } catch (e: Exception) {
            null
          }) ?: true)
      }
      .distinctUntilChanged()
      .flatMapLatest { (sortType, descending) -> database.playlists(sortType, descending) }
      .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

  suspend fun sync() {
    syncUtils.performSavedPlaylistsSync()
  }
}
