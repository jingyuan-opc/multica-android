package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.model.ListProjectsResponse
import ai.multica.android.data.model.Project
import ai.multica.android.data.model.ProjectStatus
import ai.multica.android.data.model.ProjectPriority
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
    ): ApiResult<Project> = apiCall(NetworkFactory.json) {
        api.createProject(
            ai.multica.android.data.dto.CreateProjectRequest(
                title = title.trim(),
                description = description?.trim()?.takeIf { it.isNotBlank() },
                status = status.name.lowercase(),
                priority = priority.name.lowercase(),
            )
        )
    }
}
