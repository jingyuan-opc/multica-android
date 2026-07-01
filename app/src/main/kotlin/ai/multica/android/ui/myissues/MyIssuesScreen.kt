package ai.multica.android.ui.myissues

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import ai.multica.android.data.model.Issue
import ai.multica.android.data.repository.AuthRepository
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.PriorityBars
import ai.multica.android.ui.components.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MyIssueScope { ALL, ASSIGNED, CREATED }

@HiltViewModel
class MyIssuesViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MyIssuesUiState())
    val state: StateFlow<MyIssuesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val me = authRepository.getMe().getOrNull()?.id
            _state.update { it.copy(currentUserId = me) }
            refresh()
        }
    }

    fun setScope(scope: MyIssueScope) {
        _state.update { it.copy(scope = scope) }
        refresh()
    }

    fun refresh() {
        val userId = _state.value.currentUserId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val scope = _state.value.scope
            val result = when (scope) {
                MyIssueScope.ALL -> issueRepository.list(assigneeId = userId, limit = 50)
                MyIssueScope.ASSIGNED -> issueRepository.list(assigneeId = userId, limit = 50)
                MyIssueScope.CREATED -> issueRepository.list(creatorId = userId, limit = 50)
            }
            val issues = (result as? ApiResult.Success)?.data?.issues ?: emptyList()
            // ALL = assigned ∪ created (de-duped).
            val merged = if (scope == MyIssueScope.ALL) {
                val created = (issueRepository.list(creatorId = userId, limit = 50) as? ApiResult.Success)?.data?.issues ?: emptyList()
                (issues + created).distinctBy { it.id }
            } else issues
            _state.update {
                it.copy(
                    isLoading = false,
                    issues = merged,
                    errorMessage = when {
                        result is ApiResult.HttpError -> result.message
                        result is ApiResult.NetworkError -> "Network error"
                        else -> null
                    },
                )
            }
        }
    }
}

data class MyIssuesUiState(
    val isLoading: Boolean = false,
    val scope: MyIssueScope = MyIssueScope.ASSIGNED,
    val issues: List<Issue> = emptyList(),
    val currentUserId: String? = null,
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyIssuesScreen(
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    viewModel: MyIssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.my_issues_title)) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
                TabRow(selectedTabIndex = state.scope.ordinal) {
                    Tab(
                        selected = state.scope == MyIssueScope.ASSIGNED,
                        onClick = { viewModel.setScope(MyIssueScope.ASSIGNED) },
                        text = { Text(stringResource(R.string.my_issues_scope_assigned)) },
                    )
                    Tab(
                        selected = state.scope == MyIssueScope.CREATED,
                        onClick = { viewModel.setScope(MyIssueScope.CREATED) },
                        text = { Text(stringResource(R.string.my_issues_scope_created)) },
                    )
                    Tab(
                        selected = state.scope == MyIssueScope.ALL,
                        onClick = { viewModel.setScope(MyIssueScope.ALL) },
                        text = { Text(stringResource(R.string.my_issues_scope_all)) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = state.isLoading && state.issues.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading && state.issues.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.errorMessage != null && state.issues.isEmpty() ->
                        EmptyState(icon = Icons.Filled.BugReport, title = stringResource(R.string.issues_empty), description = state.errorMessage)
                    state.issues.isEmpty() ->
                        EmptyState(icon = Icons.Filled.BugReport, title = stringResource(R.string.issues_empty))
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.issues, key = { it.id }) { issue ->
                            MyIssueRow(issue = issue, onClick = { onOpenIssue(issue.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MyIssueRow(issue: Issue, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PriorityBars(priority = issue.priority)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(issue.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(issue.identifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(status = issue.status)
        }
    }
}
