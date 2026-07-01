package ai.multica.android.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ai.multica.android.R
import ai.multica.android.data.model.PinnedItemType
import ai.multica.android.realtime.RealtimeEntryPoint
import ai.multica.android.ui.agents.AgentsScreen
import ai.multica.android.ui.inbox.InboxScreen
import ai.multica.android.ui.issues.IssuesTabScreen
import ai.multica.android.ui.projects.ProjectsScreen
import ai.multica.android.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

/**
 * The 4 primary bottom-nav tabs. Drawer-only destinations
 * (My Issues, Squads, Members, Autopilots, Labels) are reached via the
 * left drawer (see [AppDrawer]).
 */
enum class HomeTab(val label: String, val icon: ImageVector) {
    Inbox("Inbox", Icons.Filled.Inbox),
    Issues("Issues", Icons.Filled.BugReport),
    Projects("Projects", Icons.Filled.Folder),
    Agents("Agents", Icons.Filled.SmartToy),
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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Start the realtime subsystem on first composition (after login).
    val context = androidx.compose.ui.platform.LocalContext.current
    val realtimeManager: ai.multica.android.realtime.RealtimeManager = remember { RealtimeEntryPoint.get(context).realtimeManager() }
    LaunchedEffect(Unit) { realtimeManager.start() }

    val scope = androidx.compose.runtime.rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppDrawer(
                homeState = homeState,
                onNavigate = { route ->
                    scope.launch { drawerState.close() }
                    navController.navigate(route) { launchSingleTop = true }
                },
                onSelectWorkspace = { ws ->
                    homeViewModel.selectWorkspace(ws)
                    scope.launch { drawerState.close() }
                },
                onOpenSettings = { showSettings = true; scope.launch { drawerState.close() } },
            )
        },
    ) {
        Scaffold(
            topBar = {
                HomeTopBar(
                    viewModel = homeViewModel,
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = { navController.navigate("search") },
                    onSettingsClick = { showSettings = true },
                )
            },
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
                val activeWs = homeState.activeWorkspace
                if (activeWs == null) {
                    WorkspaceSplash(
                        isLoading = homeState.isLoading,
                        errorMessage = homeState.errorMessage,
                        onRetry = homeViewModel::refresh,
                        modifier = Modifier.fillMaxSize().padding(padding),
                    )
                } else {
                    // Per-tab visit counters. Because Hilt VMs survive tab
                    // switches, `forceRefreshOnAppear = (selectedTab == X)` is
                    // a constant true inside each branch — so the screens'
                    // `LaunchedEffect(forceRefreshOnAppear)` never re-fires on
                    // re-entry. Instead we hand each tab a monotonically
                    // increasing token that bumps every time the user lands on
                    // it; the screens key their refresh LaunchedEffect off it.
                    var issuesVisits by remember { mutableIntStateOf(0) }
                    var projectsVisits by remember { mutableIntStateOf(0) }
                    var agentsVisits by remember { mutableIntStateOf(0) }
                    LaunchedEffect(selectedTab) {
                        when (selectedTab) {
                            HomeTab.Inbox -> Unit
                            HomeTab.Issues -> issuesVisits++
                            HomeTab.Projects -> projectsVisits++
                            HomeTab.Agents -> agentsVisits++
                        }
                    }
                    when (selectedTab) {
                        HomeTab.Inbox -> InboxScreen(contentPadding = padding, onOpenIssue = onOpenIssue)
                        HomeTab.Issues -> IssuesTabScreen(
                            contentPadding = padding,
                            onOpenIssue = onOpenIssue,
                            onCreateIssue = { navController.navigate("create-issue") },
                            refreshTrigger = issuesVisits,
                        )
                        HomeTab.Projects -> ProjectsScreen(
                            contentPadding = padding,
                            onOpenProject = onOpenProject,
                            onCreateProject = { navController.navigate("create-project") },
                            refreshTrigger = projectsVisits,
                        )
                        HomeTab.Agents -> AgentsScreen(
                            contentPadding = padding,
                            onOpenAgent = { navController.navigate("agent/$it") },
                            onCreateAgent = { navController.navigate("create-agent") },
                            refreshTrigger = agentsVisits,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppDrawer(
    homeState: HomeUiState,
    onNavigate: (String) -> Unit,
    onSelectWorkspace: (ai.multica.android.data.model.Workspace) -> Unit,
    onOpenSettings: () -> Unit,
) {
    ModalDrawerSheet {
        // Workspace switcher section.
        Text(
            text = stringResource(R.string.drawer_workspaces),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(16.dp),
        )
        homeState.workspaces.forEach { ws ->
            NavigationDrawerItem(
                label = { Text(ws.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                selected = ws.id == homeState.activeWorkspace?.id,
                onClick = { onSelectWorkspace(ws) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Navigation entries — grouped to mirror the web sidebar.
        // Web groups: Personal (inbox, myIssues) | Workspace (issues, projects,
        // autopilots, agents, squads, usage) | Configure (settings). Inbox/
        // issues/projects/agents live in the bottom tab bar here, so the drawer
        // surfaces the remaining destinations under the same conceptual groups.
        DrawerSectionTitle(text = stringResource(R.string.drawer_section_personal))
        DrawerEntry(icon = Icons.AutoMirrored.Filled.List, label = stringResource(R.string.drawer_my_issues)) { onNavigate("my-issues") }

        DrawerSectionTitle(text = stringResource(R.string.drawer_section_workspace))
        DrawerEntry(icon = Icons.Filled.Bolt, label = stringResource(R.string.drawer_autopilots)) { onNavigate("autopilots") }
        DrawerEntry(icon = Icons.Filled.Group, label = stringResource(R.string.drawer_squads)) { onNavigate("squads") }
        DrawerEntry(icon = Icons.Filled.Person, label = stringResource(R.string.drawer_members)) { onNavigate("members") }
        DrawerEntry(icon = Icons.AutoMirrored.Filled.Label, label = stringResource(R.string.drawer_labels)) { onNavigate("labels") }

        // Pinned items (if any).
        val pins = homeState.pins
        if (!pins.isNullOrEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            DrawerSectionTitle(text = stringResource(R.string.drawer_pinned))
            pins.forEach { pin ->
                val (icon, label, route) = when (pin.itemType) {
                    PinnedItemType.ISSUE -> Triple(Icons.Filled.BugReport, "Issue", "issue/${pin.itemId}")
                    PinnedItemType.PROJECT -> Triple(Icons.Filled.Folder, "Project", "project/${pin.itemId}")
                }
                DrawerEntry(icon = icon, label = label) { onNavigate(route) }
            }
        }
        Spacer(Modifier.weight(1f))
        DrawerEntry(icon = Icons.Filled.Menu, label = stringResource(R.string.settings_title), onClick = onOpenSettings)
    }
}

@Composable
private fun DrawerEntry(icon: ImageVector, label: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}

/** Small uppercase section label inside the drawer, mirroring the web sidebar groupings. */
@Composable
private fun DrawerSectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 4.dp),
        letterSpacing = androidx.compose.ui.unit.TextUnit(1.2f, androidx.compose.ui.unit.TextUnitType.Sp),
    )
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
                    Text(stringResource(R.string.common_error), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.size(8.dp))
                    Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(16.dp))
                    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
                }
            }
            isLoading -> {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.size(12.dp))
                    Text("Loading workspaces…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            else -> Text("No workspace available", style = MaterialTheme.typography.bodyMedium)
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
                icon = {
                    BadgedBox(badge = {
                        if (tab == HomeTab.Inbox && homeState.totalUnread > 0) {
                            Badge { Text(homeState.totalUnread.toString()) }
                        }
                    }) {
                        Icon(tab.icon, contentDescription = tab.label)
                    }
                },
                label = { Text(tab.label) },
            )
        }
    }
}
