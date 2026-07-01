package ai.multica.android.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.theme.PriorityColors
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.MemberWithUser
import ai.multica.android.data.model.PinnedItem
import ai.multica.android.data.model.PinnedItemType
import ai.multica.android.data.model.Project
import ai.multica.android.data.model.ProjectPriority
import ai.multica.android.data.model.ProjectStatus
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.data.repository.MemberRepository
import ai.multica.android.data.repository.PinRepository
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.PriorityBars
import ai.multica.android.ui.components.ProjectStatusChip
import ai.multica.android.ui.components.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val issueRepository: IssueRepository,
    private val memberRepository: MemberRepository,
    private val agentRepository: ai.multica.android.data.repository.AgentRepository,
    private val pinRepository: PinRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val projectId: String = savedStateHandle.get<String>("id")
        ?: error("ProjectDetailViewModel requires 'id' nav arg")

    private val _state = MutableStateFlow(ProjectDetailUiState())
    val state: StateFlow<ProjectDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val projectResult = projectRepository.get(projectId)
            val issuesResult = issueRepository.list(projectId = projectId, limit = 50)
            val project = (projectResult as? ApiResult.Success)?.data
            val issues = (issuesResult as? ApiResult.Success)?.data?.issues ?: emptyList()

            // Load members + agents (for lead picker) lazily.
            if (project != null && _state.value.members.isEmpty()) {
                (memberRepository.list(project.workspaceId) as? ApiResult.Success)?.data?.let { members ->
                    _state.update { it.copy(members = members) }
                }
            }
            if (project != null && _state.value.agents.isEmpty()) {
                (agentRepository.list() as? ApiResult.Success)?.data?.let { agents ->
                    _state.update { it.copy(agents = agents.filterNot { a -> a.archivedAt != null }) }
                }
            }
            // Pin status.
            if (_state.value.pins == null) {
                (pinRepository.list() as? ApiResult.Success)?.data?.let { pins ->
                    _state.update { it.copy(pins = pins) }
                }
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    project = project,
                    issues = issues,
                    errorMessage = when {
                        projectResult is ApiResult.HttpError -> projectResult.message
                        issuesResult is ApiResult.HttpError -> issuesResult.message
                        projectResult is ApiResult.NetworkError -> "Network error"
                        else -> null
                    },
                )
            }
        }
    }

    fun updateStatus(status: ProjectStatus) {
        viewModelScope.launch {
            when (val r = projectRepository.update(projectId, status = status)) {
                is ApiResult.Success -> _state.update { it.copy(project = r.data) }
                else -> Unit
            }
        }
    }

    fun updatePriority(priority: ProjectPriority) {
        viewModelScope.launch {
            when (val r = projectRepository.update(projectId, priority = priority)) {
                is ApiResult.Success -> _state.update { it.copy(project = r.data) }
                else -> Unit
            }
        }
    }

    fun updateTitle(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            when (val r = projectRepository.update(projectId, title = title.trim())) {
                is ApiResult.Success -> _state.update { it.copy(project = r.data) }
                else -> Unit
            }
        }
    }

    fun updateDescription(description: String) {
        viewModelScope.launch {
            when (val r = projectRepository.update(projectId, description = description)) {
                is ApiResult.Success -> _state.update { it.copy(project = r.data, isEditingDescription = false) }
                else -> Unit
            }
        }
    }

    fun updateLead(leadType: ai.multica.android.data.model.ProjectLeadType?, leadId: String?) {
        viewModelScope.launch {
            when (val r = projectRepository.update(projectId, leadType = leadType, leadId = leadId)) {
                is ApiResult.Success -> _state.update { it.copy(project = r.data) }
                else -> Unit
            }
        }
    }

    fun setEditingDescription(editing: Boolean) {
        _state.update {
            it.copy(isEditingDescription = editing, descriptionDraft = it.project?.description.orEmpty())
        }
    }

    fun onDescriptionDraftChange(value: String) {
        _state.update { it.copy(descriptionDraft = value) }
    }

    fun togglePin() {
        viewModelScope.launch {
            val isPinned = _state.value.isPinned
            val result = if (isPinned) pinRepository.delete(PinnedItemType.PROJECT, projectId)
            else pinRepository.create(PinnedItemType.PROJECT, projectId)
            if (result is ApiResult.Success) {
                (pinRepository.list() as? ApiResult.Success)?.data?.let { pins ->
                    _state.update { it.copy(pins = pins) }
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (projectRepository.delete(projectId)) {
                is ApiResult.Success -> onDeleted()
                else -> Unit
            }
        }
    }
}

data class ProjectDetailUiState(
    val isLoading: Boolean = false,
    val project: Project? = null,
    val issues: List<Issue> = emptyList(),
    val members: List<MemberWithUser> = emptyList(),
    val agents: List<ai.multica.android.data.model.Agent> = emptyList(),
    val pins: List<PinnedItem>? = null,
    val isEditingDescription: Boolean = false,
    val descriptionDraft: String = "",
    val errorMessage: String? = null,
) {
    val isPinned: Boolean
        get() = pins?.any { it.itemType == PinnedItemType.PROJECT && it.itemId == project?.id } == true
    val progress: Float
        get() {
            val total = project?.issueCount ?: 0
            val done = project?.doneCount ?: 0
            return if (total > 0) done.toFloat() / total else 0f
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showStatusPicker by remember { mutableStateOf(false) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    var showLeadPicker by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.project?.title ?: stringResource(R.string.projects_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.pins_add),
                            tint = if (state.isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { showActions = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.project_delete)) },
                            onClick = { showActions = false; showDeleteConfirm = true },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        )
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.errorMessage != null && state.project == null -> {
                    EmptyState(
                        icon = Icons.Filled.BugReport,
                        title = "Couldn't load project",
                        description = state.errorMessage,
                    )
                }
                state.project == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                }
                else -> ProjectDetailContent(
                    state = state,
                    onOpenIssue = onOpenIssue,
                    onStatusClick = { showStatusPicker = true },
                    onPriorityClick = { showPriorityPicker = true },
                    onLeadClick = { showLeadPicker = true },
                    onStartEditDescription = viewModel::setEditingDescription,
                    onDescriptionChange = viewModel::onDescriptionDraftChange,
                    onDescriptionSave = { viewModel.updateDescription(state.descriptionDraft) },
                    onCancelEditDescription = { viewModel.setEditingDescription(false) },
                )
            }
        }
    }

    if (showStatusPicker) {
        ProjectStatusPickerSheet(state.project?.status, viewModel::updateStatus) { showStatusPicker = false }
    }
    if (showPriorityPicker) {
        ProjectPriorityPickerSheet(state.project?.priority, viewModel::updatePriority) { showPriorityPicker = false }
    }
    if (showLeadPicker) {
        ProjectLeadPickerSheet(
            currentLeadType = state.project?.leadType,
            currentLeadId = state.project?.leadId,
            members = state.members,
            agents = state.agents,
            onPick = { t, id -> viewModel.updateLead(t, id); showLeadPicker = false },
            onDismiss = { showLeadPicker = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.project_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(onBack) }) {
                    Text(stringResource(R.string.project_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun ProjectDetailContent(
    state: ProjectDetailUiState,
    onOpenIssue: (String) -> Unit,
    onStatusClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onLeadClick: () -> Unit,
    onStartEditDescription: (Boolean) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDescriptionSave: () -> Unit,
    onCancelEditDescription: () -> Unit,
) {
    val project = state.project!!
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = project.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Tappable status chip.
                    Surface(onClick = onStatusClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            ProjectStatusChip(status = project.status)
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Surface(onClick = onPriorityClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            PriorityBars(priority = project.priority.toIssuePriority())
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = project.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                // Lead row (tappable).
                Spacer(Modifier.height(8.dp))
                val leadLabel = when (project.leadType) {
                    ai.multica.android.data.model.ProjectLeadType.MEMBER ->
                        state.members.firstOrNull { it.userId == project.leadId }?.name ?: "Member"
                    ai.multica.android.data.model.ProjectLeadType.AGENT ->
                        state.agents.firstOrNull { it.id == project.leadId }?.name ?: "Agent"
                    null -> stringResource(R.string.issue_unassigned)
                }
                Surface(onClick = onLeadClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${stringResource(R.string.project_lead)}: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(leadLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (!project.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        onClick = { onStartEditDescription(true) },
                    ) {
                        Text(
                            text = project.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { onStartEditDescription(true) }) { Text("+ Add description") }
                }
                if (state.isEditingDescription) {
                    Column {
                        OutlinedTextField(
                            value = state.descriptionDraft,
                            onValueChange = onDescriptionChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Description") },
                            minLines = 2,
                            maxLines = 6,
                        )
                        Row {
                            TextButton(onClick = onDescriptionSave) { Text("Save") }
                            TextButton(onClick = onCancelEditDescription) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stat(label = "Issues", value = project.issueCount.toString())
                    Stat(label = "Done", value = project.doneCount.toString())
                    Stat(label = "Resources", value = project.resourceCount.toString())
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(8.dp).background(PriorityColors.forProjectPriority(project.priority), CircleShape))
                }
                // Progress bar.
                if (project.issueCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    Column {
                        Text(
                            text = "${stringResource(R.string.project_progress)}: ${project.doneCount}/${project.issueCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "Issues (${state.issues.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (state.issues.isEmpty()) {
            item { EmptyState(icon = Icons.Filled.BugReport, title = "No issues in this project") }
        } else {
            items(state.issues, key = { it.id }) { issue ->
                IssueCardCompact(issue = issue, onClick = { onOpenIssue(issue.id) })
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun IssueCardCompact(issue: Issue, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PriorityBars(priority = issue.priority)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(issue.identifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(status = issue.status)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectStatusPickerSheet(current: ProjectStatus?, onPick: (ProjectStatus) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Change status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            ProjectStatus.entries.forEach { status ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(status) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ProjectStatusChip(status = status)
                    Spacer(Modifier.width(12.dp))
                    Text(status.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (status == current) Icon(Icons.Filled.Check, "Current", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPriorityPickerSheet(current: ProjectPriority?, onPick: (ProjectPriority) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Change priority", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            ProjectPriority.ORDER.forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onPick(p) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBars(priority = p.toIssuePriority())
                    Spacer(Modifier.width(12.dp))
                    Text(p.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (p == current) Icon(Icons.Filled.Check, "Current", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

/** Map ProjectPriority → IssuePriority so PriorityBars can render. */
private fun ProjectPriority.toIssuePriority() = when (this) {
    ProjectPriority.URGENT -> ai.multica.android.data.model.IssuePriority.URGENT
    ProjectPriority.HIGH -> ai.multica.android.data.model.IssuePriority.HIGH
    ProjectPriority.MEDIUM -> ai.multica.android.data.model.IssuePriority.MEDIUM
    ProjectPriority.LOW -> ai.multica.android.data.model.IssuePriority.LOW
    ProjectPriority.NONE -> ai.multica.android.data.model.IssuePriority.NONE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectLeadPickerSheet(
    currentLeadType: ai.multica.android.data.model.ProjectLeadType?,
    currentLeadId: String?,
    members: List<MemberWithUser>,
    agents: List<ai.multica.android.data.model.Agent>,
    onPick: (ai.multica.android.data.model.ProjectLeadType?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).then(Modifier.heightIn(max = 480.dp)).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.project_lead), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            LeadOptionRow(
                label = stringResource(R.string.issue_unassigned),
                selected = currentLeadType == null,
                onClick = { onPick(null, null) },
            )
            if (members.isNotEmpty()) {
                Text("Members", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                members.forEach { m ->
                    LeadOptionRow(
                        label = m.name,
                        subtitle = m.email,
                        selected = currentLeadType == ai.multica.android.data.model.ProjectLeadType.MEMBER && currentLeadId == m.userId,
                        leading = { ai.multica.android.ui.components.MulticaAvatar(name = m.name, avatarUrl = m.avatarUrl, size = 32.dp) },
                        onClick = { onPick(ai.multica.android.data.model.ProjectLeadType.MEMBER, m.userId) },
                    )
                }
            }
            if (agents.isNotEmpty()) {
                Text("Agents", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                agents.forEach { a ->
                    LeadOptionRow(
                        label = a.name,
                        subtitle = a.model.ifBlank { null },
                        selected = currentLeadType == ai.multica.android.data.model.ProjectLeadType.AGENT && currentLeadId == a.id,
                        leading = { Icon(Icons.Filled.SmartToy, null) },
                        onClick = { onPick(ai.multica.android.data.model.ProjectLeadType.AGENT, a.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LeadOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}
