package ai.multica.android.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Label request DTOs. Mirror packages/core/types/label.ts. */

@Serializable
data class CreateLabelRequest(
    val name: String,
    val color: String = "#3b82f6",
)

@Serializable
data class UpdateLabelRequest(
    val name: String? = null,
    val color: String? = null,
)
