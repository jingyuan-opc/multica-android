package ai.multica.android.data.model

import kotlinx.serialization.Serializable

/**
 * Mirrors server/internal/handler/agent.go::AgentResponse.
 * Excludes runtime_config, mcp_config, env keys (sensitive).
 */
@Serializable
data class Agent(
    val id: String,
    @kotlinx.serialization.SerialName("workspace_id") val workspaceId: String,
    @kotlinx.serialization.SerialName("runtime_id") val runtimeId: String = "",
    val name: String,
    val description: String = "",
    val instructions: String = "",
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
    @kotlinx.serialization.SerialName("runtime_mode") val runtimeMode: String = "managed",
    val status: String = "active",
    @kotlinx.serialization.SerialName("max_concurrent_tasks") val maxConcurrentTasks: Int = 1,
    val model: String = "",
    @kotlinx.serialization.SerialName("thinking_level") val thinkingLevel: String = "",
    @kotlinx.serialization.SerialName("owner_id") val ownerId: String? = null,
    @kotlinx.serialization.SerialName("has_custom_env") val hasCustomEnv: Boolean = false,
    @kotlinx.serialization.SerialName("custom_env_key_count") val customEnvKeyCount: Int = 0,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String = "",
    @kotlinx.serialization.SerialName("archived_at") val archivedAt: String? = null,
    @kotlinx.serialization.SerialName("archived_by") val archivedBy: String? = null,
)

@Serializable
data class AgentSkillSummary(
    val id: String,
    val name: String = "",
)
