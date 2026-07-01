package ai.multica.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Resource type for a [ProjectResource].
 *
 * Mirrors packages/core/types/project.ts::ProjectResourceType:
 *   - github_repo: cloud-side git checkout, ref = { url }
 *   - local_directory: in-place agent execution on a specific daemon,
 *     ref = { local_path, daemon_id, label? }
 */
@Serializable
enum class ProjectResourceType(val wireValue: String) {
    @SerialName("github_repo") GITHUB_REPO("github_repo"),
    @SerialName("local_directory") LOCAL_DIRECTORY("local_directory"),
}

/**
 * A resource attached to a project (a GitHub repo or a local working directory).
 *
 * `resourceRef` is opaque JSON on the client because the two variants carry
 * different fields. Builders ([githubRepoRef] / [localDirectoryRef]) construct
 * the correct shapes; read-only consumers should inspect `resourceType` first.
 *
 * Mirrors packages/core/types/project.ts::ProjectResource.
 */
@Serializable
data class ProjectResource(
    val id: String,
    @SerialName("project_id") val projectId: String,
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("resource_type") val resourceType: ProjectResourceType,
    @SerialName("resource_ref") val resourceRef: JsonElement,
    val label: String? = null,
    val position: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("created_by") val createdBy: String? = null,
) {
    /** Best-effort extraction of a github repo url from resource_ref JSON. */
    val githubUrl: String?
        get() = stringField("url")

    /** Best-effort extraction of a local directory path from resource_ref JSON. */
    val localPath: String?
        get() = stringField("local_path")

    private fun stringField(key: String): String? {
        val obj = resourceRef as? JsonObject ?: return null
        return (obj[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
    }
}

/** Response wrapper for GET /api/projects/{id}/resources. */
@Serializable
data class ListProjectResourcesResponse(
    val resources: List<ProjectResource> = emptyList(),
)

/** Build the resource_ref JSON for a github_repo resource: `{ "url": <url> }`. */
fun githubRepoRef(url: String): JsonObject = buildJsonObject { put("url", url.trim()) }

/**
 * Build the resource_ref JSON for a local_directory resource:
 * `{ "local_path": <path>, "daemon_id": <id>, "label"?: <label> }`.
 * `label` is omitted when blank (server treats it as optional).
 */
fun localDirectoryRef(localPath: String, daemonId: String, label: String? = null): JsonObject = buildJsonObject {
    put("local_path", localPath.trim())
    put("daemon_id", daemonId.trim())
    if (!label.isNullOrBlank()) put("label", label.trim())
}
