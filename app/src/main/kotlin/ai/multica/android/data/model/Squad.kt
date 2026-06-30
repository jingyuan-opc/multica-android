package ai.multica.android.data.model

import kotlinx.serialization.Serializable

/**
 * Mirrors server/internal/handler/squad.go::SquadResponse.
 */
@Serializable
data class Squad(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    val name: String,
    val description: String = "",
    val instructions: String = "",
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
    @kotlinx.serialization.SerialName("leader_id") val leaderId: String = "",
    @kotlinx.serialization.SerialName("creator_id") val creatorId: String = "",
    @kotlinx.serialization.SerialName("member_count") val memberCount: Int = 0,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String = "",
    @kotlinx.serialization.SerialName("archived_at") val archivedAt: String? = null,
    @kotlinx.serialization.SerialName("archived_by") val archivedBy: String? = null,
)
