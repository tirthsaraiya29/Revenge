package com.revenge.music.listentogether

import com.google.protobuf.ByteString
import com.revenge.music.listentogether.proto.Listentogether
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Wire framing for the Listen Together socket.
 *
 * A proto Envelope with JSON payload bytes is used for the binary format; the envelope's
 * `compressed` flag and GZIP handling match the transport contract. Text frames are plain JSON.
 *
 * shortcut: the binary payload carries the same JSON document as the text frame instead of the
 * generated per-message protobuf. Upgrade when a server requires the typed payload messages.
 */
class MessageCodec {

  @Volatile var useProtobuf: Boolean = false
  @Volatile var compressionEnabled: Boolean = false

  data class Decoded(val type: String, val payloadJson: String?)

  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun shouldUpgradeToProtobuf(frame: ByteArray): Boolean =
    frame.isNotEmpty() && frame[0] != '{'.code.toByte() && !isGzip(frame)

  fun isJsonFrame(frame: ByteArray): Boolean =
    frame.isEmpty() || frame[0] == '{'.code.toByte() || isGzip(frame)

  fun encode(type: String, payloadJson: String?): ByteArray {
    val jsonBytes =
      buildJsonObject {
          put("type", type)
          if (payloadJson != null) {
            val payload = runCatching { json.parseToJsonElement(payloadJson) }.getOrNull()
            put("payload", payload ?: JsonNull)
          } else {
            put("payload", JsonNull)
          }
        }
        .toString()
        .toByteArray(Charsets.UTF_8)
    if (!useProtobuf) {
      return if (compressionEnabled && jsonBytes.size > 100) gzip(jsonBytes) ?: jsonBytes else jsonBytes
    }
    var payload = payloadJson?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
    var compressed = false
    if (compressionEnabled && payload.size > 100) {
      val gzipped = gzip(payload)
      if (gzipped != null && gzipped.size < payload.size) {
        payload = gzipped
        compressed = true
      }
    }
    return Listentogether.Envelope.newBuilder()
      .setType(type)
      .setPayload(ByteString.copyFrom(payload))
      .setCompressed(compressed)
      .build()
      .toByteArray()
  }

  fun decode(frame: ByteArray): Decoded? {
    if (frame.isEmpty()) return Decoded("", null)
    if (isGzip(frame)) {
      val inflated = gunzip(frame)
      if (inflated != null) return decode(inflated)
    }
    if (frame[0] == '{'.code.toByte()) {
      val root =
        runCatching { json.parseToJsonElement(String(frame, Charsets.UTF_8)).jsonObject }
          .getOrNull()
          ?: return null
      return Decoded(
        type = root.stringOrNull("type").orEmpty(),
        payloadJson =
          (root["payload"] as? JsonObject)?.toString()
            ?: root["payload"]?.takeIf { it != JsonNull }?.toString(),
      )
    }
    val envelope =
      runCatching { Listentogether.Envelope.parseFrom(frame) }.getOrNull() ?: return null
    var payloadBytes = envelope.payload.toByteArray()
    if (envelope.compressed) {
      payloadBytes = gunzip(payloadBytes) ?: payloadBytes
    }
    val payload =
      if (payloadBytes.isEmpty()) null else String(payloadBytes, Charsets.UTF_8)
    return Decoded(envelope.type, payload?.takeIf { it.isNotBlank() })
  }

  private fun isGzip(frame: ByteArray): Boolean =
    frame.size >= 2 && frame[0] == 0x1f.toByte() && frame[1] == 0x8b.toByte()

  private fun gzip(bytes: ByteArray): ByteArray? =
    runCatching {
      ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
      }
    }.getOrNull()

  private fun gunzip(bytes: ByteArray): ByteArray? =
    runCatching {
      GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }
    }.getOrNull()

  private fun JsonObject.stringOrNull(key: String): String? =
    runCatching { (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content }.getOrNull()
}
