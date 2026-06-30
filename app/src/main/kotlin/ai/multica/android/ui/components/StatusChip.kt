package ai.multica.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.multica.android.data.model.IssueStatus
import ai.multica.android.data.model.ProjectStatus
import ai.multica.android.core.theme.StatusColors

/**
 * A compact pill that displays an Issue.status or Project.status.
 * Color is bound to the [StatusColors] palette which mirrors
 * the web app's `STATUS_CONFIG.iconColor`.
 */
@Composable
fun StatusChip(
    status: IssueStatus,
    modifier: Modifier = Modifier,
) {
    val color = StatusColors.forStatus(status)
    Pill(label = labelFor(status), color = color, modifier = modifier)
}

@Composable
fun ProjectStatusChip(
    status: ProjectStatus,
    modifier: Modifier = Modifier,
) {
    val color = when (status) {
        ProjectStatus.PLANNED -> MaterialTheme.colorScheme.onSurfaceVariant
        ProjectStatus.IN_PROGRESS -> StatusColors.inProgress
        ProjectStatus.PAUSED -> StatusColors.blocked
        ProjectStatus.COMPLETED -> StatusColors.done
        ProjectStatus.CANCELLED -> StatusColors.cancelled
    }
    Pill(label = labelFor(status), color = color, modifier = modifier)
}

@Composable
private fun Pill(label: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.12f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

internal fun labelFor(status: IssueStatus): String = when (status) {
    IssueStatus.BACKLOG -> "Backlog"
    IssueStatus.TODO -> "Todo"
    IssueStatus.IN_PROGRESS -> "In progress"
    IssueStatus.IN_REVIEW -> "In review"
    IssueStatus.DONE -> "Done"
    IssueStatus.BLOCKED -> "Blocked"
    IssueStatus.CANCELLED -> "Cancelled"
}

internal fun labelFor(status: ProjectStatus): String = when (status) {
    ProjectStatus.PLANNED -> "Planned"
    ProjectStatus.IN_PROGRESS -> "In progress"
    ProjectStatus.PAUSED -> "Paused"
    ProjectStatus.COMPLETED -> "Completed"
    ProjectStatus.CANCELLED -> "Cancelled"
}
