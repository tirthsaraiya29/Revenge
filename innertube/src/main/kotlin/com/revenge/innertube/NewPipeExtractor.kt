package com.revenge.innertube

import com.revenge.innertube.models.IpVersion
import com.revenge.innertube.models.YouTubeClient
import com.revenge.innertube.models.response.PlayerResponse
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NewPipeRequest
import org.schabi.newpipe.extractor.downloader.Response as NewPipeResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream

private const val WATCH_URL = "https://www.youtube.com/watch?v="

/** NewPipe transport backed by OkHttp. Proxy and IP-version choices are read per request. */
class NewPipeDownloaderImpl
@Suppress("UNUSED_PARAMETER")
constructor(
  proxy: Proxy?,
  proxyAuth: String? = null,
) : Downloader() {

  private val client: OkHttpClient =
    OkHttpClient.Builder()
      .dns(
        object : Dns {
          override fun lookup(hostname: String): List<InetAddress> {
            val resolved = Dns.SYSTEM.lookup(hostname)
            return when (YouTube.ipVersion) {
              IpVersion.IPV4 -> resolved.filterIsInstance<Inet4Address>().ifEmpty { resolved }
              IpVersion.IPV6 -> resolved.filterIsInstance<Inet6Address>().ifEmpty { resolved }
              IpVersion.AUTO -> resolved
            }
          }
        },
      )
      .proxySelector(
        object : ProxySelector() {
          override fun select(uri: URI): List<Proxy> = listOf(YouTube.proxy ?: Proxy.NO_PROXY)

          override fun connectFailed(uri: URI, sa: SocketAddress, ioe: IOException) = Unit
        },
      )
      .proxyAuthenticator { _, response ->
        val credentials = YouTube.proxyAuth
        if (credentials == null) null
        else response.request.newBuilder().header("Proxy-Authorization", credentials).build()
      }
      .build()

  override fun execute(request: NewPipeRequest): NewPipeResponse {
    val method = request.httpMethod().uppercase(Locale.ROOT)
    val payload = request.dataToSend()
    val body =
      if (method == "GET" || method == "HEAD" || payload == null) null
      else payload.toRequestBody(null)
    val builder = okhttp3.Request.Builder().url(request.url()).method(method, body)

    builder.header("User-Agent", YouTubeClient.USER_AGENT_WEB)
    YouTube.cookie?.let { builder.header("Cookie", it) }
    request.headers().forEach { (name, values) ->
      builder.removeHeader(name)
      values.forEach { value -> builder.addHeader(name, value) }
    }

    client.newCall(builder.build()).execute().use { response ->
      if (response.code == 429) {
        throw ReCaptchaException("reCaptcha Challenge requested", request.url())
      }
      return NewPipeResponse(
        response.code,
        response.message,
        response.headers.toMultimap(),
        response.body?.string().orEmpty(),
        response.request.url.toString(),
      )
    }
  }
}

class NewPipeUtils(downloader: Downloader) {

  init {
    NewPipe.init(downloader)
  }

  fun getSignatureTimestamp(videoId: String): Result<Int> =
    try {
      val timestamp: Int? = YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
      if (timestamp == null) {
        Result.failure(InnerTubeException.ParseError("Signature timestamp unavailable"))
      } else {
        Result.success(timestamp)
      }
    } catch (error: Exception) {
      Result.failure(error)
    }

  fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
    return try {
      val url = format.url ?: signatureCipherUrl(format.signatureCipher, videoId) ?: return null
      YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
    } catch (error: Exception) {
      null
    }
  }

  private fun signatureCipherUrl(cipher: String?, videoId: String): String? {
    if (cipher == null) return null
    val parameters = parseQuery(cipher)
    val signature = parameters["s"] ?: return null
    val parameter = parameters["sp"] ?: return null
    val url = parameters["url"] ?: return null
    val deobfuscated = YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature)
    val separator = if ('?' in url) '&' else '?'
    return "$url$separator$parameter=${URLEncoder.encode(deobfuscated, "UTF-8")}"
  }
}

private fun parseQuery(query: String): Map<String, String> =
  query
    .split('&')
    .mapNotNull { part ->
      val separator = part.indexOf('=')
      if (separator <= 0) {
        null
      } else {
        decode(part.substring(0, separator)) to decode(part.substring(separator + 1))
      }
    }
    .toMap()

private fun decode(value: String): String =
  try {
    URLDecoder.decode(value, "UTF-8")
  } catch (error: Exception) {
    value
  }

data class NewPipeVideoTrack(
  val label: String,
  val height: Int,
  val videoUrl: String,
  val audioUrl: String? = null,
  val isMuxed: Boolean = true,
)

/** Process-wide NewPipe bridge; the downloader/utility pair is built at most once. */
object NewPipeExtractor {

  private val lock = Any()

  @Volatile private var utility: NewPipeUtils? = null

  private fun utils(): NewPipeUtils =
    utility
      ?: synchronized(lock) {
        utility
          ?: NewPipeUtils(NewPipeDownloaderImpl(YouTube.proxy, YouTube.proxyAuth)).also {
            utility = it
          }
      }

  fun getSignatureTimestamp(videoId: String): Result<Int> = utils().getSignatureTimestamp(videoId)

  fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? =
    utils().getStreamUrl(format, videoId)

  fun newPipePlayer(videoId: String): List<Pair<Int, String>> =
    try {
      utils()
      val info = StreamInfo.getInfo(NewPipe.getService(0), WATCH_URL + videoId)
      val streams = info.videoStreams + info.audioStreams + info.videoOnlyStreams
      streams.mapNotNull { stream ->
        val item = stream.itagItem ?: return@mapNotNull null
        val content = stream.content ?: return@mapNotNull null
        item.id to content
      }
    } catch (error: Exception) {
      emptyList()
    }

  fun getVideoUrlAndDuration(videoId: String): Pair<String, Long>? =
    try {
      utils()
      val info = StreamInfo.getInfo(NewPipe.getService(0), WATCH_URL + videoId)
      val streams = (info.videoStreams + info.videoOnlyStreams).distinctBy { it.content }
      val chosen = chooseVideoStream(streams)
      val url = chosen?.content
      if (url.isNullOrBlank()) null else url to info.duration
    } catch (error: Exception) {
      null
    }

  fun getVideoTracks(videoId: String): List<NewPipeVideoTrack> =
    try {
      utils()
      val info = StreamInfo.getInfo(NewPipe.getService(0), WATCH_URL + videoId)
      val bestAudio =
        info.audioStreams
          .filter { !it.content.isNullOrBlank() }
          .maxByOrNull { it.averageBitrate }
          ?.content

      val muxed =
        info.videoStreams.mapNotNull { stream ->
          val url = stream.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
          val height = stream.height.takeIf { it > 0 } ?: 360
          NewPipeVideoTrack("${height}p", height, url, audioUrl = null, isMuxed = true)
        }
      val videoOnly =
        info.videoOnlyStreams.mapNotNull { stream ->
          val url = stream.content?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
          val height = stream.height.takeIf { it > 0 } ?: 720
          NewPipeVideoTrack("${height}p", height, url, audioUrl = bestAudio, isMuxed = false)
        }
      muxed + videoOnly
    } catch (error: Exception) {
      emptyList()
    }

  private fun chooseVideoStream(streams: List<VideoStream>): VideoStream? {
    val mp4 = streams.filter { it.format == MediaFormat.MPEG_4 }
    fun List<VideoStream>.range(low: Int, high: Int) = filter {
      it.height in low..high
    }
      .maxByOrNull { it.height }

    return mp4.range(1080, 1440)
      ?: streams.range(1080, 1440)
      ?: mp4.range(720, 1079)
      ?: streams.range(720, 1079)
      ?: mp4.maxByOrNull { it.height }
      ?: streams.maxByOrNull { it.height }
  }
}
