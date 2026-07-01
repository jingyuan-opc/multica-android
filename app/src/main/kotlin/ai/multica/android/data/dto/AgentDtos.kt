package ai.multica.android.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Agent request DTOs. Mirror packages/core/types/agent.ts.
 * `custom_env` is intentionally excluded from create/update here — it is
 * managed via a dedicated endpoint (PUT /api/agents/{id}/env); the server
 * rejects env keys inside the general update body.
 */

@Serializable
data class CreateAgentRequest(
    val name: String,
    val description: String? = null,
    val instructions: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("runtime_id") val runtimeId: String,
    @SerialName("runtime_config") val runtimeConfig: JsonElement? = null,
    @SerialName("custom_args") val customArgs: List<String> = emptyList(),
    val visibility: String = "workspace",
    @SerialName("max_concurrent_tasks") val maxConcurrentTasks: Int = 1,
    val model: String? = null,
    @SerialName("thinking_level") val thinkingLevel: String? = null,
)

@Serializable
data class UpdateAgentRequest(
    val name: String? = null,
    val description: String? = null,
    val instructions: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("runtime_id") val runtimeId: String? = null,
    @SerialName("runtime_config") val runtimeConfig: JsonElement? = null,
    @SerialName("custom_args") val customArgs: List<String>? = null,
    val visibility: String? = null,
    @SerialName("max_concurrent_tasks") val maxConcurrentTasks: Int? = null,
    val model: String? = null,
    @SerialName("thinking_level") val thinkingLevel: String? = null,
)
