package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.model.MemberWithUser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-only access to workspace members. Issue create uses this to
 * populate the assignee picker.
 */
@Singleton
class MemberRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(workspaceId: String): ApiResult<List<MemberWithUser>> =
        apiCall(NetworkFactory.json) { api.listMembers(workspaceId) }
}
