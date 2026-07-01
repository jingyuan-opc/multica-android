package ai.multica.android.ui.autopilots

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material.icons.filled.Bolt
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
import ai.multica.android.data.model.Autopilot
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.data.repository.AutopilotRepository
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.data.repository.SquadRepository
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
class AutopilotsViewModel @Inject constructor(
    private val autopilotRepository: AutopilotRepository,
    private val agentRepository: AgentRepository,
    private val squadRepository: SquadRepository,
    private val projectRepository: ProjectRepository,
    private val workspaceStore: ai.multica.android.core.auth.WorkspaceStore,
    private val workspaceEvents: WorkspaceEvents,
) : ViewModel() {

    private val _state = MutableStateFlow(AutopilotsUiState())
    val state: StateFlow<AutopilotsUiState> = _state.asStateFlow()
    private var workspaceJob: Job? = null

    init {
        refresh()
        observeWorkspaceChanges()
    }

    private fun observeWorkspaceChanges() {
        workspaceJob?.cancel()
        workspaceJob = viewModelScope.launch {
            workspaceEvents.changes.collect {
                _state.update { it.copy(autopilots = emptyList()) }
                refresh()
            }
        }
    }

    fun refresh() {
        if (workspaceStore.getId() == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val listResult = autopilotRepository.list()
            val autopilots = (listResult as? ApiResult.Success)?.data?.autopilots ?: emptyList()
            // Preload assignee/project option lists for the create dialog.
            if (_state.value.agents.isEmpty()) {
                (agentRepository.list() as? ApiResult.Success)?.data?.let {
                    _state.update { s -> s.copy(agents = it.filterNot { a -> a.archivedAt != null }) }
                }
            }
            if (_state.value.squads.isEmpty()) {
                (squadRepository.list() as? ApiResult.Success)?.data?.let {
                    _state.update { s -> s.copy(squads = it) }
                }
            }
            if (_state.value.projects.isEmpty()) {
                (projectRepository.list() as? ApiResult.Success)?.data?.projects?.let {
                    _state.update { s -> s.copy(projects = it) }
                }
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    autopilots = autopilots,
                    errorMessage = when {
                        listResult is ApiResult.HttpError -> listResult.message
                        listResult is ApiResult.NetworkError -> "Network error"
                        else -> null
                    },
                )
            }
        }
    }

    fun togglePause(autopilot: Autopilot) {
        viewModelScope.launch {
            val newStatus = if (autopilot.status == ai.multica.android.data.model.AutopilotStatus.ACTIVE)
                ai.multica.android.data.model.AutopilotStatus.PAUSED
            else ai.multica.android.data.model.AutopilotStatus.ACTIVE
            autopilotRepository.setStatus(autopilot.id, newStatus)
            refresh()
        }
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        super.onCleared()
    }
}

data class AutopilotsUiState(
    val isLoading: Boolean = false,
    val autopilots: List<Autopilot> = emptyList(),
    val agents: List<ai.multica.android.data.model.Agent> = emptyList(),
    val squads: List<ai.multica.android.data.model.Squad> = emptyList(),
    val projects: List<ai.multica.android.data.model.Project> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutopilotsScreen(
    contentPadding: PaddingValues,
    onOpenAutopilot: (String) -> Unit,
    onCreateAutopilot: () -> Unit,
    onBack: () -> Unit = {},
    forceRefreshOnAppear: Boolean = false,
    viewModel: AutopilotsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(forceRefreshOnAppear) { if (forceRefreshOnAppear) viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.autopilots_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.autopilots.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.autopilots.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null && state.autopilots.isEmpty() ->
                    EmptyState(icon = Icons.Filled.Bolt, title = stringResource(R.string.autopilots_empty), description = state.errorMessage)
                state.autopilots.isEmpty() ->
                    EmptyState(
                        icon = Icons.Filled.Bolt,
                        title = stringResource(R.string.autopilots_empty),
                        description = "Automate issue creation with schedules or webhooks",
                    ) {
                        FilledTonalButton(onClick = onCreateAutopilot) { Text(stringResource(R.string.autopilots_new)) }
                    }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.autopilots, key = { it.id }) { ap ->
                        AutopilotCard(
                            autopilot = ap,
                            onClick = { onOpenAutopilot(ap.id) },
                            onTogglePause = { viewModel.togglePause(ap) },
                        )
                    }
                }
            }
        }
        FloatingActionButton(onClick = onCreateAutopilot, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.autopilots_new))
        }
    }
    }
}

@Composable
private fun AutopilotCard(
    autopilot: Autopilot,
    onClick: () -> Unit,
    onTogglePause: () -> Unit,
) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Bolt, null, tint = if (autopilot.status == ai.multica.android.data.model.AutopilotStatus.ACTIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(autopilot.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Trigger kind icons.
                    autopilot.triggerKinds.forEach { kind ->
                        val icon = when (kind) {
                            "schedule" -> Icons.Filled.Schedule
                            "webhook" -> Icons.Filled.Webhook
                            "api" -> Icons.Filled.PlayArrow
                            else -> Icons.Filled.Bolt
                        }
                        Icon(icon, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = autopilot.executionMode.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusPill(status = autopilot.status.name)
            IconButton(onClick = onTogglePause) {
                Icon(
                    if (autopilot.status == ai.multica.android.data.model.AutopilotStatus.ACTIVE) Icons.Filled.Schedule else Icons.Filled.PlayArrow,
                    contentDescription = if (autopilot.status == ai.multica.android.data.model.AutopilotStatus.ACTIVE) stringResource(R.string.autopilots_pause) else stringResource(R.string.autopilots_resume),
                )
            }
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    val color = when (status) {
        "active" -> Color(0xFF10B981)
        "paused" -> MaterialTheme.colorScheme.outline
        "archived" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text = status,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
