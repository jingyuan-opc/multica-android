package ai.multica.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Mirrors packages/core/types/inbox.ts::InboxItem
 * and server/internal/handler/inbox.go::InboxItemResponse.
 *
 * `details` is `json.RawMessage` on the Go side — we accept any JSON
 * shape. Per `packages/core/inbox/queries.ts`, the only documented key
 * is `comment_id`; treat the rest as opaque.
 *
 * `issue_status` is hydrated from the issues table on the server when
 * `issue_id` is set (see `enrichInboxResponse`). It's nullable.
 */
@Serializable
data class InboxItem(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    @kotlinx.serialization.SerialName("recipient_type") val recipientType: InboxRecipientType,
    @kotlinx.serialization.SerialName("recipient_id") val recipientId: String,
    val type: InboxItemType,
    val severity: InboxSeverity,
    @kotlinx.serialization.SerialName("issue_id") val issueId: String? = null,
    val title: String,
    val body: String? = null,
    @kotlinx.serialization.SerialName("issue_status") val issueStatus: IssueStatus? = null,
    val read: Boolean,
    val archived: Boolean,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("actor_type") val actorType: InboxActorType? = null,
    @kotlinx.serialization.SerialName("actor_id") val actorId: String? = null,
    val details: JsonElement? = null,
) {
    /** Convenience: read comment_id from details if present. */
    val commentId: String?
        get() = details?.let {
            (it as? JsonObject)?.get("comment_id")
                ?.let { v -> (v as? JsonPrimitive)?.contentOrNull }
        }
}

@Serializable
data class InboxUnreadCountResponse(
    val count: Long = 0,
)

@Serializable
data class InboxWorkspaceUnread(
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    val count: Long = 0,
)
