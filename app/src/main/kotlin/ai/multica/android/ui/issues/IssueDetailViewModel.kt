package ai.multica.android.ui.issues

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.dto.UpdateIssueRequest
import ai.multica.android.data.model.Comment
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.TimelineEntry
import ai.multica.android.data.model.TimelineRow
import ai.multica.android.data.repository.AuthRepository
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.domain.TimelineCoalesce
import ai.multica.android.domain.TimelineThread
import ai.multica.android.realtime.RealtimeEntryPoint
import ai.multica.android.realtime.WsEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IssueDetailViewModel @Inject constructor(
    private val issueRepository: IssueRepository,
    private val authRepository: AuthRepository,
    savedStateHandle: SavedStateHandle,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val issueId: String = savedStateHandle.get<String>("id")
        ?: error("IssueDetailViewModel requires 'id' nav arg")

    private val _state = MutableStateFlow(IssueDetailUiState())
    val state: StateFlow<IssueDetailUiState> = _state.asStateFlow()

    private val realtimeManager = RealtimeEntryPoint.get(context).realtimeManager()
    private var wsJob: Job? = null
    private var currentUserId: String? = null

    init {
        viewModelScope.launch { currentUserId = authRepository.getMe().getOrNull()?.id }
        refresh()
        observeRealtime()
    }

    private fun observeRealtime() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            realtimeManager.events.collect { event ->
                when (event) {
                    is WsEvent.CommentCreated, is WsEvent.CommentUpdated,
                    is WsEvent.CommentDeleted, is WsEvent.CommentResolved,
                    is WsEvent.CommentUnresolved -> {
                        if (issueId == (event as? WsEvent.CommentCreated)?.issueId
                            || issueId == (event as? WsEvent.CommentUpdated)?.issueId
                            || issueId == (event as? WsEvent.CommentDeleted)?.issueId
                            || issueId == (event as? WsEvent.CommentResolved)?.issueId
                            || issueId == (event as? WsEvent.CommentUnresolved)?.issueId
                        ) {
                            refresh()
                        }
                    }
                    is WsEvent.IssueUpdated -> {
                        if (event.issueId == issueId) refresh()
                    }
                    else -> Unit
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            val issueResult = issueRepository.get(issueId)
            val timelineResult = issueRepository.timeline(issueId)
            val issue = (issueResult as? ApiResult.Success)?.data
            val rawTimeline = (timelineResult as? ApiResult.Success)?.data ?: emptyList()

            val coalesced = TimelineCoalesce.coalesce(rawTimeline)
            val rows = TimelineThread.build(coalesced)

            _state.update {
                it.copy(
                    isLoading = false,
                    issue = issue,
                    rawTimeline = rawTimeline,
                    rows = rows,
                    errorMessage = when {
                        issueResult is ApiResult.HttpError -> issueResult.message
                        timelineResult is ApiResult.HttpError -> timelineResult.message
                        issueResult is ApiResult.NetworkError -> "Network error"
                        else -> null
                    },
                )
            }
        }
    }

    fun postComment(content: String, parentId: String? = null) {
        if (content.isBlank()) return
        viewModelScope.launch {
            // Optimistic insert: show the comment immediately, replace
            // when the server responds. The TimelineEntry is built
            // from a temp id; the refresh after success will replace
            // it with the real one from the server.
            val tempId = "temp-${System.currentTimeMillis()}"
            val now = kotlinx.datetime.Clock.System.now().toString()
            val optimistic = TimelineEntry(
                type = "comment",
                id = tempId,
                actorType = ai.multica.android.data.model.CommentAuthorType.MEMBER,
                actorId = currentUserId,
                content = content.trim(),
                parentId = parentId,
                createdAt = now,
            )
            _state.update { it.copy(isPostingComment = true) }
            // Optimistic — add to the rows.
            _state.update { st ->
                val newRows = if (parentId == null) {
                    st.rows + TimelineRow(root = optimistic)
                } else {
                    // Attach to parent's row.
                    st.rows.map { row ->
                        if (row.root.id == parentId) {
                            row.copy(replies = row.replies + optimistic)
                        } else row
                    }
                }
                st.copy(rows = newRows, draftComment = "", draftReplyTo = null)
            }
            when (issueRepository.createComment(issueId, content.trim(), parentId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isPostingComment = false) }
                    refresh()
                }
                else -> {
                    _state.update { it.copy(isPostingComment = false) }
                    refresh()
                }
            }
        }
    }

    fun onDraftChange(value: String) {
        _state.update { it.copy(draftComment = value) }
    }

    fun startReply(commentId: String) {
        val comment = _state.value.rows.firstNotNullOfOrNull { row ->
            if (row.root.id == commentId) row.root
            else row.replies.firstOrNull { it.id == commentId }
        }
        _state.update {
            it.copy(
                draftReplyTo = commentId,
                draftComment = if (comment != null) "@${comment.actorId?.take(8) ?: ""} " else "",
            )
        }
    }

    fun cancelReply() {
        _state.update { it.copy(draftReplyTo = null, draftComment = "") }
    }

    fun toggleReaction(commentId: String, emoji: String) {
        viewModelScope.launch {
            // Find the comment and check whether we have an existing reaction.
            val existing = _state.value.rawTimeline.firstNotNullOfOrNull { entry ->
                if (entry.id == commentId) entry
                else null
            }
            val myExisting = existing?.reactions?.firstOrNull {
                it.emoji == emoji && it.actorId == currentUserId
            }
            if (myExisting != null) {
                issueRepository.removeReaction(commentId, emoji)
            } else {
                issueRepository.addReaction(commentId, emoji)
            }
            refresh()
        }
    }

    fun updateStatus(status: IssueStatus) {
        viewModelScope.launch {
            when (val r = issueRepository.update(issueId, UpdateIssueRequest(status = status.name.lowercase()))) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data) }
                else -> Unit
            }
        }
    }

    fun updatePriority(priority: IssuePriority) {
        viewModelScope.launch {
            when (val r = issueRepository.update(issueId, UpdateIssueRequest(priority = priority.name.lowercase()))) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data) }
                else -> Unit
            }
        }
    }

    fun toggleResolveComment(commentId: String, isCurrentlyResolved: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyResolved) {
                issueRepository.unresolveComment(commentId)
            } else {
                issueRepository.resolveComment(commentId)
            }
            refresh()
        }
    }

    override fun onCleared() {
        wsJob?.cancel()
        super.onCleared()
    }
}

data class IssueDetailUiState(
    val isLoading: Boolean = false,
    val issue: Issue? = null,
    val rawTimeline: List<TimelineEntry> = emptyList(),
    val rows: List<TimelineRow> = emptyList(),
    val isPostingComment: Boolean = false,
    val draftComment: String = "",
    val draftReplyTo: String? = null,
    val errorMessage: String? = null,
)
