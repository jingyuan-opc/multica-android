package ai.multica.android.core.di

import ai.multica.android.realtime.AppStateObserver
import ai.multica.android.realtime.RealtimeManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides lifecycle hooks for the realtime subsystem.
 *
 * Note: in v1 we don't call `appStateObserver.install()` here
 * because the Application class is the cleanest place (Hilt entry
 * point is too early for ProcessLifecycleOwner). MulticaApp calls
 * it after Hilt is ready.
 */
@Module
@InstallIn(SingletonComponent::class)
object RealtimeModule {

    @Provides
    @Singleton
    fun provideRealtimeBootstrap(
        realtimeManager: RealtimeManager,
        appStateObserver: AppStateObserver,
    ): RealtimeBootstrap = RealtimeBootstrap(realtimeManager, appStateObserver)
}

/**
 * Helper invoked by MulticaApp.onCreate to start the WS subsystem
 * and the AppState observer. We don't actually start the WS socket
 * until the user logs in — that happens in HomeScaffold.onStart.
 */
class RealtimeBootstrap(
    val realtimeManager: RealtimeManager,
    val appStateObserver: AppStateObserver,
)
