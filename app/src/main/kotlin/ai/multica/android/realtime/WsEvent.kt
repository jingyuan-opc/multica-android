package ai.multica.android.realtime

import ai.multica.android.data.model.InboxItem
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Strongly-typed WebSocket event catalog. The Multica server sends
 * a `Message { type, payload, actor_id, actor_type }` envelope — we
 * parse it into one of these sealed variants.
 *
 * Subset scoped to the inbox/issue MVP per plan §6 (Tier 2).
 * Server auto-subscribes us to `workspace:{id}` and `user:{id}`
 * scopes on connect, so we don't need to send subscribe messages.
 */
sealed interface WsEvent {
    val actorId: String?
    val actorType: String?

    data class InboxNew(
        val item: InboxItem,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class InboxRead(
        val itemId: String,
        val recipientId: String,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class InboxArchived(
        val itemId: String,
        val issueId: String?,
        val recipientId: String,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class InboxBatchRead(
        val recipientId: String,
        val count: Long,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class InboxBatchArchived(
        val recipientId: String,
        val count: Long,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class CommentCreated(
        val issueId: String,
        val raw: JsonObject,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class CommentUpdated(
        val issueId: String,
        val raw: JsonObject,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class CommentDeleted(
        val issueId: String,
        val commentId: String,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class CommentResolved(
        val issueId: String,
        val raw: JsonObject,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class CommentUnresolved(
        val issueId: String,
        val raw: JsonObject,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class IssueUpdated(
        val issueId: String,
        val raw: JsonObject,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent

    data class AuthAck(val raw: JsonObject) : WsEvent {
        override val actorId: String? = null
        override val actorType: String? = null
    }

    data class Unknown(
        val type: String,
        val raw: JsonElement,
        override val actorId: String?,
        override val actorType: String?,
    ) : WsEvent
}
