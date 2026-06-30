package ai.multica.android

import android.app.Application
import ai.multica.android.core.di.RealtimeBootstrap
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class. Hilt entry point for dependency injection.
 *
 * MulticaApp.onCreate installs the AppStateObserver (which mirrors
 * the process lifecycle to pause/resume the WS socket). The WS
 * itself is started in HomeScaffold on first composition (after
 * login) — that's when the user has a workspace slug to subscribe
 * against.
 */
@HiltAndroidApp
class MulticaApp : Application() {

    @Inject
    lateinit var realtimeBootstrap: RealtimeBootstrap

    override fun onCreate() {
        super.onCreate()
        realtimeBootstrap.appStateObserver.install()
    }
}

