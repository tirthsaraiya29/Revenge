package com.revenge.music.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.revenge.core.extensions.toEnum
import com.revenge.core.utils.dataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun <T> rememberPreference(
    key: Preferences.Key<T>,
    defaultValue: T,
): MutableState<T> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember(key) { mutableStateOf(defaultValue) }

    LaunchedEffect(key) {
        state.value = try {
            val stored = context.dataStore.data.first()[key]
            when {
                stored == null -> defaultValue
                defaultValue == null -> stored
                stored::class == defaultValue::class -> stored
                stored is Set<*> || defaultValue is Set<*> -> stored
                else -> defaultValue
            }
        } catch (_: Exception) {
            defaultValue
        }
    }

    return remember(key, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    scope.launch {
                        runCatching {
                            context.dataStore.edit { it[key] = newValue }
                        }
                    }
                }

            override fun component1(): T = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: Preferences.Key<String>,
    defaultValue: T,
): MutableState<T> {
    val stringState = rememberPreference(key, defaultValue.name)
    return remember(key) {
        object : MutableState<T> {
            override var value: T
                get() = stringState.value.toEnum(defaultValue)
                set(newValue) {
                    stringState.value = newValue.name
                }

            override fun component1(): T = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
