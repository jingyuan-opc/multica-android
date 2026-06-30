package ai.multica.android.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the Multica auth JWT in EncryptedSharedPreferences.
 *
 * The server returns the token in the JSON body of /auth/verify-code
 * and /auth/google. Cookies are also set on the response, but as a
 * native client we don't read them — Bearer auth is simpler and
 * sidesteps the CSRF check entirely.
 *
 * ## Why the in-memory cache matters (the OOM trap)
 *
 * `EncryptedSharedPreferences` under the hood uses Tink's
 * `DeterministicAeadKeyManager` (AesSiv), and every `getString()` call
 * triggers a Tink decrypt — which allocates a small amount of
 * **native** memory per call. On heavy traffic (e.g. WebSocket
 * reconnect storming at one per second), these allocations
 * accumulate on the native heap faster than the GC can reclaim
 * them, and the process dies with `OutOfMemoryError` in
 * `com.google.crypto.tink.subtle.EngineFactory.getInstance`.
 *
 * We solve this by caching the token in a `@Volatile` field after the
 * first read. Writes still go through both layers (encrypted + cache),
 * reads only hit memory.
 */
@Singleton
class TokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var cacheLoaded: Boolean = false

    private val _tokenState = MutableStateFlow<String?>(loadToken())
    val tokenState: StateFlow<String?> = _tokenState.asStateFlow()

    /**
     * Read the JWT. Reads from in-memory cache after the first call —
     * never touches [EncryptedSharedPreferences] again until [setToken]
     * or [clear] is invoked.
     */
    fun getToken(): String? {
        if (!cacheLoaded) {
            cachedToken = prefs.getString(KEY_TOKEN, null)
            cacheLoaded = true
        }
        return cachedToken
    }

    fun setToken(token: String) {
        cachedToken = token
        cacheLoaded = true
        prefs.edit().putString(KEY_TOKEN, token).apply()
        _tokenState.value = token
    }

    fun clear() {
        cachedToken = null
        cacheLoaded = true
        prefs.edit().remove(KEY_TOKEN).apply()
        _tokenState.value = null
    }

    private fun loadToken(): String? {
        cachedToken = prefs.getString(KEY_TOKEN, null)
        cacheLoaded = true
        return cachedToken
    }

    companion object {
        private const val FILE_NAME = "auth_prefs"
        private const val KEY_TOKEN = "auth_token"
    }
}
