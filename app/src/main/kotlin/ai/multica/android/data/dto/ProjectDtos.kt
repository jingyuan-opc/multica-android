package ai.multica.android.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Request body for POST /api/projects.
 * Mirrors server/internal/handler/project.go::CreateProjectRequest.
 *
 * `resources` attaches GitHub repos / local directories in the same
 * transaction; the server rolls back the whole project if any resource is
 * invalid. Each entry mirrors the web CreateProjectResourceRequest.
 */
@Serializable
data class CreateProjectRequest(
    val title: String,
    val description: String? = null,
    val icon: String? = null,
    val status: String = "planned",
    val priority: String = "none",
    @SerialName("lead_type") val leadType: String? = null,
    @SerialName("lead_id") val leadId: String? = null,
    val resources: List<CreateProjectResourceInput> = emptyList(),
)

/**
 * A resource to attach at project creation time. Mirrors the server's
 * CreateProjectResourceRequestPayload: `resource_ref` is raw JSON (built via
 * [ai.multica.android.data.model.githubRepoRef] /
 * [ai.multica.android.data.model.localDirectoryRef]).
 */
@Serializable
data class CreateProjectResourceInput(
    @SerialName("resource_type") val resourceType: String,
    @SerialName("resource_ref") val resourceRef: JsonElement,
    val label: String? = null,
    val position: Int? = null,
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
