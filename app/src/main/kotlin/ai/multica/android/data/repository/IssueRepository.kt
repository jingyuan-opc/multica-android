package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.CreateCommentRequest
import ai.multica.android.data.model.Comment
import ai.multica.android.data.model.GroupedIssuesResponse
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.IssueAssigneeType
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.ListIssuesResponse
import ai.multica.android.data.model.Reaction
import ai.multica.android.data.model.SearchIssuesResponse
import ai.multica.android.data.model.TimelineEntry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IssueRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(
        status: IssueStatus? = null,
        priority: IssuePriority? = null,
        projectId: String? = null,
        assigneeId: String? = null,
        limit: Int = 50,
        offset: Int = 0,
    ): ApiResult<ListIssuesResponse> = apiCall(NetworkFactory.json) {
        api.listIssues(
            status = status?.name?.lowercase(),
            priority = priority?.name?.lowercase(),
            projectId = projectId,
            assigneeId = assigneeId,
            limit = limit,
            offset = offset,
        )
    }

    suspend fun grouped(projectId: String? = null): ApiResult<GroupedIssuesResponse> =
        apiCall(NetworkFactory.json) { api.listGroupedIssues(projectId = projectId) }

    suspend fun get(id: String): ApiResult<Issue> =
        apiCall(NetworkFactory.json) { api.getIssue(id) }

    suspend fun update(id: String, body: ai.multica.android.data.dto.UpdateIssueRequest): ApiResult<Issue> =
        apiCall(NetworkFactory.json) { api.updateIssue(id, body) }

    suspend fun create(
        title: String,
        description: String? = null,
        status: IssueStatus = IssueStatus.TODO,
        priority: IssuePriority = IssuePriority.NONE,
        projectId: String? = null,
        assigneeType: IssueAssigneeType? = null,
        assigneeId: String? = null,
    ): ApiResult<Issue> = apiCall(NetworkFactory.json) {
        api.createIssue(
            ai.multica.android.data.dto.CreateIssueRequest(
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                status = status.name.lowercase(),
                priority = priority.name.lowercase(),
                projectId = projectId,
                assigneeType = assigneeType?.name?.lowercase(),
                assigneeId = assigneeId,
            )
        )
    }

    suspend fun search(q: String, limit: Int = 50): ApiResult<SearchIssuesResponse> =
        apiCall(NetworkFactory.json) { api.searchIssues(q = q, limit = limit) }

    // --- Timeline & comments ---

    suspend fun timeline(issueId: String): ApiResult<List<TimelineEntry>> =
        apiCall(NetworkFactory.json) { api.listTimeline(issueId) }

    suspend fun createComment(
        issueId: String,
        content: String,
        parentId: String? = null,
    ): ApiResult<Comment> = apiCall(NetworkFactory.json) {
        api.createComment(
            issueId,
            CreateCommentRequest(content = content, parentId = parentId),
        )
    }

    suspend fun addReaction(commentId: String, emoji: String): ApiResult<Reaction> =
        apiCall(NetworkFactory.json) { api.addCommentReaction(commentId, ReactionRequestShim(emoji)) }

    suspend fun removeReaction(commentId: String, emoji: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.removeCommentReaction(commentId, ReactionRequestShim(emoji)) }

    suspend fun resolveComment(commentId: String): ApiResult<Comment> =
        apiCall(NetworkFactory.json) { api.resolveComment(commentId) }

    suspend fun unresolveComment(commentId: String): ApiResult<Comment> =
        apiCall(NetworkFactory.json) { api.unresolveComment(commentId) }
}

// We need a type-aligned shim because the API expects a `ReactionRequest`.
// Using typealias here keeps the call site readable.
private typealias ReactionRequestShim = ai.multica.android.data.dto.ReactionRequest
