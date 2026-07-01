package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.UpdateProjectRequest
import ai.multica.android.data.model.ListProjectsResponse
import ai.multica.android.data.model.Project
import ai.multica.android.data.model.ProjectLeadType
import ai.multica.android.data.model.ProjectPriority
import ai.multica.android.data.model.ProjectStatus
import ai.multica.android.data.model.SearchProjectsResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(
        status: ProjectStatus? = null,
        priority: ProjectPriority? = null,
    ): ApiResult<ListProjectsResponse> =
        apiCall(NetworkFactory.json) {
            api.listProjects(
                status = status?.name?.lowercase(),
                priority = priority?.name?.lowercase(),
            )
        }

    suspend fun get(id: String): ApiResult<Project> =
        apiCall(NetworkFactory.json) { api.getProject(id) }

    suspend fun create(
        title: String,
        description: String? = null,
        status: ProjectStatus = ProjectStatus.PLANNED,
        priority: ProjectPriority = ProjectPriority.NONE,
        resources: List<ai.multica.android.data.dto.CreateProjectResourceInput> = emptyList(),
    ): ApiResult<Project> = apiCall(NetworkFactory.json) {
        api.createProject(
            ai.multica.android.data.dto.CreateProjectRequest(
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                status = status.name.lowercase(),
                priority = priority.name.lowercase(),
                resources = resources,
            )
        )
    }

    suspend fun update(
        id: String,
        title: String? = null,
        description: String? = null,
        status: ProjectStatus? = null,
        priority: ProjectPriority? = null,
        leadType: ProjectLeadType? = null,
        leadId: String? = null,
    ): ApiResult<Project> = apiCall(NetworkFactory.json) {
        api.updateProject(
            id,
            UpdateProjectRequest(
                title = title?.trim(),
                description = description?.trim(),
                status = status?.name?.lowercase(),
                priority = priority?.name?.lowercase(),
                leadType = leadType?.name?.lowercase(),
                leadId = leadId,
            ),
        )
    }

    suspend fun delete(id: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.deleteProject(id) }

    suspend fun search(q: String, limit: Int = 20): ApiResult<SearchProjectsResponse> =
        apiCall(NetworkFactory.json) { api.searchProjects(q = q, limit = limit) }
}
