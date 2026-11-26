package com.revenge.music.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.revenge.music.extensions.toEnum
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private fun <T> readBlocking(store: DataStore<Preferences>, key: Preferences.Key<T>): T? = try {
    runBlocking { store.data.first()[key] }
} catch (_: Exception) {
    null
}

operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T? = readBlocking(this, key)

fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>, defaultValue: T): T =
    get(key) ?: defaultValue

fun <T> preference(
    context: Context,
    key: Preferences.Key<T>,
    defaultValue: T,
): ReadOnlyProperty<Any?, T> = delegate { context.dataStore.get(key, defaultValue) }

inline fun <reified T : Enum<T>> enumPreference(
    context: Context,
    key: Preferences.Key<String>,
    defaultValue: T,
): ReadOnlyProperty<Any?, T> = delegate { context.dataStore[key].toEnum(defaultValue) }

@PublishedApi
internal fun <T> delegate(read: () -> T): ReadOnlyProperty<Any?, T> =
    object : ReadOnlyProperty<Any?, T> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): T = read()
    }
