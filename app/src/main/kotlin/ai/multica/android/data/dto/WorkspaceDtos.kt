package ai.multica.android.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Workspace request DTOs. Mirror packages/core/types/workspace.ts. */

@Serializable
data class CreateWorkspaceRequest(
    val name: String,
    val slug: String,
    val description: String? = null,
    val context: String? = null,
)

@Serializable
data class UpdateWorkspaceRequest(
    val name: String? = null,
    val description: String? = null,
    val context: String? = null,
    val settings: JsonElement? = null,
    @SerialName("issue_prefix") val issuePrefix: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
