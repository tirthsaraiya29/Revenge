package com.revenge.canvas

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arrayValue(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonObject.textValue(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
