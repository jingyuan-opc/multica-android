package ai.multica.android.ui.autopilots

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Webhook
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
import ai.multica.android.data.model.AutopilotAssigneeType
import ai.multica.android.data.model.AutopilotExecutionMode
import ai.multica.android.data.model.AutopilotTriggerKind
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.data.repository.AutopilotRepository
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.data.repository.SquadRepository
import ai.multica.android.ui.components.MulticaAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateAutopilotViewModel @Inject constructor(
    private val autopilotRepository: AutopilotRepository,
    private val agentRepository: AgentRepository,
    private val squadRepository: SquadRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAutopilotUiState())
    val state: StateFlow<CreateAutopilotUiState> = _state.asStateFlow()

    init { loadOptions() }

    private fun loadOptions() {
        viewModelScope.launch {
            (agentRepository.list() as? ApiResult.Success)?.data?.let {
                _state.update { s -> s.copy(agents = it.filterNot { a -> a.archivedAt != null }) }
            }
            (squadRepository.list() as? ApiResult.Success)?.data?.let {
                _state.update { s -> s.copy(squads = it) }
            }
            (projectRepository.list() as? ApiResult.Success)?.data?.projects?.let {
                _state.update { s -> s.copy(projects = it) }
            }
        }
    }

    fun onTitleChange(v: String) = _state.update { it.copy(title = v) }
    fun onDescriptionChange(v: String) = _state.update { it.copy(description = v) }
    fun onAssigneeTypeChange(t: AutopilotAssigneeType) = _state.update { it.copy(assigneeType = t, assigneeId = "") }
    fun onAssigneeIdChange(id: String) = _state.update { it.copy(assigneeId = id) }
    fun onExecutionModeChange(m: AutopilotExecutionMode) = _state.update { it.copy(executionMode = m) }
    fun onProjectChange(id: String?) = _state.update { it.copy(projectId = id) }
    fun onTriggerKindChange(k: AutopilotTriggerKind) = _state.update { it.copy(triggerKind = k) }
    fun onCronChange(c: String) { _state.update { it.copy(cronExpression = c) } }
    fun onTimeChange(t: String) { _state.update { it.copy(time = t) }; rebuildCron() }
    fun onTimezoneChange(tz: String) { _state.update { it.copy(timezone = tz) } }
    fun onFrequencyChange(f: ScheduleFrequency) { _state.update { it.copy(frequency = f) }; rebuildCron() }

    /** Build a cron expression from frequency + time (HH:mm). */
    private fun rebuildCron() {
        val s = _state.value
        val parts = s.time.split(":")
        val hh = parts.getOrNull(0)?.padStart(2, '0') ?: "09"
        val mm = parts.getOrNull(1)?.padStart(2, '0') ?: "00"
        val cron = when (s.frequency) {
            ScheduleFrequency.HOURLY -> "0 * * * *"
            ScheduleFrequency.DAILY -> "$mm $hh * * *"
            ScheduleFrequency.WEEKDAYS -> "$mm $hh * * 1-5"
            ScheduleFrequency.WEEKLY -> "$mm $hh * * 1"
        }
        _state.update { it.copy(cronExpression = cron) }
    }

    fun create(onCreated: (String) -> Unit) {
        val s = _state.value
        if (s.title.isBlank() || s.assigneeId.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val result = autopilotRepository.create(
                title = s.title,
                assigneeId = s.assigneeId,
                assigneeType = s.assigneeType,
                executionMode = s.executionMode,
                description = s.description.ifBlank { null },
                projectId = s.projectId,
            )
            _state.update { it.copy(isSubmitting = false) }
            if (result is ApiResult.Success) {
                val apId = result.data.id
                // Create the initial trigger if schedule selected.
                if (s.triggerKind == AutopilotTriggerKind.SCHEDULE && s.cronExpression.isNotBlank()) {
                    autopilotRepository.createTrigger(apId, AutopilotTriggerKind.SCHEDULE, s.cronExpression, s.timezone)
                } else if (s.triggerKind == AutopilotTriggerKind.WEBHOOK) {
                    autopilotRepository.createTrigger(apId, AutopilotTriggerKind.WEBHOOK)
                }
                onCreated(apId)
            }
        }
    }
}

data class CreateAutopilotUiState(
    val title: String = "",
    val description: String = "",
    val assigneeType: AutopilotAssigneeType = AutopilotAssigneeType.AGENT,
    val assigneeId: String = "",
    val executionMode: AutopilotExecutionMode = AutopilotExecutionMode.CREATE_ISSUE,
    val projectId: String? = null,
    val triggerKind: AutopilotTriggerKind = AutopilotTriggerKind.SCHEDULE,
    val cronExpression: String = "",
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,
    val time: String = "09:00",
    val timezone: String = "Asia/Shanghai",
    val agents: List<ai.multica.android.data.model.Agent> = emptyList(),
    val squads: List<ai.multica.android.data.model.Squad> = emptyList(),
    val projects: List<ai.multica.android.data.model.Project> = emptyList(),
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = title.isNotBlank() && assigneeId.isNotBlank() && !isSubmitting
}

enum class ScheduleFrequency { HOURLY, DAILY, WEEKDAYS, WEEKLY }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateAutopilotScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateAutopilotViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.autopilots_new)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.create { id -> onBack(); onCreated(id) } }, enabled = state.canSubmit) {
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
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.autopilot_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
            // Execution mode.
            Text(stringResource(R.string.autopilot_mode), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.executionMode == AutopilotExecutionMode.CREATE_ISSUE,
                    onClick = { viewModel.onExecutionModeChange(AutopilotExecutionMode.CREATE_ISSUE) },
                    label = { Text(stringResource(R.string.autopilot_mode_create_issue)) },
                )
                FilterChip(
                    selected = state.executionMode == AutopilotExecutionMode.RUN_ONLY,
                    onClick = { viewModel.onExecutionModeChange(AutopilotExecutionMode.RUN_ONLY) },
                    label = { Text(stringResource(R.string.autopilot_mode_run_only)) },
                )
            }
            // Assignee type.
            Text(stringResource(R.string.autopilot_assignee), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.assigneeType == AutopilotAssigneeType.AGENT,
                    onClick = { viewModel.onAssigneeTypeChange(AutopilotAssigneeType.AGENT) },
                    leadingIcon = { Icon(Icons.Filled.SmartToy, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Agent") },
                )
                FilterChip(
                    selected = state.assigneeType == AutopilotAssigneeType.SQUAD,
                    onClick = { viewModel.onAssigneeTypeChange(AutopilotAssigneeType.SQUAD) },
                    leadingIcon = { Icon(Icons.Filled.Group, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Squad") },
                )
            }
            // Assignee dropdown.
            AssigneeDropdown(state = state, onAssigneeIdChange = viewModel::onAssigneeIdChange)
            // Project (only for create_issue).
            if (state.executionMode == AutopilotExecutionMode.CREATE_ISSUE) {
                ProjectDropdown(state = state, onProjectChange = viewModel::onProjectChange)
            }
            // Trigger kind.
            Text(stringResource(R.string.autopilot_trigger_kind), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.triggerKind == AutopilotTriggerKind.SCHEDULE,
                    onClick = { viewModel.onTriggerKindChange(AutopilotTriggerKind.SCHEDULE) },
                    leadingIcon = { Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(16.dp)) },
                    label = { Text(stringResource(R.string.autopilot_trigger_schedule)) },
                )
                FilterChip(
                    selected = state.triggerKind == AutopilotTriggerKind.WEBHOOK,
                    onClick = { viewModel.onTriggerKindChange(AutopilotTriggerKind.WEBHOOK) },
                    leadingIcon = { Icon(Icons.Filled.Webhook, null, modifier = Modifier.size(16.dp)) },
                    label = { Text(stringResource(R.string.autopilot_trigger_webhook)) },
                )
            }
            // Schedule config (schedule only): frequency presets + time + timezone.
            if (state.triggerKind == AutopilotTriggerKind.SCHEDULE) {
                Text(stringResource(R.string.autopilot_trigger_schedule), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                // Frequency presets.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ScheduleFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = state.frequency == freq,
                            onClick = { viewModel.onFrequencyChange(freq) },
                            label = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                // Time (HH:mm) — only meaningful for non-hourly.
                if (state.frequency != ScheduleFrequency.HOURLY) {
                    var showTimePicker by remember { mutableStateOf(false) }
                    OutlinedButton(
                        onClick = { showTimePicker = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Time: ${state.time}", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(18.dp))
                    }
                    if (showTimePicker) {
                        val parts = state.time.split(":")
                        val tpState = rememberTimePickerState(
                            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9,
                            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                            is24Hour = true,
                        )
                        AlertDialog(
                            onDismissRequest = { showTimePicker = false },
                            title = { Text("Select time") },
                            text = { TimePicker(state = tpState) },
                            confirmButton = {
                                TextButton(onClick = {
                                    val hh = tpState.hour.toString().padStart(2, '0')
                                    val mm = tpState.minute.toString().padStart(2, '0')
                                    viewModel.onTimeChange("$hh:$mm")
                                    showTimePicker = false
                                }) { Text(stringResource(R.string.common_ok)) }
                            },
                            dismissButton = {
                                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
                            },
                        )
                    }
                }
                // Timezone.
                OutlinedTextField(
                    value = state.timezone,
                    onValueChange = viewModel::onTimezoneChange,
                    label = { Text("Timezone (e.g. UTC, Asia/Shanghai)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                // Cron preview (read-only, derived).
                AssistChip(onClick = {}, label = { Text("Cron: ${state.cronExpression.ifBlank { "—" }}", style = MaterialTheme.typography.labelSmall) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssigneeDropdown(state: CreateAutopilotUiState, onAssigneeIdChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = if (state.assigneeType == AutopilotAssigneeType.AGENT) state.agents else state.squads
    val selectedName = when (state.assigneeType) {
        AutopilotAssigneeType.AGENT -> state.agents.firstOrNull { it.id == state.assigneeId }?.name
        AutopilotAssigneeType.SQUAD -> state.squads.firstOrNull { it.id == state.assigneeId }?.name
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedName ?: "Select…",
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(stringResource(R.string.autopilot_assignee)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.exposedDropdownSize()) {
            options.forEach { opt ->
                val id = when (opt) {
                    is ai.multica.android.data.model.Agent -> opt.id
                    is ai.multica.android.data.model.Squad -> opt.id
                    else -> ""
                }
                val name = when (opt) {
                    is ai.multica.android.data.model.Agent -> opt.name
                    is ai.multica.android.data.model.Squad -> opt.name
                    else -> ""
                }
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onAssigneeIdChange(id); expanded = false },
                    leadingIcon = {
                        if (state.assigneeType == AutopilotAssigneeType.AGENT) Icon(Icons.Filled.SmartToy, null)
                        else Icon(Icons.Filled.Group, null)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectDropdown(state: CreateAutopilotUiState, onProjectChange: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = state.projects.firstOrNull { it.id == state.projectId }?.title ?: "None"
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            label = { Text(stringResource(R.string.issue_project)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.exposedDropdownSize()) {
            DropdownMenuItem(text = { Text("None") }, onClick = { onProjectChange(null); expanded = false })
            state.projects.forEach { p ->
                DropdownMenuItem(text = { Text(p.title) }, onClick = { onProjectChange(p.id); expanded = false })
            }
        }
    }
}
