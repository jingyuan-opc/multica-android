package ai.multica.android.ui.agents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.repository.AgentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateAgentViewModel @Inject constructor(
    private val agentRepository: AgentRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAgentUiState())
    val state: StateFlow<CreateAgentUiState> = _state.asStateFlow()

    init { loadRuntimes() }

    private fun loadRuntimes() {
        viewModelScope.launch {
            (agentRepository.listRuntimes() as? ApiResult.Success)?.data?.let { runtimes ->
                _state.update { it.copy(runtimes = runtimes) }
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onInstructionsChange(v: String) = _state.update { it.copy(instructions = v) }
    fun onModelChange(v: String) = _state.update { it.copy(model = v) }
    fun onRuntimeIdChange(v: String) = _state.update { it.copy(runtimeId = v, runtimeIdError = null) }
    fun onVisibilityChange(v: String) = _state.update { it.copy(visibility = v) }
    fun onConcurrencyChange(v: Int) = _state.update { it.copy(concurrency = v) }

    fun submit(onCreated: (String) -> Unit) {
        val s = _state.value
        if (s.name.isBlank()) {
            _state.update { it.copy(nameError = "Name is required") }
            return
        }
        if (s.runtimeId.isBlank()) {
            _state.update { it.copy(runtimeIdError = "Runtime ID is required") }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = agentRepository.create(
                name = s.name,
                runtimeId = s.runtimeId.trim(),
                description = s.description.takeIf { it.isNotBlank() },
                instructions = s.instructions.takeIf { it.isNotBlank() },
                model = s.model.takeIf { it.isNotBlank() },
                visibility = s.visibility,
                maxConcurrentTasks = s.concurrency,
            )) {
                is ApiResult.Success -> _state.update { it.copy(isSubmitting = false, createdId = r.data.id) }
                is ApiResult.HttpError -> _state.update { it.copy(isSubmitting = false, errorMessage = r.message) }
                is ApiResult.NetworkError -> _state.update { it.copy(isSubmitting = false, errorMessage = "Network error — try again") }
                is ApiResult.Unknown -> _state.update { it.copy(isSubmitting = false, errorMessage = "Unexpected error") }
            }
        }
    }
}

data class CreateAgentUiState(
    val name: String = "",
    val nameError: String? = null,
    val description: String = "",
    val instructions: String = "",
    val model: String = "",
    val runtimes: List<ai.multica.android.data.model.RuntimeDevice> = emptyList(),
    val runtimeId: String = "",
    val runtimeIdError: String? = null,
    val visibility: String = "workspace",
    val concurrency: Int = 1,
    val isSubmitting: Boolean = false,
    val createdId: String? = null,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && runtimeId.isNotBlank() && !isSubmitting
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAgentScreen(
    onBack: () -> Unit,
    onCreated: (agentId: String) -> Unit,
    viewModel: CreateAgentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.createdId) {
        state.createdId?.let { id -> onCreated(id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.agents_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.submit { id -> onBack(); onCreated(id) } },
                        enabled = state.canSubmit,
                    ) {
                        if (state.isSubmitting) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.common_ok))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = state.nameError != null,
                supportingText = state.nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            // Visibility: workspace vs private (mirrors web create-agent-dialog).
            Text("Visibility", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.visibility == "workspace",
                    onClick = { viewModel.onVisibilityChange("workspace") },
                    leadingIcon = { Icon(Icons.Filled.Public, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Workspace") },
                )
                FilterChip(
                    selected = state.visibility == "private",
                    onClick = { viewModel.onVisibilityChange("private") },
                    leadingIcon = { Icon(Icons.Filled.Lock, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Private") },
                )
            }
            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::onModelChange,
                label = { Text(stringResource(R.string.agents_model)) },
                placeholder = { Text("e.g. glm-4.6") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            RuntimeDropdown(state = state, onRuntimeIdChange = viewModel::onRuntimeIdChange)
            // Concurrency.
            Text("Max concurrent tasks: ${state.concurrency}", style = MaterialTheme.typography.labelLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 2, 4, 8).forEach { n ->
                    FilterChip(
                        selected = state.concurrency == n,
                        onClick = { viewModel.onConcurrencyChange(n) },
                        label = { Text(n.toString()) },
                    )
                }
            }
            OutlinedTextField(
                value = state.instructions,
                onValueChange = viewModel::onInstructionsChange,
                label = { Text("Instructions") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            if (state.errorMessage != null) {
                Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * Runtime picker. Mirrors the web create-agent runtime dropdown: lists the
 * runtimes visible in the current workspace (from GET /api/runtimes) and lets
 * the user pick one by display name. Falls back to a free-text field when the
 * workspace has no runtimes yet, so the form is still usable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuntimeDropdown(
    state: CreateAgentUiState,
    onRuntimeIdChange: (String) -> Unit,
) {
    val runtimes = state.runtimes
    if (runtimes.isEmpty()) {
        // No runtimes loaded yet: keep the editable field so the user is never
        // blocked by an empty list (e.g. a workspace with only built-in runtimes
        // that the list endpoint omits, or a transient load failure).
        OutlinedTextField(
            value = state.runtimeId,
            onValueChange = onRuntimeIdChange,
            label = { Text("Runtime ID *") },
            placeholder = { Text("Runtime this agent runs on") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            isError = state.runtimeIdError != null,
            supportingText = state.runtimeIdError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedName = runtimes.firstOrNull { it.id == state.runtimeId }?.name
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName ?: "",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text("Runtime *") },
            placeholder = { Text("Select a runtime") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            isError = state.runtimeIdError != null,
            supportingText = state.runtimeIdError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            runtimes.forEach { rt ->
                DropdownMenuItem(
                    text = { Text(rt.name) },
                    onClick = {
                        onRuntimeIdChange(rt.id)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
