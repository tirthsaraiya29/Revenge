package com.revenge.music.ai.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationProvider
@Inject
constructor(
  @ApplicationContext private val context: Context,
) {
  companion object {
    private const val MAX_AGE_MS = 30L * 60 * 1000
  }

  /** Returns the newest last-known fix not older than 30 minutes. Caller owns the permission. */
  @SuppressLint("MissingPermission")
  fun getLastKnownLocation(): Location? {
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val candidates = ArrayList<Location>()
    for (provider in
      listOf(
        LocationManager.GPS_PROVIDER,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
      )) {
      val location = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
      if (location != null) candidates += location
    }
    val now = System.currentTimeMillis()
    val fresh = candidates.filter { now - it.time <= MAX_AGE_MS }
    return fresh.maxByOrNull { it.elapsedRealtimeNanos }
  }

  /** Best-effort fused provider probe; returns null when Play Services is unavailable. */
  fun getFusedLocation(): Location? = runCatching {
    val servicesClass = Class.forName("com.google.android.gms.location.LocationServices")
    val clientMethod =
      servicesClass.getMethod("getFusedLocationProviderClient", Context::class.java)
    val client = clientMethod.invoke(null, context) ?: return@runCatching null
    val task =
      client.javaClass.getMethod("getLastLocation").invoke(client) ?: return@runCatching null
    val isComplete = task.javaClass.getMethod("isComplete")
    val deadline = System.currentTimeMillis() + 2_000
    while (isComplete.invoke(task) != true && System.currentTimeMillis() < deadline) {
      Thread.sleep(50)
    }
    task.javaClass.getMethod("getResult").invoke(task) as? Location
  }
    .getOrNull()
}
