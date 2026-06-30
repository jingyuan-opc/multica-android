package ai.multica.android.core.auth

import ai.multica.android.BuildConfig
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds Multica-required headers to every request:
 * - `Authorization: Bearer <jwt>` if logged in
 * - `X-Workspace-Slug: <slug>` for workspace-scoped routes
 * - `X-Client-Platform: android`
 * - `X-Client-Version: <versionName>`
 * - `X-Client-OS: android`
 *
 * Workspace slug is read from a [WorkspaceStore] singleton set after
 * the user picks a workspace.
 *
 * Auth flow for native Bearer:
 * - Server's `ValidateCSRF` only runs when `multica_auth` cookie is
 *   present (see server/internal/auth/cookie.go). We do NOT send
 *   cookies — so we don't need X-CSRF-Token either.
 * - See plan §5 for full rationale.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenStore: TokenStore,
    private val workspaceStore: WorkspaceStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()
            .header(HEADER_CLIENT_PLATFORM, BuildConfig.CLIENT_PLATFORM)
            .header(HEADER_CLIENT_VERSION, BuildConfig.VERSION_NAME)
            .header(HEADER_CLIENT_OS, BuildConfig.CLIENT_OS)

        tokenStore.getToken()?.let { token ->
            builder.header("Authorization", "Bearer $token")
        }

        workspaceStore.getSlug()?.let { slug ->
            builder.header("X-Workspace-Slug", slug)
        }

        return chain.proceed(builder.build())
    }

    companion object {
        const val HEADER_CLIENT_PLATFORM = "X-Client-Platform"
        const val HEADER_CLIENT_VERSION = "X-Client-Version"
        const val HEADER_CLIENT_OS = "X-Client-OS"
    }
}

/**
 * Holds the currently-active workspace slug (and id). Updated by
 * WorkspaceRepository when the user switches; AuthInterceptor reads
 * the slug on every request. Kept in-memory only — re-derives from
 * [ServerUrlStore]-independent flow on app launch.
 */
@Singleton
class WorkspaceStore @Inject constructor() {
    @Volatile
    private var slug: String? = null

    @Volatile
    private var id: String? = null

    fun getSlug(): String? = slug
    fun getId(): String? = id

    fun set(slug: String?, id: String?) {
        this.slug = slug
        this.id = id
    }

    fun clear() {
        slug = null
        id = null
    }
}
