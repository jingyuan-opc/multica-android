package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.CreateAgentRequest
import ai.multica.android.data.dto.UpdateAgentRequest
import ai.multica.android.data.model.Agent
import ai.multica.android.data.model.CancelledCountResponse
import ai.multica.android.data.model.RuntimeDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AgentRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(includeArchived: Boolean = false): ApiResult<List<Agent>> =
        apiCall(NetworkFactory.json) { api.listAgents(includeArchived) }

    /** Read-only list of runtimes available to bind an agent to. */
    suspend fun listRuntimes(): ApiResult<List<RuntimeDevice>> =
        apiCall(NetworkFactory.json) { api.listRuntimes() }

    suspend fun get(id: String): ApiResult<Agent> =
        apiCall(NetworkFactory.json) { api.getAgent(id) }

    suspend fun create(
        name: String,
        runtimeId: String,
        description: String? = null,
        instructions: String? = null,
        model: String? = null,
        visibility: String = "workspace",
        maxConcurrentTasks: Int = 1,
    ): ApiResult<Agent> = apiCall(NetworkFactory.json) {
        api.createAgent(
            CreateAgentRequest(
                name = name.trim(),
                runtimeId = runtimeId,
                description = description?.trim()?.takeIf { it.isNotBlank() },
                instructions = instructions?.trim()?.takeIf { it.isNotBlank() },
                model = model,
                visibility = visibility,
                maxConcurrentTasks = maxConcurrentTasks,
            )
        )
    }

    suspend fun update(id: String, body: UpdateAgentRequest): ApiResult<Agent> =
        apiCall(NetworkFactory.json) { api.updateAgent(id, body) }

    suspend fun archive(id: String): ApiResult<Agent> =
        apiCall(NetworkFactory.json) { api.archiveAgent(id) }

    suspend fun restore(id: String): ApiResult<Agent> =
        apiCall(NetworkFactory.json) { api.restoreAgent(id) }

    suspend fun cancelTasks(id: String): ApiResult<CancelledCountResponse> =
        apiCall(NetworkFactory.json) { api.cancelAgentTasks(id) }
}

