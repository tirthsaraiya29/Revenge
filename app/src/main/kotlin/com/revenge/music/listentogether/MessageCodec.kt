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
 * Binary frames carry a typed per-message protobuf payload inside [Listentogether.Envelope].
 * Message types without a proto definition (chat) fall back to JSON framing even after the
 * protobuf upgrade, per the protocol invariant; that fallback is gzipped as a whole frame.
 * Text frames are plain JSON.
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

  /** Decodes a typed payload to its JSON representation, or null for unknown/malformed types. */
  fun decodePayload(type: String, payload: ByteArray): String? = PayloadMapper.decode(type, payload)

  fun encode(type: String, payloadJson: String?): ByteArray {
    if (useProtobuf) {
      val typed = payloadJson?.let { PayloadMapper.encode(type, it) }
      // payloadJson == null means a payload-less message (e.g. ping); still use an envelope.
      if (payloadJson == null || typed != null) {
        return encodeEnvelope(type, typed ?: ByteArray(0))
      }
    }
    // JSON framing, including the chat fallback after a protobuf upgrade.
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
    if (compressionEnabled && jsonBytes.size > 100) {
      val gzipped = gzip(jsonBytes)
      if (gzipped != null && gzipped.size < jsonBytes.size) return gzipped
    }
    return jsonBytes
  }

  private fun encodeEnvelope(type: String, serializedPayload: ByteArray): ByteArray {
    var payload = serializedPayload
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
    if (payloadBytes.isEmpty()) return Decoded(envelope.type, null)

    val typed = PayloadMapper.decode(envelope.type, payloadBytes)
    if (typed != null) return Decoded(envelope.type, typed)

    // Chat (and legacy clients) may still put a JSON document in the envelope payload.
    val text = String(payloadBytes, Charsets.UTF_8)
    val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: return null
    return Decoded(
      envelope.type,
      (parsed as? JsonObject)?.toString() ?: parsed.takeIf { it != JsonNull }?.toString(),
    )
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
