package ai.multica.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    private val _events = MutableStateFlow<LoginEvent?>(null)
    val events: StateFlow<LoginEvent?> = _events.asStateFlow()

    private var cooldownJob: Job? = null

    fun onEmailChange(value: String) {
        _state.update { it.copy(email = value.trim(), emailError = null, errorMessage = null) }
    }

    fun onCodeChange(value: String) {
        // Allow only digits, cap at 6
        val sanitized = value.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = sanitized, codeError = null, errorMessage = null) }
    }

    fun sendCode() {
        val current = _state.value
        if (!current.canSendCode) return
        val email = current.email
        if (!isValidEmail(email)) {
            _state.update { it.copy(emailError = "Please enter a valid email") }
            return
        }
        _state.update { it.copy(isSending = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.sendCode(email)) {
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            step = LoginStep.Code(email),
                            isSending = false,
                            sendCooldownSeconds = 60,
                        )
                    }
                    startCooldown()
                }
                is ApiResult.HttpError -> {
                    _state.update {
                        it.copy(
                            isSending = false,
                            errorMessage = result.message,
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _state.update {
                        it.copy(
                            isSending = false,
                            errorMessage = "Network error — check your connection",
                        )
                    }
                }
                is ApiResult.Unknown -> {
                    _state.update {
                        it.copy(
                            isSending = false,
                            errorMessage = "Unexpected error",
                        )
                    }
                }
            }
        }
    }

    fun verifyCode() {
        val current = _state.value
        val email = when (val s = current.step) {
            is LoginStep.Code -> s.email
            else -> current.email
        }
        if (!current.canVerify) return
        if (current.code.length != 6) {
            _state.update { it.copy(codeError = "Enter the 6-digit code") }
            return
        }
        _state.update { it.copy(isVerifying = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.verifyCode(email, current.code)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isVerifying = false) }
                    _events.value = LoginEvent.Success(result.data.user)
                }
                is ApiResult.HttpError -> {
                    _state.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = result.message,
                            code = "",
                        )
                    }
                }
                is ApiResult.NetworkError -> {
                    _state.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = "Network error — check your connection",
                        )
                    }
                }
                is ApiResult.Unknown -> {
                    _state.update {
                        it.copy(
                            isVerifying = false,
                            errorMessage = "Unexpected error",
                        )
                    }
                }
            }
        }
    }

    fun resendCode() {
        val email = when (val s = _state.value.step) {
            is LoginStep.Code -> s.email
            else -> return
        }
        _state.update { it.copy(email = email) }
        sendCode()
    }

    fun goBackToEmail() {
        cooldownJob?.cancel()
        _state.update {
            LoginUiState(email = it.email)
        }
    }

    fun consumeEvent() {
        _events.value = null
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldownJob = viewModelScope.launch {
            while (_state.value.sendCooldownSeconds > 0) {
                delay(1000)
                _state.update {
                    val next = (it.sendCooldownSeconds - 1).coerceAtLeast(0)
                    it.copy(sendCooldownSeconds = next)
                }
            }
        }
    }

    private fun isValidEmail(email: String): Boolean {
        if (email.isBlank()) return false
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    override fun onCleared() {
        cooldownJob?.cancel()
        super.onCleared()
    }
}

sealed interface LoginEvent {
    data class Success(val user: ai.multica.android.data.model.User) : LoginEvent
}
