package ai.multica.android.core.di

import ai.multica.android.core.auth.AuthInterceptor
import ai.multica.android.core.auth.ServerUrlStore
import ai.multica.android.core.network.MulticaApi
import ai.multica.android.core.network.NetworkFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Wires up the network stack.
 *
 * - [MulticaApi] is a singleton bound to whatever the current active
 *   server URL is. When the user switches servers, they must re-login
 *   (per plan §5) — we don't hot-swap the Retrofit instance for v1.
 * - [OkHttpClient] is also a singleton (reuses connection pool).
 * - [AuthInterceptor] is bound separately so the same client is used
 *   for the WebSocket and any future HTTP-only deps.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient =
        NetworkFactory.buildOkHttpClient(authInterceptor)

    @Provides
    @Singleton
    fun provideMulticaApi(
        serverUrlStore: ServerUrlStore,
        okHttpClient: OkHttpClient,
    ): MulticaApi {
        // Read active URL synchronously at injection time. ServerUrlStore
        // uses DataStore which is async, so we block briefly on first
        // construction; subsequent reads from AuthInterceptor are sync.
        val baseUrl = runBlocking { serverUrlStore.getActiveUrl() }
        return NetworkFactory.buildRetrofit(baseUrl, okHttpClient)
            .create(MulticaApi::class.java)
    }
}
