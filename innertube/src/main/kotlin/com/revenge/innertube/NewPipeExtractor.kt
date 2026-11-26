package com.revenge.innertube

import com.revenge.innertube.models.IpVersion
import com.revenge.innertube.models.YouTubeClient
import com.revenge.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import java.io.IOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Bridges NewPipe's downloader contract onto the same proxy, IP and cookie settings as [YouTube]. */
class NewPipeDownloaderImpl(
  proxy: Proxy?,
  proxyAuth: String? = null,
) : Downloader() {
  private val client =
    OkHttpClient.Builder()
      .dns(
        object : Dns {
          override fun lookup(hostname: String): List<InetAddress> {
            val addresses = Dns.SYSTEM.lookup(hostname)
            return when (YouTube.ipVersion) {
              IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
              IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
              IpVersion.AUTO -> addresses
            }
          }
        },
      )
      .proxySelector(
        object : ProxySelector() {
          override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)

          override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
        },
      )
      .proxyAuthenticator { _, response ->
        YouTube.proxyAuth?.let { auth ->
          response.request.newBuilder().header("Proxy-Authorization", auth).build()
        } ?: response.request
      }
      .build()

  @Throws(IOException::class, ReCaptchaException::class)
  override fun execute(request: Request): Response {
    val method = request.httpMethod()
    val data = request.dataToSend()
    val builder =
      okhttp3.Request.Builder()
        .method(method, data?.toRequestBody())
        .url(request.url())
        .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)
        .also { b -> YouTube.cookie?.let { b.addHeader("Cookie", it) } }

    request.headers().forEach { (name, values) ->
      when {
        values.size > 1 -> {
          builder.removeHeader(name)
          values.forEach { builder.addHeader(name, it) }
        }
        values.size == 1 -> builder.header(name, values[0])
      }
    }

    return client.newCall(builder.build()).execute().use { response ->
      if (response.code == 429) {
        throw ReCaptchaException("reCaptcha Challenge requested", request.url())
      }
      Response(
        response.code,
        response.message,
        response.headers.toMultimap(),
        response.body?.string().orEmpty(),
        response.request.url.toString(),
      )
    }
  }

}

/** Holds the shared NewPipe instance and the signature/throttling helpers. */
class NewPipeUtils(downloader: Downloader) {
  init {
    NewPipe.init(downloader)
  }

  fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
    YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
  }

  /** Deciphers a format's signature and removes the throttling parameter, if any. */
  fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? =
    try {
      val url =
        format.url
          ?: format.signatureCipher?.let { cipher ->
            val params = parseQueryString(cipher)
            val signature = params["s"] ?: throw ParsingException("Could not parse cipher signature")
            val signatureParam =
              params["sp"] ?: throw ParsingException("Could not parse cipher signature parameter")
            val builder =
              params["url"]?.let { URLBuilder(it) }
                ?: throw ParsingException("Could not parse cipher url")
            builder.parameters[signatureParam] =
              YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, signature)
            builder.toString()
          }
          ?: throw ParsingException("Could not find format url")

      YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
    } catch (e: Exception) {
      null
    }
}

object NewPipeExtractor {
  private var downloader: NewPipeDownloaderImpl? = null
  private var utils: NewPipeUtils? = null

  @Synchronized
  private fun init() {
    if (utils == null) {
      val created = NewPipeDownloaderImpl(proxy = YouTube.proxy, proxyAuth = YouTube.proxyAuth)
      downloader = created
      utils = NewPipeUtils(created)
    }
  }

  fun getSignatureTimestamp(videoId: String): Result<Int> {
    init()
    return utils?.getSignatureTimestamp(videoId)
      ?: Result.failure(IllegalStateException("NewPipeUtils not initialized"))
  }

  fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
    init()
    return utils?.getStreamUrl(format, videoId)
  }

  /** Every stream NewPipe can find for [videoId], as `itag to url` pairs. */
  fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
    init()
    return try {
      val info = StreamInfo.getInfo(NewPipe.getService(0), watchUrl(videoId))
      (info.audioStreams + info.videoStreams + info.videoOnlyStreams).mapNotNull { stream ->
        stream.itagItem?.id?.let { itag -> itag to stream.content }
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  fun getVideoUrlAndDuration(videoId: String): Pair<String, Long>? {
    init()
    return try {
      val info = StreamInfo.getInfo(NewPipe.getService(0), watchUrl(videoId))
      val videos = (info.videoStreams + info.videoOnlyStreams).distinctBy { it.content }
      val isMp4 = { stream: org.schabi.newpipe.extractor.stream.VideoStream ->
        val format = stream.format
        val name = format?.name.orEmpty()
        val mime = format?.mimeType.orEmpty()
        val suffix = format?.suffix.orEmpty()
        val content = stream.content.orEmpty()
        name.contains("mp4", ignoreCase = true) ||
          name.contains("mpeg_4", ignoreCase = true) ||
          mime.contains("mp4", ignoreCase = true) ||
          suffix.equals("mp4", ignoreCase = true) ||
          content.contains("mime=video%2Fmp4", ignoreCase = true) ||
          content.contains(".mp4", ignoreCase = true)
      }
      val chosen =
        videos.filter { isMp4(it) && it.height in 1080..1440 }.maxByOrNull { it.height }
          ?: videos.filter { it.height in 1080..1440 }.maxByOrNull { it.height }
          ?: videos.filter { isMp4(it) && it.height in 720..1079 }.maxByOrNull { it.height }
          ?: videos.filter { it.height in 720..1079 }.maxByOrNull { it.height }
          ?: videos.filter { isMp4(it) }.maxByOrNull { it.height }
          ?: videos.maxByOrNull { it.height }
      val url = chosen?.content
      if (!url.isNullOrBlank()) url to info.duration else null
    } catch (e: Exception) {
      null
    }
  }

  /** Muxed and video-only tracks, each paired with the best audio stream for video-only ones. */
  fun getVideoTracks(videoId: String): List<NewPipeVideoTrack> {
    init()
    return try {
      val info = StreamInfo.getInfo(NewPipe.getService(0), watchUrl(videoId))
      val bestAudio = info.audioStreams.maxByOrNull { it.averageBitrate }?.content
      buildList {
        for (video in info.videoStreams) {
          val url = video.content ?: continue
          val height = if (video.height > 0) video.height else 360
          add(NewPipeVideoTrack("${height}p", height, url, audioUrl = null, isMuxed = true))
        }
        for (video in info.videoOnlyStreams) {
          val url = video.content ?: continue
          val height = if (video.height > 0) video.height else 720
          add(NewPipeVideoTrack("${height}p", height, url, audioUrl = bestAudio, isMuxed = false))
        }
      }
    } catch (e: Exception) {
      emptyList()
    }
  }

  private fun watchUrl(videoId: String) = "https://www.youtube.com/watch?v=$videoId"
}

data class NewPipeVideoTrack(
  val label: String,
  val height: Int,
  val videoUrl: String,
  val audioUrl: String? = null,
  val isMuxed: Boolean = true,
)
