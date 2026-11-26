package com.revenge.innertube.models.body

import com.revenge.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class AccountMenuBody(
    val context: Context,
    val deviceTheme: String = "DEVICE_THEME_SELECTED",
    val userInterfaceTheme: String = "USER_INTERFACE_THEME_DARK",
)

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?,
)

@Serializable
data class CreatePlaylistBody(
    val context: Context,
    val title: String,
    val privacyStatus: String = "PRIVATE",
    val videoIds: List<String>? = null,
) {
    object PrivacyStatus {
        const val PRIVATE = "PRIVATE"
        const val PUBLIC = "PUBLIC"
        const val UNLISTED = "UNLISTED"
    }
}

@Serializable
data class EditPlaylistBody(
    val context: Context,
    val playlistId: String,
    val actions: List<Action>,
) {
    @Serializable
    sealed class Action {
        @Serializable
        data class AddVideoAction(
            val action: String = "ACTION_ADD_VIDEO",
            val addedVideoId: String,
        ) : Action()

        @Serializable
        data class AddPlaylistAction(
            val action: String = "ACTION_ADD_PLAYLIST",
            val addedFullListId: String,
        ) : Action()

        @Serializable
        data class MoveVideoAction(
            val action: String = "ACTION_MOVE_VIDEO_BEFORE",
            val setVideoId: String,
            val movedSetVideoIdSuccessor: String?,
        ) : Action()

        @Serializable
        data class RemoveVideoAction(
            val action: String = "ACTION_REMOVE_VIDEO",
            val setVideoId: String,
            val removedVideoId: String,
        ) : Action()

        @Serializable
        data class RenamePlaylistAction(
            val action: String = "ACTION_SET_PLAYLIST_NAME",
            val playlistName: String,
        ) : Action()

        @Serializable
        data class SetCustomThumbnailAction(
            val action: String = "ACTION_SET_CUSTOM_THUMBNAIL",
            val addedCustomThumbnail: AddedCustomThumbnail,
        ) : Action()

        @Serializable
        data class RemoveCustomThumbnailAction(
            val action: String = "ACTION_REMOVE_CUSTOM_THUMBNAIL",
            val deletedCustomThumbnail: DeletedCustomThumbnail = DeletedCustomThumbnail(),
        ) : Action()

        @Serializable
        data class AddedCustomThumbnail(
            val imageKey: ImageKey = ImageKey("studio_square_thumbnail", "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL"),
            val playlistScottyEncryptedBlobId: String,
        )

        @Serializable
        data class DeletedCustomThumbnail(
            val name: String = "studio_square_thumbnail",
            val type: String = "PLAYLIST_IMAGE_TYPE_CUSTOM_THUMBNAIL",
        )

        @Serializable
        data class ImageKey(
            val name: String,
            val type: String,
        )
    }
}

typealias Action = EditPlaylistBody.Action

@Serializable
data class FeedbackBody(
    val context: Context,
    val feedbackTokens: List<String>,
    val isFeedbackTokenUnencrypted: Boolean = false,
    val shouldMerge: Boolean = false,
)

@Serializable
data class GetQueueBody(
    val context: Context,
    val videoIds: List<String>?,
    val playlistId: String?,
)

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)

@Serializable
data class LikeBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    sealed class Target {
        @Serializable
        data class VideoTarget(val videoId: String) : Target()

        @Serializable
        data class PlaylistTarget(val playlistId: String) : Target()
    }
}

@Serializable
data class NextBody(
    val context: Context,
    val videoId: String?,
    val playlistId: String?,
    val playlistSetVideoId: String?,
    val index: Int?,
    val params: String?,
    val continuation: String?,
)

@Serializable
data class PlayerBody(
    val context: Context,
    val videoId: String,
    val playlistId: String?,
    val playbackContext: PlaybackContext? = null,
    val serviceIntegrityDimensions: ServiceIntegrityDimensions? = null,
    val contentCheckOk: Boolean = true,
    val racyCheckOk: Boolean = true,
) {
    @Serializable
    data class PlaybackContext(
        val contentPlaybackContext: ContentPlaybackContext,
    )

    @Serializable
    data class ContentPlaybackContext(
        val signatureTimestamp: Int,
    )

    @Serializable
    data class ServiceIntegrityDimensions(
        val poToken: String,
    )
}

@Serializable
data class PlaylistDeleteBody(
    val context: Context,
    val playlistId: String,
)

@Serializable
data class SearchBody(
    val context: Context,
    val query: String?,
    val params: String?,
)

@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
