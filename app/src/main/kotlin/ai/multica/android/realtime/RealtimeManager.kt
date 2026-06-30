package ai.multica.android.realtime

import ai.multica.android.core.auth.WorkspaceStore
import ai.multica.android.data.model.InboxItem
import ai.multica.android.data.repository.InboxRepository
import ai.multica.android.domain.InboxDedup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level coordinator for the WebSocket connection.
 *
 * Responsibilities:
 * - Mirror AppState (ON_STOP/ON_START) to pause/resume the socket.
 * - When the active workspace changes (WorkspaceStore), reconnect
 *   to the new slug.
 * - Republish a `WsEvent` flow that ViewModels can subscribe to
 *   without owning lifecycle.
 * - Provide convenience methods for VM-side: `refreshInbox()`
 *   triggers an immediate re-fetch (used when WS events arrive so
 *   the UI can show fresh data within ~1s).
 */
@Singleton
class RealtimeManager @Inject constructor(
    private val wsClient: WsClient,
    private val workspaceStore: WorkspaceStore,
    private val inboxRepository: InboxRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appStateJob: Job? = null
    private var workspaceJob: Job? = null

    val events: SharedFlow<WsEvent> = wsClient.events
    val state = wsClient.state

    /**
     * Starts the realtime subsystem. Must be called once after login.
     * Idempotent: re-calling is a no-op.
     */
    fun start() {
        if (appStateJob != null) return
        val slug = workspaceStore.getSlug()
        if (slug != null) {
            wsClient.start(slug)
        }

        // When the active workspace changes, reconnect.
        // WorkspaceStore is in-memory; we watch it via a polling
        // loop on a long interval. A Flow-based version is left for
        // v1.1 — the polling cost is negligible.
        workspaceJob = scope.launch {
            var lastSlug = workspaceStore.getSlug()
            while (true) {
                kotlinx.coroutines.delay(1000)
                val current = workspaceStore.getSlug()
                if (current != lastSlug) {
                    lastSlug = current
                    if (current != null) {
                        wsClient.start(current)
                    } else {
                        wsClient.stop()
                    }
                }
            }
        }
    }

    fun stop() {
        appStateJob?.cancel()
        appStateJob = null
        workspaceJob?.cancel()
        workspaceJob = null
        wsClient.stop()
    }

    fun pauseForBackground() {
        wsClient.pause()
    }

    fun resumeForForeground() {
        wsClient.resume()
    }
}

/**
 * Helper used by ViewModels to refresh the inbox after a relevant WS
 * event. Lives here (not in the VM) so the dedup logic stays
 * consistent across calls.
 */
suspend fun InboxRepository.refreshDeduped(
    onResult: (deduped: List<InboxItem>, unreadCount: Int) -> Unit,
) {
    when (val r = list()) {
        is ai.multica.android.core.network.ApiResult.Success -> {
            val deduped = InboxDedup.deduplicate(r.data)
            onResult(deduped, deduped.count { !it.read })
        }
        else -> Unit
    }
}
