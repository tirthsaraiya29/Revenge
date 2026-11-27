package com.revenge.core.utils

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NetworkConnectivityObserverTest {

  @Test
  fun emitsInitialValueAndSupportsRepeatedUnregister() = runBlocking {
    val observer = NetworkConnectivityObserver(RuntimeEnvironment.getApplication())

    val initial = observer.networkStatus.first()
    assertNotNull(initial)

    observer.isCurrentlyConnected()
    observer.unregister()
    observer.unregister()
  }
}
