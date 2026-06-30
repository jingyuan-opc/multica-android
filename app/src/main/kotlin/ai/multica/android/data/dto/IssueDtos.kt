package ai.multica.android.data.dto

import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import kotlinx.serialization.Serializable

/**
 * Request body for POST /api/issues. Mirrors
 * server/internal/handler/issue.go::CreateIssueRequest.
 *
 * All field names carry @SerialName to align with the server's
 * snake_case wire format. (This was the bug behind the original
 * "server returned assignee_type: null" — the client was sending
 * `assigneeType`/`assigneeId` in camelCase and the server silently
 * dropped unknown fields. See plan §13 for the parity contract.)
 */
@Serializable
data class CreateIssueRequest(
    val title: String,
    val description: String? = null,
    val status: String = "todo",
    val priority: String = "none",
    @kotlinx.serialization.SerialName("assignee_type") val assigneeType: String? = null,
    @kotlinx.serialization.SerialName("assignee_id") val assigneeId: String? = null,
    @kotlinx.serialization.SerialName("parent_issue_id") val parentIssueId: String? = null,
    @kotlinx.serialization.SerialName("project_id") val projectId: String? = null,
    @kotlinx.serialization.SerialName("start_date") val startDate: String? = null,
    @kotlinx.serialization.SerialName("due_date") val dueDate: String? = null,
    @kotlinx.serialization.SerialName("attachment_ids") val attachmentIds: List<String>? = null,
)

@Serializable
data class UpdateIssueRequest(
    val title: String? = null,
    val description: String? = null,
    val status: String? = null,
    val priority: String? = null,
    @kotlinx.serialization.SerialName("assignee_type") val assigneeType: String? = null,
    @kotlinx.serialization.SerialName("assignee_id") val assigneeId: String? = null,
    @kotlinx.serialization.SerialName("parent_issue_id") val parentIssueId: String? = null,
    @kotlinx.serialization.SerialName("project_id") val projectId: String? = null,
    val position: Double? = null,
    @kotlinx.serialization.SerialName("start_date") val startDate: String? = null,
    @kotlinx.serialization.SerialName("due_date") val dueDate: String? = null,
    val stage: Int? = null,
)

/**
 * Request body for POST /api/issues/{issueId}/comments.
 */
@Serializable
data class CreateCommentRequest(
    val content: String,
    val type: String = "comment",
    @kotlinx.serialization.SerialName("parent_id") val parentId: String? = null,
    @kotlinx.serialization.SerialName("attachment_ids") val attachmentIds: List<String>? = null,
)

@Serializable
data class UpdateCommentRequest(
    val content: String,
    @kotlinx.serialization.SerialName("attachment_ids") val attachmentIds: List<String>? = null,
)

@Serializable
data class ReactionRequest(val emoji: String)
