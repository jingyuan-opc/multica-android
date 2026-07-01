package ai.multica.android.data.repository

import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import ai.multica.android.core.network.apiCall
import ai.multica.android.data.dto.CreateAutopilotRequest
import ai.multica.android.data.dto.CreateAutopilotTriggerRequest
import ai.multica.android.data.dto.UpdateAutopilotRequest
import ai.multica.android.data.model.Autopilot
import ai.multica.android.data.model.AutopilotAssigneeType
import ai.multica.android.data.model.AutopilotExecutionMode
import ai.multica.android.data.model.AutopilotRun
import ai.multica.android.data.model.AutopilotStatus
import ai.multica.android.data.model.AutopilotTrigger
import ai.multica.android.data.model.AutopilotTriggerKind
import ai.multica.android.data.model.GetAutopilotResponse
import ai.multica.android.data.model.ListAutopilotRunsResponse
import ai.multica.android.data.model.ListAutopilotsResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutopilotRepository @Inject constructor(
    private val api: MulticaApi,
) {
    suspend fun list(status: AutopilotStatus? = null): ApiResult<ListAutopilotsResponse> =
        apiCall(NetworkFactory.json) { api.listAutopilots(status?.name?.lowercase()) }

    suspend fun get(id: String): ApiResult<GetAutopilotResponse> =
        apiCall(NetworkFactory.json) { api.getAutopilot(id) }

    suspend fun create(
        title: String,
        assigneeId: String,
        assigneeType: AutopilotAssigneeType = AutopilotAssigneeType.AGENT,
        executionMode: AutopilotExecutionMode = AutopilotExecutionMode.CREATE_ISSUE,
        description: String? = null,
        projectId: String? = null,
    ): ApiResult<Autopilot> = apiCall(NetworkFactory.json) {
        api.createAutopilot(
            CreateAutopilotRequest(
                title = title.trim(),
                assigneeId = assigneeId,
                assigneeType = assigneeType,
                executionMode = executionMode,
                description = description,
                projectId = projectId,
            )
        )
    }

    suspend fun update(id: String, body: UpdateAutopilotRequest): ApiResult<Autopilot> =
        apiCall(NetworkFactory.json) { api.updateAutopilot(id, body) }

    suspend fun setStatus(id: String, status: AutopilotStatus): ApiResult<Autopilot> =
        apiCall(NetworkFactory.json) { api.updateAutopilot(id, UpdateAutopilotRequest(status = status)) }

    suspend fun delete(id: String): ApiResult<Unit> =
        apiCall(NetworkFactory.json) { api.deleteAutopilot(id) }

    suspend fun trigger(id: String): ApiResult<AutopilotRun> =
        apiCall(NetworkFactory.json) { api.triggerAutopilot(id) }

    suspend fun listRuns(id: String, limit: Int = 20): ApiResult<ListAutopilotRunsResponse> =
        apiCall(NetworkFactory.json) { api.listAutopilotRuns(id, limit = limit) }

    // --- Triggers ---

    suspend fun createTrigger(
        autopilotId: String,
        kind: AutopilotTriggerKind,
        cronExpression: String? = null,
        timezone: String? = null,
    ): ApiResult<AutopilotTrigger> = apiCall(NetworkFactory.json) {
        api.createAutopilotTrigger(
            autopilotId,
            CreateAutopilotTriggerRequest(kind = kind, cronExpression = cronExpression, timezone = timezone),
        )
    }
}
