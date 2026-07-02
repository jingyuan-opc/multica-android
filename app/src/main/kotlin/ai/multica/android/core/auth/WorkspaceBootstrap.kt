package ai.multica.android.core.auth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restores the last-active workspace into [WorkspaceStore] on app launch,
 * independent of any screen or ViewModel.
 *
 * ## Why this exists
 * The old flow restored the workspace only inside `HomeViewModel.init`.
 * That works when the app launches into Home, but when the OS kills the
 * process in the background and the user returns directly to a detail
 * screen (e.g. IssueDetail), HomeViewModel is never created, WorkspaceStore
 * stays empty, and every API call 400s with
 * "workspace_id or workspace_slug is required".
 *
 * ## How it works
 * [MulticaApp.onCreate] calls [start] once. It reads the persisted slug/id
 * from DataStore and pushes them into WorkspaceStore. VMs that issue
 * workspace-scoped requests on init can `await()` [restored] to avoid
 * racing the restore.
 */
@Singleton
class WorkspaceBootstrap @Inject constructor(
    private val workspacePreference: WorkspacePreference,
    private val workspaceStore: WorkspaceStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Completes once the persisted workspace has been loaded into WorkspaceStore. */
    val restored = CompletableDeferred<Unit>()

    /**
     * Kick off the restore. Safe to call multiple times — the second call
     * is a no-op because [restored] is already completed.
     */
    fun start() {
        if (restored.isCompleted) return
        scope.launch {
            val (slug, id) = workspacePreference.getLastActive()
            if (slug != null) {
                workspaceStore.set(slug = slug, id = id)
            }
            restored.complete(Unit)
        }
    }
}
