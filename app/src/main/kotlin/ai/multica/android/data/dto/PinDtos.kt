package ai.multica.android.data.dto

import ai.multica.android.data.model.PinnedItemType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pin request DTOs. Mirror packages/core/types/pin.ts. */

@Serializable
data class CreatePinRequest(
    @SerialName("item_type") val itemType: PinnedItemType,
    @SerialName("item_id") val itemId: String,
)

@Serializable
data class ReorderPinItem(
    val id: String,
    val position: Int,
)

@Serializable
data class ReorderPinsRequest(
    val items: List<ReorderPinItem>,
)
