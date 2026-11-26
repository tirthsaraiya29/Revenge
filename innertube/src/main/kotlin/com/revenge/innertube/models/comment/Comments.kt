package com.revenge.innertube.models.comment

import com.revenge.innertube.models.Button
import com.revenge.innertube.models.NavigationEndpoint
import com.revenge.innertube.models.Runs
import com.revenge.innertube.models.Thumbnails
import kotlinx.serialization.Serializable

@Serializable
data class CommentThreadRenderer(
    val comment: Comment? = null,
    val commentViewModel: CommentViewModelWrapper? = null,
    val replies: Replies? = null,
) {
    @Serializable
    data class Comment(
        val commentRenderer: CommentRenderer? = null,
    )

    @Serializable
    data class CommentViewModelWrapper(
        val commentViewModel: CommentViewModel? = null,
    )

    @Serializable
    data class CommentViewModel(
        val commentId: String? = null,
        val commentKey: String? = null,
    )

    @Serializable
    data class Replies(
        val commentRepliesRenderer: CommentRepliesRenderer? = null,
    )

    @Serializable
    data class CommentRepliesRenderer(
        val contents: List<Content>? = null,
        val viewReplies: Button? = null,
        val hideReplies: Button? = null,
    )

    @Serializable
    data class Content(
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )
}

@Serializable
data class CommentRenderer(
    val authorText: Runs? = null,
    val authorThumbnail: Thumbnails? = null,
    val contentText: Runs? = null,
    val publishedTimeText: Runs? = null,
    val authorEndpoint: NavigationEndpoint? = null,
    val commentId: String? = null,
    val voteCount: Runs? = null,
    val voteStatus: String? = null,
    val replyCount: Int? = null,
)

@Serializable
data class ContinuationItemRenderer(
    val trigger: String? = null,
    val continuationEndpoint: ContinuationEndpoint? = null,
    val button: Button? = null,
) {
    @Serializable
    data class ContinuationEndpoint(
        val clickTrackingParams: String? = null,
        val continuationCommand: ContinuationCommand? = null,
    )

    @Serializable
    data class ContinuationCommand(
        val token: String? = null,
    )
}

@Serializable
data class ContinuationItem(
    val commentThreadRenderer: CommentThreadRenderer? = null,
    val continuationItemRenderer: ContinuationItemRenderer? = null,
    val commentRenderer: CommentRenderer? = null,
)

@Serializable
data class CommentResponse(
    val onResponseReceivedEndpoints: List<OnResponseReceivedEndpoint>? = null,
    val frameworkUpdates: FrameworkUpdates? = null,
) {
    @Serializable
    data class OnResponseReceivedEndpoint(
        val reloadContinuationItemsCommand: ReloadContinuationItemsCommand? = null,
        val appendContinuationItemsAction: AppendContinuationItemsAction? = null,
    )

    @Serializable
    data class ReloadContinuationItemsCommand(
        val continuationItems: List<ContinuationItem>? = null,
    )

    @Serializable
    data class AppendContinuationItemsAction(
        val continuationItems: List<ContinuationItem>? = null,
    )

    @Serializable
    data class FrameworkUpdates(
        val entityBatchUpdate: EntityBatchUpdate? = null,
    )

    @Serializable
    data class EntityBatchUpdate(
        val mutations: List<Mutation>? = null,
    )

    @Serializable
    data class Mutation(
        val payload: Payload? = null,
    )

    @Serializable
    data class Payload(
        val commentEntityPayload: CommentEntityPayload? = null,
        val engagementToolbarStateEntityPayload: EngagementToolbarStateEntityPayload? = null,
        val engagementToolbarSurfaceEntityPayload: EngagementToolbarSurfaceEntityPayload? = null,
    )

    @Serializable
    data class CommentEntityPayload(
        val key: String? = null,
        val properties: Properties? = null,
        val author: Author? = null,
        val toolbar: Toolbar? = null,
    ) {
        @Serializable
        data class Properties(
            val commentId: String? = null,
            val content: Content? = null,
            val publishedTime: String? = null,
            val toolbarStateKey: String? = null,
            val toolbarSurfaceKey: String? = null,
        ) {
            @Serializable
            data class Content(
                val content: String? = null,
            )
        }

        @Serializable
        data class Author(
            val displayName: String? = null,
            val avatarThumbnailUrl: String? = null,
        )

        @Serializable
        data class Toolbar(
            val likeCountNotliked: String? = null,
            val likeCountLiked: String? = null,
            val replyCount: Int? = null,
        )
    }

    @Serializable
    data class EngagementToolbarStateEntityPayload(
        val key: String? = null,
        val likeState: String? = null,
        val heartState: String? = null,
    )

    @Serializable
    data class EngagementToolbarSurfaceEntityPayload(
        val key: String? = null,
        val toolbar: Toolbar? = null,
    ) {
        @Serializable
        data class Toolbar(
            val likeCountNotliked: String? = null,
            val likeCountLiked: String? = null,
            val replyCount: Int? = null,
        )
    }
}
