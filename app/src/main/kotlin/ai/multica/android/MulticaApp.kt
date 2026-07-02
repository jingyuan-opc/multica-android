package ai.multica.android

import android.app.Application
import ai.multica.android.core.auth.WorkspaceBootstrap
import ai.multica.android.core.di.RealtimeBootstrap
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class. Hilt entry point for dependency injection.
 *
 * MulticaApp.onCreate:
 * 1. Restores the last-active workspace into WorkspaceStore (so API calls
 *    have the X-Workspace-Slug header even when the process is recreated
 *    by the OS and the user lands directly on a detail screen).
 * 2. Installs the AppStateObserver (mirrors process lifecycle to the WS).
 */
@HiltAndroidApp
class MulticaApp : Application() {

    @Inject
    lateinit var realtimeBootstrap: RealtimeBootstrap

    @Inject
    lateinit var workspaceBootstrap: WorkspaceBootstrap

    override fun onCreate() {
        super.onCreate()
        workspaceBootstrap.start()
        realtimeBootstrap.appStateObserver.install()
    }
}

