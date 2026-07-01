package ai.multica.android.ui.members

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import ai.multica.android.R
import ai.multica.android.core.auth.WorkspaceEvents
import ai.multica.android.core.auth.WorkspaceStore
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.model.MemberWithUser
import ai.multica.android.data.repository.MemberRepository
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.MulticaAvatar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MembersViewModel @Inject constructor(
    private val memberRepository: MemberRepository,
    private val workspaceStore: WorkspaceStore,
    private val workspaceEvents: WorkspaceEvents,
    private val realtimeManager: ai.multica.android.realtime.RealtimeManager,
) : ViewModel() {

    private val _state = MutableStateFlow(MembersUiState())
    val state: StateFlow<MembersUiState> = _state.asStateFlow()
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
                _state.update { it.copy(members = emptyList()) }
                refresh()
            }
        }
    }

    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                if (event is ai.multica.android.realtime.WsEvent.MemberChanged) refresh()
            }
        }
    }

    fun refresh() {
        val wsId = workspaceStore.getId() ?: return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            when (val result = memberRepository.list(wsId)) {
                is ApiResult.Success -> _state.update { it.copy(isLoading = false, members = result.data) }
                is ApiResult.HttpError -> _state.update { it.copy(isLoading = false, errorMessage = result.message) }
                is ApiResult.NetworkError -> _state.update { it.copy(isLoading = false, errorMessage = "Network error") }
                is ApiResult.Unknown -> _state.update { it.copy(isLoading = false, errorMessage = "Unexpected error") }
            }
        }
    }

    fun updateRole(memberId: String, role: ai.multica.android.data.model.MemberRole) {
        val wsId = workspaceStore.getId() ?: return
        viewModelScope.launch {
            memberRepository.updateRole(wsId, memberId, role)
            refresh()
        }
    }

    fun remove(memberId: String) {
        val wsId = workspaceStore.getId() ?: return
        viewModelScope.launch {
            memberRepository.delete(wsId, memberId)
            refresh()
        }
    }

    override fun onCleared() {
        workspaceJob?.cancel()
        realtimeJob?.cancel()
        super.onCleared()
    }
}

data class MembersUiState(
    val isLoading: Boolean = false,
    val members: List<MemberWithUser> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    contentPadding: PaddingValues,
    onOpenMember: (String) -> Unit,
    onBack: () -> Unit = {},
    forceRefreshOnAppear: Boolean = false,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(forceRefreshOnAppear) { if (forceRefreshOnAppear) viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.members_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = state.isLoading && state.members.isNotEmpty(),
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading && state.members.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    state.errorMessage != null && state.members.isEmpty() ->
                        EmptyState(icon = Icons.Filled.Person, title = stringResource(R.string.members_empty), description = state.errorMessage)
                    state.members.isEmpty() ->
                        EmptyState(icon = Icons.Filled.Person, title = stringResource(R.string.members_empty))
                    else -> LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.members, key = { it.id }) { member ->
                            MemberCard(
                                member = member,
                                onClick = { onOpenMember(member.userId) },
                                onManage = { viewModel.updateRole(member.id, it) },
                                onRemove = { viewModel.remove(member.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberCard(
    member: MemberWithUser,
    onClick: () -> Unit,
    onManage: (ai.multica.android.data.model.MemberRole) -> Unit,
    onRemove: () -> Unit,
) {
    var showSheet by remember { mutableStateOf(false) }
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MulticaAvatar(name = member.name, avatarUrl = member.avatarUrl, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(member.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(member.email, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            // Tappable role chip → opens management sheet.
            AssistChip(onClick = { showSheet = true }, label = { Text(member.role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall) })
        }
    }
    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(stringResource(R.string.members_role), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
                ai.multica.android.data.model.MemberRole.entries.forEach { role ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onManage(role); showSheet = false }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(role.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (role == member.role) Icon(Icons.Filled.Check, "Current", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (member.role != ai.multica.android.data.model.MemberRole.OWNER) {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { showSheet = false; onRemove() }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Remove member", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
