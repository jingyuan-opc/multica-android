package ai.multica.android.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import ai.multica.android.core.auth.WorkspaceEvents
import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.theme.PriorityColors
import ai.multica.android.data.model.Project
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.ProjectStatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val workspaceEvents: WorkspaceEvents,
) : ViewModel() {

    private val _state = MutableStateFlow(ProjectsUiState())
    val state: StateFlow<ProjectsUiState> = _state.asStateFlow()

    private var workspaceJob: Job? = null

    init {
        refresh()
        observeWorkspaceChanges()
    }

    private fun observeWorkspaceChanges() {
        workspaceJob?.cancel()
        workspaceJob = viewModelScope.launch {
            workspaceEvents.changes.collect {
                _state.update { it.copy(projects = emptyList()) }
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = projectRepository.list()) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, projects = result.data.projects)
                }
                is ApiResult.HttpError -> _state.update {
                    it.copy(isLoading = false, errorMessage = result.message)
                }
                is ApiResult.NetworkError -> _state.update {
                    it.copy(isLoading = false, errorMessage = "Network error")
                }
                is ApiResult.Unknown -> _state.update {
                    it.copy(isLoading = false, errorMessage = "Unexpected error")
                }
            }
        }
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        super.onCleared()
    }
}

data class ProjectsUiState(
    val isLoading: Boolean = false,
    val projects: List<Project> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    contentPadding: PaddingValues,
    onOpenProject: (String) -> Unit,
    onCreateProject: () -> Unit,
    refreshTrigger: Int = 0,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 每次 tab 出现在屏幕上就强制 refresh。用递增 Int token 作为 key
    // 确保每次切回都 re-fire (布尔值在分支内恒真)。
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.projects.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.projects.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.errorMessage != null && state.projects.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Folder,
                        title = stringResource(R.string.common_error),
                        description = state.errorMessage,
                    ) {
                        OutlinedButton(onClick = viewModel::refresh) {
                            Text(stringResource(R.string.common_retry))
                        }
                    }
                }
                state.projects.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Folder,
                        title = stringResource(R.string.projects_empty),
                        action = {
                            FilledTonalButton(onClick = onCreateProject) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Create project")
                            }
                        },
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(state.projects, key = { it.id }) { project ->
                            ProjectCard(project = project, onClick = { onOpenProject(project.id) })
                        }
                    }
                }
            }
        }

        // FAB (always visible) — bottom-right, above bottom nav
        FloatingActionButton(
            onClick = onCreateProject,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create project")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = project.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                ProjectStatusChip(status = project.status)
            }
            if (!project.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = project.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${project.issueCount} issues · ${project.doneCount} done",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(PriorityColors.forProjectPriority(project.priority), CircleShape),
                )
            }
        }
    }
}
