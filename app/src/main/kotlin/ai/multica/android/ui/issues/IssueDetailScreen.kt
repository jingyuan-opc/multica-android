package ai.multica.android.ui.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.multica.android.R
import ai.multica.android.core.theme.PriorityColors
import ai.multica.android.data.model.CommentAuthorType
import ai.multica.android.data.model.Issue
import ai.multica.android.data.model.IssuePriority
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.TimelineEntry
import ai.multica.android.data.model.TimelineRow
import ai.multica.android.ui.comments.MarkdownLite
import ai.multica.android.ui.comments.MarkdownText
import ai.multica.android.ui.comments.ReactionsBar
import ai.multica.android.ui.components.MulticaAvatar
import ai.multica.android.ui.components.PriorityBars
import ai.multica.android.ui.components.StatusChip
import ai.multica.android.ui.components.labelFor as labelForStatus
import kotlinx.datetime.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IssueDetailScreen(
    onBack: () -> Unit,
    viewModel: IssueDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showStatusPicker by remember { mutableStateOf(false) }
    var showPriorityPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.issue?.identifier ?: "Issue",
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            CommentComposer(
                value = state.draftComment,
                replyTo = state.draftReplyTo,
                isPosting = state.isPostingComment,
                onValueChange = viewModel::onDraftChange,
                onPost = { viewModel.postComment(state.draftComment, state.draftReplyTo) },
                onCancelReply = viewModel::cancelReply,
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.issue != null,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.isLoading && state.issue == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@PullToRefreshBox
            }
            if (state.errorMessage != null && state.issue == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(state.errorMessage!!, color = MaterialTheme.colorScheme.error)
                }
                return@PullToRefreshBox
            }
            val issue = state.issue ?: return@PullToRefreshBox
            Column(modifier = Modifier.fillMaxSize()) {
                IssueHeader(
                    issue = issue,
                    onStatusClick = { showStatusPicker = true },
                    onPriorityClick = { showPriorityPicker = true },
                )
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.issue_comments),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(16.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.rows, key = { it.root.id }) { row ->
                        TimelineRowView(
                            row = row,
                            currentActorId = state.rawTimeline.firstOrNull()?.actorId,
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

    if (showStatusPicker) {
        StatusPickerSheet(
            current = state.issue?.status,
            onPick = {
                viewModel.updateStatus(it)
                showStatusPicker = false
            },
            onDismiss = { showStatusPicker = false },
        )
    }
    if (showPriorityPicker) {
        PriorityPickerSheet(
            current = state.issue?.priority,
            onPick = {
                viewModel.updatePriority(it)
                showPriorityPicker = false
            },
            onDismiss = { showPriorityPicker = false },
        )
    }
}

@Composable
private fun IssueHeader(
    issue: Issue,
    onStatusClick: () -> Unit,
    onPriorityClick: () -> Unit,
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            text = issue.title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tappable status chip
            Surface(
                onClick = onStatusClick,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(status = issue.status)
                }
            }
            Spacer(Modifier.width(8.dp))
            // Tappable priority bars
            Surface(
                onClick = onPriorityClick,
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBars(priority = issue.priority)
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
        if (!issue.description.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            ) {
                MarkdownText(
                    text = issue.description,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    linkColor = MaterialTheme.colorScheme.primary,
                    codeColor = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

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
            "comment" -> CommentEntry(
                entry = row.root,
                isTop = true,
                currentActorId = currentActorId,
                onToggleReaction = onToggleReaction,
                onReply = onReply,
                onToggleResolve = onToggleResolve,
            )
            "activity" -> ActivityEntry(entry = row.root)
            "status_change" -> StatusChangeEntry(entry = row.root)
            "progress_update" -> ProgressUpdateEntry(entry = row.root)
            "system" -> SystemEntry(entry = row.root)
            else -> CommentEntry(
                entry = row.root,
                isTop = true,
                currentActorId = currentActorId,
                onToggleReaction = onToggleReaction,
                onReply = onReply,
                onToggleResolve = onToggleResolve,
            )
        }
        if (row.replies.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .padding(start = 32.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.replies.forEach { reply ->
                    CommentEntry(
                        entry = reply,
                        isTop = false,
                        currentActorId = currentActorId,
                        onToggleReaction = onToggleReaction,
                        onReply = onReply,
                        onToggleResolve = onToggleResolve,
                    )
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
                Text(
                    text = authorLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isResolved) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                    ) {
                        Text(
                            text = "Resolved",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            if (entry.content.isNullOrBlank()) {
                Text(
                    text = "(deleted)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                )
            } else {
                MarkdownText(
                    text = entry.content,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    linkColor = MaterialTheme.colorScheme.primary,
                    codeColor = MaterialTheme.colorScheme.tertiary,
                )
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
                    TextButton(
                        onClick = { onReply(entry.id) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Reply,
                            contentDescription = "Reply",
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(2.dp))
                        Text("Reply", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(
                        onClick = { onToggleResolve(entry.id, isResolved) },
                        contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(
                            text = if (isResolved) "Reopen" else "Resolve",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityEntry(entry: TimelineEntry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${actorLabel(entry)} • ${entry.action ?: "activity"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusChangeEntry(entry: TimelineEntry) {
    Text(
        text = "${actorLabel(entry)} changed status • ${formatTimestamp(entry.createdAt)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ProgressUpdateEntry(entry: TimelineEntry) {
    Text(
        text = entry.content ?: "Progress update",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SystemEntry(entry: TimelineEntry) {
    Text(
        text = entry.content ?: entry.action.orEmpty(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentComposer(
    value: String,
    replyTo: String?,
    isPosting: Boolean,
    onValueChange: (String) -> Unit,
    onPost: () -> Unit,
    onCancelReply: () -> Unit,
) {
    Surface(tonalElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
        Column {
            if (replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Reply,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Replying to a comment",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onCancelReply) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.issue_add_comment)) },
                    maxLines = 4,
                    enabled = !isPosting,
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(
                    onClick = onPost,
                    enabled = value.isNotBlank() && !isPosting,
                ) {
                    if (isPosting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Filled.Send, contentDescription = stringResource(R.string.issue_post))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPickerSheet(
    current: IssueStatus?,
    onPick: (IssueStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Change status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp),
            )
            IssueStatus.entries.forEach { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(status) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(status = status)
                    Spacer(Modifier.width(12.dp))
                    Text(labelForStatus(status), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    if (status == current) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Current",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityPickerSheet(
    current: IssuePriority?,
    onPick: (IssuePriority) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = "Change priority",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(16.dp),
            )
            IssuePriority.ORDER.forEach { p ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(p) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PriorityBars(priority = p)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = p.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    if (p == current) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Current",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

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
