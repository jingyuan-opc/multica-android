package ai.multica.android.data.model

import kotlinx.serialization.Serializable

/**
 * Mirrors packages/core/types/project.ts::Project
 * and server/internal/handler/project.go::ProjectResponse.
 */
@Serializable
data class Project(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val status: ProjectStatus,
    val priority: ProjectPriority,
    @kotlinx.serialization.SerialName("lead_type") val leadType: ProjectLeadType? = null,
    @kotlinx.serialization.SerialName("lead_id") val leadId: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.SerialName("issue_count") val issueCount: Long = 0,
    @kotlinx.serialization.SerialName("done_count") val doneCount: Long = 0,
    @kotlinx.serialization.SerialName("resource_count") val resourceCount: Long = 0,
)

@Serializable
data class ListProjectsResponse(
    val projects: List<Project> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class SearchProjectResult(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String = "",
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val status: ProjectStatus = ProjectStatus.PLANNED,
    val priority: ProjectPriority = ProjectPriority.NONE,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
    @kotlinx.serialization.SerialName("match_source") val matchSource: String = "title",
    @kotlinx.serialization.SerialName("matched_snippet") val matchedSnippet: String? = null,
)

@Serializable
data class SearchProjectsResponse(
    val projects: List<SearchProjectResult> = emptyList(),
    val total: Long = 0,
)
