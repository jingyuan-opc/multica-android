package ai.multica.android.core.auth

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Singleton event bus for "active workspace changed" notifications.
 *
 * Tab ViewModels (Inbox, Projects, Issues) collect this flow and
 * call [refresh][ai.multica.android.data.repository.InboxRepository.refresh]
 * when the user switches workspaces via the top-bar switcher. This
 * keeps each tab reactive without coupling them to HomeViewModel.
 *
 * Singleton-scoped so emissions and collectors survive tab switches
 * (and even HomeViewModel re-creation on configuration change).
 */
@Singleton
class WorkspaceEvents @Inject constructor() {
    // Replay = 0: a tab that subscribes AFTER a switch shouldn't get
    // the stale event. It can read the current slug from [WorkspaceStore]
    // directly and skip the initial event.
    private val _changes = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changes: SharedFlow<String> = _changes.asSharedFlow()

    fun emit(workspaceId: String) {
        _changes.tryEmit(workspaceId)
    }
}
