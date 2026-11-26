package com.revenge.dsp.di

import com.revenge.dsp.controller.DspController
import com.revenge.dsp.controller.DspControllerImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DspModule {

  @Binds @Singleton abstract fun bindDspController(impl: DspControllerImpl): DspController
}
