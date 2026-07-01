package ai.multica.android.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Squad request DTOs. Mirror packages/core/types/squad.ts. */

@Serializable
data class CreateSquadRequest(
    val name: String,
    val description: String? = null,
    @SerialName("leader_id") val leaderId: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class UpdateSquadRequest(
    val name: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    @SerialName("leader_id") val leaderId: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class AddSquadMemberRequest(
    @SerialName("member_type") val memberType: String,
    @SerialName("member_id") val memberId: String,
    val role: String? = null,
)

/** DELETE /api/squads/{id}/members uses a JSON body (not path params). */
@Serializable
data class RemoveSquadMemberRequest(
    @SerialName("member_type") val memberType: String,
    @SerialName("member_id") val memberId: String,
)

@Serializable
data class UpdateSquadMemberRoleRequest(
    @SerialName("member_type") val memberType: String,
    @SerialName("member_id") val memberId: String,
    val role: String,
)
