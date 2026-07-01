package ai.multica.android.ui.squads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.Agent
import ai.multica.android.data.model.MemberWithUser
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.data.repository.MemberRepository
import ai.multica.android.data.repository.SquadRepository
import ai.multica.android.core.auth.WorkspaceStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateSquadViewModel @Inject constructor(
    private val squadRepository: SquadRepository,
    private val memberRepository: MemberRepository,
    private val agentRepository: AgentRepository,
    private val workspaceStore: WorkspaceStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateSquadUiState())
    val state: StateFlow<CreateSquadUiState> = _state.asStateFlow()

    init { loadLeaders() }

    private fun loadLeaders() {
        val wsId = workspaceStore.getId() ?: return
        viewModelScope.launch {
            (memberRepository.list(wsId) as? ApiResult.Success)?.data?.let {
                _state.update { s -> s.copy(members = it) }
            }
            (agentRepository.list() as? ApiResult.Success)?.data?.let {
                _state.update { s -> s.copy(agents = it.filterNot { a -> a.archivedAt != null }) }
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onLeaderChange(id: String) = _state.update { it.copy(leaderId = id) }

    fun create(onCreated: (String) -> Unit) {
        val s = _state.value
        if (s.name.isBlank() || s.leaderId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            when (val r = squadRepository.create(s.name, s.leaderId, s.description.ifBlank { null })) {
                is ApiResult.Success -> { _state.update { it.copy(isSubmitting = false) }; onCreated(r.data.id) }
                else -> _state.update { it.copy(isSubmitting = false) }
            }
        }
    }
}

data class CreateSquadUiState(
    val name: String = "",
    val description: String = "",
    val leaderId: String = "",
    val members: List<MemberWithUser> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && leaderId.isNotBlank() && !isSubmitting
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSquadScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateSquadViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var leaderExpanded by remember { mutableStateOf(false) }
    val leaderName = state.members.firstOrNull { it.userId == state.leaderId }?.name
        ?: state.agents.firstOrNull { it.id == state.leaderId }?.name
        ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.squads_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(onClick = { viewModel.create { id -> onBack(); onCreated(id) } }, enabled = state.canSubmit) {
                        if (state.isSubmitting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.common_ok))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(value = state.name, onValueChange = viewModel::onNameChange, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.description, onValueChange = viewModel::onDescriptionChange, label = { Text("Description") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            // Leader picker.
            ExposedDropdownMenuBox(expanded = leaderExpanded, onExpandedChange = { leaderExpanded = it }) {
                OutlinedTextField(
                    value = leaderName.ifBlank { "Select leader…" },
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    label = { Text(stringResource(R.string.squads_leader)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(leaderExpanded) },
                )
                DropdownMenu(expanded = leaderExpanded, onDismissRequest = { leaderExpanded = false }, modifier = Modifier.exposedDropdownSize()) {
                    if (state.members.isNotEmpty()) {
                        Text("Members", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp, 6.dp))
                        state.members.forEach { m ->
                            DropdownMenuItem(text = { Text(m.name) }, onClick = { viewModel.onLeaderChange(m.userId); leaderExpanded = false }, leadingIcon = { Icon(Icons.Filled.Group, null) })
                        }
                    }
                    if (state.agents.isNotEmpty()) {
                        Text("Agents", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(16.dp, 6.dp))
                        state.agents.forEach { a ->
                            DropdownMenuItem(text = { Text(a.name) }, onClick = { viewModel.onLeaderChange(a.id); leaderExpanded = false }, leadingIcon = { Icon(Icons.Filled.SmartToy, null) })
                        }
                    }
                }
            }
        }
    }
}
