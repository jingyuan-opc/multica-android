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
    @kotlinx.serialization.SerialName("member_preview") val memberPreview: List<SquadMemberPreview> = emptyList(),
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String = "",
    @kotlinx.serialization.SerialName("archived_at") val archivedAt: String? = null,
    @kotlinx.serialization.SerialName("archived_by") val archivedBy: String? = null,
)

@Serializable
data class SquadMemberPreview(
    @kotlinx.serialization.SerialName("member_type") val memberType: String,
    @kotlinx.serialization.SerialName("member_id") val memberId: String,
    val role: String = "",
)

@Serializable
data class SquadMember(
    val id: String,
    @kotlinx.serialization.SerialName("squad_id") val squadId: String,
    @kotlinx.serialization.SerialName("member_type") val memberType: String,
    @kotlinx.serialization.SerialName("member_id") val memberId: String,
    val role: String = "",
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class SquadActiveIssueBrief(
    @kotlinx.serialization.SerialName("issue_id") val issueId: String,
    val identifier: String,
    val title: String,
    @kotlinx.serialization.SerialName("issue_status") val issueStatus: String,
)

@Serializable
data class SquadMemberStatus(
    @kotlinx.serialization.SerialName("member_type") val memberType: String,
    @kotlinx.serialization.SerialName("member_id") val memberId: String,
    /** working/idle/offline/unstable/archived; null for human members. */
    val status: String? = null,
    @kotlinx.serialization.SerialName("active_issues") val activeIssues: List<SquadActiveIssueBrief> = emptyList(),
    @kotlinx.serialization.SerialName("last_active_at") val lastActiveAt: String? = null,
)

@Serializable
data class SquadMemberStatusListResponse(
    val members: List<SquadMemberStatus> = emptyList(),
)
