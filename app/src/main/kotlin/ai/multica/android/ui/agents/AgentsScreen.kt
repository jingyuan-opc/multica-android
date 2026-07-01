package ai.multica.android.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.auth.WorkspaceEvents
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.Agent
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentsViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    private val workspaceStore: ai.multica.android.core.auth.WorkspaceStore,
    private val workspaceEvents: WorkspaceEvents,
    private val realtimeManager: ai.multica.android.realtime.RealtimeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(AgentsUiState())
    val state: StateFlow<AgentsUiState> = _state.asStateFlow()
    private var workspaceJob: Job? = null
    private var realtimeJob: Job? = null

    init {
        refresh()
        observeWorkspaceChanges()
        observeRealtime()
    }

    private fun observeWorkspaceChanges() {
        workspaceJob?.cancel()
        workspaceJob = viewModelScope.launch {
            workspaceEvents.changes.collect {
                _state.update { it.copy(agents = emptyList()) }
                refresh()
            }
        }
    }

    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                if (event is ai.multica.android.realtime.WsEvent.AgentChanged) refresh()
            }
        }
    }

    fun refresh() {
        // Guard: don't fire workspace-scoped requests until a workspace is set,
        // otherwise the server 400s with "workspace_id or workspace_slug required".
        if (workspaceStore.getId() == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = agentRepository.list()) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, agents = result.data.filterNot { a -> a.archivedAt != null })
                }
                is ApiResult.HttpError -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.NetworkError -> _state.update { it.copy(isLoading = false, errorMessage = "Network error") }
                is ApiResult.Unknown -> _state.update { it.copy(isLoading = false, errorMessage = "Unexpected error") }
            }
        }
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        realtimeJob?.cancel()
        super.onCleared()
    }
}

data class AgentsUiState(
    val isLoading: Boolean = false,
    val agents: List<Agent> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(
    contentPadding: PaddingValues,
    onOpenAgent: (String) -> Unit,
    refreshTrigger: Int = 0,
    viewModel: AgentsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(refreshTrigger) { if (refreshTrigger > 0) viewModel.refresh() }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.agents.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.agents.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null && state.agents.isEmpty() ->
                    EmptyState(icon = Icons.Filled.SmartToy, title = stringResource(R.string.agents_empty), description = state.errorMessage)
                state.agents.isEmpty() ->
                    EmptyState(icon = Icons.Filled.SmartToy, title = stringResource(R.string.agents_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.agents, key = { it.id }) { agent ->
                        AgentCard(agent = agent, onClick = { onOpenAgent(agent.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentCard(agent: Agent, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SmartToy, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(agent.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (agent.description.isNotBlank()) {
                    Text(agent.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                StatusDot(status = agent.status)
                Spacer(Modifier.height(2.dp))
                if (agent.model.isNotBlank()) {
                    Text(agent.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
internal fun StatusDot(status: String) {
    // AgentStatus union (server/internal/handler/agent.go): idle/working/
    // blocked/error/offline. Default "active" (model fallback) is treated as
    // idle-ish green so a freshly-listed agent doesn't render grey "unknown".
    val color = when (status) {
        "idle", "active" -> Color(0xFF10B981)
        "working", "running" -> Color(0xFF3B82F6)
        "blocked", "error" -> MaterialTheme.colorScheme.error
        "offline" -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.outline
    }
    val label = when (status) {
        "active" -> "idle"
        "running" -> "working"
        else -> status
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
