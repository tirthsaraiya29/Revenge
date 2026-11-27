package com.revenge.innertube

import com.revenge.innertube.models.Context
import com.revenge.innertube.models.IpVersion
import com.revenge.innertube.models.MediaInfo
import com.revenge.innertube.models.ReturnYouTubeDislikeResponse
import com.revenge.innertube.models.YouTubeClient
import com.revenge.innertube.models.YouTubeLocale
import com.revenge.innertube.models.body.AccountMenuBody
import com.revenge.innertube.models.body.BrowseBody
import com.revenge.innertube.models.body.CreatePlaylistBody
import com.revenge.innertube.models.body.EditPlaylistBody
import com.revenge.innertube.models.body.FeedbackBody
import com.revenge.innertube.models.body.GetQueueBody
import com.revenge.innertube.models.body.GetSearchSuggestionsBody
import com.revenge.innertube.models.body.GetTranscriptBody
import com.revenge.innertube.models.body.LikeBody
import com.revenge.innertube.models.body.NextBody
import com.revenge.innertube.models.body.PlaylistDeleteBody
import com.revenge.innertube.models.body.SearchBody
import com.revenge.innertube.models.body.SubscribeBody
import com.revenge.innertube.models.response.NextResponse
import com.revenge.innertube.utils.parseCookieString
import com.revenge.innertube.utils.sha1
import com.revenge.metadata.cleaner.ApiText
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.content.ProgressListener
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cache

private const val TRANSCRIPT_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3"
private const val CACHE_BYTES = 50L * 1024L * 1024L
private const val MAX_ATTEMPTS = 3

/**
 * HTTP-only InnerTube transport. It owns request construction, headers, retries and the JSON
 * decoder; it never decodes page models.
 */
class InnerTube {

  var locale: YouTubeLocale =
    YouTubeLocale(
      gl = Locale.getDefault().country,
      hl = Locale.getDefault().toLanguageTag(),
    )

  var visitorData: String? = null
  var dataSyncId: String? = null
  var useLoginForBrowse: Boolean = false
  var ipVersion: IpVersion = IpVersion.AUTO

  var cookie: String? = null
    set(value) {
      field = value
      sapisid = value?.let { parseCookieString(it)["SAPISID"] }
    }

  var proxy: Proxy? = null
    set(value) {
      field = value
      val previous = http
      http = buildClient()
      previous.close()
    }

  /** Read when a client is built, i.e. at construction and on every [proxy] change. */
  var proxyAuth: String? = null

  private var sapisid: String? = null
  private var http: HttpClient = buildClient()

  private fun buildClient(): HttpClient {
    val currentProxy = proxy
    val currentAuth = proxyAuth
    return HttpClient(OkHttp) {
      expectSuccess = true
      install(ContentNegotiation) { json(innertubeJson) }
      install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
      }
      engine {
        config {
          cache(
            Cache(File(System.getProperty("java.io.tmpdir"), "revenge_http_cache"), CACHE_BYTES),
          )
          if (currentProxy != null) proxy(currentProxy)
          if (currentAuth != null) {
            proxyAuthenticator { _, response ->
              response.request.newBuilder().header("Proxy-Authorization", currentAuth).build()
            }
          }
        }
      }
    }
  }

  // ---- transport ------------------------------------------------------------------------

  private suspend fun execute(block: suspend () -> HttpResponse): HttpResponse {
    var attempt = 0
    while (true) {
      try {
        return block()
      } catch (error: IOException) {
        attempt++
        if (attempt >= MAX_ATTEMPTS) throw error
        delay(if (attempt == 1) 500L else 1000L)
      }
    }
  }

  private fun io.ktor.client.request.HttpRequestBuilder.baseHeaders() {
    header(HttpHeaders.Accept, ContentType.Application.Json.toString())
    header(HttpHeaders.AcceptLanguage, "${locale.hl},${locale.gl};q=0.9,en;q=0.8")
    header(HttpHeaders.CacheControl, "no-cache")
  }

  private fun io.ktor.client.request.HttpRequestBuilder.clientHeaders(
    client: YouTubeClient,
    login: Boolean,
  ) {
    header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    header("X-Goog-Api-Format-Version", "1")
    header("X-YouTube-Client-Name", client.clientId)
    header("X-YouTube-Client-Version", client.clientVersion)
    header("X-Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
    header("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
    header(HttpHeaders.UserAgent, client.userAgent)
    visitorData?.let { header("X-Goog-Visitor-Id", it) }
    if (login && client.loginSupported && !cookie.isNullOrEmpty()) {
      cookie?.let { header(HttpHeaders.Cookie, it) }
      val sid = sapisid
      if (sid != null) {
        val epoch = System.currentTimeMillis() / 1000L
        val digest = sha1("$epoch $sid ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
        header(HttpHeaders.Authorization, "SAPISIDHASH ${epoch}_$digest")
      }
    }
  }

  private fun contextFor(client: YouTubeClient, includeDataSync: Boolean): Context =
    client.toContext(locale, visitorData, if (includeDataSync) dataSyncId else null)

  /** Serialises the request context, optionally with an embedded-player thirdParty block. */
  private fun contextJson(client: YouTubeClient, embedUrl: String? = null): String {
    val encoded =
      innertubeJson.encodeToString(
        Context.serializer(),
        contextFor(client, includeDataSync = true),
      )
    if (embedUrl == null) return encoded
    return encoded.dropLast(1) + ",\"thirdParty\":{\"embedUrl\":" + JsonPrimitive(embedUrl) + "}}"
  }

  private suspend inline fun <reified T : Any> postJson(
    client: YouTubeClient,
    path: String,
    body: T,
    login: Boolean,
    extraParams: Map<String, String> = emptyMap(),
  ): HttpResponse = execute {
    http.post(YouTubeClient.API_URL_YOUTUBE_MUSIC + path) {
      baseHeaders()
      clientHeaders(client, login)
      parameter("prettyPrint", false)
      extraParams.forEach { (name, value) -> parameter(name, value) }
      setBody(body)
    }
  }

  // ---- endpoints ------------------------------------------------------------------------

  suspend fun search(
    client: YouTubeClient,
    query: String? = null,
    params: String? = null,
    continuation: String? = null,
    setLogin: Boolean? = null,
  ): HttpResponse {
    val login = (setLogin ?: useLoginForBrowse) && !cookie.isNullOrEmpty()
    val continuationParams =
      continuation?.let { mapOf("continuation" to it, "ctoken" to it) }.orEmpty()
    return postJson(
      client = client,
      path = "search",
      body = SearchBody(contextFor(client, login), query, params),
      login = login,
      extraParams = continuationParams,
    )
  }

  suspend fun player(
    client: YouTubeClient,
    videoId: String,
    playlistId: String?,
    signatureTimestamp: Int?,
    poToken: String? = null,
  ): HttpResponse {
    val embedUrl =
      if (client.isEmbedded) {
        "${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}/embed/$videoId"
      } else {
        null
      }
    val payload = buildString {
      append("{\"context\":").append(contextJson(client, embedUrl))
      append(",\"videoId\":").append(JsonPrimitive(videoId))
      if (playlistId != null) append(",\"playlistId\":").append(JsonPrimitive(playlistId))
      if (client.useSignatureTimestamp && signatureTimestamp != null) {
        append(",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":")
        append(signatureTimestamp)
        append("}}")
      }
      if (client.useWebPoTokens && poToken != null) {
        append(",\"serviceIntegrityDimensions\":{\"poToken\":")
        append(JsonPrimitive(poToken))
        append("}")
      }
      append(",\"contentCheckOk\":true,\"racyCheckOk\":true}")
    }
    return execute {
      http.post(YouTubeClient.API_URL_YOUTUBE_MUSIC + "player") {
        baseHeaders()
        clientHeaders(client, login = true)
        parameter("prettyPrint", false)
        setBody(TextContent(payload, ContentType.Application.Json))
      }
    }
  }

  suspend fun registerPlayback(
    url: String,
    cpn: String,
    playlistId: String?,
    client: YouTubeClient = YouTubeClient.WEB_REMIX,
  ): HttpResponse = execute {
    http.get(url) {
      baseHeaders()
      clientHeaders(client, login = true)
      parameter("ver", 2)
      parameter("c", client.clientName)
      parameter("cpn", cpn)
      if (playlistId != null) {
        parameter("list", playlistId)
        parameter("referrer", "https://music.youtube.com/playlist?list=$playlistId")
      }
    }
  }

  suspend fun reportWatchtime(
    url: String,
    cpn: String,
    startSeconds: Double,
    positionSeconds: Double,
    lengthSeconds: Double,
    elapsedSeconds: Double,
    state: String,
    client: YouTubeClient = YouTubeClient.WEB_REMIX,
  ): HttpResponse = execute {
    http.get(url) {
      baseHeaders()
      clientHeaders(client, login = true)
      parameter("ver", 2)
      parameter("c", client.clientName)
      parameter("cver", client.clientVersion)
      parameter("cpn", cpn)
      parameter("cmt", positionSeconds)
      parameter("st", startSeconds)
      parameter("et", positionSeconds)
      parameter("rt", elapsedSeconds)
      parameter("rtn", elapsedSeconds)
      parameter("len", lengthSeconds)
      parameter("state", state)
    }
  }

  suspend fun browse(
    client: YouTubeClient,
    browseId: String? = null,
    params: String? = null,
    continuation: String? = null,
    setLogin: Boolean = false,
  ): HttpResponse {
    val login = (setLogin || useLoginForBrowse) && !cookie.isNullOrEmpty()
    return postJson(
      client = client,
      path = "browse",
      body = BrowseBody(contextFor(client, login), browseId, params, continuation),
      login = login,
    )
  }

  suspend fun next(
    client: YouTubeClient,
    videoId: String?,
    playlistId: String?,
    playlistSetVideoId: String?,
    index: Int?,
    params: String?,
    continuation: String? = null,
  ): HttpResponse =
    postJson(
      client = client,
      path = "next",
      body =
        NextBody(
          contextFor(client, includeDataSync = true),
          videoId,
          playlistId,
          playlistSetVideoId,
          index,
          params,
          continuation,
        ),
      login = true,
    )

  suspend fun feedback(client: YouTubeClient, tokens: List<String>): HttpResponse =
    postJson(
      client = client,
      path = "feedback",
      body = FeedbackBody(contextFor(client, includeDataSync = true), tokens),
      login = true,
    )

  suspend fun getSearchSuggestions(client: YouTubeClient, input: String): HttpResponse =
    postJson(
      client = client,
      path = "music/get_search_suggestions",
      body = GetSearchSuggestionsBody(contextFor(client, includeDataSync = false), input),
      login = false,
    )

  suspend fun getQueue(
    client: YouTubeClient,
    videoIds: List<String>?,
    playlistId: String?,
  ): HttpResponse =
    postJson(
      client = client,
      path = "music/get_queue",
      body = GetQueueBody(contextFor(client, includeDataSync = false), videoIds, playlistId),
      login = false,
    )

  @OptIn(ExperimentalEncodingApi::class)
  suspend fun getTranscript(client: YouTubeClient, videoId: String): HttpResponse {
    val params = Base64.Default.encode("\n\u000B$videoId".toByteArray(Charsets.UTF_8))
    return execute {
      http.post("https://music.youtube.com/youtubei/v1/get_transcript") {
        baseHeaders()
        clientHeaders(client, login = false)
        parameter("key", TRANSCRIPT_KEY)
        parameter("prettyPrint", false)
        contentType(ContentType.Application.Json)
        setBody(GetTranscriptBody(contextFor(client, includeDataSync = false), params))
      }
    }
  }

  suspend fun getSwJsData(): HttpResponse = execute {
    http.get("https://music.youtube.com/sw.js_data") {
      baseHeaders()
    }
  }

  suspend fun accountMenu(client: YouTubeClient): HttpResponse =
    postJson(
      client = client,
      path = "account/account_menu",
      body = AccountMenuBody(contextFor(client, includeDataSync = true)),
      login = true,
    )

  suspend fun likeVideo(client: YouTubeClient, videoId: String): HttpResponse =
    postJson(
      client = client,
      path = "like/like",
      body =
        LikeBody(contextFor(client, includeDataSync = true), LikeBody.Target.VideoTarget(videoId)),
      login = true,
    )

  suspend fun unlikeVideo(client: YouTubeClient, videoId: String): HttpResponse =
    postJson(
      client = client,
      path = "like/removelike",
      body =
        LikeBody(contextFor(client, includeDataSync = true), LikeBody.Target.VideoTarget(videoId)),
      login = true,
    )

  suspend fun likePlaylist(client: YouTubeClient, playlistId: String): HttpResponse =
    postJson(
      client = client,
      path = "like/like",
      body =
        LikeBody(
          contextFor(client, includeDataSync = true),
          LikeBody.Target.PlaylistTarget(playlistId),
        ),
      login = true,
    )

  suspend fun unlikePlaylist(client: YouTubeClient, playlistId: String): HttpResponse =
    postJson(
      client = client,
      path = "like/removelike",
      body =
        LikeBody(
          contextFor(client, includeDataSync = true),
          LikeBody.Target.PlaylistTarget(playlistId),
        ),
      login = true,
    )

  suspend fun subscribeChannel(
    client: YouTubeClient,
    channelId: String,
    params: String? = null,
  ): HttpResponse =
    postJson(
      client,
      "subscription/subscribe",
      SubscribeBody(listOf(channelId), contextFor(client, includeDataSync = true)),
      login = true,
    )

  suspend fun unsubscribeChannel(
    client: YouTubeClient,
    channelId: String,
    params: String? = null,
  ): HttpResponse =
    postJson(
      client,
      "subscription/unsubscribe",
      SubscribeBody(listOf(channelId), contextFor(client, includeDataSync = true)),
      login = true,
    )

  private suspend fun editPlaylist(
    client: YouTubeClient,
    playlistId: String,
    action: EditPlaylistBody.Action,
  ): HttpResponse =
    postJson(
      client = client,
      path = "playlist/edit",
      body =
        EditPlaylistBody(contextFor(client, includeDataSync = true), playlistId, listOf(action)),
      login = true,
    )

  suspend fun addToPlaylist(
    client: YouTubeClient,
    playlistId: String,
    videoId: String,
  ): HttpResponse =
    editPlaylist(
      client,
      playlistId.removePrefix("VL"),
      EditPlaylistBody.Action.AddVideoAction(addedVideoId = videoId),
    )

  suspend fun addPlaylistToPlaylist(
    client: YouTubeClient,
    playlistId: String,
    addPlaylistId: String,
  ): HttpResponse =
    editPlaylist(
      client,
      playlistId.removePrefix("VL"),
      EditPlaylistBody.Action.AddPlaylistAction(addedFullListId = addPlaylistId),
    )

  suspend fun removeFromPlaylist(
    client: YouTubeClient,
    playlistId: String,
    videoId: String,
    setVideoId: String,
  ): HttpResponse =
    editPlaylist(
      client,
      playlistId.removePrefix("VL"),
      EditPlaylistBody.Action.RemoveVideoAction(setVideoId = setVideoId, removedVideoId = videoId),
    )

  suspend fun moveSongPlaylist(
    client: YouTubeClient,
    playlistId: String,
    setVideoId: String,
    successorSetVideoId: String?,
  ): HttpResponse =
    editPlaylist(
      client,
      playlistId,
      EditPlaylistBody.Action.MoveVideoAction(
        setVideoId = setVideoId,
        movedSetVideoIdSuccessor = successorSetVideoId,
      ),
    )

  suspend fun renamePlaylist(
    client: YouTubeClient,
    playlistId: String,
    name: String,
  ): HttpResponse =
    editPlaylist(
      client,
      playlistId,
      EditPlaylistBody.Action.RenamePlaylistAction(playlistName = name),
    )

  suspend fun createPlaylist(client: YouTubeClient, title: String): HttpResponse =
    postJson(
      client = client,
      path = "playlist/create",
      body = CreatePlaylistBody(contextFor(client, includeDataSync = true), title),
      login = true,
    )

  suspend fun deletePlaylist(client: YouTubeClient, playlistId: String): HttpResponse =
    postJson(
      client = client,
      path = "playlist/delete",
      body = PlaylistDeleteBody(contextFor(client, includeDataSync = true), playlistId),
      login = true,
    )

  suspend fun setThumbnailPlaylist(
    client: YouTubeClient,
    playlistId: String,
    blobId: String,
  ): HttpResponse =
    editPlaylist(
      client,
      playlistId,
      EditPlaylistBody.Action.SetCustomThumbnailAction(
        addedCustomThumbnail =
          EditPlaylistBody.Action.AddedCustomThumbnail(
            playlistScottyEncryptedBlobId = blobId,
          ),
      ),
    )

  suspend fun removeThumbnailPlaylist(client: YouTubeClient, playlistId: String): HttpResponse =
    editPlaylist(client, playlistId, EditPlaylistBody.Action.RemoveCustomThumbnailAction())

  // ---- media upload ---------------------------------------------------------------------

  suspend fun getUploadCustomThumbnailLink(
    client: YouTubeClient,
    contentLength: Int,
  ): HttpResponse = execute {
    http.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
      baseHeaders()
      clientHeaders(client, login = true)
      header("X-Goog-Upload-Command", "start")
      header("X-Goog-Upload-Protocol", "resumable")
      header("X-Goog-Upload-Header-Content-Length", contentLength.toString())
    }
  }

  suspend fun uploadCustomThumbnail(
    client: YouTubeClient,
    uploadId: String,
    image: ByteArray,
  ): HttpResponse = execute {
    http.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
      baseHeaders()
      clientHeaders(client, login = true)
      parameter("upload_id", uploadId)
      parameter("upload_protocol", "resumable")
      header("X-Goog-Upload-Command", "upload, finalize")
      header("X-Goog-Upload-Offset", "0")
      contentType(ContentType.Application.OctetStream)
      setBody(image)
    }
  }

  suspend fun initSongUpload(filename: String, contentLength: Long): HttpResponse = execute {
    http.post("https://upload.youtube.com/upload/usermusic/http?authuser=0") {
      baseHeaders()
      clientHeaders(YouTubeClient.WEB_REMIX, login = true)
      header("X-Goog-Upload-Command", "start")
      header("X-Goog-Upload-Protocol", "resumable")
      header("X-Goog-Upload-Header-Content-Length", contentLength.toString())
      header("X-Goog-AuthUser", "0")
      header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
      setBody(TextContent("filename=$filename", ContentType.Application.FormUrlEncoded))
    }
  }

  suspend fun uploadSongData(
    uploadUrl: String,
    data: ByteArray,
    onProgress: ((Float) -> Unit)? = null,
  ): HttpResponse {
    val progress = onProgress
    val totalSize = data.size.toLong()
    return execute {
      http.post(uploadUrl) {
        baseHeaders()
        clientHeaders(YouTubeClient.WEB_REMIX, login = true)
        header("X-Goog-Upload-Command", "upload, finalize")
        header("X-Goog-Upload-Offset", "0")
        header("X-Goog-AuthUser", "0")
        header("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
        contentType(ContentType.Application.OctetStream)
        setBody(data)
        onUpload(
          object : ProgressListener {
            override suspend fun onProgress(bytesSentTotal: Long, contentLength: Long?) {
              val denominator = contentLength ?: totalSize
              if (denominator > 0L) progress?.invoke(bytesSentTotal.toFloat() / denominator)
            }
          },
        )
      }
    }
  }

  suspend fun deletePrivatelyOwnedEntity(entityId: String): HttpResponse {
    val context = contextFor(YouTubeClient.WEB_REMIX, includeDataSync = true)
    val payload =
      "{\"context\":" +
        innertubeJson.encodeToString(Context.serializer(), context) +
        ",\"entityId\":" +
        JsonPrimitive(entityId) +
        "}"
    return execute {
      http.post("https://music.youtube.com/youtubei/v1/music/delete_privately_owned_entity") {
        baseHeaders()
        clientHeaders(YouTubeClient.WEB_REMIX, login = true)
        parameter("key", TRANSCRIPT_KEY)
        parameter("prettyPrint", false)
        setBody(TextContent(payload, ContentType.Application.Json))
      }
    }
  }

  // ---- media info -----------------------------------------------------------------------

  suspend fun getMediaInfo(videoId: String): Result<MediaInfo> =
    try {
      val next = next(YouTubeClient.WEB, videoId, null, null, null, null, null).body<NextResponse>()
      val contents = next.contents.twoColumnWatchNextResults?.results?.results?.contents.orEmpty()
      val primary = contents.firstNotNullOfOrNull { it?.videoPrimaryInfoRenderer }
      val secondary = contents.firstNotNullOfOrNull { it?.videoSecondaryInfoRenderer }
      val owner = secondary?.owner?.videoOwnerRenderer

      val dislikes = execute {
        http.get("https://returnyoutubedislikeapi.com/Votes") {
          baseHeaders()
          parameter("videoId", videoId)
        }
      }
        .body<ReturnYouTubeDislikeResponse>()

      Result.success(
        MediaInfo(
          videoId = videoId,
          title = primary?.title?.runs?.joinToString("") { it.text }?.let(ApiText::clean),
          author = owner?.title?.runs?.joinToString("") { it.text }?.let(ApiText::clean),
          authorId = owner?.navigationEndpoint?.browseEndpoint?.browseId,
          authorThumbnail =
            owner
              ?.thumbnail
              ?.thumbnails
              ?.firstOrNull { it.height == 48 }
              ?.url
              ?.replace("s48", "s960"),
          description = secondary?.attributedDescription?.content,
          uploadDate = primary?.dateText?.simpleText,
          subscribers =
            owner?.subscriberCountText?.simpleText?.trim()?.split(Regex("\\s+"))?.firstOrNull(),
          viewCount = dislikes.viewCount,
          like = dislikes.likes,
          dislike = dislikes.dislikes,
        ),
      )
    } catch (cancellation: CancellationException) {
      throw cancellation
    } catch (error: Throwable) {
      Result.failure(InnerTubeErrors.classify(error))
    }
}
