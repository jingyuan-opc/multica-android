package ai.multica.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.auth.WorkspaceEvents
import ai.multica.android.core.auth.WorkspacePreference
import ai.multica.android.core.auth.WorkspaceStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.InboxWorkspaceUnread
import ai.multica.android.data.model.PinnedItem
import ai.multica.android.data.model.Workspace
import ai.multica.android.data.repository.InboxRepository
import ai.multica.android.data.repository.PinRepository
import ai.multica.android.data.repository.WorkspaceRepository
import ai.multica.android.domain.InboxDedup
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the top-bar state: list of workspaces, the active one, and
 * the cross-workspace unread dot.
 *
 * On launch:
 *  1. Read the last-active workspace from [WorkspacePreference] and
 *     push it into [WorkspaceStore] synchronously. This ensures
 *     AuthInterceptor adds the right `X-Workspace-Slug` header on
 *     the very first request — otherwise the server rejects with
 *     "workspace_id or workspace_slug is required".
 *  2. Fetch the live workspace list to validate the cached slug
 *     still exists; fall back to the first workspace if not.
 *  3. Emit the new active workspace id via [WorkspaceEvents] so
 *     tab ViewModels (Inbox/Projects/Issues) re-fetch.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workspaceRepository: WorkspaceRepository,
    private val inboxRepository: InboxRepository,
    private val pinRepository: PinRepository,
    private val workspaceStore: WorkspaceStore,
    private val workspacePreference: WorkspacePreference,
    private val workspaceEvents: WorkspaceEvents,
    private val realtimeManager: ai.multica.android.realtime.RealtimeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var pinJob: kotlinx.coroutines.Job? = null

    init {
        loadInitial()
        observeRealtime()
    }

    private fun observeRealtime() {
        pinJob?.cancel()
        pinJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                // Pin changes from another session/device should update the
                // drawer's pinned list immediately.
                if (event is ai.multica.android.realtime.WsEvent.PinChanged) loadPins()
            }
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Restore the last-active workspace synchronously so the
            // very first request from any tab VM goes out with the
            // right X-Workspace-Slug header.
            val (cachedSlug, cachedId) = workspacePreference.getLastActive()
            if (cachedSlug != null) {
                workspaceStore.set(slug = cachedSlug, id = cachedId)
            }

            // 2. Fetch the live list and reconcile.
            when (val result = workspaceRepository.list()) {
                is ApiResult.Success -> {
                    val list = result.data
                    if (list.isEmpty()) {
                        workspaceStore.clear()
                        _state.update {
                            it.copy(isLoading = false, workspaces = emptyList(), activeWorkspace = null)
                        }
                        return@launch
                    }
                    val active = list.firstOrNull { it.slug == cachedSlug } ?: list.first()
                    workspaceRepository.setActive(active)
                    workspacePreference.setLastActive(active.slug, active.id)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            workspaces = list,
                            activeWorkspace = active,
                        )
                    }
                    workspaceEvents.emit(active.id)
                    refreshUnreadSummary()
                    loadPins()
                }
                is ApiResult.HttpError -> {
                    _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                }
                is ApiResult.NetworkError -> {
                    _state.update { it.copy(isLoading = false, errorMessage = "Network error") }
                }
                is ApiResult.Unknown -> {
                    _state.update { it.copy(isLoading = false, errorMessage = "Unexpected error") }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = workspaceRepository.list()) {
                is ApiResult.Success -> {
                    val list = result.data
                    val activeSlug = workspaceStore.getSlug()
                    val active = list.firstOrNull { it.slug == activeSlug } ?: list.firstOrNull()
                    _state.update {
                        it.copy(
                            workspaces = list,
                            activeWorkspace = active ?: it.activeWorkspace,
                        )
                    }
                    refreshUnreadSummary()
                }
                else -> Unit
            }
        }
    }

    fun refreshUnreadSummary() {
        viewModelScope.launch {
            when (val result = inboxRepository.unreadSummary()) {
                is ApiResult.Success -> {
                    val summary = result.data
                    val activeId = _state.value.activeWorkspace?.id
                    val hasOther = InboxDedup.hasOtherWorkspaceUnread(summary, activeId)
                    val total = summary.sumOf { it.count }.toInt()
                    _state.update {
                        it.copy(unreadSummary = summary, hasOtherWorkspaceUnread = hasOther, totalUnread = total)
                    }
                }
                else -> Unit
            }
        }
    }

    private fun loadPins() {
        viewModelScope.launch {
            when (val result = pinRepository.list()) {
                is ApiResult.Success -> _state.update { it.copy(pins = result.data) }
                else -> Unit
            }
        }
    }

    fun selectWorkspace(workspace: Workspace) {
        if (workspace.id == _state.value.activeWorkspace?.id) return
        workspaceRepository.setActive(workspace)
        viewModelScope.launch {
            workspacePreference.setLastActive(workspace.slug, workspace.id)
        }
        _state.update { it.copy(activeWorkspace = workspace) }
        workspaceEvents.emit(workspace.id)
        refreshUnreadSummary()
    }

    fun clearActiveWorkspace() {
        workspaceRepository.clear()
        viewModelScope.launch {
            workspacePreference.setLastActive(null, null)
        }
        _state.update { it.copy(activeWorkspace = null) }
    }
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val workspaces: List<Workspace> = emptyList(),
    val activeWorkspace: Workspace? = null,
    val unreadSummary: List<InboxWorkspaceUnread> = emptyList(),
    val hasOtherWorkspaceUnread: Boolean = false,
    val totalUnread: Int = 0,
    val pins: List<PinnedItem>? = null,
    val errorMessage: String? = null,
)
