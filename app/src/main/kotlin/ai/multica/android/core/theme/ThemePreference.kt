package ai.multica.android.core.theme

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeStore by preferencesDataStore(name = "theme_store")

/**
 * Persists the user's theme choice across app launches. Mirrors
 * the way `apps/mobile/data/theme.ts` (and the iOS counterpart)
 * persist theme via the same `theme-preference` key — except we
 * use DataStore instead of expo-secure-store.
 *
 * Three values: `system` (default), `light`, `dark`. Unknown values
 * fall back to `system` for safe defaults.
 */
@Singleton
class ThemePreference @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val themeFlow: Flow<ThemeMode> = context.themeStore.data
        .map { prefs ->
            when (prefs[KEY_THEME]) {
                "light" -> ThemeMode.Light
                "dark" -> ThemeMode.Dark
                else -> ThemeMode.System
            }
        }

    suspend fun set(mode: ThemeMode) {
        context.themeStore.edit { prefs ->
            prefs[KEY_THEME] = when (mode) {
                ThemeMode.Light -> "light"
                ThemeMode.Dark -> "dark"
                ThemeMode.System -> "system"
            }
        }
    }

    private companion object {
        val KEY_THEME = stringPreferencesKey("theme_preference")
    }
}

enum class ThemeMode {
    System, Light, Dark;

    fun label(): String = when (this) {
        System -> "Follow system"
        Light -> "Light"
        Dark -> "Dark"
    }
}
