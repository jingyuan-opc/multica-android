package ai.multica.android.data.repository

import ai.multica.android.core.auth.WorkspaceStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.CreateWorkspaceRequest
import ai.multica.android.data.dto.UpdateWorkspaceRequest
import ai.multica.android.data.model.Workspace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the workspace list and the currently-active workspace.
 */
@Singleton
class WorkspaceRepository @Inject constructor(
    private val api: MulticaApi,
    private val workspaceStore: WorkspaceStore,
) {

    suspend fun list(): ApiResult<List<Workspace>> =
        apiCall(NetworkFactory.json) { api.listWorkspaces() }

    suspend fun get(id: String): ApiResult<Workspace> =
        apiCall(NetworkFactory.json) { api.getWorkspace(id) }

    suspend fun create(name: String, slug: String, description: String? = null): ApiResult<Workspace> =
        apiCall(NetworkFactory.json) {
            api.createWorkspace(CreateWorkspaceRequest(name = name.trim(), slug = slug.trim(), description = description))
        }

    suspend fun update(id: String, body: UpdateWorkspaceRequest): ApiResult<Workspace> =
        apiCall(NetworkFactory.json) { api.updateWorkspace(id, body) }

    /**
     * Choose a workspace as the active one. Idempotent: passing the
     * currently-active slug is a no-op.
     */
    fun setActive(workspace: Workspace) {
        workspaceStore.set(slug = workspace.slug, id = workspace.id)
    }

    fun getActiveSlug(): String? = workspaceStore.getSlug()

    fun getActiveId(): String? = workspaceStore.getId()

    fun clear() = workspaceStore.clear()
}
