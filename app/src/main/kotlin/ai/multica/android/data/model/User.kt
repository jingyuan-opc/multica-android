package ai.multica.android.data.model

import kotlinx.serialization.Serializable

/**
 * Mirrors packages/core/types/workspace.ts::User
 * and server/internal/handler/auth.go::UserResponse.
 */
@Serializable
data class User(
    val id: String,
    val name: String = "",
    val email: String = "",
    @kotlinx.serialization.SerialName("avatar_url") val avatarUrl: String? = null,
    @kotlinx.serialization.SerialName("onboarded_at") val onboardedAt: String? = null,
    @kotlinx.serialization.SerialName("onboarding_questionnaire") val onboardingQuestionnaire: kotlinx.serialization.json.JsonElement? = null,
    @kotlinx.serialization.SerialName("starter_content_state") val starterContentState: String? = null,
    val language: String? = null,
    @kotlinx.serialization.SerialName("profile_description") val profileDescription: String = "",
    val timezone: String? = null,
    @kotlinx.serialization.SerialName("created_at") val createdAt: String = "",
    @kotlinx.serialization.SerialName("updated_at") val updatedAt: String = "",
)
