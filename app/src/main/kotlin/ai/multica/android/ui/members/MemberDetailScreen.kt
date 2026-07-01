package ai.multica.android.ui.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
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
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.auth.WorkspaceStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.MemberWithUser
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.data.repository.MemberRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.MulticaAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemberDetailViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val issueRepository: IssueRepository,
    private val workspaceStore: WorkspaceStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("id")
        ?: error("MemberDetailViewModel requires 'id' nav arg")

    private val _state = MutableStateFlow(MemberDetailUiState())
    val state: StateFlow<MemberDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val wsId = workspaceStore.getId()
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val member: MemberWithUser? = if (wsId != null) {
                val members = (memberRepository.list(wsId) as? ApiResult.Success)?.data
                members?.firstOrNull { it.userId == userId }
            } else null
            val issuesResult = issueRepository.list(assigneeId = userId, limit = 50)
            val issues = (issuesResult as? ApiResult.Success)?.data?.issues ?: emptyList()
            _state.update {
                it.copy(
                    isLoading = false,
                    member = member,
                    issues = issues,
                    errorMessage = when {
                        issuesResult is ApiResult.HttpError -> issuesResult.message
                        issuesResult is ApiResult.NetworkError -> "Network error"
                        else -> null
                    },
                )
            }
        }
    }
}

data class MemberDetailUiState(
    val isLoading: Boolean = false,
    val member: MemberWithUser? = null,
    val issues: List<Issue> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    onBack: () -> Unit,
    onOpenIssue: (String) -> Unit,
    viewModel: MemberDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.member?.name ?: stringResource(R.string.members_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    val m = state.member
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MulticaAvatar(name = m?.name ?: "M", avatarUrl = m?.avatarUrl, size = 56.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(m?.name ?: "Member", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            if (m != null) {
                                Text(m.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(m.role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    Text("Assigned issues (${state.issues.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                if (state.issues.isEmpty()) {
                    item { EmptyState(icon = Icons.Filled.Person, title = "No assigned issues") }
                } else {
                    items(state.issues, key = { it.id }) { issue ->
                        Card(onClick = { onOpenIssue(issue.id) }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(issue.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(issue.identifier, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                ai.multica.android.ui.components.StatusChip(status = issue.status)
                            }
                        }
                    }
                }
            }
        }
    }
}
