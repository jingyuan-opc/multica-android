package ai.multica.android.data.dto

import ai.multica.android.data.model.MemberRole
import kotlinx.serialization.Serializable

/** Member request DTOs. Mirror packages/core/types/api.ts. */

@Serializable
data class CreateMemberRequest(
    val email: String,
    val role: MemberRole = MemberRole.MEMBER,
)

@Serializable
data class UpdateMemberRequest(
    val role: MemberRole,
)
