package ai.multica.android.ui.login

/**
 * Three-step login state machine, mirroring the web app's
 * `step: "email" | "code" | "submitting"`.
 */
sealed interface LoginStep {
    data object Email : LoginStep
    data class Code(val email: String) : LoginStep
    data object Submitting : LoginStep
}

data class LoginUiState(
    val step: LoginStep = LoginStep.Email,
    val email: String = "",
    val code: String = "",
    val emailError: String? = null,
    val codeError: String? = null,
    val sendCooldownSeconds: Int = 0,
    val isSending: Boolean = false,
    val isVerifying: Boolean = false,
    val errorMessage: String? = null,
) {
    val canSendCode: Boolean
        get() = !isSending && email.isNotBlank() && sendCooldownSeconds == 0

    val canVerify: Boolean
        get() = !isVerifying && code.length == 6
}
