package ai.multica.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors packages/core/types/issue.ts::Issue
 * and server/internal/handler/issue.go::IssueResponse.
 */
@Serializable
data class Issue(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    val number: Long,
    val identifier: String,
    val title: String,
    val description: String? = null,
    val status: IssueStatus,
    val priority: IssuePriority,
    @kotlinx.serialization.SerialName("assignee_type") val assigneeType: IssueAssigneeType? = null,
    @kotlinx.serialization.SerialName("assignee_id") val assigneeId: String? = null,
    @kotlinx.serialization.SerialName("creator_type") val creatorType: IssueAssigneeType,
    @kotlinx.serialization.SerialName("creator_id") val creatorId: String,
    @kotlinx.serialization.SerialName("parent_issue_id") val parentIssueId: String? = null,
    @kotlinx.serialization.SerialName("project_id") val projectId: String? = null,
    val position: Double = 0.0,
    val stage: Int? = null,
    @kotlinx.serialization.SerialName("start_date") val startDate: String? = null,
    @kotlinx.serialization.SerialName("due_date") val dueDate: String? = null,
    val metadata: Map<String, JsonElement> = emptyMap(),
    val reactions: List<IssueReaction> = emptyList(),
    val labels: List<Label> = emptyList(),
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class IssueReaction(
    val id: String,
    @kotlinx.serialization.SerialName("issue_id") val issueId: String,
    @kotlinx.serialization.SerialName("actor_type") val actorType: String,
    @kotlinx.serialization.SerialName("actor_id") val actorId: String,
    val emoji: String,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
)

@Serializable
data class Label(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String = "",
    val name: String,
    /** Lowercase hex color, e.g. "#3b82f6". Default applied client-side. */
    val color: String? = "#3b82f6",
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class ListLabelsResponse(
    val labels: List<Label> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class IssueLabelsResponse(
    val labels: List<Label> = emptyList(),
)

@Serializable
data class IssueSubscriber(
    @kotlinx.serialization.SerialName("issue_id") val issueId: String,
    @kotlinx.serialization.SerialName("user_type") val userType: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    val reason: String = "",
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class ListIssuesResponse(
    val issues: List<Issue> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class SearchIssueResult(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    val number: Long,
    val identifier: String,
    val title: String,
    val description: String? = null,
    val status: IssueStatus,
    val priority: IssuePriority,
    @kotlinx.serialization.SerialName("assignee_type") val assigneeType: IssueAssigneeType? = null,
    @kotlinx.serialization.SerialName("assignee_id") val assigneeId: String? = null,
    @kotlinx.serialization.SerialName("creator_type") val creatorType: IssueAssigneeType,
    @kotlinx.serialization.SerialName("creator_id") val creatorId: String,
    @kotlinx.serialization.SerialName("parent_issue_id") val parentIssueId: String? = null,
    @kotlinx.serialization.SerialName("project_id") val projectId: String? = null,
    val position: Double = 0.0,
    val stage: Int? = null,
    @kotlinx.serialization.SerialName("start_date") val startDate: String? = null,
    @kotlinx.serialization.SerialName("due_date") val dueDate: String? = null,
    val metadata: Map<String, JsonElement> = emptyMap(),
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.SerialName("match_source") val matchSource: String,
    @kotlinx.serialization.SerialName("matched_snippet") val matchedSnippet: String? = null,
    @kotlinx.serialization.SerialName("matched_description_snippet") val matchedDescriptionSnippet: String? = null,
    @kotlinx.serialization.SerialName("matched_comment_snippet") val matchedCommentSnippet: String? = null,
)

@Serializable
data class SearchIssuesResponse(
    val issues: List<SearchIssueResult> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class IssueAssigneeGroup(
    val id: String,
    @kotlinx.serialization.SerialName("assignee_type") val assigneeType: IssueAssigneeType? = null,
    @kotlinx.serialization.SerialName("assignee_id") val assigneeId: String? = null,
    val issues: List<Issue> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class GroupedIssuesResponse(
    val groups: List<IssueAssigneeGroup> = emptyList(),
)

@Serializable
data class QuickCreateIssueResponse(
    @kotlinx.serialization.SerialName("task_id") val taskId: String,
)

/** GET /api/issues/{id}/children → { issues: Issue[] }. */
@Serializable
data class ChildIssuesResponse(
    val issues: List<Issue> = emptyList(),
)

@Serializable
data class ChildIssueProgressEntry(
    @kotlinx.serialization.SerialName("parent_issue_id") val parentIssueId: String,
    val total: Int = 0,
    val done: Int = 0,
)

/** GET /api/issues/child-progress → { progress: [...] }. */
@Serializable
data class ChildIssueProgressResponse(
    val progress: List<ChildIssueProgressEntry> = emptyList(),
)
