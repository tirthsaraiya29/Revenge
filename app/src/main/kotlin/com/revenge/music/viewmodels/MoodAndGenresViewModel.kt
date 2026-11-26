package com.revenge.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.revenge.innertube.YouTube
import com.revenge.innertube.pages.MoodAndGenres
import com.revenge.core.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class MoodAndGenresViewModel @Inject constructor() : ViewModel() {
  val moodAndGenres = MutableStateFlow<List<MoodAndGenres>?>(null)

  init {
    viewModelScope.launch {
      YouTube.moodAndGenres()
        .onSuccess { moodAndGenres.value = it }
        .onFailure { reportException(it) }
    }
  }
}
