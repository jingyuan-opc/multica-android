package ai.multica.android.realtime

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Helper for non-Hilt-injected callers (e.g. composables) to obtain
 * the [RealtimeManager] singleton from the application graph.
 *
 * Usage in a Composable:
 * ```kotlin
 * val ctx = LocalContext.current
 * val manager = remember { RealtimeEntryPoint.get(ctx).realtimeManager() }
 * ```
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface RealtimeEntryPoint {
    fun realtimeManager(): RealtimeManager

    companion object {
        fun get(context: Context): RealtimeEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, RealtimeEntryPoint::class.java)
    }
}
