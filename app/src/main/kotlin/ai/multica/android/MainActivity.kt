package ai.multica.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ai.multica.android.core.theme.MulticaTheme
import ai.multica.android.core.theme.ThemeMode
import ai.multica.android.ui.home.HomeScaffold
import ai.multica.android.ui.home.HomeTab
import ai.multica.android.ui.login.LoginScreen
import ai.multica.android.ui.settings.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Root(deepLinkIntent = intent)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Re-render with the new intent (handles shortcut taps while running).
        setIntent(intent)
        setContent { Root(deepLinkIntent = intent) }
    }
}

@Composable
private fun Root(
    deepLinkIntent: Intent?,
    bootstrapViewModel: BootstrapViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel(),
) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val isSystemDark = isSystemInDarkTheme()
    val isDark = when (themeMode) {
        ThemeMode.System -> isSystemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    MulticaTheme(darkTheme = isDark) {
        Surface(modifier = Modifier.fillMaxSize()) {
            AppRoot(deepLinkIntent = deepLinkIntent, bootstrapViewModel = bootstrapViewModel)
        }
    }
}

@Composable
private fun AppRoot(
    deepLinkIntent: Intent?,
    bootstrapViewModel: BootstrapViewModel,
) {
    val navController = rememberNavController()
    val isAuthenticated by bootstrapViewModel.isAuthenticated.collectAsStateWithLifecycle()
    val isReady by bootstrapViewModel.isReady.collectAsStateWithLifecycle()

    if (!isReady) return

    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated) "home" else "login",
    ) {
        composable("login") {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("login") { inclusive = true }
                    }
                    handleDeepLink(deepLinkIntent, navController)
                },
            )
        }
        composable("home") { backStackEntry ->
            // Handle initial deep link after auth + nav settle.
            androidx.compose.runtime.LaunchedEffect(Unit) {
                handleDeepLink(deepLinkIntent, navController)
            }
            HomeScaffold(
                navController = navController,
                onLogout = {
                    bootstrapViewModel.logout()
                    navController.navigate("login") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onOpenIssue = { issueId -> navController.navigate("issue/$issueId") },
                onOpenProject = { projectId -> navController.navigate("project/$projectId") },
            )
        }
        composable("issue/{id}") { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            ai.multica.android.ui.issues.IssueDetailScreen(onBack = { navController.popBackStack() })
        }
        composable(
            "project/{id}",
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            ai.multica.android.ui.projects.ProjectDetailScreen(
                projectId = id,
                onBack = { navController.popBackStack() },
                onOpenIssue = { issueId -> navController.navigate("issue/$issueId") },
            )
        }
        composable("create-issue") {
            ai.multica.android.ui.issues.CreateIssueScreen(
                onBack = { navController.popBackStack() },
                onCreated = { issueId ->
                    // Pop the create screen, then navigate to the new issue.
                    navController.popBackStack()
                    navController.navigate("issue/$issueId")
                },
            )
        }
        composable("create-project") {
            ai.multica.android.ui.projects.CreateProjectScreen(
                onBack = { navController.popBackStack() },
                onCreated = { projectId ->
                    navController.popBackStack()
                    navController.navigate("project/$projectId")
                },
            )
        }
        // --- New feature routes (drawer + detail destinations) ---
        composable("agent/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("id") ?: return@composable
            ai.multica.android.ui.agents.AgentDetailScreen(onBack = { navController.popBackStack() })
        }
        composable("create-agent") {
            ai.multica.android.ui.agents.CreateAgentScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id -> navController.popBackStack(); navController.navigate("agent/$id") },
            )
        }
        composable("squad/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ai.multica.android.ui.squads.SquadDetailScreen(onBack = { navController.popBackStack() })
        }
        composable("member/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ai.multica.android.ui.members.MemberDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenIssue = { issueId -> navController.navigate("issue/$issueId") },
            )
        }
        composable("autopilot/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ai.multica.android.ui.autopilots.AutopilotDetailScreen(onBack = { navController.popBackStack() })
        }
        composable("autopilots") {
            ai.multica.android.ui.autopilots.AutopilotsScreen(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                onOpenAutopilot = { id -> navController.navigate("autopilot/$id") },
                onCreateAutopilot = { navController.navigate("create-autopilot") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("create-autopilot") {
            ai.multica.android.ui.autopilots.CreateAutopilotScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.popBackStack()
                    navController.navigate("autopilot/$id")
                },
            )
        }
        composable("squads") {
            ai.multica.android.ui.squads.SquadsScreen(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                onOpenSquad = { id -> navController.navigate("squad/$id") },
                onBack = { navController.popBackStack() },
                onCreateSquad = { navController.navigate("create-squad") },
            )
        }
        composable("create-squad") {
            ai.multica.android.ui.squads.CreateSquadScreen(
                onBack = { navController.popBackStack() },
                onCreated = { id -> navController.popBackStack(); navController.navigate("squad/$id") },
            )
        }
        composable("members") {
            ai.multica.android.ui.members.MembersScreen(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                onOpenMember = { id -> navController.navigate("member/$id") },
                onBack = { navController.popBackStack() },
            )
        }
        composable("labels") {
            ai.multica.android.ui.labels.LabelsScreen(onBack = { navController.popBackStack() })
        }
        composable("my-issues") {
            ai.multica.android.ui.myissues.MyIssuesScreen(
                onBack = { navController.popBackStack() },
                onOpenIssue = { issueId -> navController.navigate("issue/$issueId") },
            )
        }
        composable("search") {
            ai.multica.android.ui.search.GlobalSearchScreen(
                onBack = { navController.popBackStack() },
                onOpenIssue = { issueId -> navController.navigate("issue/$issueId") },
                onOpenProject = { projectId -> navController.navigate("project/$projectId") },
            )
        }
    }
}

private fun handleDeepLink(intent: Intent?, navController: androidx.navigation.NavHostController) {
    val data: Uri = intent?.data ?: return
    val pathSegments = data.pathSegments
    when {
        data.scheme == "multica" && data.host == "inbox" -> {
            // Already on home; ensure the inbox tab is selected.
        }
        data.scheme == "multica" && data.host == "issues" -> {
            // Same.
        }
        data.scheme == "multica" && data.host == "projects" -> {
            // Same.
        }
        data.scheme == "multica" && data.host == "issue" && pathSegments.isNotEmpty() -> {
            navController.navigate("issue/${pathSegments[0]}")
        }
        data.scheme == "multica" && data.host == "project" && pathSegments.isNotEmpty() -> {
            navController.navigate("project/${pathSegments[0]}")
        }
        (data.scheme == "https" || data.scheme == "http") && data.host == "multica.ai" -> {
            // e.g. https://multica.ai/issue/123
            if (pathSegments.size >= 2 && pathSegments[0] == "issue") {
                navController.navigate("issue/${pathSegments[1]}")
            } else if (pathSegments.size >= 2 && pathSegments[0] == "project") {
                navController.navigate("project/${pathSegments[1]}")
            }
        }
    }
}
