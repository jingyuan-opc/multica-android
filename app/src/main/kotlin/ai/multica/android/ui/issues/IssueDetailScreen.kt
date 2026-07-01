package ai.multica.android.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.multica.android.R
import ai.multica.android.data.model.CommentAuthorType
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.IssueAssigneeType
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.Label
import ai.multica.android.data.model.TimelineEntry
import ai.multica.android.data.model.TimelineRow
import ai.multica.android.ui.comments.MarkdownText
import ai.multica.android.ui.comments.ReactionsBar
import ai.multica.android.ui.components.MulticaAvatar
import ai.multica.android.ui.components.StatusChip
import ai.multica.android.ui.components.labelFor as labelForStatus
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    onBack: () -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var showStatusPicker by remember { mutableStateOf(false) }
    var showPriorityPicker by remember { mutableStateOf(false) }
    var showLabelPicker by remember { mutableStateOf(false) }
    var showAssigneePicker by remember { mutableStateOf(false) }
    var showProjectPicker by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var dateEditTarget by remember { mutableStateOf<DateEditTarget?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.issue?.identifier ?: "Issue",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Pin toggle.
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.pins_add),
                            tint = if (state.isPinned) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Actions menu (kebab).
                    IconButton(onClick = { showActions = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = showActions, onDismissRequest = { showActions = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.issue_copy_link)) },
                            onClick = {
                                showActions = false
                                state.issue?.let { issue ->
                                    val url = "${ai.multica.android.BuildConfig.DEFAULT_SERVER_URL.trimEnd('/')}/issue/${issue.id}"
                                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                                }
                            },
                            leadingIcon = { Icon(Icons.Filled.Link, null) },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.issue_delete)) },
                            onClick = { showActions = false; showDeleteConfirm = true },
                            leadingIcon = { Icon(Icons.Filled.Delete, null) },
                        )
                    }
                },
            )
        },
        bottomBar = {
            CommentComposer(
                value = state.draftComment,
                replyTo = state.draftReplyTo,
                isPosting = state.isPostingComment,
                error = state.commentError,
                onValueChange = viewModel::onDraftChange,
                onPost = { viewModel.postComment(state.draftComment, state.draftReplyTo) },
                onCancelReply = viewModel::cancelReply,
                onDismissError = viewModel::clearCommentError,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.issue != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isLoading && state.issue == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@PullToRefreshBox
            }
            if (state.errorMessage != null && state.issue == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                return@PullToRefreshBox
            }
            val issue = state.issue ?: return@PullToRefreshBox
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                // Header: identifier + title (editable) + status/priority row.
                item(key = "header") {
                    IssueHeader(
                        issue = issue,
                        state = state,
                        onStatusClick = { showStatusPicker = true },
                        onPriorityClick = { showPriorityPicker = true },
                        onStartEditTitle = { viewModel.setEditingTitle(true) },
                        onTitleChange = viewModel::onTitleDraftChange,
                        onTitleSave = viewModel::saveTitle,
                        onCancelEditTitle = { viewModel.setEditingTitle(false) },
                        onStartEditDescription = { viewModel.setEditingDescription(true) },
                        onDescriptionChange = viewModel::onDescriptionDraftChange,
                        onDescriptionSave = { viewModel.updateDescription(state.descriptionDraft) },
                        onCancelEditDescription = { viewModel.setEditingDescription(false) },
                        onToggleIssueReaction = viewModel::toggleIssueReaction,
                    )
                }
                // Properties sidebar.
                item(key = "properties") {
                    IssuePropertiesSection(
                        issue = issue,
                        state = state,
                        onStatusClick = { showStatusPicker = true },
                        onPriorityClick = { showPriorityPicker = true },
                        onAssigneeClick = { showAssigneePicker = true },
                        onProjectClick = { showProjectPicker = true },
                        onLabelsClick = { showLabelPicker = true },
                        onStartDateClick = { dateEditTarget = DateEditTarget.START },
                        onDueDateClick = { dateEditTarget = DateEditTarget.DUE },
                    )
                }
                // Sub-issues (only for non-child issues).
                if (issue.parentIssueId == null) {
                    item(key = "sub_issues") {
                        SubIssuesSection(
                            children = state.childIssues,
                            draftTitle = state.draftChildTitle,
                            onDraftChange = viewModel::onChildTitleChange,
                            onAddChild = { viewModel.createSubIssue(state.draftChildTitle) },
                        )
                    }
                }
                // Activity / subscribers.
                item(key = "activity_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.issue_comments),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = viewModel::toggleSubscribe) {
                            Text(
                                text = if (state.isSubscribed) stringResource(R.string.issue_unsubscribe)
                                else stringResource(R.string.issue_subscribe),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
                // Timeline rows.
                items(state.rows, key = { it.root.id }) { row ->
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        TimelineRowView(
                            row = row,
                            currentActorId = state.currentUserId,
                            onToggleReaction = { id, emoji -> viewModel.toggleReaction(id, emoji) },
                            onReply = { id -> viewModel.startReply(id) },
                            onToggleResolve = { id, isResolved ->
                                viewModel.toggleResolveComment(id, isResolved)
                            },
                        )
                    }
                }
            }
        }
    }

    // Pickers (as bottom sheets).
    if (showStatusPicker) {
        StatusPickerSheet(state.issue?.status, viewModel::updateStatus) { showStatusPicker = false }
    }
    if (showPriorityPicker) {
        PriorityPickerSheet(state.issue?.priority, viewModel::updatePriority) { showPriorityPicker = false }
    }
    if (showAssigneePicker) {
        AssigneePickerSheet(
            currentType = state.issue?.assigneeType,
            currentId = state.issue?.assigneeId,
            members = state.members,
            agents = state.agents,
            squads = state.squads,
            onPick = { t, id -> viewModel.updateAssignee(t, id); showAssigneePicker = false },
            onDismiss = { showAssigneePicker = false },
        )
    }
    if (showProjectPicker) {
        ProjectPickerSheet(
            currentId = state.issue?.projectId,
            projects = state.projects,
            onPick = { viewModel.updateProject(it); showProjectPicker = false },
            onDismiss = { showProjectPicker = false },
        )
    }
    if (showLabelPicker) {
        LabelPickerSheet(
            issueLabels = state.issue?.labels ?: emptyList(),
            workspaceLabels = state.workspaceLabels,
            onToggle = viewModel::toggleLabel,
            onDismiss = { showLabelPicker = false },
        )
    }
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.issue_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete(onBack)
                }) { Text(stringResource(R.string.issue_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }
    // Date picker dialog for start/due date editing.
    dateEditTarget?.let { target ->
        IssueDatePickerDialog(
            title = if (target == DateEditTarget.START) stringResource(R.string.issue_start_date)
            else stringResource(R.string.issue_due_date),
            onPick = { iso ->
                if (target == DateEditTarget.START) viewModel.updateStartDate(iso)
                else viewModel.updateDueDate(iso)
                dateEditTarget = null
            },
            onClear = {
                if (target == DateEditTarget.START) viewModel.updateStartDate(null)
                else viewModel.updateDueDate(null)
                dateEditTarget = null
            },
            onDismiss = { dateEditTarget = null },
        )
    }
}

enum class DateEditTarget { START, DUE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueDatePickerDialog(
    title: String,
    onPick: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                DatePicker(state = state, showModeToggle = false)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    // Format as YYYY-MM-DD (the server expects calendar dates, no time/tz).
                    val instant = Instant.fromEpochMilliseconds(it)
                    val ldt = instant.toLocalDateTime(TimeZone.UTC)
                    onPick("${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}-${ldt.dayOfMonth.toString().padStart(2, '0')}")
                }
            }) { Text(stringResource(R.string.common_ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
}

// ---------- Header ----------

@Composable
private fun IssueHeader(
    issue: Issue,
    state: IssueDetailUiState,
    onStatusClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onStartEditTitle: () -> Unit,
    onTitleChange: (String) -> Unit,
    onTitleSave: () -> Unit,
    onCancelEditTitle: () -> Unit,
    onStartEditDescription: () -> Unit,
    onDescriptionChange: (String) -> Unit,
    onDescriptionSave: () -> Unit,
    onCancelEditDescription: () -> Unit,
    onToggleIssueReaction: (String) -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        // Title — inline editable.
        if (state.isEditingTitle) {
            OutlinedTextField(
                value = state.titleDraft,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.titleLarge,
                trailingIcon = {
                    IconButton(onClick = onTitleSave) { Icon(Icons.Filled.Check, "Save") }
                },
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = issue.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).clickable { onStartEditTitle() },
                )
                IconButton(onClick = onStartEditTitle) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Edit title")
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // Status + priority row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = onStatusClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status = issue.status)
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(onClick = onPriorityClick, shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    ai.multica.android.ui.components.PriorityBars(priority = issue.priority)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = issue.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "Updated ${formatTimestamp(issue.updatedAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Description — editable.
        Spacer(Modifier.height(12.dp))
        if (state.isEditingDescription) {
            Column {
                OutlinedTextField(
                    value = state.descriptionDraft,
                    onValueChange = onDescriptionChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Description") },
                    minLines = 3,
                    maxLines = 8,
                )
                Row {
                    TextButton(onClick = onDescriptionSave) { Text("Save") }
                    TextButton(onClick = onCancelEditDescription) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        } else if (!issue.description.isNullOrBlank()) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                onClick = onStartEditDescription,
            ) {
                MarkdownText(
                    text = issue.description,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    linkColor = MaterialTheme.colorScheme.primary,
                    codeColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                )
            }
        } else {
            TextButton(onClick = onStartEditDescription) { Text("+ Add description") }
        }
        // Labels chips.
        if (issue.labels.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(issue.labels, key = { it.id }) { label ->
                    LabelChip(label = label)
                }
            }
        }
        // Issue-level reactions.
        Spacer(Modifier.height(8.dp))
        ReactionsBar(
            reactions = issue.reactions.map {
                ai.multica.android.data.model.Reaction(
                    id = it.id,
                    commentId = "",
                    actorType = it.actorType,
                    actorId = it.actorId,
                    emoji = it.emoji,
                    createdAt = it.createdAt,
                )
            },
            currentActorId = state.currentUserId,
            onToggle = onToggleIssueReaction,
            onAdd = onToggleIssueReaction,
        )
    }
}

// ---------- Properties section ----------

@Composable
private fun IssuePropertiesSection(
    issue: Issue,
    state: IssueDetailUiState,
    onStatusClick: () -> Unit,
    onPriorityClick: () -> Unit,
    onAssigneeClick: () -> Unit,
    onProjectClick: () -> Unit,
    onLabelsClick: () -> Unit,
    onStartDateClick: () -> Unit,
    onDueDateClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.issue_properties),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            // Each property is a tappable row opening a picker sheet.
            PropertyRow(label = "Status", value = labelForStatus(issue.status), onClick = onStatusClick)
            PropertyRow(
                label = stringResource(R.string.issue_assignee),
                value = state.issue?.let {
                    when (it.assigneeType) {
                        IssueAssigneeType.MEMBER -> state.members.firstOrNull { m -> m.userId == it.assigneeId }?.name ?: "Member"
                        IssueAssigneeType.AGENT -> state.agents.firstOrNull { a -> a.id == it.assigneeId }?.name ?: "Agent"
                        IssueAssigneeType.SQUAD -> state.squads.firstOrNull { s -> s.id == it.assigneeId }?.name ?: "Squad"
                        null -> stringResource(R.string.issue_unassigned)
                    }
                } ?: stringResource(R.string.issue_unassigned),
                onClick = onAssigneeClick,
            )
            PropertyRow(
                label = stringResource(R.string.issue_project),
                value = state.projects.firstOrNull { it.id == issue.projectId }?.title
                    ?: "None",
                onClick = onProjectClick,
            )
            PropertyRow(
                label = stringResource(R.string.issue_priority),
                value = issue.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                onClick = onPriorityClick,
            )
            PropertyRow(
                label = stringResource(R.string.issue_labels),
                value = if (issue.labels.isEmpty()) stringResource(R.string.issue_no_labels)
                else issue.labels.joinToString { it.name },
                onClick = onLabelsClick,
            )
            PropertyRow(
                label = stringResource(R.string.issue_start_date),
                value = issue.startDate ?: "—",
                onClick = onStartDateClick,
            )
            PropertyRow(
                label = stringResource(R.string.issue_due_date),
                value = issue.dueDate ?: "—",
                onClick = onDueDateClick,
            )
        }
    }
}

@Composable
private fun PropertyRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------- Sub-issues ----------

@Composable
private fun SubIssuesSection(
    children: List<Issue>,
    draftTitle: String,
    onDraftChange: (String) -> Unit,
    onAddChild: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.issue_sub_issues),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (children.isNotEmpty()) {
                Text(
                    text = "${children.count { it.status == IssueStatus.DONE }}/${children.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        children.forEach { child ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(status = child.status)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${child.identifier}  ${child.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Quick add child.
        OutlinedTextField(
            value = draftTitle,
            onValueChange = onDraftChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.issue_add_sub_issue)) },
            singleLine = true,
            trailingIcon = {
                if (draftTitle.isNotBlank()) {
                    IconButton(onClick = onAddChild) {
                        Icon(Icons.Filled.Add, contentDescription = "Add sub-issue")
                    }
                }
            },
        )
    }
}

// ---------- Timeline ----------

@Composable
private fun TimelineRowView(
    row: TimelineRow,
    currentActorId: String?,
    onToggleReaction: (commentId: String, emoji: String) -> Unit,
    onReply: (commentId: String) -> Unit,
    onToggleResolve: (commentId: String, isResolved: Boolean) -> Unit,
) {
    Column {
        when (row.root.type) {
            "comment" -> CommentEntry(row.root, true, currentActorId, onToggleReaction, onReply, onToggleResolve)
            "activity" -> ActivityEntry(row.root)
            "status_change" -> StatusChangeEntry(row.root)
            "progress_update" -> ProgressUpdateEntry(row.root)
            "system" -> SystemEntry(row.root)
            else -> CommentEntry(row.root, true, currentActorId, onToggleReaction, onReply, onToggleResolve)
        }
        if (row.replies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .padding(start = 32.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.replies.forEach { reply ->
                    CommentEntry(reply, false, currentActorId, onToggleReaction, onReply, onToggleResolve)
                }
            }
        }
    }
}

@Composable
private fun CommentEntry(
    entry: TimelineEntry,
    isTop: Boolean,
    currentActorId: String?,
    onToggleReaction: (commentId: String, emoji: String) -> Unit,
    onReply: (commentId: String) -> Unit,
    onToggleResolve: (commentId: String, isResolved: Boolean) -> Unit,
) {
    val authorLabel = when (entry.actorType) {
        CommentAuthorType.AGENT -> "Agent ${entry.actorId?.takeLast(4).orEmpty()}"
        CommentAuthorType.SYSTEM -> "System"
        else -> "User"
    }
    val isResolved = !entry.resolvedAt.isNullOrBlank()
    Row(verticalAlignment = Alignment.Top) {
        MulticaAvatar(name = authorLabel, avatarUrl = null, size = if (isTop) 32.dp else 24.dp)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(authorLabel, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(8.dp))
                Text(formatTimestamp(entry.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isResolved) {
                    Spacer(Modifier.width(8.dp))
                    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)) {
                        Text("Resolved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (entry.content.isNullOrBlank()) {
                Text("(deleted)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
            } else {
                MarkdownText(text = entry.content, textColor = MaterialTheme.colorScheme.onSurface, linkColor = MaterialTheme.colorScheme.primary, codeColor = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ReactionsBar(
                    reactions = entry.reactions,
                    currentActorId = currentActorId,
                    onToggle = { emoji -> onToggleReaction(entry.id, emoji) },
                    onAdd = { emoji -> onToggleReaction(entry.id, emoji) },
                )
                Spacer(Modifier.weight(1f))
                if (isTop) {
                    TextButton(onClick = { onReply(entry.id) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Icon(Icons.AutoMirrored.Filled.Reply, "Reply", modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Reply", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { onToggleResolve(entry.id, isResolved) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(if (isResolved) "Reopen" else "Resolve", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityEntry(entry: TimelineEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text("${actorLabel(entry)} • ${entry.action ?: "activity"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatusChangeEntry(entry: TimelineEntry) {
    Text("${actorLabel(entry)} changed status • ${formatTimestamp(entry.createdAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ProgressUpdateEntry(entry: TimelineEntry) {
    Text(entry.content ?: "Progress update", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SystemEntry(entry: TimelineEntry) {
    Text(entry.content ?: entry.action.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

// ---------- Composer ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentComposer(
    value: String,
    replyTo: String?,
    isPosting: Boolean,
    error: String?,
    onValueChange: (String) -> Unit,
    onPost: () -> Unit,
    onCancelReply: () -> Unit,
    onDismissError: () -> Unit,
) {
    Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column {
            if (error != null) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = error,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onDismissError) {
                            Text(stringResource(R.string.common_ok), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (replyTo != null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Reply, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Replying to a comment", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onCancelReply) { Text("Cancel", style = MaterialTheme.typography.labelSmall) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = value, onValueChange = onValueChange, modifier = Modifier.weight(1f), placeholder = { Text(stringResource(R.string.issue_add_comment)) }, maxLines = 4, enabled = !isPosting)
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onPost, enabled = value.isNotBlank() && !isPosting) {
                    if (isPosting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.issue_post))
                }
            }
        }
    }
}

// ---------- Picker sheets ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPickerSheet(current: IssueStatus?, onPick: (IssueStatus) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Change status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            IssueStatus.entries.forEach { status ->
                Row(Modifier.fillMaxWidth().clickable { onPick(status) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(status = status)
                    Spacer(Modifier.width(12.dp))
                    Text(labelForStatus(status), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (status == current) Icon(Icons.Filled.Check, "Current", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityPickerSheet(current: IssuePriority?, onPick: (IssuePriority) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Change priority", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            IssuePriority.ORDER.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { onPick(p) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ai.multica.android.ui.components.PriorityBars(priority = p)
                    Spacer(Modifier.width(12.dp))
                    Text(p.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (p == current) Icon(Icons.Filled.Check, "Current", tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssigneePickerSheet(
    currentType: IssueAssigneeType?,
    currentId: String?,
    members: List<ai.multica.android.data.model.MemberWithUser>,
    agents: List<ai.multica.android.data.model.Agent>,
    squads: List<ai.multica.android.data.model.Squad>,
    onPick: (IssueAssigneeType?, String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text("Assign to", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            // Unassigned.
            AssigneeOptionRow(label = stringResource(R.string.issue_unassigned), selected = currentType == null, onClick = { onPick(null, null) })
            if (members.isNotEmpty()) {
                PickerSheetHeader("Members")
                members.forEach { m ->
                    AssigneeOptionRow(
                        label = m.name,
                        subtitle = m.email,
                        selected = currentType == IssueAssigneeType.MEMBER && currentId == m.userId,
                        leading = { MulticaAvatar(name = m.name, avatarUrl = m.avatarUrl, size = 32.dp) },
                        onClick = { onPick(IssueAssigneeType.MEMBER, m.userId) },
                    )
                }
            }
            if (agents.isNotEmpty()) {
                PickerSheetHeader("Agents")
                agents.forEach { a ->
                    AssigneeOptionRow(
                        label = a.name,
                        subtitle = a.model.ifBlank { null },
                        selected = currentType == IssueAssigneeType.AGENT && currentId == a.id,
                        leading = { Icon(Icons.Filled.SmartToy, null) },
                        onClick = { onPick(IssueAssigneeType.AGENT, a.id) },
                    )
                }
            }
            if (squads.isNotEmpty()) {
                PickerSheetHeader("Squads")
                squads.forEach { s ->
                    AssigneeOptionRow(
                        label = s.name,
                        subtitle = "${s.memberCount} members",
                        selected = currentType == IssueAssigneeType.SQUAD && currentId == s.id,
                        leading = { Icon(Icons.Filled.Group, null) },
                        onClick = { onPick(IssueAssigneeType.SQUAD, s.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProjectPickerSheet(
    currentId: String?,
    projects: List<ai.multica.android.data.model.Project>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text("Select project", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            AssigneeOptionRow(label = "None", selected = currentId == null, onClick = { onPick(null) })
            projects.forEach { p ->
                AssigneeOptionRow(label = p.title, selected = p.id == currentId, onClick = { onPick(p.id) })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LabelPickerSheet(
    issueLabels: List<Label>,
    workspaceLabels: List<Label>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.issue_labels), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp))
            workspaceLabels.forEach { label ->
                val attached = issueLabels.any { it.id == label.id }
                Row(
                    Modifier.fillMaxWidth().clickable { onToggle(label.id) }.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LabelChip(label = label)
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = attached, onCheckedChange = { onToggle(label.id) })
                }
            }
            if (workspaceLabels.isEmpty()) {
                Text(stringResource(R.string.labels_empty), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun PickerSheetHeader(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

@Composable
private fun AssigneeOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (selected) Icon(Icons.Filled.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
    }
}

// ---------- Helpers ----------

private fun actorLabel(entry: TimelineEntry): String = when (entry.actorType) {
    CommentAuthorType.AGENT -> "Agent"
    CommentAuthorType.SYSTEM -> "System"
    else -> "User"
}

private fun formatTimestamp(iso: String): String = try {
    Instant.parse(iso).toString().replace("T", " ").take(16)
} catch (e: Throwable) {
    iso.take(10)
}
