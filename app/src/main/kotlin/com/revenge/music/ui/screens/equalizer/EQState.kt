package com.revenge.music.ui.screens.equalizer

import com.revenge.dsp.core.models.SavedEQProfile

data class EQState(
  val profiles: List<SavedEQProfile> = emptyList(),
  val activeProfileId: String? = null,
  val importStatus: String? = null,
  val error: String? = null,
)
