package ai.multica.android.ui.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Inbox
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
import ai.multica.android.core.theme.SeverityColors
import ai.multica.android.data.model.InboxItem
import ai.multica.android.data.model.InboxItemType
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.ui.components.EmptyState
import ai.multica.android.ui.components.StatusChip
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    contentPadding: PaddingValues,
    onOpenIssue: (String) -> Unit,
    viewModel: InboxViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(modifier = Modifier.padding(contentPadding)) {
        InboxFilterBar(
            filter = state.filter,
            unreadCount = state.unreadCount,
            onFilterChange = viewModel::setFilter,
            onMarkAllRead = viewModel::markAllRead,
            onArchiveAllRead = viewModel::archiveAllRead,
        )
        PullToRefreshBox(
            isRefreshing = state.isLoading && state.items.isNotEmpty(),
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.isLoading && state.items.isEmpty() -> {
                    CenterBox { CircularProgressIndicator() }
                }
                state.errorMessage != null && state.items.isEmpty() -> {
                    val errorResult = ai.multica.android.core.network.ApiResult.NetworkError(
                        RuntimeException(state.errorMessage),
                    )
                    ai.multica.android.ui.components.ErrorState(
                        result = errorResult,
                        onRetry = viewModel::refresh,
                    )
                }
                state.displayed.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Filled.Inbox,
                        title = stringResource(R.string.inbox_empty),
                        description = if (state.filter == InboxFilter.Unread) {
                            "You're all caught up."
                        } else null,
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        items(state.displayed, key = { it.id }) { item ->
                            InboxRow(
                                item = item,
                                onClick = { item.issueId?.let(onOpenIssue) },
                                onMarkRead = { viewModel.markRead(item) },
                                onArchive = { viewModel.archive(item) },
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxFilterBar(
    filter: InboxFilter,
    unreadCount: Int,
    onFilterChange: (InboxFilter) -> Unit,
    onMarkAllRead: () -> Unit,
    onArchiveAllRead: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SegmentedButtons(
            options = listOf(InboxFilter.Unread, InboxFilter.All),
            selected = filter,
            onSelect = onFilterChange,
            label = { f ->
                Text(
                    when (f) {
                        InboxFilter.Unread -> "Unread ($unreadCount)"
                        InboxFilter.All -> "All"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            },
        )
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onMarkAllRead) {
            Text(stringResource(R.string.inbox_mark_all_read))
        }
        TextButton(onClick = onArchiveAllRead) {
            Text(stringResource(R.string.inbox_archive_read))
        }
    }
}

@Composable
private fun SegmentedButtons(
    options: List<InboxFilter>,
    selected: InboxFilter,
    onSelect: (InboxFilter) -> Unit,
    label: @Composable (InboxFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .clickable { onSelect(option) }
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.surface
                        else Color.Transparent,
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                ProvideTextStyle(
                    MaterialTheme.typography.labelLarge.copy(
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                ) {
                    label(option)
                }
            }
        }
    }
}

@Composable
private fun InboxRow(
    item: InboxItem,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
    onArchive: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    val severityColor = SeverityColors.forSeverity(item.severity)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Unread dot
        Box(
            modifier = Modifier
                .padding(top = 6.dp, end = 12.dp)
                .size(8.dp)
                .clip(CircleShape)
                .background(if (item.read) Color.Transparent else MaterialTheme.colorScheme.primary),
        )

        Column(modifier = Modifier.weight(1f)) {
            // Title + severity color
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(width = 3.dp, height = 14.dp)
                        .background(severityColor),
                )
                Text(
                    text = titleFor(item),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (item.body != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = relativeTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                item.issueStatus?.let { status ->
                    Spacer(Modifier.width(8.dp))
                    StatusChip(status = status)
                }
            }
        }

        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Filled.Done,
                contentDescription = "More",
            )
        }

        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            if (!item.read) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.inbox_swipe_read)) },
                    onClick = {
                        showMenu = false
                        onMarkRead()
                    },
                    leadingIcon = { Icon(Icons.Filled.Done, contentDescription = null) },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.inbox_swipe_archive)) },
                onClick = {
                    showMenu = false
                    onArchive()
                },
                leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
            )
        }
    }
}

private fun titleFor(item: InboxItem): String = when (item.type) {
    InboxItemType.ISSUE_ASSIGNED -> "Assigned: ${item.title}"
    InboxItemType.NEW_COMMENT -> "Comment on ${item.title}"
    InboxItemType.MENTIONED -> "Mentioned in ${item.title}"
    InboxItemType.STATUS_CHANGED -> "${item.title}: status changed"
    InboxItemType.ASSIGNEE_CHANGED -> "${item.title}: assignee changed"
    InboxItemType.PRIORITY_CHANGED -> "${item.title}: priority changed"
    InboxItemType.DUE_DATE_CHANGED -> "${item.title}: due date changed"
    InboxItemType.START_DATE_CHANGED -> "${item.title}: start date changed"
    InboxItemType.TASK_COMPLETED -> "${item.title}: task completed"
    InboxItemType.TASK_FAILED -> "${item.title}: task failed"
    InboxItemType.AGENT_BLOCKED -> "${item.title}: agent blocked"
    InboxItemType.AGENT_COMPLETED -> "${item.title}: agent completed"
    InboxItemType.REVIEW_REQUESTED -> "Review requested: ${item.title}"
    InboxItemType.REACTION_ADDED -> "${item.title}: reaction added"
    InboxItemType.UNASSIGNED -> "${item.title}: unassigned"
    InboxItemType.ISSUE_SUBSCRIBED -> "Subscribed to ${item.title}"
    InboxItemType.QUICK_CREATE_DONE -> "Quick create done: ${item.title}"
    InboxItemType.QUICK_CREATE_FAILED -> "Quick create failed: ${item.title}"
}

private fun relativeTime(iso: String): String = try {
    val then = Instant.parse(iso)
    val now = Clock.System.now()
    val diffMs = (now - then).inWholeMilliseconds
    when {
        diffMs < 60_000 -> "just now"
        diffMs < 3_600_000 -> "${diffMs / 60_000}m"
        diffMs < 86_400_000 -> "${diffMs / 3_600_000}h"
        diffMs < 7 * 86_400_000 -> "${diffMs / 86_400_000}d"
        else -> {
            val ldt = then.toLocalDateTime(TimeZone.currentSystemDefault())
            "${ldt.year}-${ldt.monthNumber.toString().padStart(2, '0')}-${ldt.dayOfMonth.toString().padStart(2, '0')}"
        }
    }
} catch (e: Throwable) {
    iso.take(10)
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
