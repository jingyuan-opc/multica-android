package ai.multica.android.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.Project
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.ui.components.EmptyState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class GlobalSearchViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(GlobalSearchUiState())
    val state: StateFlow<GlobalSearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _state.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.update { it.copy(issues = emptyList(), projects = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // debounce
            _state.update { it.copy(isSearching = true) }
            val issueResult = issueRepository.search(q, limit = 20)
            val projectResult = projectRepository.search(q, limit = 20)
            _state.update {
                it.copy(
                    isSearching = false,
                    issues = (issueResult as? ApiResult.Success)?.data?.issues?.map { i ->
                        Issue(
                            id = i.id,
                            workspaceId = i.workspaceId,
                            number = i.number,
                            identifier = i.identifier,
                            title = i.title,
                            status = i.status,
                            priority = i.priority,
                            creatorType = i.creatorType,
                            creatorId = i.creatorId,
                            createdAt = i.createdAt,
                            updatedAt = i.updatedAt,
                        )
                    } ?: emptyList(),
                    projects = (projectResult as? ApiResult.Success)?.data?.projects?.map { p ->
                        Project(
                            id = p.id,
                            workspaceId = p.workspaceId,
                            title = p.title,
                            status = p.status,
                            priority = p.priority,
                            createdAt = p.createdAt,
                            updatedAt = p.createdAt,
                        )
                    } ?: emptyList(),
                )
            }
        }
    }
}

data class GlobalSearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val issues: List<Issue> = emptyList(),
    val projects: List<Project> = emptyList(),
) {
    val hasResults: Boolean get() = issues.isNotEmpty() || projects.isNotEmpty()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    onOpenProject: (String) -> Unit,
    viewModel: GlobalSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.issues_search)) },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        if (state.query.isBlank()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(icon = Icons.Filled.Search, title = stringResource(R.string.issues_search))
            }
            return@Scaffold
        }
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.projects.isNotEmpty()) {
                item { Text("Projects", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                items(state.projects, key = { it.id }) { p ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenProject(p.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, null)
                        Spacer(Modifier.width(12.dp))
                        Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (state.issues.isNotEmpty()) {
                item { Text("Issues", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                items(state.issues, key = { it.id }) { i ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenIssue(i.id) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.BugReport, null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(i.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(i.identifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (!state.isSearching && !state.hasResults) {
                item { EmptyState(icon = Icons.Filled.Search, title = "No results") }
            }
        }
    }
}
