package ai.multica.android.ui.autopilots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
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
import ai.multica.android.data.model.AutopilotRun
import ai.multica.android.data.model.AutopilotStatus
import ai.multica.android.data.model.AutopilotTrigger
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
                else -> AutopilotDetailContent(state = state, onTogglePause = viewModel::togglePause)
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
                Text("Triggers", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(detail.triggers, key = { it.id }) { trigger ->
                TriggerRow(trigger = trigger)
            }
        }
        // Runs.
        item {
            Text(stringResource(R.string.autopilot_runs), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
private fun TriggerRow(trigger: AutopilotTrigger) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val icon = when (trigger.kind) {
                ai.multica.android.data.model.AutopilotTriggerKind.SCHEDULE -> Icons.Filled.PlayArrow
                ai.multica.android.data.model.AutopilotTriggerKind.WEBHOOK -> Icons.Filled.Webhook
                ai.multica.android.data.model.AutopilotTriggerKind.API -> Icons.Filled.Bolt
            }
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(trigger.kind.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (trigger.cronExpression != null) {
                    Text("Cron: ${trigger.cronExpression}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (trigger.webhookUrl != null) {
                    Text(trigger.webhookUrl, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
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
