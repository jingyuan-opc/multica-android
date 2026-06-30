package ai.multica.android

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.auth.TokenStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the initial auth-bootstrap check on app launch:
 * - Reads the token from [TokenStore]
 * - If present, calls GET /api/me to validate
 * - On success → isAuthenticated=true; on 401/empty → false
 *
 * The result gates the NavHost's startDestination (login vs home).
 * `isReady` flips to true once the check is done — it controls the
 * splash → UI transition.
 */
@HiltViewModel
class BootstrapViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        viewModelScope.launch {
            val token = tokenStore.getToken()
            if (token == null) {
                _isAuthenticated.value = false
                _isReady.value = true
                return@launch
            }
            when (authRepository.getMe()) {
                is ApiResult.Success -> _isAuthenticated.value = true
                else -> _isAuthenticated.value = false
            }
            _isReady.value = true
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _isAuthenticated.value = false
        }
    }
}
