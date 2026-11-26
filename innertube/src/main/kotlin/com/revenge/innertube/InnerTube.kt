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
import com.revenge.innertube.models.body.EditPlaylistBody.Action
import com.revenge.innertube.models.body.FeedbackBody
import com.revenge.innertube.models.body.GetQueueBody
import com.revenge.innertube.models.body.GetSearchSuggestionsBody
import com.revenge.innertube.models.body.GetTranscriptBody
import com.revenge.innertube.models.body.LikeBody
import com.revenge.innertube.models.body.NextBody
import com.revenge.innertube.models.body.PlayerBody
import com.revenge.innertube.models.body.PlaylistDeleteBody
import com.revenge.innertube.models.body.SearchBody
import com.revenge.innertube.models.body.SubscribeBody
import com.revenge.innertube.models.response.NextResponse
import com.revenge.innertube.utils.parseCookieString
import com.revenge.innertube.utils.sha1
import com.revenge.metadata.cleaner.ApiText
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.onUpload
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.delay
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Sends InnerTube requests. This class only talks HTTP; decoding and interpreting the responses is
 * [YouTube]'s job.
 */
@OptIn(ExperimentalEncodingApi::class)
class InnerTube {
  var locale = YouTubeLocale(gl = Locale.getDefault().country, hl = Locale.getDefault().toLanguageTag())

  var visitorData: String? = null
  var dataSyncId: String? = null

  var cookie: String? = null
    set(value) {
      field = value
      cookieMap = if (value == null) emptyMap() else parseCookieString(value)
    }

  private var cookieMap = emptyMap<String, String>()

  var proxy: Proxy? = null
    set(value) {
      field = value
      httpClient.close()
      httpClient = buildClient()
    }

  var proxyAuth: String? = null

  var useLoginForBrowse: Boolean = false

  var ipVersion: IpVersion = IpVersion.AUTO

  // Declared after locale/proxy so buildClient() observes the initialised values.
  private var httpClient: HttpClient = buildClient()

  @OptIn(ExperimentalSerializationApi::class)
  private fun buildClient(): HttpClient =
    HttpClient(OkHttp) {
      expectSuccess = true

      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
          },
        )
      }

      install(ContentEncoding) {
        gzip(0.9F)
        deflate(0.8F)
      }

      install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
      }

      engine {
        config {
          connectionPool(okhttp3.ConnectionPool(10, 5, TimeUnit.MINUTES))
          connectTimeout(30, TimeUnit.SECONDS)
          readTimeout(60, TimeUnit.SECONDS)
          writeTimeout(60, TimeUnit.SECONDS)
          protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
          retryOnConnectionFailure(true)
          cache(
            okhttp3.Cache(
              File(System.getProperty("java.io.tmpdir"), "revenge_http_cache"),
              50L * 1024L * 1024L,
            ),
          )
          this@InnerTube.proxy?.let { proxy(it) }
          this@InnerTube.proxyAuth?.let { auth ->
            proxyAuthenticator { _, response ->
              response.request.newBuilder().header("Proxy-Authorization", auth).build()
            }
          }
        }
      }

      defaultRequest {
        url(YouTubeClient.API_URL_YOUTUBE_MUSIC)
        header("Accept", "application/json")
        header("Accept-Language", "${locale.hl},${locale.gl};q=0.9,en;q=0.8")
        header("Cache-Control", "no-cache")
      }
    }

  private fun io.ktor.client.request.HttpRequestBuilder.attachClient(
    client: YouTubeClient,
    withLogin: Boolean = false,
  ) {
    contentType(ContentType.Application.Json)
    headers {
      append("X-Goog-Api-Format-Version", "1")
      append("X-YouTube-Client-Name", client.clientId)
      append("X-YouTube-Client-Version", client.clientVersion)
      append("X-Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
      append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
      // The stream clients that still mint direct URLs need the visitor id even when they do not
      // accept a login; only the account-scoped dataSyncId is gated on login support below.
      visitorData?.let { append("X-Goog-Visitor-Id", it) }
      if (withLogin && client.loginSupported) {
        cookie?.let { headerValue ->
          append("cookie", headerValue)
          val sapisid = cookieMap["SAPISID"]
          if (sapisid != null) {
            val now = System.currentTimeMillis() / 1000
            val hash = sha1("$now $sapisid ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
            append("Authorization", "SAPISIDHASH ${now}_$hash")
          }
        }
      }
    }
    header("User-Agent", client.userAgent)
    parameter("prettyPrint", false)
  }

  /** Retries only transient socket failures, backing off; a cancelled coroutine aborts the delay. */
  private suspend fun <T> withRetry(block: suspend () -> T): T {
    var wait = 500L
    repeat(3) { attempt ->
      try {
        return block()
      } catch (e: IOException) {
        if (attempt == 2) throw e
        delay(wait)
        wait *= 2
      }
    }
    error("unreachable")
  }

  suspend fun search(
    client: YouTubeClient,
    query: String? = null,
    params: String? = null,
    continuation: String? = null,
    setLogin: Boolean? = null,
  ) = withRetry {
    val login = (setLogin ?: useLoginForBrowse) && !cookie.isNullOrEmpty()
    httpClient.post("search") {
      attachClient(client, withLogin = login)
      setBody(
        SearchBody(
          context = client.toContext(locale, visitorData, if (login) dataSyncId else null),
          query = query,
          params = params,
        ),
      )
      parameter("continuation", continuation)
      parameter("ctoken", continuation)
    }
  }

  suspend fun player(
    client: YouTubeClient,
    videoId: String,
    playlistId: String?,
    signatureTimestamp: Int?,
    poToken: String? = null,
  ) = withRetry {
    httpClient.post("player") {
      attachClient(client, withLogin = true)
      setBody(
        PlayerBody(
          context =
            client.toContext(locale, visitorData, dataSyncId).let { base ->
              if (client.isEmbedded) {
                base.copy(
                  thirdParty = Context.ThirdParty(embedUrl = "https://www.youtube.com/watch?v=$videoId"),
                )
              } else {
                base
              }
            },
          videoId = videoId,
          playlistId = playlistId,
          playbackContext =
            if (client.useSignatureTimestamp && signatureTimestamp != null) {
              PlayerBody.PlaybackContext(
                PlayerBody.PlaybackContext.ContentPlaybackContext(signatureTimestamp),
              )
            } else {
              null
            },
          serviceIntegrityDimensions =
            if (client.useWebPoTokens && poToken != null) {
              PlayerBody.ServiceIntegrityDimensions(poToken)
            } else {
              null
            },
        ),
      )
    }
  }

  suspend fun registerPlayback(
    url: String,
    cpn: String,
    playlistId: String?,
    client: YouTubeClient = YouTubeClient.WEB_REMIX,
  ) = withRetry {
    httpClient.get(url) {
      attachClient(client, withLogin = true)
      parameter("ver", "2")
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
  ) = withRetry {
    httpClient.get(url) {
      attachClient(client, withLogin = true)
      parameter("ver", "2")
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
  ) = withRetry {
    val login = (setLogin || useLoginForBrowse) && !cookie.isNullOrEmpty()
    httpClient.post("browse") {
      attachClient(client, withLogin = login)
      setBody(
        BrowseBody(
          context = client.toContext(locale, visitorData, if (login) dataSyncId else null),
          browseId = browseId,
          params = params,
          continuation = continuation,
        ),
      )
    }
  }

  suspend fun next(
    client: YouTubeClient,
    videoId: String?,
    playlistId: String?,
    playlistSetVideoId: String?,
    index: Int?,
    params: String?,
    continuation: String? = null,
  ) = withRetry {
    httpClient.post("next") {
      attachClient(client, withLogin = true)
      setBody(
        NextBody(
          context = client.toContext(locale, visitorData, dataSyncId),
          videoId = videoId,
          playlistId = playlistId,
          playlistSetVideoId = playlistSetVideoId,
          index = index,
          params = params,
          continuation = continuation,
        ),
      )
    }
  }

  suspend fun feedback(client: YouTubeClient, tokens: List<String>) = withRetry {
    httpClient.post("feedback") {
      attachClient(client, withLogin = true)
      setBody(FeedbackBody(context = client.toContext(locale, visitorData, dataSyncId), feedbackTokens = tokens))
    }
  }

  suspend fun getSearchSuggestions(client: YouTubeClient, input: String) = withRetry {
    httpClient.post("music/get_search_suggestions") {
      attachClient(client)
      setBody(GetSearchSuggestionsBody(client.toContext(locale, visitorData, null), input))
    }
  }

  suspend fun getQueue(
    client: YouTubeClient,
    videoIds: List<String>?,
    playlistId: String?,
  ) = withRetry {
    httpClient.post("music/get_queue") {
      attachClient(client)
      setBody(GetQueueBody(client.toContext(locale, visitorData, null), videoIds, playlistId))
    }
  }

  suspend fun getTranscript(client: YouTubeClient, videoId: String) = withRetry {
    httpClient.post("https://music.youtube.com/youtubei/v1/get_transcript") {
      parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
      headers { append("Content-Type", "application/json") }
      setBody(
        GetTranscriptBody(
          context = client.toContext(locale, null, null),
          params = Base64.Default.encode("\n${11.toChar()}$videoId".encodeToByteArray()),
        ),
      )
    }
  }

  suspend fun getSwJsData() = withRetry { httpClient.get("https://music.youtube.com/sw.js_data") }

  suspend fun accountMenu(client: YouTubeClient) = withRetry {
    httpClient.post("account/account_menu") {
      attachClient(client, withLogin = true)
      setBody(AccountMenuBody(client.toContext(locale, visitorData, dataSyncId)))
    }
  }

  suspend fun likeVideo(client: YouTubeClient, videoId: String) = withRetry {
    postLike(client, "like/like", LikeBody.Target.VideoTarget(videoId))
  }

  suspend fun unlikeVideo(client: YouTubeClient, videoId: String) = withRetry {
    postLike(client, "like/removelike", LikeBody.Target.VideoTarget(videoId))
  }

  suspend fun likePlaylist(client: YouTubeClient, playlistId: String) = withRetry {
    postLike(client, "like/like", LikeBody.Target.PlaylistTarget(playlistId))
  }

  suspend fun unlikePlaylist(client: YouTubeClient, playlistId: String) = withRetry {
    postLike(client, "like/removelike", LikeBody.Target.PlaylistTarget(playlistId))
  }

  private suspend fun postLike(
    client: YouTubeClient,
    path: String,
    target: LikeBody.Target,
  ): HttpResponse =
    httpClient.post(path) {
      attachClient(client, withLogin = true)
      setBody(LikeBody(context = client.toContext(locale, visitorData, dataSyncId), target = target))
    }

  suspend fun subscribeChannel(
    client: YouTubeClient,
    channelId: String,
    params: String? = null,
  ) = withRetry {
    httpClient.post("subscription/subscribe") {
      attachClient(client, withLogin = true)
      setBody(SubscribeBody(channelIds = listOf(channelId), context = client.toContext(locale, visitorData, dataSyncId)))
    }
  }

  suspend fun unsubscribeChannel(
    client: YouTubeClient,
    channelId: String,
    params: String? = null,
  ) = withRetry {
    httpClient.post("subscription/unsubscribe") {
      attachClient(client, withLogin = true)
      setBody(SubscribeBody(channelIds = listOf(channelId), context = client.toContext(locale, visitorData, dataSyncId)))
    }
  }

  suspend fun addToPlaylist(client: YouTubeClient, playlistId: String, videoId: String) = withRetry {
    editPlaylist(client, playlistId.removePrefix("VL"), listOf(Action.AddVideoAction(addedVideoId = videoId)))
  }

  suspend fun addPlaylistToPlaylist(
    client: YouTubeClient,
    playlistId: String,
    addPlaylistId: String,
  ) = withRetry {
    editPlaylist(client, playlistId.removePrefix("VL"), listOf(Action.AddPlaylistAction(addedFullListId = addPlaylistId)))
  }

  suspend fun removeFromPlaylist(
    client: YouTubeClient,
    playlistId: String,
    videoId: String,
    setVideoId: String,
  ) = withRetry {
    editPlaylist(
      client,
      playlistId.removePrefix("VL"),
      listOf(Action.RemoveVideoAction(removedVideoId = videoId, setVideoId = setVideoId)),
    )
  }

  suspend fun moveSongPlaylist(
    client: YouTubeClient,
    playlistId: String,
    setVideoId: String,
    successorSetVideoId: String?,
  ) = withRetry {
    editPlaylist(
      client,
      playlistId,
      listOf(
        Action.MoveVideoAction(
          setVideoId = setVideoId,
          movedSetVideoIdSuccessor = successorSetVideoId,
        ),
      ),
    )
  }

  suspend fun createPlaylist(client: YouTubeClient, title: String) = withRetry {
    httpClient.post("playlist/create") {
      attachClient(client, withLogin = true)
      setBody(CreatePlaylistBody(client.toContext(locale, visitorData, dataSyncId), title))
    }
  }

  suspend fun renamePlaylist(client: YouTubeClient, playlistId: String, name: String) = withRetry {
    editPlaylist(client, playlistId, listOf(Action.RenamePlaylistAction(playlistName = name)))
  }

  suspend fun getUploadCustomThumbnailLink(client: YouTubeClient, contentLength: Int) = withRetry {
    httpClient.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
      attachClient(client, withLogin = true)
      headers {
        append("X-Goog-Upload-Command", "start")
        append("X-Goog-Upload-Protocol", "resumable")
        append("X-Goog-Upload-Header-Content-Length", contentLength.toString())
      }
    }
  }

  suspend fun uploadCustomThumbnail(
    client: YouTubeClient,
    uploadId: String,
    image: ByteArray,
  ) = withRetry {
    httpClient.post("https://music.youtube.com/playlist_image_upload/playlist_custom_thumbnail") {
      attachClient(client, withLogin = true)
      parameter("upload_id", uploadId)
      parameter("upload_protocol", "resumable")
      headers {
        append("X-Goog-Upload-Command", "upload, finalize")
        append("X-Goog-Upload-Offset", "0")
      }
      setBody(image)
    }
  }

  suspend fun setThumbnailPlaylist(
    client: YouTubeClient,
    playlistId: String,
    blobId: String,
  ) = withRetry {
    editPlaylist(
      client,
      playlistId,
      listOf(
        Action.SetCustomThumbnailAction(
          addedCustomThumbnail =
            Action.SetCustomThumbnailAction.AddedCustomThumbnail(playlistScottyEncryptedBlobId = blobId),
        ),
      ),
    )
  }

  suspend fun removeThumbnailPlaylist(client: YouTubeClient, playlistId: String) = withRetry {
    editPlaylist(client, playlistId, listOf(Action.RemoveCustomThumbnailAction()))
  }

  suspend fun deletePlaylist(client: YouTubeClient, playlistId: String) = withRetry {
    httpClient.post("playlist/delete") {
      attachClient(client, withLogin = true)
      setBody(PlaylistDeleteBody(client.toContext(locale, visitorData, dataSyncId), playlistId))
    }
  }

  private suspend fun editPlaylist(
    client: YouTubeClient,
    playlistId: String,
    actions: List<Action>,
  ): HttpResponse =
    httpClient.post("browse/edit_playlist") {
      attachClient(client, withLogin = true)
      setBody(
        EditPlaylistBody(
          context = client.toContext(locale, visitorData, dataSyncId),
          playlistId = playlistId,
          actions = actions,
        ),
      )
    }

  private suspend fun returnYouTubeDislike(videoId: String) = withRetry {
    httpClient.get("https://returnyoutubedislikeapi.com/Votes?videoId=$videoId") {
      contentType(ContentType.Application.Json)
    }
  }

  suspend fun initSongUpload(filename: String, contentLength: Long) = withRetry {
    val authUser = "0"
    httpClient.post("https://upload.youtube.com/upload/usermusic/http?authuser=$authUser") {
      withSapisidAuth {
        headers {
          append("X-Goog-Upload-Command", "start")
          append("X-Goog-Upload-Protocol", "resumable")
          append("X-Goog-Upload-Header-Content-Length", contentLength.toString())
          append("X-Goog-AuthUser", authUser)
          append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
        }
      }
      contentType(ContentType.Application.FormUrlEncoded)
      setBody("filename=$filename")
    }
  }

  suspend fun uploadSongData(
    uploadUrl: String,
    data: ByteArray,
    onProgress: ((Float) -> Unit)? = null,
  ) = withRetry {
    httpClient.post(uploadUrl) {
      withSapisidAuth {
        headers {
          append("X-Goog-Upload-Command", "upload, finalize")
          append("X-Goog-Upload-Offset", "0")
          append("X-Goog-AuthUser", "0")
          append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
        }
      }
      contentType(ContentType.Application.FormUrlEncoded)
      setBody(data)
      onUpload { sent, total -> total?.let { onProgress?.invoke(sent.toFloat() / it.toFloat()) } }
    }
  }

  suspend fun deletePrivatelyOwnedEntity(entityId: String) = withRetry {
    val context = YouTubeClient.WEB_REMIX.toContext(locale, visitorData, null)
    httpClient.post("https://music.youtube.com/youtubei/v1/music/delete_privately_owned_entity") {
      contentType(ContentType.Application.Json)
      withSapisidAuth {
        headers {
          append("Referer", YouTubeClient.REFERER_YOUTUBE_MUSIC)
          append("Origin", YouTubeClient.ORIGIN_YOUTUBE_MUSIC)
        }
      }
      parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
      parameter("prettyPrint", false)
      setBody("""{"context":${Json.encodeToString(context)},"entityId":"$entityId"}""")
    }
  }

  private fun io.ktor.client.request.HttpRequestBuilder.withSapisidAuth(block: () -> Unit) {
    block()
    cookie?.let { headerValue ->
      header("cookie", headerValue)
      val sapisid = cookieMap["SAPISID"] ?: return@let
      val now = System.currentTimeMillis() / 1000
      val hash = sha1("$now $sapisid ${YouTubeClient.ORIGIN_YOUTUBE_MUSIC}")
      header("Authorization", "SAPISIDHASH ${now}_$hash")
    }
  }

  suspend fun getMediaInfo(videoId: String): Result<MediaInfo> = runCatching {
    val response =
      next(client = YouTubeClient.WEB, videoId, null, null, null, null, null).body<NextResponse>()

    val secondary =
      response.contents.twoColumnWatchNextResults
        ?.results
        ?.results
        ?.content
        ?.find { it?.videoSecondaryInfoRenderer != null }
        ?.videoSecondaryInfoRenderer
    val primary =
      response.contents.twoColumnWatchNextResults
        ?.results
        ?.results
        ?.content
        ?.find { it?.videoPrimaryInfoRenderer != null }
        ?.videoPrimaryInfoRenderer
    val votes = returnYouTubeDislike(videoId).body<ReturnYouTubeDislikeResponse>()

    MediaInfo(
      videoId = videoId,
      title = primary?.title?.runs?.firstOrNull()?.text?.let(ApiText::clean),
      author = secondary?.owner?.videoOwnerRenderer?.title?.runs?.firstOrNull()?.text?.let(ApiText::clean),
      authorId = secondary?.owner?.videoOwnerRenderer?.navigationEndpoint?.browseEndpoint?.browseId,
      authorThumbnail =
        secondary?.owner?.videoOwnerRenderer?.thumbnail?.thumbnails
          ?.find { it.height == 48 }
          ?.url
          ?.replace("s48", "s960"),
      description = secondary?.attributedDescription?.content,
      subscribers =
        secondary?.owner?.videoOwnerRenderer?.subscriberCountText?.simpleText
          ?.split(" ")
          ?.firstOrNull(),
      uploadDate = primary?.dateText?.simpleText,
      viewCount = votes.viewCount,
      like = votes.likes,
      dislike = votes.dislikes,
    )
  }
}
