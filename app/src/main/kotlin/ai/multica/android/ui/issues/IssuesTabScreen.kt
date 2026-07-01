package ai.multica.android.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.auth.WorkspaceEvents
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.SearchIssueResult
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.realtime.WsEvent
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.PriorityBars
import ai.multica.android.ui.components.StatusChip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class IssuesViewMode { Board, List }

@OptIn(FlowPreview::class)
@HiltViewModel
class IssuesViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val workspaceEvents: WorkspaceEvents,
    @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(IssuesUiState())
    val state: StateFlow<IssuesUiState> = _state.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val realtimeManager = ai.multica.android.realtime.RealtimeEntryPoint.get(context).realtimeManager()
    private var workspaceJob: Job? = null
    private var realtimeJob: Job? = null

    init {
        refresh()
        observeSearch()
        observeWorkspaceChanges()
        observeRealtime()
    }

    private fun observeWorkspaceChanges() {
        workspaceJob?.cancel()
        workspaceJob = viewModelScope.launch {
            workspaceEvents.changes.collect {
                _state.update {
                    it.copy(issues = emptyList(), searchResults = null)
                }
                _searchQuery.value = ""
                refresh()
            }
        }
    }

    /**
     * Subscribe to the WebSocket event stream and refresh whenever an
     * issue in this workspace is created, updated, or deleted. Fixes
     * the "create a new issue and it doesn't show up in the list until
     * pull-to-refresh" bug.
     */
    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                when (event) {
                    is WsEvent.IssueUpdated -> refresh()
                    is WsEvent.InboxNew -> {
                        // Inbox events on a newly created issue are sent
                        // immediately after the issue is broadcast; this
                        // is a strong signal the list just changed.
                        refresh()
                    }
                    else -> Unit
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            _searchQuery
                .debounce(350)
                .distinctUntilChanged()
                .collect { q ->
                    if (q.isBlank()) {
                        _state.update { it.copy(searchResults = null, isSearching = false) }
                    } else {
                        _state.update { it.copy(isSearching = true) }
                        when (val r = issueRepository.search(q)) {
                            is ApiResult.Success -> _state.update {
                                it.copy(searchResults = r.data.issues, isSearching = false)
                            }
                            else -> _state.update { it.copy(isSearching = false) }
                        }
                    }
                }
        }
    }

    fun setMode(mode: IssuesViewMode) = _state.update { it.copy(mode = mode) }

    fun setStatusFilter(status: IssueStatus?) = _state.update { it.copy(statusFilter = status) }

    fun setPriorityFilter(priority: IssuePriority?) = _state.update { it.copy(priorityFilter = priority) }

    fun onSearchQueryChange(q: String) {
        _searchQuery.value = q
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _state.update { it.copy(searchResults = null) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = issueRepository.list(limit = 100)) {
                is ApiResult.Success -> _state.update {
                    it.copy(isLoading = false, issues = result.data.issues)
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
        realtimeJob?.cancel()
        super.onCleared()
    }
}

data class IssuesUiState(
    val isLoading: Boolean = false,
    val issues: List<Issue> = emptyList(),
    val mode: IssuesViewMode = IssuesViewMode.Board,
    val statusFilter: IssueStatus? = null,
    val priorityFilter: IssuePriority? = null,
    val searchResults: List<SearchIssueResult>? = null,
    val isSearching: Boolean = false,
    val errorMessage: String? = null,
) {
    val isSearchActive: Boolean
        get() = searchResults != null

    val visibleIssues: List<Issue>
        get() = issues.filter { issue ->
            (statusFilter == null || issue.status == statusFilter) &&
            (priorityFilter == null || issue.priority == priorityFilter)
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssuesTabScreen(
    contentPadding: PaddingValues,
    onOpenIssue: (String) -> Unit,
    onCreateIssue: () -> Unit,
    refreshTrigger: Int = 0,
    viewModel: IssuesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var showSearch by rememberSaveable { mutableStateOf(false) }

    // 每次 tab 出现在屏幕上(切回 Issues)就强制 refresh,
    // 因为 Hilt VM 跨 tab 切换只 init 一次,不重新拉数据。
    // 用一个递增的 Int token 作为 key,确保每次切回都重新触发
    // (布尔值在分支内恒真,LaunchedEffect 不会 re-fire)。
    LaunchedEffect(refreshTrigger) {
        if (refreshTrigger > 0) viewModel.refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (showSearch) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClose = {
                        viewModel.clearSearch()
                        showSearch = false
                    },
                )
            } else {
                IssuesToolbar(
                    mode = state.mode,
                    statusFilter = state.statusFilter,
                    priorityFilter = state.priorityFilter,
                    onSetMode = viewModel::setMode,
                    onSetStatusFilter = viewModel::setStatusFilter,
                    onSetPriorityFilter = viewModel::setPriorityFilter,
                    onOpenSearch = { showSearch = true },
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isLoading && state.issues.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isSearchActive -> SearchResultsList(
                        results = state.searchResults.orEmpty(),
                        isLoading = state.isSearching,
                        query = searchQuery,
                        onOpen = onOpenIssue,
                    )
                    state.isLoading && state.issues.isEmpty() -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.errorMessage != null && state.issues.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Filled.BugReport,
                            title = stringResource(R.string.common_error),
                            description = state.errorMessage,
                        ) {
                            OutlinedButton(onClick = viewModel::refresh) {
                                Text(stringResource(R.string.common_retry))
                            }
                        }
                    }
                    state.visibleIssues.isEmpty() -> {
                        EmptyState(
                            icon = Icons.Filled.BugReport,
                            title = stringResource(R.string.issues_empty),
                            action = {
                                FilledTonalButton(onClick = onCreateIssue) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Create issue")
                                }
                            },
                        )
                    }
                    else -> when (state.mode) {
                        IssuesViewMode.Board -> IssueBoard(issues = state.visibleIssues, onOpen = onOpenIssue)
                        IssuesViewMode.List -> IssueList(issues = state.visibleIssues, onOpen = onOpenIssue)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onCreateIssue,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create issue")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Surface(
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.issues_search)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchIssueResult>,
    isLoading: Boolean,
    query: String,
    onOpen: (String) -> Unit,
) {
    when {
        isLoading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        results.isEmpty() -> {
            EmptyState(
                icon = Icons.Filled.Search,
                title = "No results for \"$query\"",
            )
        }
        else -> {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results, key = { it.id }) { issue ->
                    SearchIssueRow(issue = issue, onClick = { onOpen(issue.id) })
                }
            }
        }
    }
}

@Composable
private fun SearchIssueRow(issue: SearchIssueResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
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
                val snippet = issue.matchedSnippet
                    ?: issue.matchedDescriptionSnippet
                    ?: issue.matchedCommentSnippet
                if (snippet != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            StatusChip(status = issue.status)
        }
    }
}

@Composable
private fun IssuesToolbar(
    mode: IssuesViewMode,
    statusFilter: IssueStatus?,
    priorityFilter: IssuePriority?,
    onSetMode: (IssuesViewMode) -> Unit,
    onSetStatusFilter: (IssueStatus?) -> Unit,
    onSetPriorityFilter: (IssuePriority?) -> Unit,
    onOpenSearch: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.issues_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSearch) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            IconToggleButton(checked = mode == IssuesViewMode.Board, onCheckedChange = {
                onSetMode(if (it) IssuesViewMode.Board else IssuesViewMode.List)
            }) {
                Icon(
                    imageVector = if (mode == IssuesViewMode.Board) Icons.Filled.ViewColumn else Icons.Filled.ViewAgenda,
                    contentDescription = "Toggle view",
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { onSetStatusFilter(null) },
                label = { Text("All") },
            )
            IssueStatus.BOARD.forEach { status ->
                FilterChip(
                    selected = statusFilter == status,
                    onClick = { onSetStatusFilter(status) },
                    label = { Text(labelFor(status)) },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = priorityFilter == null,
                onClick = { onSetPriorityFilter(null) },
                label = { Text("Any priority") },
            )
            IssuePriority.ORDER.forEach { p ->
                FilterChip(
                    selected = priorityFilter == p,
                    onClick = { onSetPriorityFilter(p) },
                    label = { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }
    }
}

@Composable
private fun IssueList(issues: List<Issue>, onOpen: (String) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(issues, key = { it.id }) { issue ->
            IssueRow(issue = issue, onClick = { onOpen(issue.id) })
        }
    }
}

@Composable
private fun IssueRow(issue: Issue, onClick: () -> Unit) {
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

@Composable
private fun IssueBoard(issues: List<Issue>, onOpen: (String) -> Unit) {
    val byStatus = remember(issues) {
        IssueStatus.BOARD.associateWith { status -> issues.filter { it.status == status } }
    }
    val scroll = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scroll)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IssueStatus.BOARD.forEach { status ->
            BoardColumn(
                title = labelFor(status),
                issues = byStatus[status].orEmpty(),
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun BoardColumn(
    title: String,
    issues: List<Issue>,
    onOpen: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = MaterialTheme.shapes.medium)
            .padding(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = issues.size.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        if (issues.isEmpty()) {
            Text(
                text = "No issues",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(8.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                issues.forEach { issue ->
                    IssueRow(issue = issue, onClick = { onOpen(issue.id) })
                }
            }
        }
    }
}

private fun labelFor(status: IssueStatus): String = when (status) {
    IssueStatus.BACKLOG -> "Backlog"
    IssueStatus.TODO -> "Todo"
    IssueStatus.IN_PROGRESS -> "In progress"
    IssueStatus.IN_REVIEW -> "In review"
    IssueStatus.DONE -> "Done"
    IssueStatus.BLOCKED -> "Blocked"
    IssueStatus.CANCELLED -> "Cancelled"
}
