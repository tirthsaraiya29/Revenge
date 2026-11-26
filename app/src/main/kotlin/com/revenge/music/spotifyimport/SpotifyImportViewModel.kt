package com.revenge.music.spotifyimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SpotifyImportViewModel @Inject constructor(
  private val repository: SpotifyImportRepository,
) : ViewModel() {

  private val _uiState = MutableStateFlow(SpotifyImportUiState(isLoading = true))
  val uiState: StateFlow<SpotifyImportUiState> = _uiState.asStateFlow()

  private val sourceObjects = mutableMapOf<String, SpotifyImportSource>()
  private var importJob: Job? = null

  init {
    restoreSession()
  }

  fun restoreSession() {
    viewModelScope.launch {
      try {
        val session = repository.restoreSession()
        _uiState.update {
          it.copy(
            isAuthenticated = session.isAuthenticated,
            accountName = session.accountName,
            accountAvatarUrl = session.accountAvatarUrl,
            isLoading = session.isAuthenticated,
          )
        }
        if (session.isAuthenticated) loadSourcesInternal()
        else _uiState.update { it.copy(isLoading = false) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        fail(e)
      }
    }
  }

  fun connectWithCookies(spDc: String, spKey: String) {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        val session = repository.connectWithCookies(spDc, spKey)
        _uiState.update {
          it.copy(
            isAuthenticated = session.isAuthenticated,
            accountName = session.accountName,
            accountAvatarUrl = session.accountAvatarUrl,
            isLoading = true,
          )
        }
        loadSourcesInternal()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        fail(e)
      }
    }
  }

  fun loadSources() {
    viewModelScope.launch { loadSourcesInternal() }
  }

  private suspend fun loadSourcesInternal() {
    _uiState.update { it.copy(isLoading = true, errorMessage = null) }
    try {
      val sources = repository.loadSources()
      sourceObjects.clear()
      sources.forEach { sourceObjects[it.id] = it }
      _uiState.update {
        it.copy(
          isLoading = false,
          sources = sources.map { source -> source.toUi() },
          selectedSourceIds = sources.map { source -> source.id }.toSet(),
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      fail(e)
    }
  }

  fun addPlaylistByUrl(url: String) {
    if (_uiState.value.progress != null || url.isBlank()) return
    viewModelScope.launch {
      try {
        val source = repository.addPlaylistByUrl(url)
        sourceObjects[source.id] = source
        val ui = source.toUi()
        _uiState.update { state ->
          val existing = state.sources.indexOfFirst { it.id == ui.id }
          val sources =
            if (existing >= 0) {
              state.sources.toMutableList().apply { set(existing, ui) }
            } else {
              listOf(ui) + state.sources
            }
          state.copy(
            sources = sources,
            selectedSourceIds = state.selectedSourceIds + ui.id,
            errorMessage = null,
          )
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        fail(e)
      }
    }
  }

  fun toggleSource(sourceId: String) {
    _uiState.update { state ->
      val selected =
        if (sourceId in state.selectedSourceIds) state.selectedSourceIds - sourceId
        else state.selectedSourceIds + sourceId
      state.copy(selectedSourceIds = selected)
    }
  }

  fun selectAllSources() {
    _uiState.update { it.copy(selectedSourceIds = it.sources.map { source -> source.id }.toSet()) }
  }

  fun clearSelection() {
    _uiState.update { it.copy(selectedSourceIds = emptySet()) }
  }

  fun logout() {
    if (_uiState.value.progress != null) return
    viewModelScope.launch {
      try {
        importJob?.cancel()
        repository.logout()
        sourceObjects.clear()
        _uiState.value = SpotifyImportUiState(isLoading = false)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Throwable) {
        fail(e)
      }
    }
  }

  fun importSelectedSources() {
    val state = _uiState.value
    if (!state.canImport || importJob?.isActive == true) return
    val selected =
      state.selectedSourceIds.mapNotNull { sourceObjects[it] }
    if (selected.isEmpty()) return
    importJob =
      viewModelScope.launch {
        _uiState.update { it.copy(errorMessage = null, summary = null) }
        try {
          val summary =
            repository.importSources(selected) { progress ->
              _uiState.update { it.copy(progress = progress) }
            }
          _uiState.update { it.copy(progress = null, summary = summary) }
        } catch (e: CancellationException) {
          _uiState.update { it.copy(progress = null) }
          throw e
        } catch (e: Throwable) {
          _uiState.update { it.copy(progress = null) }
          fail(e)
        }
      }
  }

  fun cancelImport() {
    importJob?.cancel()
    importJob = null
    _uiState.update { it.copy(progress = null) }
  }

  fun dismissSummary() {
    _uiState.update { it.copy(summary = null) }
  }

  fun dismissError() {
    _uiState.update { it.copy(errorMessage = null) }
  }

  private fun fail(error: Throwable) {
    if (error is CancellationException) throw error
    Timber.tag("SpotifyImportVM").w(error, "Spotify import failed")
    _uiState.update {
      it.copy(
        isLoading = false,
        progress = null,
        errorMessage = error.localizedMessage ?: error.message ?: "Something went wrong",
      )
    }
  }

  private fun SpotifyImportSource.toUi() =
    SpotifyImportSourceUi(
      id = id,
      title = title,
      subtitle = subtitle,
      thumbnailUrl = thumbnailUrl,
      trackCount = trackCount,
      type = type,
    )
}
