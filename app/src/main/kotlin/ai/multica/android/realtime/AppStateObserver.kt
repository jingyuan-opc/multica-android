package ai.multica.android.realtime

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens to the PROCESS lifecycle (entire app, not individual
 * Activities) and pauses/resumes the WS connection on
 * `ON_STOP` / `ON_START`.
 *
 * OkHttp WebSocket pings every 54s — the OS can keep the socket
 * alive briefly while the app is backgrounded, but if it stays
 * paused too long the connection will go stale. Pausing explicitly
 * saves battery and avoids zombie sockets.
 */
@Singleton
class AppStateObserver @Inject constructor(
    private val realtimeManager: RealtimeManager,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    fun install() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch { realtimeManager.resumeForForeground() }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch { realtimeManager.pauseForBackground() }
    }
}
