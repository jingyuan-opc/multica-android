package ai.multica.android.ui.inbox

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.auth.WorkspaceEvents
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.InboxItem
import ai.multica.android.data.repository.InboxRepository
import ai.multica.android.domain.InboxDedup
import ai.multica.android.realtime.RealtimeEntryPoint
import ai.multica.android.realtime.WsEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the inbox tab's UI state.
 *
 * **Behavioral parity contract** (apps/mobile/CLAUDE.md 2026-05-09
 * incident): the unread badge AND the rendered list MUST be derived
 * from `InboxDedup.deduplicate(rawItems)`, never from the raw
 * response. Both the visible list and the badge use the SAME
 * deduped list — they are always consistent.
 *
 * Subscribes to the WebSocket event flow; on any inbox:* event
 * we re-fetch and re-dedup. We don't try to patch individual
 * events into the cache (more error-prone, less safe).
 *
 * Re-fetches when the user switches workspaces via [WorkspaceEvents].
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inboxRepository: InboxRepository,
    private val workspaceEvents: WorkspaceEvents,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    private val realtimeManager = RealtimeEntryPoint.get(context).realtimeManager()
    private var wsJob: Job? = null
    private var workspaceJob: Job? = null

    init {
        refresh()
        observeRealtime()
        observeWorkspaceChanges()
    }

    private fun observeRealtime() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                if (event is WsEvent.InboxNew
                    || event is WsEvent.InboxRead
                    || event is WsEvent.InboxArchived
                    || event is WsEvent.InboxBatchRead
                    || event is WsEvent.InboxBatchArchived
                ) {
                    refresh()
                }
            }
        }
    }

    private fun observeWorkspaceChanges() {
        workspaceJob?.cancel()
        workspaceJob = viewModelScope.launch {
            workspaceEvents.changes.collect {
                // Reset local state and re-fetch against the new workspace.
                _state.update {
                    it.copy(
                        rawItems = emptyList(),
                        items = emptyList(),
                        unreadCount = 0,
                    )
                }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = inboxRepository.list()) {
                is ApiResult.Success -> {
                    val deduped = InboxDedup.deduplicate(result.data)
                    _state.update {
                        it.copy(
                            isLoading = false,
                            rawItems = result.data,
                            items = deduped,
                            unreadCount = deduped.count { item -> !item.read },
                        )
                    }
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

    fun markRead(item: InboxItem) {
        viewModelScope.launch {
            when (inboxRepository.markRead(item.id)) {
                is ApiResult.Success -> applyLocalUpdate(item.id) { it.copy(read = true) }
                else -> Unit
            }
        }
    }

    fun archive(item: InboxItem) {
        viewModelScope.launch {
            when (inboxRepository.archive(item.id)) {
                is ApiResult.Success -> applyLocalUpdate(item.id) { it.copy(archived = true) }
                else -> Unit
            }
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            when (inboxRepository.markAllRead()) {
                is ApiResult.Success -> refresh()
                else -> Unit
            }
        }
    }

    fun archiveAllRead() {
        viewModelScope.launch {
            when (inboxRepository.archiveAllRead()) {
                is ApiResult.Success -> refresh()
                else -> Unit
            }
        }
    }

    fun setFilter(filter: InboxFilter) {
        _state.update { it.copy(filter = filter) }
    }

    private fun applyLocalUpdate(id: String, transform: (InboxItem) -> InboxItem) {
        val newRaw = _state.value.rawItems.map { if (it.id == id) transform(it) else it }
        val newDeduped = InboxDedup.deduplicate(newRaw)
        _state.update {
            it.copy(
                rawItems = newRaw,
                items = newDeduped,
                unreadCount = newDeduped.count { item -> !item.read },
            )
        }
    }

    override fun onCleared() {
        wsJob?.cancel()
        workspaceJob?.cancel()
        super.onCleared()
    }
}

enum class InboxFilter { Unread, All }

data class InboxUiState(
    val isLoading: Boolean = false,
    val rawItems: List<InboxItem> = emptyList(),
    val items: List<InboxItem> = emptyList(),
    val unreadCount: Int = 0,
    val filter: InboxFilter = InboxFilter.Unread,
    val errorMessage: String? = null,
) {
    val displayed: List<InboxItem>
        get() = when (filter) {
            InboxFilter.Unread -> items.filter { !it.read }
            InboxFilter.All -> items
        }
}
