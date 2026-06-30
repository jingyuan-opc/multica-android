package ai.multica.android.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.multica.android.R
import ai.multica.android.realtime.RealtimeEntryPoint
import ai.multica.android.realtime.RealtimeManager
import ai.multica.android.ui.inbox.InboxScreen
import ai.multica.android.ui.issues.IssuesTabScreen
import ai.multica.android.ui.projects.ProjectsScreen
import ai.multica.android.ui.settings.SettingsScreen

enum class HomeTab(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Filled.Inbox),
    Projects("Projects", Icons.Filled.Folder),
    Issues("Issues", Icons.Filled.BugReport),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScaffold(
    navController: androidx.navigation.NavHostController,
    onLogout: () -> Unit,
    onOpenIssue: (String) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val homeViewModel: HomeViewModel = hiltViewModel()
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Inbox) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    // Start the realtime subsystem on first composition (after login).
    val context = androidx.compose.ui.platform.LocalContext.current
    val realtimeManager: RealtimeManager = remember { RealtimeEntryPoint.get(context).realtimeManager() }
    LaunchedEffect(Unit) { realtimeManager.start() }

    Scaffold(
        topBar = { HomeTopBar(viewModel = homeViewModel, onSettingsClick = { showSettings = true }) },
        bottomBar = { HomeBottomBar(selectedTab, homeState, homeViewModel) { selectedTab = it } },
    ) { padding ->
        if (showSettings) {
            SettingsScreen(
                onClose = { showSettings = false },
                onLogout = {
                    showSettings = false
                    homeViewModel.clearActiveWorkspace()
                    onLogout()
                },
                contentPadding = padding,
            )
        } else {
            // CRITICAL: only render tabs (and thereby their ViewModels) once
            // an active workspace is selected. Otherwise InboxViewModel/
            // ProjectsViewModel/IssuesViewModel all `init { refresh() }` fire
            // requests with no `X-Workspace-Slug` header — the server then
            // returns "workspace_id or workspace_slug is required".
            val activeWs = homeState.activeWorkspace
            if (activeWs == null) {
                WorkspaceSplash(
                    isLoading = homeState.isLoading,
                    errorMessage = homeState.errorMessage,
                    onRetry = homeViewModel::refresh,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                when (selectedTab) {
                    HomeTab.Inbox -> InboxScreen(
                        contentPadding = padding,
                        onOpenIssue = onOpenIssue,
                    )
                HomeTab.Projects -> ProjectsScreen(
                    contentPadding = padding,
                    onOpenProject = onOpenProject,
                    onCreateProject = { navController.navigate("create-project") },
                    forceRefreshOnAppear = selectedTab == HomeTab.Projects,
                )
                HomeTab.Issues -> IssuesTabScreen(
                    contentPadding = padding,
                    onOpenIssue = onOpenIssue,
                    onCreateIssue = { navController.navigate("create-issue") },
                    forceRefreshOnAppear = selectedTab == HomeTab.Issues,
                )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceSplash(
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            errorMessage != null -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.common_error),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(16.dp))
                    OutlinedButton(onClick = onRetry) {
                        Text(stringResource(R.string.common_retry))
                    }
                }
            }
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(12.dp))
                    Text(
                        text = "Loading workspaces…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                Text(
                    text = "No workspace available",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun HomeBottomBar(
    selected: HomeTab,
    homeState: HomeUiState,
    homeViewModel: HomeViewModel,
    onSelect: (HomeTab) -> Unit,
) {
    NavigationBar {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = selected == tab,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}

