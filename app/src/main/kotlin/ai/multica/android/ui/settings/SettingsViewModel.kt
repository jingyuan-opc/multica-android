package ai.multica.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.auth.ServerUrlStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.theme.ThemeMode
import ai.multica.android.core.theme.ThemePreference
import ai.multica.android.data.model.User
import ai.multica.android.data.model.Workspace
import ai.multica.android.data.repository.AuthRepository
import ai.multica.android.data.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreference: ThemePreference,
    private val serverUrlStore: ServerUrlStore,
    private val authRepository: AuthRepository,
    private val workspaceRepository: WorkspaceRepository,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePreference.themeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.System)

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val saved = serverUrlStore.getSavedUrls()
            val active = serverUrlStore.getActiveUrl()
            _state.update { it.copy(savedServers = saved, activeServerUrl = active) }

            when (val result = authRepository.getMe()) {
                is ApiResult.Success -> _state.update { it.copy(user = result.data) }
                else -> Unit
            }
            when (val result = workspaceRepository.list()) {
                is ApiResult.Success -> _state.update {
                    it.copy(workspaces = result.data, activeWorkspaceSlug = workspaceRepository.getActiveSlug())
                }
                else -> Unit
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { themePreference.set(mode) }
    }

    fun addServer(name: String, url: String) {
        viewModelScope.launch {
            // Add to saved list (use URL as name if empty).
            val normalized = serverUrlStore.normalizePublic(url)
            val current = _state.value.savedServers.toMutableList()
            if (!current.contains(normalized)) {
                current.add(normalized)
                serverUrlStore.replaceSaved(current)
            }
            serverUrlStore.setActiveUrl(normalized)
            _state.update {
                it.copy(savedServers = current, activeServerUrl = normalized)
            }
        }
    }

    fun removeServer(url: String) {
        viewModelScope.launch {
            serverUrlStore.removeUrl(url)
            refresh()
        }
    }

    fun switchActiveServer(url: String) {
        viewModelScope.launch {
            serverUrlStore.setActiveUrl(url)
            _state.update { it.copy(activeServerUrl = url) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            workspaceRepository.clear()
        }
    }
}

data class SettingsUiState(
    val user: User? = null,
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspaceSlug: String? = null,
    val savedServers: List<String> = emptyList(),
    val activeServerUrl: String = ServerUrlStore.DEFAULT_SERVER_URL,
)
