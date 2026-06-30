package ai.multica.android.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.serverStore by preferencesDataStore(name = "server_store")

/**
 * Stores the current backend base URL and a list of recently-used
 * server URLs (so users can quickly switch between Multica Cloud and
 * their self-hosted instance).
 *
 * NOT sensitive — the URLs alone don't grant access, the JWT does —
 * so we use DataStore Preferences (not EncryptedSharedPreferences).
 *
 * The active URL is read synchronously by AuthInterceptor on every
 * request; the list of saved URLs is read on demand in Settings.
 */
@Singleton
class ServerUrlStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val activeUrl: Flow<String> = context.serverStore.data
        .map { it[KEY_ACTIVE_URL] ?: defaultUrl() }

    val activeUrlFlow: Flow<String> = activeUrl

    suspend fun getActiveUrl(): String =
        context.serverStore.data.first()[KEY_ACTIVE_URL] ?: defaultUrl()

    suspend fun setActiveUrl(url: String) {
        val normalized = normalize(url)
        context.serverStore.edit { prefs ->
            prefs[KEY_ACTIVE_URL] = normalized
            val saved = prefs[KEY_SAVED_URLS]?.toMutableSet() ?: mutableSetOf()
            saved.add(normalized)
            prefs[KEY_SAVED_URLS] = saved.toList().takeLast(MAX_SAVED).toSet()
        }
    }

    suspend fun getSavedUrls(): List<String> =
        context.serverStore.data.first()[KEY_SAVED_URLS]?.toList() ?: listOf(defaultUrl())

    suspend fun replaceSaved(urls: List<String>) {
        context.serverStore.edit { prefs ->
            prefs[KEY_SAVED_URLS] = urls.take(MAX_SAVED).toSet()
        }
    }

    suspend fun removeUrl(url: String) {
        context.serverStore.edit { prefs ->
            val current = prefs[KEY_SAVED_URLS]?.toMutableSet() ?: mutableSetOf()
            current.remove(url)
            prefs[KEY_SAVED_URLS] = current
            if (prefs[KEY_ACTIVE_URL] == url) {
                prefs[KEY_ACTIVE_URL] = current.firstOrNull() ?: defaultUrl()
            }
        }
    }

    /**
     * Public, pure helper exposed for callers (e.g. Settings
     * "add server" dialog) that need to canonicalize a URL without
     * writing it.
     */
    fun normalizePublic(url: String): String = normalize(url)

    private fun defaultUrl(): String = DEFAULT_SERVER_URL

    private fun normalize(url: String): String {
        var u = url.trim()
        if (u.isEmpty()) return defaultUrl()
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://$u"
        }
        return u.trimEnd('/')
    }

    companion object {
        /**
         * Default Multica Cloud URL, set at build time from local.properties
         * (or `https://multica.ai` if not overridden).
         */
        val DEFAULT_SERVER_URL: String = ai.multica.android.BuildConfig.DEFAULT_SERVER_URL

        private const val MAX_SAVED = 10

        private val KEY_ACTIVE_URL = stringPreferencesKey("active_url")
        private val KEY_SAVED_URLS = stringSetPreferencesKey("saved_urls")
    }
}
