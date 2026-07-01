package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.AddSquadMemberRequest
import ai.multica.android.data.dto.CreateSquadRequest
import ai.multica.android.data.dto.RemoveSquadMemberRequest
import ai.multica.android.data.dto.UpdateSquadMemberRoleRequest
import ai.multica.android.data.dto.UpdateSquadRequest
import ai.multica.android.data.model.Squad
import ai.multica.android.data.model.SquadMember
import ai.multica.android.data.model.SquadMemberStatusListResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SquadRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(): ApiResult<List<Squad>> =
        apiCall(NetworkFactory.json) { api.listSquads() }

    suspend fun get(id: String): ApiResult<Squad> =
        apiCall(NetworkFactory.json) { api.getSquad(id) }

    suspend fun create(name: String, leaderId: String, description: String? = null): ApiResult<Squad> =
        apiCall(NetworkFactory.json) {
            api.createSquad(CreateSquadRequest(name = name.trim(), leaderId = leaderId, description = description))
        }

    suspend fun update(id: String, body: UpdateSquadRequest): ApiResult<Squad> =
        apiCall(NetworkFactory.json) { api.updateSquad(id, body) }

    suspend fun delete(id: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.deleteSquad(id) }

    // --- Members ---

    suspend fun listMembers(id: String): ApiResult<List<SquadMember>> =
        apiCall(NetworkFactory.json) { api.listSquadMembers(id) }

    suspend fun addMember(squadId: String, memberType: String, memberId: String, role: String? = null): ApiResult<SquadMember> =
        apiCall(NetworkFactory.json) {
            api.addSquadMember(squadId, AddSquadMemberRequest(memberType = memberType, memberId = memberId, role = role))
        }

    suspend fun removeMember(squadId: String, memberType: String, memberId: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) {
            api.removeSquadMember(squadId, RemoveSquadMemberRequest(memberType = memberType, memberId = memberId))
        }

    suspend fun updateMemberRole(squadId: String, memberType: String, memberId: String, role: String): ApiResult<SquadMember> =
        apiCall(NetworkFactory.json) {
            api.updateSquadMemberRole(squadId, UpdateSquadMemberRoleRequest(memberType = memberType, memberId = memberId, role = role))
        }

    suspend fun memberStatus(squadId: String): ApiResult<SquadMemberStatusListResponse> =
        apiCall(NetworkFactory.json) { api.getSquadMemberStatus(squadId) }
}

