package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.UpdateMemberRequest
import ai.multica.android.data.model.Invitation
import ai.multica.android.data.model.MemberRole
import ai.multica.android.data.model.MemberWithUser
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemberRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(workspaceId: String): ApiResult<List<MemberWithUser>> =
        apiCall(NetworkFactory.json) { api.listMembers(workspaceId) }

    suspend fun updateRole(workspaceId: String, memberId: String, role: MemberRole): ApiResult<MemberWithUser> =
        apiCall(NetworkFactory.json) { api.updateMember(workspaceId, memberId, UpdateMemberRequest(role)) }

    suspend fun delete(workspaceId: String, memberId: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.deleteMember(workspaceId, memberId) }

    // --- Invitations (cross-workspace; no workspace header) ---

    suspend fun listMyInvitations(): ApiResult<List<Invitation>> =
        apiCall(NetworkFactory.json) { api.listMyInvitations() }

    suspend fun acceptInvitation(invitationId: String): ApiResult<MemberWithUser> =
        apiCall(NetworkFactory.json) { api.acceptInvitation(invitationId) }

    suspend fun declineInvitation(invitationId: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.declineInvitation(invitationId) }
}

