package ai.multica.android.data.repository

import ai.multica.android.core.auth.WorkspaceStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.model.Workspace
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the workspace list and the currently-active workspace.
 *
 * - On login, call [loadActive] to fetch the list, persist the first
 *   one (or the user's previously-selected one) into [WorkspaceStore]
 *   so AuthInterceptor starts sending the right `X-Workspace-Slug`.
 * - On workspace switch, call [setActive] which updates the store
 *   and triggers a re-fetch of workspace-scoped data.
 */
@Singleton
class WorkspaceRepository @Inject constructor(
    private val api: MulticaApi,
    private val workspaceStore: WorkspaceStore,
) {

    suspend fun list(): ApiResult<List<Workspace>> =
        apiCall(NetworkFactory.json) { api.listWorkspaces() }

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
