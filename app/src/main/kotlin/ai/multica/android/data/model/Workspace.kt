package ai.multica.android.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors packages/core/types/workspace.ts::Workspace
 * and server/internal/handler/workspace.go::WorkspaceResponse.
 *
 * Server emits JSON in snake_case (issue_prefix, avatar_url, created_at,
 * updated_at) — every field below is annotated with @SerialName so
 * kotlinx-serialization maps to the wire format correctly.
 */
@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val slug: String,
    val description: String? = null,
    val context: String? = null,
    val settings: JsonElement? = null,
    val repos: JsonElement? = null,
    @kotlinx.serialization.SerialName("issue_prefix") val issuePrefix: String,
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class Member(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    val role: MemberRole,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
)

@Serializable
data class MemberWithUser(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    @kotlinx.serialization.SerialName("user_id") val userId: String,
    val role: MemberRole,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    val name: String,
    val email: String,
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class Invitation(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    @kotlinx.serialization.SerialName("inviter_id") val inviterId: String,
    @kotlinx.serialization.SerialName("invitee_email") val inviteeEmail: String,
    @kotlinx.serialization.SerialName("invitee_user_id") val inviteeUserId: String? = null,
    val role: MemberRole,
    val status: InvitationStatus,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String,
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String,
    @kotlinx.serialization.SerialName("expires_at") val expiresAt: String,
    @kotlinx.serialization.SerialName("inviter_name") val inviterName: String? = null,
    @kotlinx.serialization.SerialName("inviter_email") val inviterEmail: String? = null,
    @kotlinx.serialization.SerialName("workspace_name") val workspaceName: String? = null,
)
