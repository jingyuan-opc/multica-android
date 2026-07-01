package ai.multica.android.ui.squads

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.Squad
import ai.multica.android.data.model.SquadMember
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.data.repository.MemberRepository
import ai.multica.android.data.repository.SquadRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.MulticaAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SquadDetailViewModel @Inject constructor(
    private val squadRepository: SquadRepository,
    private val memberRepository: MemberRepository,
    private val agentRepository: AgentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val squadId: String = savedStateHandle.get<String>("id")
        ?: error("SquadDetailViewModel requires 'id' nav arg")

    private val _state = MutableStateFlow(SquadDetailUiState())
    val state: StateFlow<SquadDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val squadResult = squadRepository.get(squadId)
            val membersResult = squadRepository.listMembers(squadId)
            val squad = (squadResult as? ApiResult.Success)?.data
            val members = (membersResult as? ApiResult.Success)?.data ?: emptyList()
            // Resolve display names for members (members/agents).
            val wsId = squad?.workspaceId
            if (wsId != null) {
                if (_state.value.workspaceMembers.isEmpty()) {
                    (memberRepository.list(wsId) as? ApiResult.Success)?.data?.let { m ->
                        _state.update { it.copy(workspaceMembers = m) }
                    }
                }
                if (_state.value.workspaceAgents.isEmpty()) {
                    (agentRepository.list() as? ApiResult.Success)?.data?.let { a ->
                        _state.update { it.copy(workspaceAgents = a.filterNot { it.archivedAt != null }) }
                    }
                }
            }
            _state.update {
                it.copy(isLoading = false, squad = squad, members = members, errorMessage = when {
                    squadResult is ApiResult.HttpError -> squadResult.message
                    membersResult is ApiResult.HttpError -> membersResult.message
                    squadResult is ApiResult.NetworkError -> "Network error"
                    else -> null
                })
            }
        }
    }

    fun removeMember(memberType: String, memberId: String) {
        viewModelScope.launch {
            squadRepository.removeMember(squadId, memberType, memberId)
            refresh()
        }
    }

    fun addMember(memberType: String, memberId: String, role: String? = null) {
        viewModelScope.launch {
            squadRepository.addMember(squadId, memberType, memberId, role)
            refresh()
        }
    }

    fun updateMemberRole(memberType: String, memberId: String, role: String) {
        viewModelScope.launch {
            squadRepository.updateMemberRole(squadId, memberType, memberId, role)
            refresh()
        }
    }

    fun setLeader(leaderId: String) {
        viewModelScope.launch {
            squadRepository.update(squadId, ai.multica.android.data.dto.UpdateSquadRequest(leaderId = leaderId))
            refresh()
        }
    }

    fun updateSquad(
        name: String? = null,
        description: String? = null,
        instructions: String? = null,
    ) {
        viewModelScope.launch {
            squadRepository.update(
                squadId,
                ai.multica.android.data.dto.UpdateSquadRequest(
                    name = name?.trim(),
                    description = description?.trim(),
                    instructions = instructions?.trim(),
                ),
            )
            refresh()
        }
    }
}

data class SquadDetailUiState(
    val isLoading: Boolean = false,
    val squad: Squad? = null,
    val members: List<SquadMember> = emptyList(),
    val workspaceMembers: List<ai.multica.android.data.model.MemberWithUser> = emptyList(),
    val workspaceAgents: List<ai.multica.android.data.model.Agent> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SquadDetailScreen(
    onBack: () -> Unit,
    viewModel: SquadDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val squad = state.squad
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(squad?.name ?: stringResource(R.string.squads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && squad != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.errorMessage != null && squad == null ->
                    EmptyState(icon = Icons.Filled.Group, title = "Couldn't load squad", description = state.errorMessage)
                squad == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> SquadDetailContent(
                    state = state,
                    onRemoveMember = { memberType, memberId -> viewModel.removeMember(memberType, memberId) },
                    onAddMember = { memberType, memberId -> viewModel.addMember(memberType, memberId) },
                    onSetLeader = { leaderId -> viewModel.setLeader(leaderId) },
                )
            }
        }
    }
    // Edit squad dialog (name / description / instructions).
    if (showEditDialog) {
        SquadEditDialog(
            squad = squad,
            onConfirm = { name, description, instructions ->
                viewModel.updateSquad(name = name, description = description, instructions = instructions)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }
}

@Composable
private fun SquadEditDialog(
    squad: ai.multica.android.data.model.Squad?,
    onConfirm: (String, String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(squad?.name.orEmpty()) }
    var description by remember { mutableStateOf(squad?.description.orEmpty()) }
    var instructions by remember { mutableStateOf(squad?.instructions.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit squad") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text(stringResource(R.string.squads_instructions)) }, minLines = 3, maxLines = 10, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, description, instructions) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun SquadDetailContent(
    state: SquadDetailUiState,
    onRemoveMember: (String, String) -> Unit,
    onAddMember: (String, String) -> Unit,
    onSetLeader: (String) -> Unit,
) {
    val squad = state.squad!!
    var showAddMember by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Group, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(squad.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${squad.memberCount} ${stringResource(R.string.squads_members)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (squad.description.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(squad.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.squads_members), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showAddMember = true }) { Text(stringResource(R.string.squads_add_member)) }
            }
        }
        if (state.members.isEmpty()) {
            item { EmptyState(icon = Icons.Filled.Group, title = "No members") }
        } else {
            items(state.members, key = { it.id }) { member ->
                SquadMemberRow(
                    member = member,
                    state = state,
                    isLeader = squad.leaderId == member.memberId,
                    onRemove = { onRemoveMember(member.memberType, member.memberId) },
                    onSetLeader = { onSetLeader(member.memberId) },
                )
            }
        }
        if (squad.instructions.isNotBlank()) {
            item {
                HorizontalDivider()
                Text(stringResource(R.string.squads_instructions), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(squad.instructions, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    if (showAddMember) {
        AddSquadMemberSheet(
            state = state,
            existingMemberIds = state.members.map { it.memberId }.toSet(),
            onAdd = { memberType, memberId -> onAddMember(memberType, memberId); showAddMember = false },
            onDismiss = { showAddMember = false },
        )
    }
}

@Composable
private fun SquadMemberRow(
    member: SquadMember,
    state: SquadDetailUiState,
    isLeader: Boolean,
    onRemove: () -> Unit,
    onSetLeader: () -> Unit,
) {
    val isAgent = member.memberType == "agent"
    val name = if (isAgent) {
        state.workspaceAgents.firstOrNull { it.id == member.memberId }?.name ?: "Agent"
    } else {
        state.workspaceMembers.firstOrNull { it.userId == member.memberId }?.name ?: "Member"
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isAgent) {
                Icon(Icons.Filled.SmartToy, null)
            } else {
                MulticaAvatar(name = name, avatarUrl = state.workspaceMembers.firstOrNull { it.userId == member.memberId }?.avatarUrl, size = 32.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (isLeader) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.StarBorder, contentDescription = "Leader", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (member.role.isNotBlank()) {
                    Text(member.role, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!isLeader) {
                TextButton(onClick = onSetLeader) {
                    Text(stringResource(R.string.squads_make_leader), style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(onClick = onRemove) {
                Text(stringResource(R.string.squads_remove_member), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddSquadMemberSheet(
    state: SquadDetailUiState,
    existingMemberIds: Set<String>,
    onAdd: (memberType: String, memberId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val availableMembers = state.workspaceMembers.filter { it.userId !in existingMemberIds }
    val availableAgents = state.workspaceAgents.filter { it.id !in existingMemberIds }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.squads_add_member), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            if (availableMembers.isEmpty() && availableAgents.isEmpty()) {
                Text("No candidates available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            if (availableMembers.isNotEmpty()) {
                Text("Members", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                availableMembers.forEach { m ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAdd("member", m.userId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MulticaAvatar(name = m.name, avatarUrl = m.avatarUrl, size = 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.bodyLarge)
                            Text(m.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (availableAgents.isNotEmpty()) {
                Text("Agents", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                availableAgents.forEach { a ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAdd("agent", a.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.SmartToy, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(a.name, style = MaterialTheme.typography.bodyLarge)
                            if (a.model.isNotBlank()) Text(a.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
