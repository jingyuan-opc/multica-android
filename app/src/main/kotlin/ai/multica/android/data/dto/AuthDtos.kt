package ai.multica.android.data.dto

import ai.multica.android.data.model.User
import kotlinx.serialization.Serializable

@Serializable
data class SendCodeRequest(val email: String)

@Serializable
data class SendCodeResponse(val message: String)

@Serializable
data class VerifyCodeRequest(
    val email: String,
    val code: String,
)

/**
 * Response from POST /auth/verify-code and POST /auth/google.
 * `token` is the JWT we must keep; the Go handler also sets HttpOnly
 * cookies but as a native client we ignore them and use Bearer auth.
 */
@Serializable
data class LoginResponse(
    val token: String,
    val user: User,
)

@Serializable
data class LogoutResponse(val message: String)

@Serializable
data class ApiErrorBody(val error: String)
