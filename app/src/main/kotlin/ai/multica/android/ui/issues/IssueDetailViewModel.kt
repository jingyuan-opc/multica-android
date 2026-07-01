package ai.multica.android.ui.issues

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ai.multica.android.core.network.ApiResult
import ai.multica.android.data.dto.UpdateIssueRequest
import ai.multica.android.data.model.ChildIssueProgressResponse
import ai.multica.android.data.model.Comment
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.IssueAssigneeType
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.IssueSubscriber
import ai.multica.android.data.model.Label
import ai.multica.android.data.model.MemberWithUser
import ai.multica.android.data.model.PinnedItem
import ai.multica.android.data.model.PinnedItemType
import ai.multica.android.data.model.Project
import ai.multica.android.data.model.TimelineEntry
import ai.multica.android.data.model.TimelineRow
import ai.multica.android.data.repository.AgentRepository
import ai.multica.android.data.repository.AuthRepository
import ai.multica.android.data.repository.IssueRepository
import ai.multica.android.data.repository.LabelRepository
import ai.multica.android.data.repository.MemberRepository
import ai.multica.android.data.repository.PinRepository
import ai.multica.android.data.repository.ProjectRepository
import ai.multica.android.data.repository.SquadRepository
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
    private val memberRepository: MemberRepository,
    private val agentRepository: AgentRepository,
    private val squadRepository: SquadRepository,
    private val projectRepository: ProjectRepository,
    private val labelRepository: LabelRepository,
    private val pinRepository: PinRepository,
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
        // Resolve the current user FIRST, then load data. This serializes the
        // loads so isSubscribed / reaction-ownership checks have a non-null
        // currentUserId before the subscribers/reactions lists land — otherwise
        // toggleSubscribe could flip the wrong direction and reactions could
        // stack duplicates on first interaction.
        viewModelScope.launch {
            val me = authRepository.getMe().getOrNull()?.id
            currentUserId = me
            _state.update { it.copy(currentUserId = me) }
            refresh()
        }
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

            // Load supporting data in parallel (workspace-scoped helpers).
            val wsId = issue?.workspaceId
            if (wsId != null) {
                loadHelpers(wsId, issue)
            }

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

    /**
     * Fetch pickers' option lists + child issues + subscribers + pin status.
     * Failures here are non-fatal — pickers just render empty options.
     */
    private suspend fun loadHelpers(workspaceId: String, issue: Issue) {
        // If the workspace changed since the last load, drop the cached picker
        // option lists so we don't show the previous workspace's members/labels.
        val cacheStale = _state.value.helpersWorkspaceId != null &&
            _state.value.helpersWorkspaceId != workspaceId
        if (cacheStale) {
            _state.update {
                it.copy(
                    members = emptyList(),
                    agents = emptyList(),
                    squads = emptyList(),
                    projects = emptyList(),
                    workspaceLabels = emptyList(),
                    helpersWorkspaceId = workspaceId,
                )
            }
        } else if (_state.value.helpersWorkspaceId == null) {
            _state.update { it.copy(helpersWorkspaceId = workspaceId) }
        }

        // Members/agents/squads/projects/labels — picker option lists.
        if (_state.value.members.isEmpty()) {
            (memberRepository.list(workspaceId) as? ApiResult.Success)?.data?.let { members ->
                _state.update { it.copy(members = members) }
            }
        }
        if (_state.value.agents.isEmpty()) {
            (agentRepository.list() as? ApiResult.Success)?.data?.let { agents ->
                _state.update { it.copy(agents = agents.filterNot { a -> a.archivedAt != null }) }
            }
        }
        if (_state.value.squads.isEmpty()) {
            (squadRepository.list() as? ApiResult.Success)?.data?.let { squads ->
                _state.update { it.copy(squads = squads) }
            }
        }
        if (_state.value.projects.isEmpty()) {
            (projectRepository.list() as? ApiResult.Success)?.data?.projects?.let { projects ->
                _state.update { it.copy(projects = projects) }
            }
        }
        if (_state.value.workspaceLabels.isEmpty()) {
            (labelRepository.list() as? ApiResult.Success)?.data?.labels?.let { labels ->
                _state.update { it.copy(workspaceLabels = labels) }
            }
        }
        // Sub-issues + progress.
        if (issue.parentIssueId == null) {
            (issueRepository.listChildren(issueId) as? ApiResult.Success)?.data?.let { children ->
                _state.update { it.copy(childIssues = children.issues) }
            }
        }
        // Subscribers.
        (issueRepository.listSubscribers(issueId) as? ApiResult.Success)?.data?.let { subs ->
            _state.update { it.copy(subscribers = subs) }
        }
        // Pin status (is this issue pinned by the current user?).
        if (_state.value.pins == null) {
            (pinRepository.list() as? ApiResult.Success)?.data?.let { pins ->
                _state.update { it.copy(pins = pins) }
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
                    // Attach to the row whose root or any reply matches parentId.
                    st.rows.map { row ->
                        if (row.root.id == parentId || row.replies.any { it.id == parentId }) {
                            row.copy(replies = row.replies + optimistic)
                        } else row
                    }
                }
                st.copy(rows = newRows)
            }
            when (val result = issueRepository.createComment(issueId, content.trim(), parentId)) {
                is ApiResult.Success -> {
                    _state.update { it.copy(isPostingComment = false, draftComment = "", draftReplyTo = null) }
                    refresh()
                }
                else -> {
                    // Roll back the optimistic row and restore the draft so the
                    // user can retry. Surface the error message.
                    _state.update { st ->
                        val cleaned = st.rows
                            .map { row -> row.copy(replies = row.replies.filterNot { it.id == tempId }) }
                            .filterNot { it.root.id == tempId }
                        st.copy(
                            isPostingComment = false,
                            rows = cleaned,
                            draftComment = content,
                            commentError = when (result) {
                                is ApiResult.HttpError -> result.message
                                is ApiResult.NetworkError -> "Network error — check your connection"
                                else -> "Failed to post comment"
                            },
                        )
                    }
                }
            }
        }
    }

    fun clearCommentError() {
        _state.update { it.copy(commentError = null) }
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

    fun updateTitle(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            when (val r = issueRepository.update(issueId, UpdateIssueRequest(title = title.trim()))) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data) }
                else -> Unit
            }
        }
    }

    fun updateDescription(description: String) {
        viewModelScope.launch {
            when (val r = issueRepository.update(issueId, UpdateIssueRequest(description = description.trim()))) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data, isEditingDescription = false) }
                else -> Unit
            }
        }
    }

    fun updateAssignee(type: IssueAssigneeType?, id: String?) {
        viewModelScope.launch {
            val body = UpdateIssueRequest(
                assigneeType = type?.name?.lowercase(),
                assigneeId = id,
            )
            when (val r = issueRepository.update(issueId, body)) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data) }
                else -> Unit
            }
        }
    }

    fun updateProject(projectId: String?) {
        viewModelScope.launch {
            when (val r = issueRepository.update(issueId, UpdateIssueRequest(projectId = projectId))) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data) }
                else -> Unit
            }
        }
    }

    fun updateStartDate(date: String?) {
        viewModelScope.launch {
            when (val r = issueRepository.update(issueId, UpdateIssueRequest(startDate = date))) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data) }
                else -> Unit
            }
        }
    }

    fun updateDueDate(date: String?) {
        viewModelScope.launch {
            when (val r = issueRepository.update(issueId, UpdateIssueRequest(dueDate = date))) {
                is ApiResult.Success -> _state.update { it.copy(issue = r.data) }
                else -> Unit
            }
        }
    }

    fun toggleLabel(labelId: String) {
        val attached = _state.value.issue?.labels?.any { it.id == labelId } == true
        viewModelScope.launch {
            val result = if (attached) issueRepository.detachLabel(issueId, labelId)
            else issueRepository.attachLabel(issueId, labelId)
            if (result is ApiResult.Success) {
                _state.update { st ->
                    st.copy(issue = st.issue?.copy(labels = result.data.labels))
                }
            }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            when (issueRepository.delete(issueId)) {
                is ApiResult.Success -> onDeleted()
                else -> Unit
            }
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val isPinned = _state.value.isPinned
            val result = if (isPinned) {
                pinRepository.delete(PinnedItemType.ISSUE, issueId)
            } else {
                pinRepository.create(PinnedItemType.ISSUE, issueId)
            }
            if (result is ApiResult.Success) {
                // Refresh pin list to reflect the change.
                (pinRepository.list() as? ApiResult.Success)?.data?.let { pins ->
                    _state.update { it.copy(pins = pins) }
                }
            }
        }
    }

    fun toggleSubscribe() {
        val isSubscribed = _state.value.isSubscribed
        viewModelScope.launch {
            val result = if (isSubscribed) issueRepository.unsubscribe(issueId)
            else issueRepository.subscribe(issueId)
            if (result is ApiResult.Success) {
                (issueRepository.listSubscribers(issueId) as? ApiResult.Success)?.data?.let { subs ->
                    _state.update { it.copy(subscribers = subs) }
                }
            }
        }
    }

    fun createSubIssue(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            // Atomic: pass parent_issue_id in the initial create so the link
            // can't orphan if a follow-up update fails. The child inherits the
            // active workspace via AuthInterceptor's X-Workspace-Slug (which is
            // set from WorkspaceStore — guaranteed populated because the issue
            // detail only opens after HomeViewModel selected a workspace).
            val createResult = issueRepository.create(
                title = title.trim(),
                projectId = _state.value.issue?.projectId,
                parentIssueId = issueId,
            )
            if (createResult is ApiResult.Success) {
                (issueRepository.listChildren(issueId) as? ApiResult.Success)?.data?.let { children ->
                    _state.update { it.copy(childIssues = children.issues, draftChildTitle = "") }
                }
            }
        }
    }

    fun toggleIssueReaction(emoji: String) {
        viewModelScope.launch {
            val mine = _state.value.issue?.reactions?.firstOrNull {
                it.emoji == emoji && it.actorId == currentUserId
            }
            if (mine != null) issueRepository.removeIssueReaction(issueId, emoji)
            else issueRepository.addIssueReaction(issueId, emoji)
            refresh()
        }
    }

    fun setEditingDescription(editing: Boolean) {
        _state.update {
            it.copy(isEditingDescription = editing, descriptionDraft = it.issue?.description.orEmpty())
        }
    }

    fun onDescriptionDraftChange(value: String) {
        _state.update { it.copy(descriptionDraft = value) }
    }

    fun onChildTitleChange(value: String) {
        _state.update { it.copy(draftChildTitle = value) }
    }

    fun setEditingTitle(editing: Boolean) {
        _state.update {
            it.copy(isEditingTitle = editing, titleDraft = it.issue?.title.orEmpty())
        }
    }

    fun onTitleDraftChange(value: String) {
        _state.update { it.copy(titleDraft = value) }
    }

    fun saveTitle() {
        val draft = _state.value.titleDraft
        if (draft.isNotBlank()) updateTitle(draft)
        _state.update { it.copy(isEditingTitle = false) }
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
    val commentError: String? = null,
    val errorMessage: String? = null,
    // Picker option lists.
    val helpersWorkspaceId: String? = null,
    val members: List<MemberWithUser> = emptyList(),
    val agents: List<ai.multica.android.data.model.Agent> = emptyList(),
    val squads: List<ai.multica.android.data.model.Squad> = emptyList(),
    val projects: List<Project> = emptyList(),
    val workspaceLabels: List<Label> = emptyList(),
    // Sub-issues.
    val childIssues: List<Issue> = emptyList(),
    val draftChildTitle: String = "",
    // Subscribers.
    val subscribers: List<IssueSubscriber> = emptyList(),
    // Pins.
    val pins: List<PinnedItem>? = null,
    // Current user id (for subscriber check + reaction ownership).
    val currentUserId: String? = null,
    // Inline editing state.
    val isEditingTitle: Boolean = false,
    val titleDraft: String = "",
    val isEditingDescription: Boolean = false,
    val descriptionDraft: String = "",
) {
    val isPinned: Boolean
        get() = pins?.any { it.itemType == PinnedItemType.ISSUE && it.itemId == issue?.id } == true
    val isSubscribed: Boolean
        get() = currentUserId != null && subscribers.any { it.userId == currentUserId }
}
