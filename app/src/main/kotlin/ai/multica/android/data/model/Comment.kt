package ai.multica.android.data.model

import kotlinx.serialization.Serializable

/**
 * Mirrors packages/core/types/comment.ts::Comment.
 */
@Serializable
data class Comment(
    val id: String,
    @kotlinx.serialization.SerialName("issue_id") val issueId: String,
    @kotlinx.serialization.SerialName("author_type") val authorType: CommentAuthorType,
    @kotlinx.serialization.SerialName("author_id") val authorId: String,
    val content: String,
    val type: CommentType,
    @kotlinx.serialization.SerialName("parent_id") val parentId: String? = null,
    val reactions: List<Reaction> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.SerialName("resolved_at") val resolvedAt: String? = null,
    @kotlinx.serialization.SerialName("resolved_by_type") val resolvedByType: CommentAuthorType? = null,
    @kotlinx.serialization.SerialName("resolved_by_id") val resolvedById: String? = null,
    @kotlinx.serialization.SerialName("source_task_id") val sourceTaskId: String? = null,
)

@Serializable
data class Reaction(
    val id: String,
    @kotlinx.serialization.SerialName("comment_id") val commentId: String,
    @kotlinx.serialization.SerialName("actor_type") val actorType: String,
    @kotlinx.serialization.SerialName("actor_id") val actorId: String,
    val emoji: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
)

@Serializable
data class Attachment(
    val id: String,
    val filename: String,
    @kotlinx.serialization.SerialName("content_type") val contentType: String,
    val size: Long,
    val url: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
)
