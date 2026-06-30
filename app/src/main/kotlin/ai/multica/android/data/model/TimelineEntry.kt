package ai.multica.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors packages/core/types/comment.ts + packages/core/issues/types.ts
 * for TimelineEntry. The full timeline is one endpoint
 * (GET /api/issues/{id}/timeline) that includes comments, activity
 * events, status changes, progress updates, and system messages.
 *
 * The `type` field discriminates the union — server returns one of
 * "comment" | "activity" | "status_change" | "progress_update" | "system".
 *
 * Comments are type="comment" with extra fields. Other entry types
 * differ in payload — use the [TimelineEntry] sealed shape below.
 */
@Serializable
data class TimelineEntry(
    /** Discriminator: "comment" | "activity" | "status_change" | "progress_update" | "system" */
    val type: String,
    val id: String,
    @kotlinx.serialization.SerialName("actor_type") val actorType: CommentAuthorType? = null,
    @kotlinx.serialization.SerialName("actor_id") val actorId: String? = null,
    val content: String? = null,
    @kotlinx.serialization.SerialName("parent_id") val parentId: String? = null,
    @kotlinx.serialization.SerialName("comment_type") val commentType: CommentType? = null,
    val reactions: List<Reaction> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    @kotlinx.serialization.SerialName("resolved_at") val resolvedAt: String? = null,
    @kotlinx.serialization.SerialName("resolved_by_type") val resolvedByType: CommentAuthorType? = null,
    @kotlinx.serialization.SerialName("resolved_by_id") val resolvedById: String? = null,
    @kotlinx.serialization.SerialName("source_task_id") val sourceTaskId: String? = null,
    val action: String? = null,
    val metadata: JsonElement? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String? = null,
)
