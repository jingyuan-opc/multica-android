package ai.multica.android.ui.squads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import ai.multica.android.data.model.Squad
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
class SquadsViewModel @Inject constructor(
    private val squadRepository: SquadRepository,
    private val workspaceStore: ai.multica.android.core.auth.WorkspaceStore,
    private val workspaceEvents: WorkspaceEvents,
    private val realtimeManager: ai.multica.android.realtime.RealtimeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SquadsUiState())
    val state: StateFlow<SquadsUiState> = _state.asStateFlow()
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
                _state.update { it.copy(squads = emptyList()) }
                refresh()
            }
        }
    }

    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                if (event is ai.multica.android.realtime.WsEvent.SquadChanged) refresh()
            }
        }
    }

    fun refresh() {
        if (workspaceStore.getId() == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = squadRepository.list()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, squads = result.data) }
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

data class SquadsUiState(
    val isLoading: Boolean = false,
    val squads: List<Squad> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadsScreen(
    contentPadding: PaddingValues,
    onOpenSquad: (String) -> Unit,
    onBack: () -> Unit = {},
    onCreateSquad: () -> Unit = {},
    forceRefreshOnAppear: Boolean = false,
    viewModel: SquadsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(forceRefreshOnAppear) { if (forceRefreshOnAppear) viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.squads_title)) },
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
                isRefreshing = state.isLoading && state.squads.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading && state.squads.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.errorMessage != null && state.squads.isEmpty() ->
                        EmptyState(icon = Icons.Filled.Group, title = stringResource(R.string.squads_empty), description = state.errorMessage)
                    state.squads.isEmpty() ->
                        EmptyState(icon = Icons.Filled.Group, title = stringResource(R.string.squads_empty))
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.squads, key = { it.id }) { squad ->
                            SquadCard(squad = squad, onClick = { onOpenSquad(squad.id) })
                        }
                    }
                }
            }
            FloatingActionButton(onClick = onCreateSquad, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.squads_title))
            }
        }
    }
}

@Composable
private fun SquadCard(squad: Squad, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Group, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(squad.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (squad.description.isNotBlank()) {
                    Text(squad.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${squad.memberCount} ${stringResource(R.string.squads_members)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
