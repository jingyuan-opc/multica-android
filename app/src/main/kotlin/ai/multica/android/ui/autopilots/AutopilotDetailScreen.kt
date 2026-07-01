package ai.multica.android.ui.autopilots

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.dto.UpdateAutopilotTriggerRequest
import ai.multica.android.data.model.AutopilotCollaborator
import ai.multica.android.data.model.AutopilotRun
import ai.multica.android.data.model.AutopilotStatus
import ai.multica.android.data.model.AutopilotTrigger
import ai.multica.android.data.model.AutopilotTriggerKind
import ai.multica.android.data.model.GetAutopilotResponse
import ai.multica.android.data.repository.AutopilotRepository
import ai.multica.android.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AutopilotDetailViewModel @Inject constructor(
    private val autopilotRepository: AutopilotRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val autopilotId: String = savedStateHandle.get<String>("id")
        ?: error("AutopilotDetailViewModel requires 'id' nav arg")

    private val _state = MutableStateFlow(AutopilotDetailUiState())
    val state: StateFlow<AutopilotDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val detail = (autopilotRepository.get(autopilotId) as? ApiResult.Success)?.data
            val runs = (autopilotRepository.listRuns(autopilotId) as? ApiResult.Success)?.data?.runs ?: emptyList()
            _state.update {
                it.copy(
                    isLoading = false,
                    detail = detail,
                    runs = runs,
                    errorMessage = null,
                )
            }
        }
    }

    fun togglePause() {
        val current = state.value.detail?.autopilot ?: return
        viewModelScope.launch {
            val newStatus = if (current.status == AutopilotStatus.ACTIVE) AutopilotStatus.PAUSED else AutopilotStatus.ACTIVE
            autopilotRepository.setStatus(autopilotId, newStatus)
            refresh()
        }
    }

    fun trigger() {
        viewModelScope.launch {
            autopilotRepository.trigger(autopilotId)
            refresh()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (autopilotRepository.delete(autopilotId)) {
                is ApiResult.Success -> onDeleted()
                else -> Unit
            }
        }
    }

    fun update(
        title: String? = null,
        description: String? = null,
        status: AutopilotStatus? = null,
    ) {
        viewModelScope.launch {
            autopilotRepository.update(
                autopilotId,
                ai.multica.android.data.dto.UpdateAutopilotRequest(
                    title = title?.trim(),
                    description = description?.trim(),
                    status = status,
                ),
            )
            refresh()
        }
    }

    fun updateTrigger(trigger: AutopilotTrigger, cronExpression: String, timezone: String) {
        viewModelScope.launch {
            autopilotRepository.updateTrigger(
                autopilotId,
                trigger.id,
                UpdateAutopilotTriggerRequest(cronExpression = cronExpression, timezone = timezone),
            )
            refresh()
        }
    }

    fun toggleTriggerEnabled(trigger: AutopilotTrigger) {
        viewModelScope.launch {
            autopilotRepository.updateTrigger(
                autopilotId,
                trigger.id,
                UpdateAutopilotTriggerRequest(enabled = !trigger.enabled),
            )
            refresh()
        }
    }

    fun deleteTrigger(trigger: AutopilotTrigger) {
        viewModelScope.launch {
            autopilotRepository.deleteTrigger(autopilotId, trigger.id)
            refresh()
        }
    }

    fun grantAccess(userId: String) {
        if (userId.isBlank()) return
        viewModelScope.launch {
            autopilotRepository.grantAccess(autopilotId, userId.trim())
            refresh()
        }
    }

    fun revokeAccess(userId: String) {
        viewModelScope.launch {
            autopilotRepository.revokeAccess(autopilotId, userId)
            refresh()
        }
    }
}

data class AutopilotDetailUiState(
    val isLoading: Boolean = false,
    val detail: GetAutopilotResponse? = null,
    val runs: List<AutopilotRun> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutopilotDetailScreen(
    onBack: () -> Unit,
    viewModel: AutopilotDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val detail = state.detail
    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTrigger by remember { mutableStateOf<AutopilotTrigger?>(null) }
    var deletingTrigger by remember { mutableStateOf<AutopilotTrigger?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.autopilot?.title ?: stringResource(R.string.autopilots_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { showActions = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.autopilots_delete)) },
                            onClick = { showActions = false; showDeleteConfirm = true },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (detail != null) {
                ExtendedFloatingActionButton(
                    onClick = viewModel::trigger,
                    icon = { Icon(Icons.Filled.PlayArrow, null) },
                    text = { Text(stringResource(R.string.autopilots_trigger)) },
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && detail != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> AutopilotDetailContent(
                    state = state,
                    onTogglePause = viewModel::togglePause,
                    onEditTrigger = { editingTrigger = it },
                    onToggleTrigger = viewModel::toggleTriggerEnabled,
                    onRequestDeleteTrigger = { deletingTrigger = it },
                    onGrantAccess = viewModel::grantAccess,
                    onRevokeAccess = viewModel::revokeAccess,
                )
            }
        }
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.autopilots_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { showDeleteConfirm = false; viewModel.delete(onBack) }) {
                    Text(stringResource(R.string.autopilots_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    if (showEditDialog) {
        AutopilotEditDialog(
            autopilot = detail?.autopilot,
            onConfirm = { title, description ->
                viewModel.update(title = title, description = description)
                showEditDialog = false
            },
            onDismiss = { showEditDialog = false },
        )
    }
    editingTrigger?.let { trigger ->
        TriggerEditDialog(
            trigger = trigger,
            onConfirm = { cron, tz ->
                viewModel.updateTrigger(trigger, cron, tz)
                editingTrigger = null
            },
            onDismiss = { editingTrigger = null },
        )
    }
    deletingTrigger?.let { trigger ->
        AlertDialog(
            onDismissRequest = { deletingTrigger = null },
            title = { Text(stringResource(R.string.autopilot_trigger_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    deletingTrigger = null
                    viewModel.deleteTrigger(trigger)
                }) { Text(stringResource(R.string.autopilot_trigger_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deletingTrigger = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun AutopilotEditDialog(
    autopilot: ai.multica.android.data.model.Autopilot?,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf(autopilot?.title.orEmpty()) }
    var description by remember { mutableStateOf(autopilot?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit autopilot") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.autopilot_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, minLines = 2, maxLines = 6, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title, description) }, enabled = title.isNotBlank()) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun AutopilotDetailContent(
    state: AutopilotDetailUiState,
    onTogglePause: () -> Unit,
    onEditTrigger: (AutopilotTrigger) -> Unit,
    onToggleTrigger: (AutopilotTrigger) -> Unit,
    onRequestDeleteTrigger: (AutopilotTrigger) -> Unit,
    onGrantAccess: (String) -> Unit,
    onRevokeAccess: (String) -> Unit,
) {
    val detail = state.detail!!
    val ap = detail.autopilot
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bolt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(ap.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "${ap.status.name.lowercase().replaceFirstChar { it.uppercase() }} · ${ap.executionMode.name.lowercase().replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = onTogglePause) {
                    Text(if (ap.status == AutopilotStatus.ACTIVE) stringResource(R.string.autopilots_pause) else stringResource(R.string.autopilots_resume))
                }
            }
            if (!ap.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(ap.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // Triggers.
        if (detail.triggers.isNotEmpty()) {
            item {
                ai.multica.android.ui.components.SectionHeader(title = "Triggers")
            }
            items(detail.triggers, key = { it.id }) { trigger ->
                TriggerRow(
                    trigger = trigger,
                    onEdit = { onEditTrigger(trigger) },
                    onToggle = { onToggleTrigger(trigger) },
                    onDelete = { onRequestDeleteTrigger(trigger) },
                )
            }
        }
        // Collaborators (manage access).
        item {
            CollaboratorsSection(
                collaborators = detail.collaborators,
                onGrant = onGrantAccess,
                onRevoke = onRevokeAccess,
            )
        }
        // Runs.
        item {
            ai.multica.android.ui.components.SectionHeader(title = stringResource(R.string.autopilot_runs))
        }
        if (state.runs.isEmpty()) {
            item { EmptyState(icon = Icons.Filled.Bolt, title = "No runs yet") }
        } else {
            items(state.runs, key = { it.id }) { run ->
                RunRow(run = run)
            }
        }
    }
}

@Composable
private fun TriggerRow(
    trigger: AutopilotTrigger,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (trigger.kind) {
                AutopilotTriggerKind.SCHEDULE -> Icons.Filled.PlayArrow
                AutopilotTriggerKind.WEBHOOK -> Icons.Filled.Webhook
                AutopilotTriggerKind.API -> Icons.Filled.Bolt
            }
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(trigger.kind.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (!trigger.enabled) {
                        Spacer(Modifier.width(6.dp))
                        AssistChip(onClick = {}, label = { Text("Paused", style = MaterialTheme.typography.labelSmall) })
                    }
                }
                if (trigger.cronExpression != null) {
                    Text("Cron: ${trigger.cronExpression}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (trigger.webhookUrl != null) {
                    Text(trigger.webhookUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            // Edit (only schedule triggers have an editable cron/time/timezone).
            if (trigger.kind == AutopilotTriggerKind.SCHEDULE) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.autopilot_trigger_edit))
                }
            }
            // Enable / pause toggle.
            IconButton(onClick = onToggle) {
                if (trigger.enabled) {
                    Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.autopilot_trigger_disable))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.autopilot_trigger_enable))
                }
            }
            // Delete.
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.autopilot_trigger_delete), tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun RunRow(run: AutopilotRun) {
    val color = when (run.status) {
        ai.multica.android.data.model.AutopilotRunStatus.COMPLETED -> Color(0xFF10B981)
        ai.multica.android.data.model.AutopilotRunStatus.FAILED -> MaterialTheme.colorScheme.error
        ai.multica.android.data.model.AutopilotRunStatus.SKIPPED -> MaterialTheme.colorScheme.outline
        else -> MaterialTheme.colorScheme.primary
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(run.status.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${run.source} · ${run.triggeredAt.take(19).replace("T", " ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/**
 * Build a 5-field cron expression from a [ScheduleFrequency] and a "HH:mm" time string.
 * Shared between CreateAutopilotScreen and the trigger edit dialog to keep the
 * frequency→cron mapping in one place.
 */
fun buildCronFromFrequency(frequency: ScheduleFrequency, time: String): String {
    val parts = time.split(":")
    val hh = parts.getOrNull(0)?.padStart(2, '0') ?: "09"
    val mm = parts.getOrNull(1)?.padStart(2, '0') ?: "00"
    return when (frequency) {
        ScheduleFrequency.HOURLY -> "0 * * * *"
        ScheduleFrequency.DAILY -> "$mm $hh * * *"
        ScheduleFrequency.WEEKDAYS -> "$mm $hh * * 1-5"
        ScheduleFrequency.WEEKLY -> "$mm $hh * * 1"
    }
}

/**
 * Infer the best-matching [ScheduleFrequency] preset for a cron expression.
 * Falls back to [ScheduleFrequency.DAILY] for unrecognized cron patterns so the
 * editor always opens with a sensible default the user can adjust.
 */
fun inferFrequencyFromCron(cron: String?): ScheduleFrequency {
    val parts = cron?.trim()?.split(" ").orEmpty()
    if (parts.size == 5) {
        val dow = parts[4]
        when {
            dow == "1-5" && parts[2] == "*" && parts[3] == "*" -> return ScheduleFrequency.WEEKDAYS
            dow == "1" && parts[2] == "*" && parts[3] == "*" -> return ScheduleFrequency.WEEKLY
            dow == "*" && parts[2] == "*" && parts[3] == "*" -> {
                if (parts[0] == "0" && parts[1] == "*") return ScheduleFrequency.HOURLY
                return ScheduleFrequency.DAILY
            }
        }
    }
    return ScheduleFrequency.DAILY
}

/** Extract a normalized "HH:mm" string from a cron expression, or null if none. */
fun extractTimeFromCron(cron: String?): String? {
    val parts = cron?.trim()?.split(" ").orEmpty()
    if (parts.size == 5 && parts[1] != "*") {
        val hh = parts[1].padStart(2, '0')
        val mm = parts[0].padStart(2, '0')
        return "$hh:$mm"
    }
    return null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TriggerEditDialog(
    trigger: AutopilotTrigger,
    onConfirm: (cronExpression: String, timezone: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Seed editor from the trigger's existing cron + timezone.
    var frequency by remember { mutableStateOf(inferFrequencyFromCron(trigger.cronExpression)) }
    var time by remember { mutableStateOf(extractTimeFromCron(trigger.cronExpression) ?: "09:00") }
    var timezone by remember { mutableStateOf(trigger.timezone ?: "Asia/Shanghai") }
    var showTimePicker by remember { mutableStateOf(false) }
    val cron = remember(frequency, time) { buildCronFromFrequency(frequency, time) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.autopilot_trigger_edit)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.autopilot_trigger_schedule), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                // Frequency presets.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    ScheduleFrequency.entries.forEach { freq ->
                        FilterChip(
                            selected = frequency == freq,
                            onClick = { frequency = freq },
                            label = { Text(freq.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
                // Time picker (only meaningful for non-hourly).
                if (frequency != ScheduleFrequency.HOURLY) {
                    OutlinedButton(onClick = { showTimePicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("${stringResource(R.string.autopilot_trigger_time)}: $time", modifier = Modifier.weight(1f))
                        Icon(Icons.Filled.Schedule, null, modifier = Modifier.size(18.dp))
                    }
                }
                OutlinedTextField(
                    value = timezone,
                    onValueChange = { timezone = it },
                    label = { Text(stringResource(R.string.autopilot_trigger_timezone)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                AssistChip(onClick = {}, label = { Text("Cron: $cron", style = MaterialTheme.typography.labelSmall) })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(cron, timezone.trim().ifBlank { "UTC" }) }) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )

    if (showTimePicker) {
        val parts = time.split(":")
        val tpState = rememberTimePickerState(
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.autopilot_trigger_time)) },
            text = { TimePicker(state = tpState) },
            confirmButton = {
                TextButton(onClick = {
                    val hh = tpState.hour.toString().padStart(2, '0')
                    val mm = tpState.minute.toString().padStart(2, '0')
                    time = "$hh:$mm"
                    showTimePicker = false
                }) { Text(stringResource(R.string.common_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

/**
 * Collaborators section — mirrors the web's manage-access dialog. Lists granted
 * members and supports granting (by user ID) and revoking access. Grant returns
 * the full updated collaborator list, so we refresh after each action.
 */
@Composable
private fun CollaboratorsSection(
    collaborators: List<AutopilotCollaborator>,
    onGrant: (String) -> Unit,
    onRevoke: (String) -> Unit,
) {
    var showGrant by remember { mutableStateOf(false) }
    var newUserId by remember { mutableStateOf("") }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.autopilot_collaborators), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = { showGrant = !showGrant }) {
                Text(if (showGrant) stringResource(R.string.common_cancel) else stringResource(R.string.autopilot_grant_access))
            }
        }
        if (showGrant) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newUserId,
                    onValueChange = { newUserId = it },
                    label = { Text("User ID") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onGrant(newUserId); newUserId = ""; showGrant = false },
                    enabled = newUserId.isNotBlank(),
                ) { Text(stringResource(R.string.common_ok)) }
            }
        }
        if (collaborators.isEmpty()) {
            Text("No collaborators", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            collaborators.forEach { c ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Person, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.userId.take(8), style = MaterialTheme.typography.bodyMedium)
                            Text("Granted ${c.createdAt.take(10)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onRevoke(c.userId) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Revoke", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
