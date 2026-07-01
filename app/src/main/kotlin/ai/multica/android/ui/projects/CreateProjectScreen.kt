package ai.multica.android.ui.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.dto.CreateProjectResourceInput
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.Project
import ai.multica.android.data.model.ProjectPriority
import ai.multica.android.data.model.ProjectResourceType
import ai.multica.android.data.model.ProjectStatus
import ai.multica.android.data.model.githubRepoRef
import ai.multica.android.data.model.localDirectoryRef
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.ui.components.PriorityBars
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onBack: () -> Unit,
    onCreated: (projectId: String) -> Unit,
    viewModel: CreateProjectViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New project") },
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
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name *") },
                placeholder = { Text("What's the project called?") },
                isError = state.titleError != null,
                supportingText = {
                    if (state.titleError != null) {
                        Text(state.titleError!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                singleLine = true,
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = viewModel::onDescriptionChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                placeholder = { Text("Optional description (markdown supported)") },
                minLines = 3,
                maxLines = 6,
            )

            Text("Status", style = MaterialTheme.typography.labelLarge)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ProjectStatus.entries.forEach { status ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.onStatusChange(status) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = state.status == status,
                            onClick = { viewModel.onStatusChange(status) },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(labelForStatus(status), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Text("Priority", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProjectPriority.ORDER.forEach { p ->
                    FilterChip(
                        selected = state.priority == p,
                        onClick = { viewModel.onPriorityChange(p) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PriorityBars(priority = projectPriorityToIssue(p))
                                Spacer(Modifier.width(4.dp))
                                Text(p.name.lowercase().replaceFirstChar { it.uppercase() })
                            }
                        },
                    )
                }
            }

            // Source: attach GitHub repos or a local working directory at creation.
            Text("Source", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.sourceMode == ProjectSourceMode.NONE,
                    onClick = { viewModel.onSourceModeChange(ProjectSourceMode.NONE) },
                    label = { Text("None") },
                )
                FilterChip(
                    selected = state.sourceMode == ProjectSourceMode.GITHUB,
                    onClick = { viewModel.onSourceModeChange(ProjectSourceMode.GITHUB) },
                    leadingIcon = { Icon(Icons.Filled.Public, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("GitHub repos") },
                )
                FilterChip(
                    selected = state.sourceMode == ProjectSourceMode.LOCAL,
                    onClick = { viewModel.onSourceModeChange(ProjectSourceMode.LOCAL) },
                    leadingIcon = { Icon(Icons.Filled.Folder, null, modifier = Modifier.size(16.dp)) },
                    label = { Text("Local directory") },
                )
            }

            if (state.sourceMode == ProjectSourceMode.GITHUB) {
                OutlinedTextField(
                    value = state.newGithubUrl,
                    onValueChange = viewModel::onNewGithubUrlChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("GitHub URL") },
                    placeholder = { Text("https://github.com/owner/repo") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = viewModel::onAddGithubUrl, enabled = state.newGithubUrl.isNotBlank()) {
                            Icon(Icons.Filled.Add, contentDescription = "Add")
                        }
                    },
                )
                state.githubUrls.forEach { url ->
                    InputChip(
                        onClick = { viewModel.onRemoveGithubUrl(url) },
                        label = { Text(url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = false,
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (state.sourceMode == ProjectSourceMode.LOCAL) {
                OutlinedTextField(
                    value = state.localPath,
                    onValueChange = viewModel::onLocalPathChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Local path *") },
                    placeholder = { Text("/home/user/project") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.daemonId,
                    onValueChange = viewModel::onDaemonIdChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Daemon ID") },
                    placeholder = { Text("default") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.localLabel,
                    onValueChange = viewModel::onLocalLabelChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Label (optional)") },
                    singleLine = true,
                )
                Text(
                    "Local paths are only visible to agents on this machine. Use GitHub repos for shared work.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

    LaunchedEffect(state.createdProjectId) {
        if (state.createdProjectId != null) {
            onCreated(state.createdProjectId!!)
        }
    }
}

@HiltViewModel
class CreateProjectViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CreateProjectUiState())
    val state: StateFlow<CreateProjectUiState> = _state.asStateFlow()

    fun onTitleChange(value: String) {
        _state.update { it.copy(title = value, titleError = null) }
    }
    fun onDescriptionChange(value: String) = _state.update { it.copy(description = value) }
    fun onStatusChange(status: ProjectStatus) = _state.update { it.copy(status = status) }
    fun onPriorityChange(priority: ProjectPriority) = _state.update { it.copy(priority = priority) }

    fun onSourceModeChange(mode: ProjectSourceMode) = _state.update { it.copy(sourceMode = mode) }
    fun onNewGithubUrlChange(value: String) = _state.update { it.copy(newGithubUrl = value) }
    fun onAddGithubUrl() {
        val url = _state.value.newGithubUrl.trim()
        if (url.isNotBlank() && url !in _state.value.githubUrls) {
            _state.update { it.copy(githubUrls = it.githubUrls + url, newGithubUrl = "") }
        }
    }
    fun onRemoveGithubUrl(url: String) = _state.update { it.copy(githubUrls = it.githubUrls - url) }
    fun onLocalPathChange(value: String) = _state.update { it.copy(localPath = value) }
    fun onDaemonIdChange(value: String) = _state.update { it.copy(daemonId = value) }
    fun onLocalLabelChange(value: String) = _state.update { it.copy(localLabel = value) }

    /**
     * Build the resources list from the current source mode. Mirrors the web's
     * submit handler: only the active mode contributes resources; the inactive
     * mode's stash is dropped.
     */
    private fun buildResources(s: CreateProjectUiState): List<CreateProjectResourceInput> {
        return when (s.sourceMode) {
            ProjectSourceMode.GITHUB -> s.githubUrls
                .filter { it.isNotBlank() }
                .mapIndexed { index, url ->
                    CreateProjectResourceInput(
                        resourceType = ProjectResourceType.GITHUB_REPO.wireValue,
                        resourceRef = githubRepoRef(url),
                        position = index,
                    )
                }
            ProjectSourceMode.LOCAL -> {
                if (s.localPath.isBlank()) return emptyList()
                val daemon = s.daemonId.trim().ifBlank { "default" }
                listOf(
                    CreateProjectResourceInput(
                        resourceType = ProjectResourceType.LOCAL_DIRECTORY.wireValue,
                        resourceRef = localDirectoryRef(s.localPath, daemon, s.localLabel.ifBlank { null }),
                        position = 0,
                    ),
                )
            }
            ProjectSourceMode.NONE -> emptyList()
        }
    }

    fun submit() {
        val s = _state.value
        if (s.title.isBlank()) {
            _state.update { it.copy(titleError = "Name is required") }
            return
        }
        _state.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val r = projectRepository.create(
                title = s.title,
                description = s.description.takeIf { it.isNotBlank() },
                status = s.status,
                priority = s.priority,
                resources = buildResources(s),
            )) {
                is ApiResult.Success -> _state.update {
                    it.copy(isSubmitting = false, createdProjectId = r.data.id)
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(isSubmitting = false, errorMessage = r.message)
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

data class CreateProjectUiState(
    val title: String = "",
    val titleError: String? = null,
    val description: String = "",
    val status: ProjectStatus = ProjectStatus.PLANNED,
    val priority: ProjectPriority = ProjectPriority.NONE,
    val sourceMode: ProjectSourceMode = ProjectSourceMode.NONE,
    val githubUrls: List<String> = emptyList(),
    val newGithubUrl: String = "",
    val localPath: String = "",
    val daemonId: String = "",
    val localLabel: String = "",
    val isSubmitting: Boolean = false,
    val createdProjectId: String? = null,
    val errorMessage: String? = null,
) {
    val canSubmit: Boolean
        get() = title.isNotBlank() && !isSubmitting
}

/**
 * Reuse the IssuePriority palette by mapping ProjectPriority → IssuePriority.
 * They share the same value strings in the wire format and the same
 * color mapping on the client (see `PriorityColors.forProjectPriority`).
 */
private fun projectPriorityToIssue(p: ProjectPriority): IssuePriority = when (p) {
    ProjectPriority.URGENT -> IssuePriority.URGENT
    ProjectPriority.HIGH -> IssuePriority.HIGH
    ProjectPriority.MEDIUM -> IssuePriority.MEDIUM
    ProjectPriority.LOW -> IssuePriority.LOW
    ProjectPriority.NONE -> IssuePriority.NONE
}

private fun labelForStatus(s: ProjectStatus): String = when (s) {
    ProjectStatus.PLANNED -> "Planned"
    ProjectStatus.IN_PROGRESS -> "In progress"
    ProjectStatus.PAUSED -> "Paused"
    ProjectStatus.COMPLETED -> "Completed"
    ProjectStatus.CANCELLED -> "Cancelled"
}

/**
 * Source attachment mode for project creation. Mirrors the web's binary
 * `sourceMode` toggle ("repos" vs "local"); NONE means no resources attached.
 */
enum class ProjectSourceMode { NONE, GITHUB, LOCAL }
