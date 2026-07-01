package ai.multica.android.ui.labels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.auth.WorkspaceEvents
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.Label
import ai.multica.android.data.repository.LabelRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.issues.LabelChip
import ai.multica.android.ui.issues.parseHexColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LabelsViewModel @Inject constructor(
    private val labelRepository: LabelRepository,
    private val workspaceStore: ai.multica.android.core.auth.WorkspaceStore,
    private val workspaceEvents: WorkspaceEvents,
    private val realtimeManager: ai.multica.android.realtime.RealtimeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LabelsUiState())
    val state: StateFlow<LabelsUiState> = _state.asStateFlow()
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
                _state.update { it.copy(labels = emptyList()) }
                refresh()
            }
        }
    }

    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                if (event is ai.multica.android.realtime.WsEvent.LabelChanged) refresh()
            }
        }
    }

    fun refresh() {
        if (workspaceStore.getId() == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = labelRepository.list()) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, labels = result.data.labels) }
                is ApiResult.HttpError -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.NetworkError -> _state.update { it.copy(isLoading = false, errorMessage = "Network error") }
                is ApiResult.Unknown -> _state.update { it.copy(isLoading = false, errorMessage = "Unexpected error") }
            }
        }
    }

    fun create(name: String, color: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            when (labelRepository.create(name, color)) {
                is ApiResult.Success -> refresh()
                else -> Unit
            }
        }
    }

    fun update(id: String, name: String?, color: String?) {
        viewModelScope.launch {
            when (labelRepository.update(id, name, color)) {
                is ApiResult.Success -> refresh()
                else -> Unit
            }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            when (labelRepository.delete(id)) {
                is ApiResult.Success -> refresh()
                else -> Unit
            }
        }
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        realtimeJob?.cancel()
        super.onCleared()
    }
}

data class LabelsUiState(
    val isLoading: Boolean = false,
    val labels: List<Label> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelsScreen(
    onBack: () -> Unit,
    viewModel: LabelsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showCreate by remember { mutableStateOf(false) }
    var editingLabel by remember { mutableStateOf<Label?>(null) }
    var deleteTarget by remember { mutableStateOf<Label?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.labels_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreate = true }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.labels_new))
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.labels.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                state.isLoading && state.labels.isEmpty() ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.errorMessage != null && state.labels.isEmpty() ->
                    EmptyState(icon = Icons.Filled.Label, title = stringResource(R.string.labels_empty), description = state.errorMessage)
                state.labels.isEmpty() ->
                    EmptyState(icon = Icons.Filled.Label, title = stringResource(R.string.labels_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.labels, key = { it.id }) { label ->
                        LabelRow(
                            label = label,
                            onEdit = { editingLabel = label },
                            onDelete = { deleteTarget = label },
                        )
                    }
                }
            }
        }
    }

    if (showCreate) {
        LabelEditDialog(
            title = stringResource(R.string.labels_new),
            initialName = "",
            initialColor = "#3b82f6",
            onConfirm = { name, color -> viewModel.create(name, color); showCreate = false },
            onDismiss = { showCreate = false },
        )
    }
    editingLabel?.let { label ->
        LabelEditDialog(
            title = stringResource(R.string.labels_manage),
            initialName = label.name,
            initialColor = label.color ?: "#3b82f6",
            onConfirm = { name, color -> viewModel.update(label.id, name, color); editingLabel = null },
            onDismiss = { editingLabel = null },
        )
    }
    deleteTarget?.let { label ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.labels_delete_confirm)) },
            text = { LabelChip(label = label) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(label.id); deleteTarget = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun LabelRow(label: Label, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            LabelChip(label = label)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun LabelEditDialog(
    title: String,
    initialName: String,
    initialColor: String,
    onConfirm: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableStateOf(initialColor) }
    val presetColors = listOf("#EF4444", "#F59E0B", "#10B981", "#3B82F6", "#8B5CF6", "#EC4899", "#6B7280")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.labels_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.labels_color), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presetColors.forEach { c ->
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(c))
                                .clickable { color = c },
                        ) {
                            if (c.equals(color, ignoreCase = true)) {
                                Box(Modifier.fillMaxSize().clip(CircleShape), contentAlignment = Alignment.Center) {
                                    Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, color) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
