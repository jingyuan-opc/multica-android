package ai.multica.android.data.dto

import kotlinx.serialization.Serializable

/**
 * Request body for POST /api/projects.
 * Mirrors server/internal/handler/project.go::CreateProjectRequest.
 */
@Serializable
data class CreateProjectRequest(
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val status: String = "planned",
    val priority: String = "none",
    @kotlinx.serialization.SerialName("lead_type") val leadType: String? = null,
    @kotlinx.serialization.SerialName("lead_id") val leadId: String? = null,
)
