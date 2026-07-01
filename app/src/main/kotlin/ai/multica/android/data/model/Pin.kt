package ai.multica.android.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors packages/core/types/pin.ts::PinnedItem
 * and server/internal/handler/pin.go.
 *
 * Pin metadata only — title/status/identifier are derived by the consumer
 * from the underlying issue/project so the sidebar reacts to realtime
 * `issue:updated` / `project:updated` events without a cross-entity fetch.
 */
@Serializable
data class PinnedItem(
    val id: String,
    @SerialName("workspace_id") val workspaceId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("item_type") val itemType: PinnedItemType,
    @SerialName("item_id") val itemId: String,
    val position: Int = 0,
    @SerialName("created_at") val createdAt: String = "",
)
