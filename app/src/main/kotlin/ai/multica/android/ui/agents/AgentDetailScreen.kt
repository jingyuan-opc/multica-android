package ai.multica.android.ui.agents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SmartToy
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
import ai.multica.android.data.model.Agent
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.comments.MarkdownText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AgentDetailViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val agentId: String = savedStateHandle.get<String>("id")
        ?: error("AgentDetailViewModel requires 'id' nav arg")

    private val _state = MutableStateFlow(AgentDetailUiState())
    val state: StateFlow<AgentDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = agentRepository.get(agentId)) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, agent = result.data) }
                is ApiResult.HttpError -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.NetworkError -> _state.update { it.copy(isLoading = false, errorMessage = "Network error") }
                is ApiResult.Unknown -> _state.update { it.copy(isLoading = false, errorMessage = "Unexpected error") }
            }
        }
    }

    fun archive() {
        viewModelScope.launch {
            when (val r = agentRepository.archive(agentId)) {
                is ApiResult.Success -> _state.update { it.copy(agent = r.data) }
                else -> Unit
            }
        }
    }

    fun restore() {
        viewModelScope.launch {
            when (val r = agentRepository.restore(agentId)) {
                is ApiResult.Success -> _state.update { it.copy(agent = r.data) }
                else -> Unit
            }
        }
    }

    fun update(
        name: String? = null,
        description: String? = null,
        instructions: String? = null,
        model: String? = null,
        visibility: String? = null,
        maxConcurrentTasks: Int? = null,
    ) {
        viewModelScope.launch {
            val body = ai.multica.android.data.dto.UpdateAgentRequest(
                name = name?.trim(),
                description = description?.trim(),
                instructions = instructions?.trim(),
                model = model,
                visibility = visibility,
                maxConcurrentTasks = maxConcurrentTasks,
            )
            when (val r = agentRepository.update(agentId, body)) {
                is ApiResult.Success -> _state.update { it.copy(agent = r.data) }
                else -> Unit
            }
        }
    }
}

data class AgentDetailUiState(
    val isLoading: Boolean = false,
    val agent: Agent? = null,
    val errorMessage: String? = null,
)

enum class AgentEditField { NAME, DESCRIPTION, MODEL, VISIBILITY, CONCURRENCY, INSTRUCTIONS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(
    onBack: () -> Unit,
    viewModel: AgentDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val agent = state.agent
    // Which property is being edited (null = none).
    var editingField by remember { mutableStateOf<AgentEditField?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(agent?.name ?: stringResource(R.string.agents_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (agent != null) {
                        if (agent.archivedAt != null) {
                            IconButton(onClick = viewModel::restore) {
                                Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.agents_restore))
                            }
                        } else {
                            IconButton(onClick = viewModel::archive) {
                                Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.agents_archive))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && agent != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.errorMessage != null && agent == null ->
                    EmptyState(icon = Icons.Filled.SmartToy, title = "Couldn't load agent", description = state.errorMessage)
                agent == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> AgentDetailContent(
                    agent = agent,
                    onEditName = { editingField = AgentEditField.NAME },
                    onEditDescription = { editingField = AgentEditField.DESCRIPTION },
                    onEditModel = { editingField = AgentEditField.MODEL },
                    onEditVisibility = { editingField = AgentEditField.VISIBILITY },
                    onEditConcurrency = { editingField = AgentEditField.CONCURRENCY },
                    onEditInstructions = { editingField = AgentEditField.INSTRUCTIONS },
                )
            }
        }
    }
    // Edit dialog.
    val ag = agent
    editingField?.let { field ->
        when (field) {
            AgentEditField.VISIBILITY -> {
                // Toggle between workspace/private via a simple choice dialog.
                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = { Text("Visibility") },
                    text = {
                        Column {
                            listOf("workspace", "private").forEach { v ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { viewModel.update(visibility = v); editingField = null }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(v, modifier = Modifier.weight(1f))
                                    if (ag?.visibility == v) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { editingField = null }) { Text(stringResource(R.string.common_cancel)) } },
                    dismissButton = {},
                )
            }
            AgentEditField.CONCURRENCY -> {
                var n by remember { mutableStateOf((ag?.maxConcurrentTasks ?: 1).toString()) }
                AlertDialog(
                    onDismissRequest = { editingField = null },
                    title = { Text(stringResource(R.string.agents_concurrency)) },
                    text = {
                        OutlinedTextField(value = n, onValueChange = { s -> s.filter { it.isDigit() }.let { n = it } }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    },
                    confirmButton = { TextButton(onClick = { viewModel.update(maxConcurrentTasks = n.toIntOrNull() ?: 1); editingField = null }) { Text(stringResource(R.string.common_ok)) } },
                    dismissButton = { TextButton(onClick = { editingField = null }) { Text(stringResource(R.string.common_cancel)) } },
                )
            }
            else -> {
                val title: String
                val initial: String
                val multiline: Boolean
                when (field) {
                    AgentEditField.NAME -> { title = "Name"; initial = ag?.name.orEmpty(); multiline = false }
                    AgentEditField.DESCRIPTION -> { title = "Description"; initial = ag?.description.orEmpty(); multiline = true }
                    AgentEditField.MODEL -> { title = stringResource(R.string.agents_model); initial = ag?.model.orEmpty(); multiline = false }
                    AgentEditField.INSTRUCTIONS -> { title = stringResource(R.string.agents_instructions); initial = ag?.instructions.orEmpty(); multiline = true }
                    else -> return@let
                }
                AgentTextEditDialog(
                    title = title,
                    initialValue = initial,
                    multiline = multiline,
                    onConfirm = { newValue ->
                        when (field) {
                            AgentEditField.NAME -> viewModel.update(name = newValue)
                            AgentEditField.DESCRIPTION -> viewModel.update(description = newValue)
                            AgentEditField.MODEL -> viewModel.update(model = newValue)
                            AgentEditField.INSTRUCTIONS -> viewModel.update(instructions = newValue)
                            else -> Unit
                        }
                    },
                    onDismiss = { editingField = null },
                )
            }
        }
    }
}

@Composable
private fun AgentDetailContent(
    agent: Agent,
    onEditName: () -> Unit,
    onEditDescription: () -> Unit,
    onEditModel: () -> Unit,
    onEditVisibility: () -> Unit,
    onEditConcurrency: () -> Unit,
    onEditInstructions: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Identity card.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.SmartToy, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(agent.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                StatusDot(status = agent.status)
            }
        }
        if (agent.description.isNotBlank()) {
            Text(agent.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            TextButton(onClick = onEditDescription) { Text("+ Add description") }
        }
        HorizontalDivider()
        // Properties (tappable to edit).
        PropertyRow(label = stringResource(R.string.agents_model), value = agent.model.ifBlank { "—" }, onClick = onEditModel)
        PropertyRow(label = stringResource(R.string.agents_runtime), value = agent.runtimeMode, onClick = null)
        PropertyRow(label = "Visibility", value = agent.visibility, onClick = onEditVisibility)
        PropertyRow(label = stringResource(R.string.agents_concurrency), value = agent.maxConcurrentTasks.toString(), onClick = onEditConcurrency)
        if (agent.thinkingLevel.isNotBlank()) {
            PropertyRow(label = "Thinking", value = agent.thinkingLevel, onClick = null)
        }
        // Instructions.
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.agents_instructions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onEditInstructions) { Text("Edit") }
        }
        if (agent.instructions.isNotBlank()) {
            MarkdownText(
                text = agent.instructions,
                textColor = MaterialTheme.colorScheme.onSurface,
                linkColor = MaterialTheme.colorScheme.primary,
                codeColor = MaterialTheme.colorScheme.tertiary,
            )
        } else {
            Text("(no instructions)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.then(Modifier.clickable(onClick = onClick)) else it }.padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A generic text-edit dialog for an agent property. */
@Composable
private fun AgentTextEditDialog(
    title: String,
    initialValue: String,
    multiline: Boolean = false,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                maxLines = if (multiline) 10 else 1,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value); onDismiss() }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
