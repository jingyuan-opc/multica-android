package ai.multica.android.data.dto

import ai.multica.android.data.model.AutopilotExecutionMode
import ai.multica.android.data.model.AutopilotAssigneeType
import ai.multica.android.data.model.AutopilotStatus
import ai.multica.android.data.model.AutopilotTriggerKind
import ai.multica.android.data.model.WebhookEventFilter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Autopilot request DTOs. Mirror packages/core/types/autopilot.ts.
 * All multi-word fields carry @SerialName for snake_case wire format.
 */

@Serializable
data class CreateAutopilotRequest(
    val title: String,
    val description: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assignee_type") val assigneeType: AutopilotAssigneeType = AutopilotAssigneeType.AGENT,
    @SerialName("assignee_id") val assigneeId: String,
    @SerialName("execution_mode") val executionMode: AutopilotExecutionMode = AutopilotExecutionMode.CREATE_ISSUE,
    @SerialName("issue_title_template") val issueTitleTemplate: String? = null,
    val subscribers: List<AutopilotSubscriberInput> = emptyList(),
)

@Serializable
data class UpdateAutopilotRequest(
    val title: String? = null,
    val description: String? = null,
    @SerialName("project_id") val projectId: String? = null,
    @SerialName("assignee_type") val assigneeType: AutopilotAssigneeType? = null,
    @SerialName("assignee_id") val assigneeId: String? = null,
    val status: AutopilotStatus? = null,
    @SerialName("execution_mode") val executionMode: AutopilotExecutionMode? = null,
    @SerialName("issue_title_template") val issueTitleTemplate: String? = null,
    val subscribers: List<AutopilotSubscriberInput>? = null,
)

@Serializable
data class AutopilotSubscriberInput(
    @SerialName("user_type") val userType: String = "member",
    @SerialName("user_id") val userId: String,
)

@Serializable
data class CreateAutopilotTriggerRequest(
    val kind: AutopilotTriggerKind,
    @SerialName("cron_expression") val cronExpression: String? = null,
    val timezone: String? = null,
    val label: String? = null,
    @SerialName("event_filters") val eventFilters: List<WebhookEventFilter> = emptyList(),
)

@Serializable
data class UpdateAutopilotTriggerRequest(
    val enabled: Boolean? = null,
    @SerialName("cron_expression") val cronExpression: String? = null,
    val timezone: String? = null,
    val label: String? = null,
    @SerialName("event_filters") val eventFilters: List<WebhookEventFilter>? = null,
)

@Serializable
data class CollaboratorRequest(
    @SerialName("user_id") val userId: String,
)
