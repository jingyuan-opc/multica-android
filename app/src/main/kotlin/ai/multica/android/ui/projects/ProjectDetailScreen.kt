package ai.multica.android.ui.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.network.ApiResult
import ai.multica.android.core.theme.PriorityColors
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.Project
import ai.multica.android.data.repository.IssueRepository
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
}

data class ProjectDetailUiState(
    val isLoading: Boolean = false,
    val project: Project? = null,
    val issues: List<Issue> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: String,
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.project?.title ?: "Project",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> ProjectDetailContent(
                    state = state,
                    onOpenIssue = onOpenIssue,
                )
            }
        }
    }
}

@Composable
private fun ProjectDetailContent(
    state: ProjectDetailUiState,
    onOpenIssue: (String) -> Unit,
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
                    ProjectStatusChip(status = project.status)
                }
                if (!project.description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = project.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Stat(label = "Issues", value = project.issueCount.toString())
                    Stat(label = "Done", value = project.doneCount.toString())
                    Stat(label = "Resources", value = project.resourceCount.toString())
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(PriorityColors.forProjectPriority(project.priority), CircleShape),
                    )
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
            item {
                EmptyState(
                    icon = Icons.Filled.BugReport,
                    title = "No issues in this project",
                )
            }
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
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun IssueCardCompact(issue: Issue, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PriorityBars(priority = issue.priority)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = issue.identifier,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(status = issue.status)
        }
    }
}
