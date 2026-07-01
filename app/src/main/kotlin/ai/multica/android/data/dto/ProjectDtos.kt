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

/** PUT /api/projects/{id}. Mirrors UpdateProjectRequest (types/project.ts). */
@Serializable
data class UpdateProjectRequest(
    val title: String? = null,
    val description: String? = null,
    val icon: String? = null,
    val status: String? = null,
    val priority: String? = null,
    @kotlinx.serialization.SerialName("lead_type") val leadType: String? = null,
    @kotlinx.serialization.SerialName("lead_id") val leadId: String? = null,
)
