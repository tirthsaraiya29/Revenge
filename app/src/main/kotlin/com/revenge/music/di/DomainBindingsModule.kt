package com.revenge.music.di

import com.revenge.core.repository.PlaylistRepositoryImpl
import com.revenge.core.repository.SongRepositoryImpl
import com.revenge.domain.repositories.PlaylistRepository
import com.revenge.domain.repositories.SongRepository
import com.revenge.music.musicflow.MusicFlowFeedSource
import com.revenge.music.musicflow.MusicFlowRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainBindingsModule {

  @Binds
  @Singleton
  abstract fun bindSongRepository(
    impl: SongRepositoryImpl,
  ): SongRepository

  @Binds
  @Singleton
  abstract fun bindPlaylistRepository(
    impl: PlaylistRepositoryImpl,
  ): PlaylistRepository

  @Binds
  @Singleton
  abstract fun bindMusicFlowFeedSource(
    impl: MusicFlowRepository,
  ): MusicFlowFeedSource
}
