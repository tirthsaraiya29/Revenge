package com.revenge.music.utils

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.revenge.music.ai.AiLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.io.File

private enum class ProbeEnum { FIRST, SECOND }

@RunWith(RobolectricTestRunner::class)
class DataStoreHelpersTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context get() = RuntimeEnvironment.getApplication()

    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var store: DataStore<Preferences>

    @Before
    fun setUp() {
        store = PreferenceDataStoreFactory.create(scope = storeScope) {
            File(tempFolder.root, "helpers.preferences_pb")
        }
    }

    @After
    fun tearDown() {
        runBlocking { store.edit { it.clear() } }
    }

    @Test
    fun `missing keys resolve to null or the caller default`() {
        val key = stringPreferencesKey("absent")
        assertNull(store[key])
        assertEquals("fallback", store.get(key, "fallback"))
    }

    @Test
    fun `writes are visible through the blocking readers`() {
        val key = stringPreferencesKey("present")
        runBlocking { store.edit { it[key] = "value" } }
        assertEquals("value", store[key])
        assertEquals("value", store.get(key, "fallback"))
    }

    @Test
    fun `preference delegates re-read on every access`() {
        val key = stringPreferencesKey("delegated")
        val read: () -> String = {
            val current by preference(context, key, "fallback")
            current
        }
        assertEquals("fallback", read())

        runBlocking { context.dataStore.edit { it[key] = "first" } }
        assertEquals("first", read())

        runBlocking { context.dataStore.edit { it[key] = "second" } }
        assertEquals("second", read())
    }

    @Test
    fun `enum preference converts names case sensitively and falls back`() {
        val key = stringPreferencesKey("enum_value")
        val read: () -> ProbeEnum = {
            val current by enumPreference(context, key, ProbeEnum.FIRST)
            current
        }

        assertEquals(ProbeEnum.FIRST, read())
        runBlocking { context.dataStore.edit { it[key] = "SECOND" } }
        assertEquals(ProbeEnum.SECOND, read())
        runBlocking { context.dataStore.edit { it[key] = "second" } }
        assertEquals(ProbeEnum.FIRST, read())
    }

    @Test
    fun `the settings store is a process singleton per context`() {
        assertSame(context.dataStore, context.dataStore)
        assertSame(context.dataStore, context.applicationContext.dataStore)
    }

    @Test
    fun `connectivity observer reports false without a network and unregisters safely`() {
        val observer = NetworkConnectivityObserver(context)
        assertFalse(observer.isCurrentlyConnected())
        observer.unregister()
        observer.unregister()
    }

    @Test
    fun `playback log manager keeps the newest five hundred entries`() {
        PlaybackLogManager.clearLogs()
        assertTrue(PlaybackLogManager.logs.value.isEmpty())

        repeat(501) { index -> PlaybackLogManager.log(PlaybackLogLevel.INFO, index.toString()) }
        val logs = PlaybackLogManager.logs.value
        assertEquals(500, logs.size)
        assertEquals("1", logs.first().message)
        assertEquals("500", logs.last().message)

        PlaybackLogManager.clearLogs()
        assertTrue(PlaybackLogManager.logs.value.isEmpty())
    }

    @Test
    fun `ai logger appends bounded valid json and recovers from garbage`() {
        val key = stringPreferencesKey("ai_action_logs")
        runBlocking { AiLogger.log(context, "first", "one") }
        runBlocking { AiLogger.log(context, "second", "two") }

        var array = readArray(key)
        assertEquals(2, array.size)
        assertEquals("second", actionOf(array.last()))

        repeat(51) { index ->
            runBlocking { AiLogger.log(context, "bulk-$index", "detail") }
        }
        array = readArray(key)
        assertEquals(50, array.size)
        assertEquals("bulk-50", actionOf(array.last()))

        runBlocking { context.dataStore.edit { it[key] = "not json at all" } }
        runBlocking { AiLogger.log(context, "after-garbage", "detail") }
        array = readArray(key)
        assertEquals(1, array.size)
        assertEquals("after-garbage", actionOf(array.first()))
    }

    private fun readArray(key: Preferences.Key<String>): JsonArray {
        val raw = runBlocking { context.dataStore.data.first()[key] }
        return Json.parseToJsonElement(raw!!) as JsonArray
    }

    private fun actionOf(element: kotlinx.serialization.json.JsonElement): String? =
        ((element as? JsonObject)?.get("action") as? kotlinx.serialization.json.JsonPrimitive)?.content
}
