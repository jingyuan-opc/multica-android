package ai.multica.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors packages/core/types/autopilot.ts::Autopilot
 * and server/internal/handler/autopilot.go::AutopilotResponse.
 */
@Serializable
data class Autopilot(
    val id: String,
    @SerialName("workspace_id") val workspaceId: String,
    val title: String,
    val description: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assignee_type") val assigneeType: AutopilotAssigneeType = AutopilotAssigneeType.AGENT,
    @SerialName("assignee_id") val assigneeId: String,
    val status: AutopilotStatus = AutopilotStatus.ACTIVE,
    @SerialName("execution_mode") val executionMode: AutopilotExecutionMode = AutopilotExecutionMode.CREATE_ISSUE,
    @SerialName("issue_title_template") val issueTitleTemplate: String? = null,
    @SerialName("created_by_type") val createdByType: String = "member",
    @SerialName("created_by_id") val createdById: String = "",
    @SerialName("last_run_at") val lastRunAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    // List-endpoint-only derived fields.
    @SerialName("trigger_kinds") val triggerKinds: List<String> = emptyList(),
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("last_run_status") val lastRunStatus: String? = null,
    val subscribers: List<AutopilotSubscriber> = emptyList(),
    @SerialName("can_write") val canWrite: Boolean? = null,
    @SerialName("can_manage_access") val canManageAccess: Boolean? = null,
)

@Serializable
data class AutopilotSubscriber(
    @SerialName("user_type") val userType: String = "member",
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class AutopilotCollaborator(
    @SerialName("user_type") val userType: String = "member",
    @SerialName("user_id") val userId: String,
    @SerialName("granted_by") val grantedBy: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class AutopilotCollaboratorsResponse(
    val collaborators: List<AutopilotCollaborator> = emptyList(),
)

@Serializable
data class WebhookEventFilter(
    val event: String,
    val actions: List<String> = emptyList(),
)

@Serializable
data class AutopilotTrigger(
    val id: String,
    @SerialName("autopilot_id") val autopilotId: String,
    val kind: AutopilotTriggerKind,
    val enabled: Boolean = true,
    @SerialName("cron_expression") val cronExpression: String? = null,
    val timezone: String? = null,
    @SerialName("next_run_at") val nextRunAt: String? = null,
    @SerialName("webhook_token") val webhookToken: String? = null,
    @SerialName("webhook_path") val webhookPath: String? = null,
    @SerialName("webhook_url") val webhookUrl: String? = null,
    val label: String? = null,
    @SerialName("event_filters") val eventFilters: List<WebhookEventFilter>? = null,
    @SerialName("last_fired_at") val lastFiredAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
)

@Serializable
data class AutopilotRun(
    val id: String,
    @SerialName("autopilot_id") val autopilotId: String,
    @SerialName("trigger_id") val triggerId: String? = null,
    val source: AutopilotRunSource = AutopilotRunSource.MANUAL,
    val status: AutopilotRunStatus = AutopilotRunStatus.RUNNING,
    @SerialName("issue_id") val issueId: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("triggered_at") val triggeredAt: String = "",
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("failure_reason") val failureReason: String? = null,
    @SerialName("trigger_payload") val triggerPayload: JsonElement? = null,
    val result: JsonElement? = null,
    @SerialName("created_at") val createdAt: String = "",
)

// Response envelopes.

@Serializable
data class ListAutopilotsResponse(
    val autopilots: List<Autopilot> = emptyList(),
    val total: Long = 0,
)

@Serializable
data class GetAutopilotResponse(
    val autopilot: Autopilot,
    val triggers: List<AutopilotTrigger> = emptyList(),
    val collaborators: List<AutopilotCollaborator> = emptyList(),
)

@Serializable
data class ListAutopilotRunsResponse(
    val runs: List<AutopilotRun> = emptyList(),
    val total: Long = 0,
)
