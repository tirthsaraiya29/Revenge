package com.revenge.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.BrowseEndpoint
import com.revenge.innertube.models.filterExplicit
import com.revenge.innertube.models.filterVideoSongs
import com.revenge.core.constants.HideExplicitKey
import com.revenge.core.constants.HideVideoSongsKey
import com.revenge.core.models.ItemsPage
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.core.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ArtistItemsViewModel
@Inject
constructor(
  @ApplicationContext val context: Context,
  savedStateHandle: SavedStateHandle,
) : ViewModel() {
  private val browseId = savedStateHandle.get<String>("browseId")!!
  private val params = savedStateHandle.get<String>("params")

  val title = MutableStateFlow("")
  val itemsPage = MutableStateFlow<ItemsPage?>(null)

  init {
    viewModelScope.launch {
      YouTube.artistItems(
          BrowseEndpoint(
            browseId = browseId,
            params = params,
          ),
        )
        .onSuccess { artistItemsPage ->
          val hideExplicit = context.dataStore.get(HideExplicitKey, false)
          val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
          title.value = artistItemsPage.title
          itemsPage.value =
            ItemsPage(
              items =
                artistItemsPage.items
                  .distinctBy { it.id }
                  .filterExplicit(hideExplicit)
                  .filterVideoSongs(hideVideoSongs),
              continuation = artistItemsPage.continuation,
            )
        }
        .onFailure { reportException(it) }
    }
  }

  fun loadMore() {
    viewModelScope.launch {
      val oldItemsPage = itemsPage.value ?: return@launch
      val continuation = oldItemsPage.continuation ?: return@launch
      YouTube.artistItemsContinuation(continuation)
        .onSuccess { artistItemsContinuationPage ->
          val hideExplicit = context.dataStore.get(HideExplicitKey, false)
          val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
          itemsPage.update {
            ItemsPage(
              items =
                (oldItemsPage.items + artistItemsContinuationPage.items)
                  .distinctBy { it.id }
                  .filterExplicit(hideExplicit)
                  .filterVideoSongs(hideVideoSongs),
              continuation = artistItemsContinuationPage.continuation,
            )
          }
        }
        .onFailure { reportException(it) }
    }
  }
}
