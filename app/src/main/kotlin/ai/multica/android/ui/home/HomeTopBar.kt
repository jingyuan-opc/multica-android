package ai.multica.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.multica.android.data.model.Workspace

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(
    viewModel: HomeViewModel = hiltViewModel(),
    onSettingsClick: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            WorkspaceSwitcherButton(
                workspace = state.activeWorkspace,
                isLoading = state.isLoading,
                onClick = { menuOpen = true },
            )
        },
        actions = {
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        },
    )

    WorkspaceSwitcherMenu(
        expanded = menuOpen,
        workspaces = state.workspaces,
        activeId = state.activeWorkspace?.id,
        onDismiss = { menuOpen = false },
        onSelect = { ws ->
            viewModel.selectWorkspace(ws)
            menuOpen = false
        },
        hasOtherUnread = state.hasOtherWorkspaceUnread,
    )
}

@Composable
private fun WorkspaceSwitcherButton(
    workspace: Workspace?,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
    ) {
        when {
            isLoading && workspace == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text("Loading…")
            }
            workspace == null -> Text("No workspace")
            else -> {
                Text(
                    text = workspace.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Filled.ArrowDropDown, contentDescription = "Switch workspace")
            }
        }
    }
}

@Composable
private fun WorkspaceSwitcherMenu(
    expanded: Boolean,
    workspaces: List<Workspace>,
    activeId: String?,
    onDismiss: () -> Unit,
    onSelect: (Workspace) -> Unit,
    hasOtherUnread: Boolean,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        if (workspaces.isEmpty()) {
            DropdownMenuItem(
                text = { Text("No workspaces") },
                onClick = onDismiss,
            )
        }
        workspaces.forEachIndexed { index, ws ->
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ws.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (ws.id == activeId) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                        if (ws.id == activeId) {
                            Text(
                                text = "✓",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                },
                onClick = { onSelect(ws) },
            )
            if (index < workspaces.lastIndex) {
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun UnreadDot(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.error) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
