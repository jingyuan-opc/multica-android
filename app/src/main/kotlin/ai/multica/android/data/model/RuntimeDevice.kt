package ai.multica.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read-only mirror of a daemon-registered runtime device.
 * Mirrors packages/core/types/agent.ts::RuntimeDevice. Only the fields the
 * mobile create-agent flow needs are typed here (the agent picker only needs
 * id + name); the rest are optional with safe defaults so unknown fields and
 * older-backend omissions don't break deserialization.
 *
 * Server endpoint: GET /api/runtimes (workspace scope resolved server-side
 * from the X-Workspace-Slug header).
 */
@Serializable
data class RuntimeDevice(
    val id: String,
    @SerialName("workspace_id") val workspaceId: String = "",
    @SerialName("daemon_id") val daemonId: String? = null,
    val name: String,
    @SerialName("runtime_mode") val runtimeMode: String = "managed",
    val provider: String = "",
    val status: String = "offline",
    @SerialName("owner_id") val ownerId: String? = null,
)
