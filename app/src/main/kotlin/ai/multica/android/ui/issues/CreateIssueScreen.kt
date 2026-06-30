package ai.multica.android.ui.issues

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.auth.WorkspaceStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.Agent
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.MemberWithUser
import ai.multica.android.data.model.Project
import ai.multica.android.data.model.Squad
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.data.repository.MemberRepository
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.data.repository.SquadRepository
import ai.multica.android.realtime.WsEvent
import ai.multica.android.ui.components.MulticaAvatar
import ai.multica.android.ui.components.PriorityBars
import ai.multica.android.ui.components.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AssigneeType { NONE, MEMBER, AGENT, SQUAD }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateIssueScreen(
    onBack: () -> Unit,
    onCreated: (issueId: String) -> Unit,
    viewModel: CreateIssueViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New issue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            keyboard?.hide()
                            viewModel.submit()
                        },
                        enabled = state.canSubmit,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Create")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Title
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title *") },
                placeholder = { Text("What's the issue?") },
                isError = state.titleError != null,
                supportingText = {
                    if (state.titleError != null) {
                        Text(state.titleError!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
            )

            // Description
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                placeholder = { Text("Add more details (markdown supported)") },
                minLines = 4,
                maxLines = 8,
            )

            // Status
            Text("Status", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IssueStatus.entries.forEach { status ->
                    FilterChip(
                        selected = state.status == status,
                        onClick = { viewModel.onStatusChange(status) },
                        label = { Text(labelForStatus(status), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            // Priority
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IssuePriority.ORDER.forEach { p ->
                    FilterChip(
                        selected = state.priority == p,
                        onClick = { viewModel.onPriorityChange(p) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PriorityBars(priority = p)
                                Spacer(Modifier.width(4.dp))
                                Text(p.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        },
                    )
                }
            }

            // Project (dropdown)
            if (state.projects.isNotEmpty()) {
                var projectExpanded by remember { mutableStateOf(false) }
                val selectedProject = state.projects.firstOrNull { it.id == state.projectId }
                ExposedDropdownMenuBox(
                    expanded = projectExpanded,
                    onExpandedChange = { projectExpanded = it },
                ) {
                    OutlinedTextField(
                        value = selectedProject?.title ?: "None",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Project") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = projectExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = projectExpanded,
                        onDismissRequest = { projectExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("None") },
                            onClick = {
                                viewModel.onProjectChange(null)
                                projectExpanded = false
                            },
                        )
                        state.projects.forEach { project ->
                            DropdownMenuItem(
                                text = { Text(project.title) },
                                onClick = {
                                    viewModel.onProjectChange(project.id)
                                    projectExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            // Assignee (dropdown)
            var assigneeExpanded by remember { mutableStateOf(false) }
            val selectedAssigneeLabel = when (state.assigneeType) {
                AssigneeType.NONE -> "Unassigned"
                AssigneeType.MEMBER -> state.members.firstOrNull { it.userId == state.assigneeId }?.name ?: "Member"
                AssigneeType.AGENT -> state.agents.firstOrNull { it.id == state.assigneeId }?.name ?: "Agent"
                AssigneeType.SQUAD -> state.squads.firstOrNull { it.id == state.assigneeId }?.name ?: "Squad"
            }
            ExposedDropdownMenuBox(
                expanded = assigneeExpanded,
                onExpandedChange = { assigneeExpanded = it },
            ) {
                OutlinedTextField(
                    value = selectedAssigneeLabel,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Assignee") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = assigneeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = assigneeExpanded,
                    onDismissRequest = { assigneeExpanded = false },
                ) {
                    // Unassigned
                    DropdownMenuItem(
                        text = { Text("Unassigned") },
                        onClick = {
                            viewModel.onAssigneeTypeChange(AssigneeType.NONE)
                            assigneeExpanded = false
                        },
                    )
                    HorizontalDivider()
                    // Members
                    if (state.members.isNotEmpty()) {
                        state.members.forEach { member ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        MulticaAvatar(name = member.name, avatarUrl = member.avatarUrl, size = 24.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(member.name, style = MaterialTheme.typography.bodyMedium)
                                            Text(member.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.onAssigneeChange(AssigneeType.MEMBER, member.userId)
                                    assigneeExpanded = false
                                },
                            )
                        }
                    }
                    // Agents
                    if (state.agents.isNotEmpty()) {
                        HorizontalDivider()
                        state.agents.forEach { agent ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.SmartToy, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(agent.name, style = MaterialTheme.typography.bodyMedium)
                                            if (agent.model.isNotBlank()) {
                                                Text(agent.model, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.onAssigneeChange(AssigneeType.AGENT, agent.id)
                                    assigneeExpanded = false
                                },
                            )
                        }
                    }
                    // Squads
                    if (state.squads.isNotEmpty()) {
                        HorizontalDivider()
                        state.squads.forEach { squad ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Group, contentDescription = null, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Column {
                                            Text(squad.name, style = MaterialTheme.typography.bodyMedium)
                                            Text("${squad.memberCount} member${if (squad.memberCount != 1) "s" else ""}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.onAssigneeChange(AssigneeType.SQUAD, squad.id)
                                    assigneeExpanded = false
                                },
                            )
                        }
                    }
                }
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }

    LaunchedEffect(state.createdIssueId) {
        if (state.createdIssueId != null) {
            onCreated(state.createdIssueId!!)
        }
    }
}

private data class AssigneeCounts(
    val members: Int,
    val agents: Int,
    val squads: Int,
)

@Composable
private fun AssigneeTypeSegment(
    selected: AssigneeType,
    onSelect: (AssigneeType) -> Unit,
    counts: AssigneeCounts,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(2.dp),
    ) {
        AssigneeType.entries.forEach { type ->
            val (label, count) = when (type) {
                AssigneeType.NONE -> "Unassigned" to null
                AssigneeType.MEMBER -> "Members" to counts.members
                AssigneeType.AGENT -> "Agents" to counts.agents
                AssigneeType.SQUAD -> "Squads" to counts.squads
            }
            val isSelected = selected == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(type) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    if (count != null) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "($count)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssigneeList(
    type: AssigneeType,
    members: List<MemberWithUser>,
    agents: List<Agent>,
    squads: List<Squad>,
    selectedMemberId: String?,
    onUnassign: () -> Unit,
    onSelectMember: (String) -> Unit,
    onSelectAgent: (String) -> Unit,
    onSelectSquad: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (type) {
            AssigneeType.NONE -> AssigneeRow(
                avatar = null,
                name = "Unassigned",
                email = null,
                icon = null,
                selected = true,
                onClick = onUnassign,
            )
            AssigneeType.MEMBER -> {
                if (members.isEmpty()) {
                    EmptyText("No members in this workspace")
                } else {
                    members.forEach { member ->
                        AssigneeRow(
                            avatar = member.avatarUrl,
                            name = member.name,
                            email = member.email,
                            icon = null,
                            selected = member.userId == selectedMemberId,
                            onClick = { onSelectMember(member.userId) },
                        )
                    }
                }
            }
            AssigneeType.AGENT -> {
                if (agents.isEmpty()) {
                    EmptyText("No agents in this workspace")
                } else {
                    agents.forEach { agent ->
                        AssigneeRow(
                            avatar = agent.avatarUrl,
                            name = agent.name,
                            email = agent.model.ifBlank { null },
                            icon = Icons.Filled.SmartToy,
                            selected = agent.id == selectedMemberId,
                            onClick = { onSelectAgent(agent.id) },
                        )
                    }
                }
            }
            AssigneeType.SQUAD -> {
                if (squads.isEmpty()) {
                    EmptyText("No squads in this workspace")
                } else {
                    squads.forEach { squad ->
                        AssigneeRow(
                            avatar = squad.avatarUrl,
                            name = squad.name,
                            email = "${squad.memberCount} member${if (squad.memberCount != 1) "s" else ""}",
                            icon = Icons.Filled.Group,
                            selected = squad.id == selectedMemberId,
                            onClick = { onSelectSquad(squad.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun ProjectPickerRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AssigneeRow(
    avatar: String?,
    name: String,
    email: String?,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(4.dp))
        if (icon != null) {
            // Agent / Squad: draw a tinted circle with an icon, no avatar photo.
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            MulticaAvatar(name = name, avatarUrl = avatar, size = 28.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            if (!email.isNullOrBlank()) {
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@HiltViewModel
class CreateIssueViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
    private val memberRepository: MemberRepository,
    private val agentRepository: AgentRepository,
    private val squadRepository: SquadRepository,
    private val workspaceStore: WorkspaceStore,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateIssueUiState())
    val state: StateFlow<CreateIssueUiState> = _state.asStateFlow()

    init {
        loadProjects()
        loadMembers()
        loadAgents()
        loadSquads()
    }

    private fun loadProjects() {
        viewModelScope.launch {
            when (val r = projectRepository.list()) {
                is ApiResult.Success -> _state.update { it.copy(projects = r.data.projects) }
                else -> Unit
            }
        }
    }

    private fun loadMembers() {
        val workspaceId = workspaceStore.getId() ?: return
        viewModelScope.launch {
            when (val r = memberRepository.list(workspaceId)) {
                is ApiResult.Success -> _state.update { it.copy(members = r.data) }
                else -> Unit
            }
        }
    }

    private fun loadAgents() {
        viewModelScope.launch {
            when (val r = agentRepository.list()) {
                is ApiResult.Success -> _state.update {
                    it.copy(agents = r.data.filter { a -> a.archivedAt == null })
                }
                else -> Unit
            }
        }
    }

    private fun loadSquads() {
        viewModelScope.launch {
            when (val r = squadRepository.list()) {
                is ApiResult.Success -> _state.update { it.copy(squads = r.data) }
                else -> Unit
            }
        }
    }

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value, titleError = null) }
    }
    fun onDescriptionChange(value: String) {
        _state.update { it.copy(description = value) }
    }
    fun onStatusChange(status: IssueStatus) = _state.update { it.copy(status = status) }
    fun onPriorityChange(priority: IssuePriority) = _state.update { it.copy(priority = priority) }
    fun onProjectChange(projectId: String?) = _state.update { it.copy(projectId = projectId) }

    fun onAssigneeTypeChange(type: AssigneeType) = _state.update {
        // Switching type clears the previous id (member/agent/squad ids
        // are from different namespaces).
        it.copy(assigneeType = type, assigneeId = null)
    }

    fun onAssigneeChange(type: AssigneeType, id: String) = _state.update {
        it.copy(assigneeType = type, assigneeId = id)
    }

    fun submit() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(titleError = "Title is required") }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val assigneeEnum: ai.multica.android.data.model.IssueAssigneeType? = when (s.assigneeType) {
                AssigneeType.NONE, AssigneeType.MEMBER -> ai.multica.android.data.model.IssueAssigneeType.MEMBER
                AssigneeType.AGENT -> ai.multica.android.data.model.IssueAssigneeType.AGENT
                AssigneeType.SQUAD -> ai.multica.android.data.model.IssueAssigneeType.SQUAD
            }
            val result = issueRepository.create(
                title = s.title,
                description = s.description.takeIf { it.isNotBlank() },
                status = s.status,
                priority = s.priority,
                projectId = s.projectId,
                assigneeType = if (s.assigneeType == AssigneeType.NONE) null else assigneeEnum,
                assigneeId = s.assigneeId,
            )
            when (result) {
                is ApiResult.Success -> _state.update {
                    it.copy(isSubmitting = false, createdIssueId = result.data.id)
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(isSubmitting = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(isSubmitting = false, errorMessage = "Network error — try again")
                }
                is ApiResult.Unknown -> _state.update {
                    it.copy(isSubmitting = false, errorMessage = "Unexpected error")
                }
            }
        }
    }
}

data class CreateIssueUiState(
    val title: String = "",
    val titleError: String? = null,
    val description: String = "",
    val status: IssueStatus = IssueStatus.TODO,
    val priority: IssuePriority = IssuePriority.NONE,
    val projectId: String? = null,
    val projects: List<Project> = emptyList(),
    val assigneeType: AssigneeType = AssigneeType.NONE,
    val assigneeId: String? = null,
    val members: List<MemberWithUser> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val squads: List<Squad> = emptyList(),
    val isSubmitting: Boolean = false,
    val createdIssueId: String? = null,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && !isSubmitting
}

private fun labelForStatus(s: IssueStatus): String = when (s) {
    IssueStatus.BACKLOG -> "Backlog"
    IssueStatus.TODO -> "Todo"
    IssueStatus.IN_PROGRESS -> "In progress"
    IssueStatus.IN_REVIEW -> "In review"
    IssueStatus.DONE -> "Done"
    IssueStatus.BLOCKED -> "Blocked"
    IssueStatus.CANCELLED -> "Cancelled"
}
