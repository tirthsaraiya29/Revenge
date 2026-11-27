package com.revenge.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
  val clientName: String,
  val clientVersion: String,
  val clientId: String,
  val userAgent: String,
  val osName: String? = null,
  val osVersion: String? = null,
  val deviceMake: String? = null,
  val deviceModel: String? = null,
  val androidSdkVersion: String? = null,
  val buildId: String? = null,
  val cronetVersion: String? = null,
  val packageName: String? = null,
  val friendlyName: String? = null,
  val loginSupported: Boolean = false,
  val loginRequired: Boolean = false,
  val useSignatureTimestamp: Boolean = false,
  val isEmbedded: Boolean = false,
  val useWebPoTokens: Boolean = false,
) {
  fun toContext(
    locale: YouTubeLocale,
    visitorData: String?,
    dataSyncId: String?,
  ): Context =
    Context(
      client =
        Context.Client(
          clientName = clientName,
          clientVersion = clientVersion,
          osName = osName,
          osVersion = osVersion,
          deviceMake = deviceMake,
          deviceModel = deviceModel,
          androidSdkVersion = androidSdkVersion,
          gl = locale.gl,
          hl = locale.hl,
          visitorData = visitorData,
        ),
      user =
        Context.User(
          onBehalfOfUser = if (loginSupported) dataSyncId else null,
        ),
    )

  companion object {
    const val USER_AGENT_WEB =
      "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"
    const val ORIGIN_YOUTUBE_MUSIC = "https://music.youtube.com"
    const val REFERER_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/"
    const val API_URL_YOUTUBE_MUSIC = "$ORIGIN_YOUTUBE_MUSIC/youtubei/v1/"

    private const val USER_AGENT_TV =
      "Mozilla/5.0 (SMART-TV; LINUX; Tizen 6.5) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "76.0.3809.146/6.5 TV Safari/537.36"
    private const val USER_AGENT_EMBEDDED =
      "Mozilla/5.0 (PlayStation; PlayStation 5/2.26) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
        "Version/13.0 Safari/605.1.15"
    private const val USER_AGENT_IOS =
      "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2_22C152 like Mac OS X;)"
    private const val USER_AGENT_IPAD =
      "com.google.ios.youtube/21.03.3 (iPad16,6; U; CPU iPadOS 18_2 like Mac OS X;)"
    private const val USER_AGENT_ANDROID =
      "com.google.android.youtube/21.03.38 (Linux; U; Android 15; en_US; Pixel 7; " +
        "Build/AP3A.241105.008; Cronet/130.0.6723.58)"
    private const val USER_AGENT_ANDROID_CREATOR =
      "com.google.android.youtube/25.03.101 (Linux; U; Android 15; en_US; Pixel 9 Pro Fold; " +
        "Build/AP4A.250105.002; Cronet/132.0.6834.163)"
    private const val USER_AGENT_OCULUS =
      "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12L; en_US; " +
        "Quest 3; Build/SQ3A.220605.009.A1; Cronet/83.0.4103.83)"
    private const val USER_AGENT_EUREKA =
      "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; en_US; " +
        "Quest 3; Build/SQ3A.220605.009.A1; Cronet/92.0.4515.159)"
    private const val USER_AGENT_VISIONOS =
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) " +
        "Version/18.1 Safari/605.1.15"

    val WEB =
      YouTubeClient(
        clientName = "WEB",
        clientVersion = "2.20260213.00.00",
        clientId = "1",
        userAgent = USER_AGENT_WEB,
      )

    val WEB_REMIX =
      YouTubeClient(
        clientName = "WEB_REMIX",
        clientVersion = "1.20260213.01.00",
        clientId = "67",
        userAgent = USER_AGENT_WEB,
        loginSupported = true,
        useSignatureTimestamp = true,
        useWebPoTokens = true,
      )

    val WEB_CREATOR =
      YouTubeClient(
        clientName = "WEB_CREATOR",
        clientVersion = "1.20260213.00.00",
        clientId = "62",
        userAgent = USER_AGENT_WEB,
        loginSupported = true,
        loginRequired = true,
        useSignatureTimestamp = true,
        useWebPoTokens = true,
      )

    val TVHTML5 =
      YouTubeClient(
        clientName = "TVHTML5",
        clientVersion = "7.20260213.00.00",
        clientId = "7",
        userAgent = USER_AGENT_TV,
        loginSupported = true,
        loginRequired = true,
        useSignatureTimestamp = true,
        useWebPoTokens = true,
      )

    val TVHTML5_SIMPLY_EMBEDDED_PLAYER =
      YouTubeClient(
        clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
        clientVersion = "2.0",
        clientId = "85",
        userAgent = USER_AGENT_EMBEDDED,
        loginSupported = true,
        useSignatureTimestamp = true,
        isEmbedded = true,
      )

    val IOS =
      YouTubeClient(
        clientName = "IOS",
        clientVersion = "21.03.1",
        clientId = "5",
        userAgent = USER_AGENT_IOS,
        osName = "iPhone",
        osVersion = "18.2.22C152",
        deviceMake = "Apple",
        deviceModel = "iPhone16,2",
        packageName = "com.google.ios.youtube",
      )

    val MOBILE =
      YouTubeClient(
        clientName = "ANDROID",
        clientVersion = "21.03.38",
        clientId = "3",
        userAgent = USER_AGENT_ANDROID,
        osName = "Android",
        osVersion = "15",
        deviceMake = "Google",
        deviceModel = "Pixel 7",
        androidSdkVersion = "35",
        buildId = "AP3A.241105.008",
        loginSupported = true,
        useSignatureTimestamp = true,
      )

    val ANDROID_NO_SDK =
      YouTubeClient(
        clientName = "ANDROID",
        clientVersion = "21.03.38",
        clientId = "3",
        userAgent = USER_AGENT_ANDROID,
        osName = "Android",
        osVersion = "15",
        deviceMake = "Google",
        deviceModel = "Pixel 7",
        buildId = "AP3A.241105.008",
      )

    val ANDROID_VR_NO_AUTH =
      YouTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.61.48",
        clientId = "28",
        userAgent = USER_AGENT_OCULUS,
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = "32",
        buildId = "SQ3A.220605.009.A1",
      )

    val ANDROID_VR_1_61_48 =
      YouTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.61.48",
        clientId = "28",
        userAgent = USER_AGENT_OCULUS,
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = "32",
        buildId = "SQ3A.220605.009.A1",
        cronetVersion = "83.0.4103.83",
      )

    val ANDROID_VR_1_65_10 =
      YouTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.65.10",
        clientId = "28",
        userAgent = USER_AGENT_EUREKA,
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = "32",
        buildId = "SQ3A.220605.009.A1",
        cronetVersion = "92.0.4515.159",
        friendlyName = "Android VR 1.65",
      )

    val ANDROID_VR_1_43_32 =
      YouTubeClient(
        clientName = "ANDROID_VR",
        clientVersion = "1.43.32",
        clientId = "28",
        userAgent = USER_AGENT_OCULUS,
        osName = "Android",
        osVersion = "12L",
        deviceMake = "Oculus",
        deviceModel = "Quest 3",
        androidSdkVersion = "32",
        buildId = "SQ3A.220605.009.A1",
        cronetVersion = "107.0.5304.105",
      )

    val ANDROID_CREATOR =
      YouTubeClient(
        clientName = "ANDROID_CREATOR",
        clientVersion = "25.03.101",
        clientId = "14",
        userAgent = USER_AGENT_ANDROID_CREATOR,
        osName = "Android",
        osVersion = "15",
        deviceMake = "Google",
        deviceModel = "Pixel 9 Pro Fold",
        androidSdkVersion = "35",
        buildId = "AP4A.250105.002",
        loginSupported = true,
        useSignatureTimestamp = true,
      )

    val VISIONOS =
      YouTubeClient(
        clientName = "VISIONOS",
        clientVersion = "0.1",
        clientId = "101",
        userAgent = USER_AGENT_VISIONOS,
      )

    val IPADOS =
      YouTubeClient(
        clientName = "IOS",
        clientVersion = "21.03.3",
        clientId = "5",
        userAgent = USER_AGENT_IPAD,
        osName = "iPadOS",
        osVersion = "18.2",
        deviceMake = "Apple",
        deviceModel = "iPad16,6",
        packageName = "com.google.ios.youtube",
      )
  }
}
